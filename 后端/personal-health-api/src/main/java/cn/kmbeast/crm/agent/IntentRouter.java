package cn.kmbeast.crm.agent;

import cn.kmbeast.core.graph.GraphNode;
import cn.kmbeast.core.graph.MedicalVocabulary;
import cn.kmbeast.crm.agent.tool.HealthMetric;
import jakarta.annotation.Resource;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 意图软路由（阶段 3，2026-10-08）。
 *
 * <p><b>为什么是「软」路由而非「硬」路由</b>：本项目的工具选择最终仍由 LLM 的
 * {@code tool_choice=auto} 决定（见 {@code BaseReActAgent}）。硬路由（强制改写 LLM 的
 * 工具调用）会破坏 ReAct 的多轮协作、且可能与模型判断冲突。这里只做<b>意图倾向推断</b>，
 * 把结论以一段「本轮意图倾向」追加进 system prompt，<b>引导</b>而非<b>命令</b>模型选对工具。
 *
 * <p><b>分类维度</b>（与已落地的工具对齐）：
 * <ul>
 *   <li>{@code DATA_QUERY} —— 统计/趋势/对比型提问 → {@code query_health_data}（数据智能体 NL2SQL）</li>
 *   <li>{@code HEALTH_RECORDS} —— 指标型提问（血压/血糖/体重…）→ {@code query_health_records}</li>
 *   <li>{@code GRAPH} —— 关系型提问（A 与 B 的医学关系）→ {@code query_knowledge_graph}</li>
 *   <li>{@code DRUG} —— 药品咨询 → {@code search_drug}</li>
 *   <li>{@code KNOWLEDGE} —— 健康科普 → {@code search_knowledge}</li>
 *   <li>{@code CHAT} —— 普通咨询/闲聊</li>
 * </ul>
 *
 * <p><b>优先级要点（P2-1）</b>：聚合/趋势词 &gt; 指标别名 &gt; 药品关系词 &gt; 关系词。
 * 前两条保证「最近一个月体重趋势」走 {@code query_health_data}（而非只能列原始记录的
 * {@code query_health_records}）；第三条保证「布洛芬胶囊的副作用」归 DRUG 而非 GRAPH。</p>
 *
 * <p><b>离线兜底载荷</b>：对 {@code HEALTH_RECORDS}/{@code GRAPH} 这类「有确定落点」的意图，
 * 同时给出 {@code targetTool} + {@code suggestedArgs}。当 LLM 完全故障（API 不可用）时，
 * {@code BaseReActAgent} 的四层 fail-safe 第 4 层会直接调用该工具、绕开 LLM 返回真实数据，
 * 而不是一句「服务不可用」。
 *
 * <p>{@code DATA_QUERY} 虽然也带 {@code targetTool}，但<b>不参与</b>离线兜底
 * （见 {@link IntentHint#isHighConfidenceDataIntent()}）：NL2SQL 需要 LLM 生成 SQL，
 * 无 LLM 时无从执行，混进离线兜底只会给出假数据。
 *
 * <p><b>零外部依赖</b>：纯规则 + {@link HealthMetric} 白名单 + 可选的 {@link MedicalVocabulary}
 * 词表（不存在时退化为关键词匹配），不引入任何 LLM 调用，也不触碰 DataAgent 源码。
 */
@Component
public class IntentRouter {

    /** 医用词表（别名归并、实体识别用）。允许为 null —— 缺失时退化为纯关键词匹配。 */
    @Resource
    private MedicalVocabulary vocabulary;

    /** 关系型提问关键词（命中即怀疑是 A↔B 医学关系查询） */
    private static final Set<String> RELATION_KEYWORDS = Set.of(
            "吃什么药", "可以吃", "能吃", "能不能吃", "能否吃", "适合吃", "忌", "禁忌",
            "副作用", "相互作用", "适应症", "适应征", "治疗", "缓解", "引起", "导致",
            "并发症", "危害", "功效", "作用", "用于", "关系", "关联", "哪些症状",
            "什么症状", "能不能", "可以喝", "能不能用", "能用吗"
    );

    /** 药品意图关键词 */
    private static final Set<String> DRUG_KEYWORDS = Set.of(
            "药", "用药", "服药", "剂量", "处方", "颗粒", "胶囊", "口服液",
            "药品价格", "买药", "购药", "片剂", "冲剂"
    );

    /**
     * 药品<b>关系</b>词（与 {@link #RELATION_KEYWORDS} 区分）：只在这些词出现时，
     * 才把"含药名/药词"的提问判为 DRUG 而非 GRAPH。
     * 这是 P2-1 修复「布洛芬胶囊的副作用」被误判成 GRAPH 的关键——副作用/相互作用等
     * 既在 {@code RELATION_KEYWORDS} 里、又在药品语境里，必须优先归 DRUG。
     */
    private static final Set<String> DRUG_RELATION_KEYWORDS = Set.of(
            "副作用", "相互作用", "禁忌", "不良反应", "能否同服", "一起吃", "配伍"
    );

    /**
     * 数据分析意图词（新增 {@code DATA_QUERY}）。命中即归数据智能体（query_health_data）。
     *
     * <p>注意：本词表在指标别名<b>之后</b>才判定，因此「我的血压」这类纯指标提问
     * 仍归 {@code HEALTH_RECORDS}；只有不含指标别名、但含数据词的提问才落到这里。</p>
     */
    private static final Set<String> DATA_KEYWORDS = Set.of(
            "数据", "统计", "趋势", "走势", "平均", "最多", "最少", "排名", "对比",
            "汇总", "分析一下", "最近", "多久", "分布", "变化", "记录数", "环比", "同比"
    );

    /**
     * 聚合/趋势词：<b>优先级高于指标别名</b>。
     *
     * <p>为什么必须抢在指标别名前：「最近一个月体重趋势」同时含指标别名「体重」和趋势词「趋势」。
     * 若按指标优先判成 {@code HEALTH_RECORDS}，落地工具 {@code query_health_records} 只能按
     * 指标白名单返回<b>原始测量记录列表</b>，做不了按月聚合与趋势计算；真正的趋势分析必须由
     * 数据智能体 {@code query_health_data} 走 NL2SQL 完成。因此含聚合/趋势词时一律归
     * {@code DATA_QUERY}。</p>
     *
     * <p>本集合刻意<b>不含</b>「最近 / 多久 / 变化」这类弱词——「最近血压怎么样」应仍按指标处理，
     * 不能被弱词抢走。</p>
     */
    private static final Set<String> AGGREGATE_KEYWORDS = Set.of(
            "趋势", "走势", "统计", "对比", "排名", "平均", "汇总", "分布", "环比", "同比", "记录数"
    );

    /** 科普/文章意图关键词 */
    private static final Set<String> KNOWLEDGE_KEYWORDS = Set.of(
            "文章", "科普", "养生", "调理", "吃什么好", "怎么调理", "注意事项",
            "资料", "相关知识", "科普文章"
    );

    /**
     * 入口：对一句话做意图分类。
     *
     * @param query 用户原始提问（可含多轮，但实际只取最新一句语义）
     * @return 非 null 的 {@link IntentHint}；query 为空时返回 CHAT/LOW（中性）
     */
    public IntentHint route(String query) {
        String q = query == null ? "" : query.trim();
        if (q.isEmpty()) {
            return IntentHint.chat();
        }

        // 1) 聚合/趋势型（最高优先级）：这类提问需要 NL2SQL 统计，query_health_records
        //    只能返回原始记录、做不了按月聚合，因此「体重趋势」必须判 DATA_QUERY 而非 HEALTH_RECORDS。
        if (hasAny(q, AGGREGATE_KEYWORDS)) {
            return IntentHint.dataQuery();
        }

        // 2) 指标型：白名单别名子串命中即可，覆盖「我的血压」「查血糖」等口语
        HealthMetricHit metric = matchMetric(q);
        if (metric != null) {
            return IntentHint.healthRecords(metric.alias, metric.dbName);
        }

        // 3) 实体识别（疾病/症状/药品规范名），供关系型与药品型判定
        List<String> entities = detectEntities(q);
        boolean hasRelation = hasAny(q, RELATION_KEYWORDS);
        boolean hasDrugName = entities.stream().anyMatch(this::isDrugName);
        boolean hasDrugKw = hasAny(q, DRUG_KEYWORDS);
        boolean hasDrugRelation = hasAny(q, DRUG_RELATION_KEYWORDS);
        boolean hasKnowledgeKw = hasAny(q, KNOWLEDGE_KEYWORDS);
        boolean hasDataKw = hasAny(q, DATA_KEYWORDS);

        // 4) 药品 + 药品关系词 → DRUG（抢在 GRAPH 之前，修复「布洛芬胶囊的副作用」误判 GRAPH）
        //    —— 副作用/相互作用/禁忌等既在 RELATION_KEYWORDS 也在药品语境，必须先归 DRUG
        if (hasDrugName && hasDrugRelation) {
            return IntentHint.drug();
        }
        if (hasDrugKw && hasDrugRelation) {
            return IntentHint.drug();
        }

        // 5) 关系型：有实体 + 关系词 → 高置信度；只有关系词 → 中置信度
        if (hasRelation) {
            if (!entities.isEmpty()) {
                return IntentHint.graph(entities.get(0));
            }
            return IntentHint.graphUnknown();
        }

        // 6) 药品型（无药品关系词）：命中药品规范名或药品关键词
        if (hasDrugName || hasDrugKw) {
            return IntentHint.drug();
        }

        // 7) 数据分析词表命中 → DATA_QUERY（走数据智能体 query_health_data）
        if (hasDataKw) {
            return IntentHint.dataQuery();
        }

        // 8) 科普型
        if (hasKnowledgeKw) {
            return IntentHint.knowledge();
        }

        // 9) 兜底：普通咨询/闲聊
        return IntentHint.chat();
    }

    // ==================== 指标识别（宽匹配，供软路由引导用） ====================

    /**
     * 从宽到窄匹配指标：遍历 {@link HealthMetric} 的全部别名，只要 query 含该别名子串即命中。
     * 与 {@link HealthMetric#resolve} 的区别：resolve 要求整句等于别名或互为包含（dbName 粒度），
     * 这里允许「我的血压」「血压高」这类前缀/后缀口语也命中，专门服务"软路由引导"。
     *
     * @return 命中的别名 + 其规范名集合；无命中返回 null
     */
    private HealthMetricHit matchMetric(String q) {
        String lower = q.toLowerCase();
        HealthMetricHit best = null;
        int bestLen = 0;
        for (HealthMetric m : HealthMetric.values()) {
            for (String alias : m.aliases()) {
                if (lower.contains(alias) && alias.length() > bestLen) {
                    best = new HealthMetricHit(alias, m.dbName());
                    bestLen = alias.length();
                }
            }
        }
        return best;
    }

    // ==================== 实体识别 ====================

    /**
     * 从 query 中识别医学实体（疾病/症状/药品规范名）。
     * 优先用 {@link MedicalVocabulary} 的「规范名 + 别名」命中；词表缺失时退化为空列表。
     */
    private List<String> detectEntities(String q) {
        List<String> out = new ArrayList<>();
        if (vocabulary != null) {
            // 规范名命中
            for (GraphNode node : vocabulary.matchIn(q)) {
                String name = node.getName();
                if (!out.contains(name)) {
                    out.add(name);
                }
            }
            // 别名归并（"血压高"→"高血压"）
            for (String canonical : vocabulary.matchAliases(q)) {
                if (!out.contains(canonical)) {
                    out.add(canonical);
                }
            }
        }
        return out;
    }

    private boolean isDrugName(String name) {
        if (vocabulary == null) {
            return false;
        }
        return vocabulary.drugNames().contains(name);
    }

    private static boolean hasAny(String q, Set<String> keywords) {
        for (String kw : keywords) {
            if (q.contains(kw)) {
                return true;
            }
        }
        return false;
    }

    /** 指标命中的内部承载（别名 + 首选规范名，用于 offline 兜底时给工具传参） */
    private static class HealthMetricHit {
        final String alias;
        final String dbName;

        HealthMetricHit(String alias, String dbName) {
            this.alias = alias;
            this.dbName = dbName;
        }
    }

    // ==================== 意图枚举与 Hint 载体 ====================

    public enum Intent {
        HEALTH_RECORDS, GRAPH, DRUG, KNOWLEDGE, DATA_QUERY, CHAT
    }

    public enum Confidence {
        HIGH, MEDIUM, LOW
    }

    /**
     * 一次路由的结果。
     *
     * <p>{@code targetTool}/{@code suggestedArgs} 仅在「有确定工具落点且可离线执行」时非空，
     * 当前仅 {@code HEALTH_RECORDS} 与 {@code GRAPH} 提供——它们依赖 {@link ToolContext}
     * 里的身份即可执行，无需 LLM。{@code DRUG}/{@code KNOWLEDGE}/{@code CHAT} 不提供离线路径。
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IntentHint {
        private Intent intent;
        private Confidence confidence;
        private String hintText;
        /** 离线兜底目标工具名（如 query_health_records）；CHAT/DRUG/KNOWLEDGE 为 null */
        private String targetTool;
        /** 离线兜底时直接传给工具的参数 */
        private Map<String, Object> suggestedArgs;

        // —— 静态工厂（集中维护 hint 文案与离线参数） ——

        static IntentHint of(Intent intent, Confidence confidence, String hintText,
                            String targetTool, Map<String, Object> suggestedArgs) {
            return new IntentHint(intent, confidence, hintText, targetTool, suggestedArgs);
        }

        static IntentHint healthRecords(String alias, @SuppressWarnings("unused") String dbName) {
            Map<String, Object> args = new LinkedHashMap<>();
            args.put("metric", alias);
            return of(Intent.HEALTH_RECORDS, Confidence.HIGH,
                    "用户问题涉及健康指标（如「" + alias + "」）。请优先调用 query_health_records "
                            + "获取真实测量数据，不要凭空编造数字。",
                    "query_health_records", args);
        }

        static IntentHint graph(String entity) {
            Map<String, Object> args = new LinkedHashMap<>();
            args.put("entity", entity);
            return of(Intent.GRAPH, Confidence.HIGH,
                    "用户似乎在询问「" + entity + "」相关的医学关系（如适应证、禁忌、症状、相互作用）。"
                            + "请使用 query_knowledge_graph，并以「" + entity + "」作为 entity 查询。",
                    "query_knowledge_graph", args);
        }

        static IntentHint graphUnknown() {
            return of(Intent.GRAPH, Confidence.MEDIUM,
                    "用户问题可能是医学关系类提问，可尝试 query_knowledge_graph 检索实体间关系。",
                    null, null);
        }

        static IntentHint drug() {
            return of(Intent.DRUG, Confidence.HIGH,
                    "用户问题涉及药品，请使用 search_drug 获取真实药品信息（名称/价格/规格/说明）。",
                    null, null);
        }

        static IntentHint knowledge() {
            return of(Intent.KNOWLEDGE, Confidence.MEDIUM,
                    "用户可能想查找健康科普文章，可使用 search_knowledge 检索系统内文章。",
                    null, null);
        }

        /**
         * 数据分析意图（P2-1 新增 {@link Intent#DATA_QUERY}）。
         * 落点工具为数据智能体 {@code query_health_data}（与 HEALTH_RECORDS 的
         * query_health_records 区分：一个是 NL2SQL 统计/趋势，一个是指标白名单硬编码查询）。
         */
        static IntentHint dataQuery() {
            return of(Intent.DATA_QUERY, Confidence.HIGH,
                    "用户问题涉及健康数据的统计/趋势/分析（如「最近 30 天血压趋势」「各项指标对比」）。"
                            + "请使用 query_health_data 获取真实数据后再分析，不要凭空编造数字。",
                    "query_health_data", null);
        }

        /**
         * 由意图枚举反查对应的 hint（供 {@code IntentResolver} 在 LLM 兜底命中后构造结果）。
         */
        public static IntentHint forIntent(Intent intent) {
            switch (intent) {
                case HEALTH_RECORDS:
                    return healthRecords("健康指标", "健康指标");
                case GRAPH:
                    return graphUnknown();
                case DRUG:
                    return drug();
                case KNOWLEDGE:
                    return knowledge();
                case DATA_QUERY:
                    return dataQuery();
                case CHAT:
                default:
                    return chat();
            }
        }

        public static IntentHint chat() {
            return of(Intent.CHAT, Confidence.LOW,
                    "普通健康咨询或闲聊，按需回答；若涉及具体数据再调用相应工具。",
                    null, null);
        }

        /** 是否为「高置信度 + 有数据工具落点」——四层 fail-safe 第 2/4 层据此激活 */
        public boolean isHighConfidenceDataIntent() {
            return confidence == Confidence.HIGH
                    && (intent == Intent.HEALTH_RECORDS || intent == Intent.GRAPH);
        }
    }
}
