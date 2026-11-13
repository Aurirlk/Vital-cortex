package cn.kmbeast.core.agent;

import cn.kmbeast.core.graph.GraphNode;
import cn.kmbeast.core.graph.GraphSchema;
import cn.kmbeast.core.graph.MedicalVocabulary;
import cn.kmbeast.crm.agent.IntentRouter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link IntentResolver} 统一意图入口的单元测试（P2-2 / T10）。
 *
 * <p><b>决策 D2：规则先行 + LLM 兜底</b>。本测试锁死四件事：
 * <ol>
 *   <li>规则能判定时<b>绝不调用 LLM</b>（省调用、低延迟、结果稳定）；</li>
 *   <li>规则判为 CHAT/LOW 时才问 LLM，且 LLM 命中白名单 → {@code Source.LLM}；</li>
 *   <li>LLM 输出不在 6 标签白名单内 → 一律 {@code Source.FALLBACK} + CHAT；</li>
 *   <li>LLM 抛异常 / 未注入 → 绝不向上抛，回落 CHAT。</li>
 * </ol>
 *
 * <p><b>反向验证（改坏代码必须变红）</b>：
 * <ul>
 *   <li>把 {@code IntentRouter.route} 里「药品 + 药品关系词 → DRUG」两个分支删掉（还原成旧版
 *       「先判 RELATION 再判 DRUG」）→ {@code drugRelationWordsWinOverGraph} 会由 DRUG 变 GRAPH 而失败。</li>
 *   <li>把 {@code IntentResolver.resolve} 里 {@code llmClient != null} 的守卫去掉，
 *       或让 {@code mapLabel} 接受任意标签 → 白名单/降级用例会失败。</li>
 *   <li>把 {@code ai-prompts/intent-classification.txt} 任意标签改名 →
 *       {@code labelsMatchPromptResource} 会失败（防止 Java 白名单与提示词漂移）。</li>
 * </ul>
 */
@DisplayName("IntentResolver 意图识别统一入口")
class IntentResolverTest {

    // ==================== 桩件 ====================

    /** 最小桩词表：认「高血压 / 糖尿病」两个病，认「布洛芬」为药品 */
    private static class StubVocabulary extends MedicalVocabulary {
        @Override
        public List<GraphNode> matchIn(String text) {
            if (text == null) {
                return List.of();
            }
            if (text.contains("布洛芬")) {
                return List.of(new GraphNode("布洛芬", GraphSchema.Label.DRUG, null, List.of(), null, null));
            }
            if (text.contains("高血压")) {
                return List.of(new GraphNode("高血压", GraphSchema.Label.DISEASE, null, List.of(), null, null));
            }
            if (text.contains("糖尿病")) {
                return List.of(new GraphNode("糖尿病", GraphSchema.Label.DISEASE, null, List.of(), null, null));
            }
            return List.of();
        }

        @Override
        public List<String> matchAliases(String text) {
            return List.of();
        }

        @Override
        public java.util.Set<String> drugNames() {
            return java.util.Set.of("布洛芬");
        }
    }

    /** 可计数的 LLM 桩：返回预设标签（或抛异常），并记录被调用次数 */
    private static class StubLlm implements IntentLlmClient {
        private final String reply;
        private final boolean boom;
        final AtomicInteger calls = new AtomicInteger();

        StubLlm(String reply) {
            this(reply, false);
        }

        StubLlm(String reply, boolean boom) {
            this.reply = reply;
            this.boom = boom;
        }

        @Override
        public String classify(String userQuery) throws Exception {
            calls.incrementAndGet();
            if (boom) {
                throw new IllegalStateException("LLM 挂了");
            }
            return reply;
        }
    }

    // ==================== 装配 ====================

    private static IntentRouter newRouter() {
        IntentRouter router = new IntentRouter();
        inject(router, "vocabulary", new StubVocabulary());
        return router;
    }

    private static IntentResolver newResolver(IntentRouter router, IntentLlmClient llm) {
        IntentResolver resolver = new IntentResolver();
        inject(resolver, "router", router);
        inject(resolver, "llmClient", llm);
        return resolver;
    }

    private static void inject(Object target, String field, Object value) {
        try {
            Field f = target.getClass().getDeclaredField(field);
            f.setAccessible(true);
            f.set(target, value);
        } catch (Exception e) {
            throw new AssertionError("反射注入失败: " + field, e);
        }
    }

    // ==================== 用例 ====================

    @Test
    @DisplayName("规则命中时直接返回 RULE，且不浪费一次 LLM 调用")
    void ruleWinsWithoutCallingLlm() {
        StubLlm llm = new StubLlm("《健康科普》");
        IntentResolver resolver = newResolver(newRouter(), llm);

        Resolution r = resolver.resolve("我的血压最近怎么样");

        assertEquals(IntentRouter.Intent.HEALTH_RECORDS, r.intent());
        assertSame(Resolution.Source.RULE, r.source());
        assertNull(r.llmRaw());
        assertEquals(0, llm.calls.get(), "规则已判定，不应再调 LLM");
    }

    @Test
    @DisplayName("药品关系词优先级高于关系词：「布洛芬胶囊的副作用」应判 DRUG 而非 GRAPH")
    void drugRelationWordsWinOverGraph() {
        StubLlm llm = new StubLlm("《医学关系查询》");
        IntentResolver resolver = newResolver(newRouter(), llm);

        Resolution r = resolver.resolve("布洛芬胶囊的副作用有哪些");

        assertEquals(IntentRouter.Intent.DRUG, r.intent(),
                "「副作用」既在关系词表也在药品语境，含药名时必须归 DRUG");
        assertSame(Resolution.Source.RULE, r.source());
        assertEquals(0, llm.calls.get());
    }

    @Test
    @DisplayName("纯药品词（无关系词）仍判 DRUG")
    void plainDrugKeywordStillDrug() {
        IntentResolver resolver = newResolver(newRouter(), null);

        assertEquals(IntentRouter.Intent.DRUG, resolver.resolve("感冒颗粒多少钱").intent());
    }

    @Test
    @DisplayName("数据分析词命中 DATA_QUERY，落点工具为 query_health_data")
    void dataQueryIntent() {
        IntentResolver resolver = newResolver(newRouter(), null);

        Resolution r = resolver.resolve("帮我统计最近一个月的体重趋势");

        assertEquals(IntentRouter.Intent.DATA_QUERY, r.intent());
        assertEquals("query_health_data", r.hint().getTargetTool());
        // 关键：DATA_QUERY 不算「高置信度数据意图」——四层 fail-safe 的离线兜底
        // 只覆盖 HEALTH_RECORDS / GRAPH，NL2SQL 无 LLM 不能离线执行，不能混进来。
        assertFalse(r.isHighConfidenceDataIntent());
    }

    @Test
    @DisplayName("弱词（最近/多久）不抢指标：「最近血压怎么样」仍归 HEALTH_RECORDS")
    void weakDataWordsDoNotStealMetric() {
        IntentResolver resolver = newResolver(newRouter(), null);

        assertEquals(IntentRouter.Intent.HEALTH_RECORDS,
                resolver.resolve("最近血压怎么样").intent(),
                "聚合词表刻意不含「最近」，否则纯指标提问会被误判成数据分析");
    }

    @Test
    @DisplayName("规则拿不准（CHAT）时才走 LLM 兜底，命中白名单→LLM 来源")
    void llmFallbackUsedWhenRuleIsChat() {
        StubLlm llm = new StubLlm("《健康科普》");
        IntentResolver resolver = newResolver(newRouter(), llm);

        Resolution r = resolver.resolve("嗯，说点什么吧");

        assertEquals(IntentRouter.Intent.KNOWLEDGE, r.intent());
        assertSame(Resolution.Source.LLM, r.source());
        assertEquals("《健康科普》", r.llmRaw());
        assertEquals(1, llm.calls.get());
    }

    @Test
    @DisplayName("LLM 返回白名单外的标签 → 回落 CHAT/FALLBACK，不被模型牵着走")
    void outOfWhitelistLabelRejected() {
        StubLlm llm = new StubLlm("《胡说八道》");
        IntentResolver resolver = newResolver(newRouter(), llm);

        Resolution r = resolver.resolve("嗯，说点什么吧");

        assertEquals(IntentRouter.Intent.CHAT, r.intent());
        assertSame(Resolution.Source.FALLBACK, r.source());
    }

    @Test
    @DisplayName("LLM 输出带空白/代码围栏/引号时仍能解析出标签")
    void messyLlmOutputStillParsed() {
        StubLlm llm = new StubLlm("  `《药品咨询》` \n");
        IntentResolver resolver = newResolver(newRouter(), llm);

        Resolution r = resolver.resolve("那个……怎么办");

        assertEquals(IntentRouter.Intent.DRUG, r.intent());
        assertSame(Resolution.Source.LLM, r.source());
    }

    @Test
    @DisplayName("LLM 抛异常 → 捕获并回落 CHAT，绝不向上抛")
    void llmExceptionDegradesToChat() {
        StubLlm llm = new StubLlm(null, true);
        IntentResolver resolver = newResolver(newRouter(), llm);

        Resolution r = resolver.resolve("那个……怎么办");

        assertEquals(IntentRouter.Intent.CHAT, r.intent());
        assertSame(Resolution.Source.FALLBACK, r.source());
    }

    @Test
    @DisplayName("llmClient 字段为 null 时的防御性守卫 → 不抛 NPE，直接回落")
    void absentLlmClientSafe() {
        IntentResolver resolver = newResolver(newRouter(), null);

        Resolution r = resolver.resolve("那个……怎么办");

        assertEquals(IntentRouter.Intent.CHAT, r.intent());
        assertSame(Resolution.Source.FALLBACK, r.source());
    }

    @Test
    @DisplayName("空/空白提问直接回落 CHAT，不触发任何 LLM")
    void blankQueryFallback() {
        StubLlm llm = new StubLlm("《药品咨询》");
        IntentResolver resolver = newResolver(newRouter(), llm);

        assertEquals(IntentRouter.Intent.CHAT, resolver.resolve(null).intent());
        assertEquals(IntentRouter.Intent.CHAT, resolver.resolve("   ").intent());
        assertEquals(0, llm.calls.get());
    }

    @Test
    @DisplayName("6 标签白名单与 ai-prompts/intent-classification.txt 完全一致（防漂移）")
    void labelsMatchPromptResource() throws Exception {
        assertEquals(6, IntentResolver.LABELS.size());

        // 注意：本仓库走 javac 直编（无 Maven/Gradle 资源拷贝），
        // target/classes 里没有 src/main/resources，故直接从源树读取——断言的是「唯一真源」。
        Path promptFile = Path.of("src/main/resources/ai-prompts/intent-classification.txt");
        assertTrue(Files.exists(promptFile), "提示词文件缺失: " + promptFile);
        String prompt;
        try (InputStream in = Files.newInputStream(promptFile)) {
            prompt = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        for (String label : IntentResolver.LABELS) {
            assertTrue(prompt.contains(label),
                    "提示词文件缺少标签 " + label + " —— Java 白名单与提示词已漂移");
        }
    }

    @Test
    @DisplayName("每个标签都能被 IntentHint.forIntent 覆盖，无漏项")
    void forIntentCoversAllLabels() {
        for (IntentRouter.Intent intent : IntentRouter.Intent.values()) {
            IntentRouter.IntentHint hint = IntentRouter.IntentHint.forIntent(intent);
            assertNotNull(hint);
            assertEquals(intent, hint.getIntent());
        }
    }
}
