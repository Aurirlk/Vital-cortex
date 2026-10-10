package cn.kmbeast.core.agent;

import cn.kmbeast.crm.agent.IntentRouter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 意图识别<b>统一入口</b>（P2-2，2026-10-09）。
 *
 * <p><b>决策 D2：意图识别 = 规则先行 + LLM 兜底</b>。本类是唯一对外入口：
 * <ol>
 *   <li><b>规则先行</b>：委托 {@link IntentRouter} 做零 LLM 的硬规则分类。
 *       只要规则给出「非 LOW 且非 CHAT」的结论（指标/关系/药品/数据/科普），直接采用，不浪费一次 LLM 调用。</li>
 *   <li><b>LLM 兜底</b>：仅当规则判为 CHAT/LOW（拿不准）时，才问 LLM 二分类。</li>
 *   <li><b>严格白名单</b>：LLM 输出必须精确命中 6 个标签之一（{@link #LABELS}），
 *       否则一律回落 CHAT——这是照搬 {@code DataAgentPipeline:328-345} 的防篡改加固，
 *       避免模型胡编标签把用户引到错误工具。</li>
 *   <li><b>永不抛异常</b>：LLM 超时/报错/无法解析 → {@link Resolution.Source#FALLBACK}，
 *       退回中性 CHAT，绝不拖垮主对话（与 IntentRouter 的「软路由」定位一致）。</li>
 * </ol>
 *
 * <p>{@code LABELS} 与 {@code resources/ai-prompts/intent-classification.txt} 必须保持 6 个标签一致，
 * 由 {@code IntentResolverTest} 单测断言（N8：两份并存、单测锁死）。
 * 注意：{@code ai-prompts/intent-recognition.txt} 是 {@code DataAgentPipeline} 步骤 1 的
 * <b>二分类</b>提示词，与本类的 6 标签无关，切勿混用。</p>
 */
@Slf4j
@Component
public class IntentResolver {

    /** 6 标签白名单（与 resources/ai-prompts/intent-recognition.txt 必须一致） */
    public static final List<String> LABELS = List.of(
            "《闲聊或无关指令》",
            "《健康指标查询》",
            "《医学关系查询》",
            "《药品咨询》",
            "《健康科普》",
            "《数据分析请求》"
    );

    private static final Map<String, IntentRouter.Intent> LABEL_TO_INTENT = new LinkedHashMap<>();

    static {
        LABEL_TO_INTENT.put("《闲聊或无关指令》", IntentRouter.Intent.CHAT);
        LABEL_TO_INTENT.put("《健康指标查询》", IntentRouter.Intent.HEALTH_RECORDS);
        LABEL_TO_INTENT.put("《医学关系查询》", IntentRouter.Intent.GRAPH);
        LABEL_TO_INTENT.put("《药品咨询》", IntentRouter.Intent.DRUG);
        LABEL_TO_INTENT.put("《健康科普》", IntentRouter.Intent.KNOWLEDGE);
        LABEL_TO_INTENT.put("《数据分析请求》", IntentRouter.Intent.DATA_QUERY);
    }

    @Resource
    private IntentRouter router;

    /**
     * LLM 兜底客户端。
     *
     * <p>恒为 {@code DefaultIntentLlmClient}（{@code @Component}），因此这里用必填注入：
     * 装配缺失应当启动即失败（fail-fast），而不是静默退化成「永远只有规则」。
     * 「LLM 不可用」的降级不靠这里——{@code DefaultIntentLlmClient.classify()} 在未配置
     * API 时会抛异常，由 {@link #resolve(String)} 的 try/catch 捕获并回落 CHAT。</p>
     */
    @Resource
    private IntentLlmClient llmClient;

    /**
     * 识别一句话的意图。
     *
     * @param query 用户原始提问
     * @return 非 null 的 {@link Resolution}；query 为空时返回 CHAT/FALLBACK
     */
    public Resolution resolve(String query) {
        if (query == null || query.trim().isEmpty()) {
            return Resolution.fallback(IntentRouter.IntentHint.chat(), null);
        }

        // 1) 规则先行
        IntentRouter.IntentHint hint = router.route(query);
        if (hint.getConfidence() != IntentRouter.Confidence.LOW
                && hint.getIntent() != IntentRouter.Intent.CHAT) {
            return Resolution.fromRule(hint);
        }

        // 2) LLM 兜底（仅 CHAT/LOW）
        if (llmClient != null) {
            try {
                String raw = llmClient.classify(query);
                IntentRouter.Intent mapped = mapLabel(raw);
                if (mapped != null) {
                    return Resolution.fromLlm(IntentRouter.IntentHint.forIntent(mapped), raw);
                }
                log.debug("[IntentResolver] LLM 返回非白名单标签，回落 CHAT: raw={}", raw);
            } catch (Exception e) {
                log.warn("[IntentResolver] LLM 兜底失败，回落 CHAT: {}", e.getMessage());
            }
        }

        // 3) 双重失败：回落中性 CHAT
        return Resolution.fallback(hint, null);
    }

    /**
     * 把 LLM 原始输出解析为意图枚举。
     *
     * <p>严格：只接受 6 个标签的<b>精确</b>文本（书名号可省略）。任何偏差（含「《胡说八道》」
     * 这类不在白名单里的标签）一律返回 null → 调用方回落 CHAT。</p>
     */
    private IntentRouter.Intent mapLabel(String raw) {
        if (raw == null) {
            return null;
        }
        String inner = stripBook(raw);
        if (inner.isEmpty()) {
            return null;
        }
        for (Map.Entry<String, IntentRouter.Intent> entry : LABEL_TO_INTENT.entrySet()) {
            if (inner.equals(stripBook(entry.getKey()))) {
                return entry.getValue();
            }
        }
        return null;
    }

    /** 去掉书名号/引号/反引号与首尾空白，取《...》内的文本（无书名号则取整体） */
    private static String stripBook(String s) {
        String t = s.trim().replace('`', ' ').replace('"', ' ').replace('\'', ' ');
        int a = t.indexOf('《');
        int b = t.indexOf('》');
        if (a >= 0 && b > a) {
            return t.substring(a + 1, b).trim();
        }
        return t.trim();
    }
}
