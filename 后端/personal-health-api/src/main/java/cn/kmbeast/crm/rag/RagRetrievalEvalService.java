package cn.kmbeast.crm.rag;

import cn.kmbeast.crm.rag.metrics.RetrievalMetrics;
import cn.kmbeast.mapper.RagEvalCaseMapper;
import cn.kmbeast.mapper.RagEvalRunMapper;
import cn.kmbeast.pojo.entity.RagEvalCase;
import cn.kmbeast.pojo.entity.RagEvalRun;
import com.alibaba.fastjson2.JSON;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 检索质量评测：用真实金标用例跑一遍检索，算出<b>召回率 / 精确率@K / MRR / nDCG</b>。
 *
 * <h3>为什么必须有金标才有召回率</h3>
 * <p>召回率的定义是 {@code 命中的相关文档数 / 全部相关文档数}，分母是「语料里
 * 真正相关的东西有多少」—— 这个数<b>只能由人标注</b>，任何算法都猜不出来。
 * 所以本服务把评测集 {@code rag_eval_case} 分成两类，并明确区分它们的结论：</p>
 * <ul>
 *   <li><b>金标用例（有 {@code expected_refs}）</b>：召回、精确、MRR、nDCG 全部可算。
 *       这是唯一能给出召回率的来源。</li>
 *   <li><b>关键词用例（只有 {@code expected_keywords}）</b>：只能算
 *       <b>精确率</b>（分母是 K，分子是「返回的 K 条里有几条含预期关键词」）。
 *       召回率<b>无定义</b>，绝不按 0 计入。这正是 {@code RetrievalMetrics} 里
 *       {@code recallDefined} 那套标志要解决的问题：把「无定义」和「真的 0 分」
 *       混在一起，会让一个 30% 的召回率看起来像 60%。</li>
 * </ul>
 *
 * <h3>为什么「精确率」的分母是 K 而不是返回条数</h3>
 * <p>如果用返回条数作分母，那么「检索返回 1 条且命中」就等于 100% 精确率 ——
 * 一个只敢返回一条的系统会拿到满分。分母固定为 K 之后，
 * 只有「前 K 条基本都是对的」才能得高分，这与「用户会看到前 K 条」的实际体验一致。</p>
 *
 * <h3>结果落库</h3>
 * <p>每次评测写一行 {@code rag_eval_run}，逐题明细进 {@code detail_json}。
 * 有了历史记录，「这次改完检索准确率到底是升了还是降了」才是一句话能回答的问题，
 * 而不是每次都要翻日志重新算。</p>
 */
@Slf4j
@Service
public class RagRetrievalEvalService {

    /** 逐题明细写进 detail_json 的用例上限（控制单行体积）。 */
    private static final int DETAIL_CASE_MAX = 100;

    /** detail_json 的最大字符数，序列化前主动截断。 */
    private static final int DETAIL_JSON_MAX = 60_000;

    /** 默认评测条数。 */
    private static final int DEFAULT_TOP_K = 5;

    /** 单次评测的用例上限，避免一次点下去跑几小时。 */
    private static final int MAX_CASES = 300;

    @Resource
    private RagEvalCaseMapper ragEvalCaseMapper;

    @Resource
    private RagEvalRunMapper ragEvalRunMapper;

    @Resource
    private KbSearchService kbSearchService;

    /** 单个用例的评测结果。 */
    public static final class CaseOutcome {

        public final Integer caseId;
        public final String question;
        public final String matchLevel;
        /** 是否走了金标（id）匹配。false 表示只按关键词算了精确率。 */
        public final boolean labeled;
        public final List<Long> retrieved;
        public final List<Long> golden;
        public final int hitCount;
        public final int firstHitRank;
        public final double recall;
        public final double precision;
        public final double reciprocalRank;
        public final double ndcg;
        public final String path;
        public final long latencyMs;
        public final String note;

        CaseOutcome(Integer caseId, String question, String matchLevel, boolean labeled,
                    List<Long> retrieved, List<Long> golden, int hitCount, int firstHitRank,
                    double recall, double precision, double reciprocalRank, double ndcg,
                    String path, long latencyMs, String note) {
            this.caseId = caseId;
            this.question = question;
            this.matchLevel = matchLevel;
            this.labeled = labeled;
            this.retrieved = retrieved;
            this.golden = golden;
            this.hitCount = hitCount;
            this.firstHitRank = firstHitRank;
            this.recall = recall;
            this.precision = precision;
            this.reciprocalRank = reciprocalRank;
            this.ndcg = ndcg;
            this.path = path;
            this.latencyMs = latencyMs;
            this.note = note;
        }
    }

    /** 一次评测的完整报告。 */
    public static final class Report {

        public final String runId;
        public final int topK;
        public final String audience;
        public final List<Integer> kbIds;
        public final int caseTotal;
        public final int labeledTotal;
        public final int keywordTotal;
        /** 金标子集的聚合指标（唯一有召回率的来源）。 */
        public final RetrievalMetrics.Aggregate aggregate;
        /** 关键词子集的平均精确率；keywordTotal = 0 时为 0。 */
        public final double keywordPrecision;
        public final int sparseOnly;
        public final long avgLatencyMs;
        public final List<CaseOutcome> cases;

        Report(String runId, int topK, String audience, List<Integer> kbIds, int caseTotal,
               int labeledTotal, int keywordTotal, RetrievalMetrics.Aggregate aggregate,
               double keywordPrecision, int sparseOnly, long avgLatencyMs,
               List<CaseOutcome> cases) {
            this.runId = runId;
            this.topK = topK;
            this.audience = audience;
            this.kbIds = kbIds;
            this.caseTotal = caseTotal;
            this.labeledTotal = labeledTotal;
            this.keywordTotal = keywordTotal;
            this.aggregate = aggregate;
            this.keywordPrecision = keywordPrecision;
            this.sparseOnly = sparseOnly;
            this.avgLatencyMs = avgLatencyMs;
            this.cases = cases;
        }
    }

    /** 评测集为空/无金标时的可操作异常。 */
    public static class EvalNotReadyException extends RuntimeException {
        public EvalNotReadyException(String message) {
            super(message);
        }
    }

    // ===================================================================
    // 评测
    // ===================================================================

    /**
     * 跑一次评测并落库。
     *
     * @param audience  限定受众的用例（null / all 表示全部）
     * @param kbIds     限定检索范围的知识库；为空表示按用例自身配置与受众解析
     * @param topK      评测的 K（默认 5）
     * @param triggerBy 触发者（manual / scheduled）
     * @throws EvalNotReadyException 评测集为空，或没有任何带金标的用例
     */
    public Report run(String audience, List<Integer> kbIds, Integer topK, String triggerBy) {
        int k = topK == null || topK <= 0 ? DEFAULT_TOP_K : topK;

        List<RagEvalCase> cases;
        try {
            cases = ragEvalCaseMapper.listEnabled(audience);
        } catch (Exception e) {
            throw new EvalNotReadyException("评测集不可用，请先执行 Data/02_迁移脚本/11_knowledge_base.sql");
        }
        if (cases == null || cases.isEmpty()) {
            throw new EvalNotReadyException("评测集为空。请先在「评测集」里添加用例"
                    + "（金标用例需填写 expected_refs 才能计算召回率）。");
        }
        if (cases.size() > MAX_CASES) {
            cases = cases.subList(0, MAX_CASES);
        }

        List<CaseOutcome> outcomes = new ArrayList<>(cases.size());
        List<RetrievalMetrics.CaseScore> scores = new ArrayList<>();
        int labeled = 0;
        int keywordOnly = 0;
        int sparseOnly = 0;
        double keywordPrecisionSum = 0;
        long latencySum = 0;

        for (RagEvalCase one : cases) {
            if (one == null || one.getQuestion() == null || one.getQuestion().trim().isEmpty()) {
                continue;
            }
            List<Integer> scope = kbIds;
            if (scope == null || scope.isEmpty()) {
                scope = parseKbIds(one.getKbIds());
            }
            String caseAudience = one.getAudience() == null ? audience : one.getAudience();

            long start = System.currentTimeMillis();
            KbSearchService.SearchOutcome outcome;
            try {
                outcome = kbSearchService.search(one.getQuestion(), null, scope,
                        caseAudience, null, k);
            } catch (Exception e) {
                log.warn("[RAG-eval] 用例 {} 检索异常: {}", one.getId(), e.getMessage());
                continue;
            }
            long cost = System.currentTimeMillis() - start;
            latencySum += cost;

            if (KbSearchService.PATH_SPARSE_ONLY.equals(outcome.path())
                    || KbSearchService.PATH_SPARSE_LIKE.equals(outcome.path())) {
                sparseOnly++;
            }

            boolean docLevel = "doc".equalsIgnoreCase(one.getMatchLevel());
            List<Long> retrieved = collectIds(outcome.hits(), docLevel);
            List<Long> golden = parseRefs(one.getExpectedRefs());

            if (!golden.isEmpty()) {
                RetrievalMetrics.CaseScore cs = RetrievalMetrics.score(retrieved, golden, k);
                scores.add(cs);
                labeled++;
                outcomes.add(new CaseOutcome(one.getId(), one.getQuestion(),
                        docLevel ? "doc" : "chunk", true, retrieved, golden,
                        cs.hitCount, cs.firstHitRank, round4(cs.recall),
                        round4(cs.precisionAtK), round4(cs.reciprocalRank), round4(cs.ndcg),
                        outcome.path(), cost, null));
                continue;
            }

            // 关键词模式：只有精确率有定义，召回率缺失。
            List<String> keywords = splitKeywords(one.getExpectedKeywords());
            if (keywords.isEmpty()) {
                outcomes.add(new CaseOutcome(one.getId(), one.getQuestion(),
                        docLevel ? "doc" : "chunk", false, retrieved, Collections.emptyList(),
                        0, 0, Double.NaN, Double.NaN, Double.NaN, Double.NaN,
                        outcome.path(), cost,
                        "用例既无金标也无预期关键词，本次无法评分"));
                continue;
            }
            int relevant = 0;
            for (KbSearchService.KbHit hit : outcome.hits()) {
                if (containsAny(hit.getContent(), keywords)) {
                    relevant++;
                }
            }
            double precision = k <= 0 ? 0.0 : relevant / (double) k;
            keywordPrecisionSum += precision;
            keywordOnly++;
            outcomes.add(new CaseOutcome(one.getId(), one.getQuestion(),
                    docLevel ? "doc" : "chunk", false, retrieved, Collections.emptyList(),
                    relevant, 0, Double.NaN, round4(precision), Double.NaN, Double.NaN,
                    outcome.path(), cost,
                    "未标注金标，仅按预期关键词计算精确率（召回率无定义）"));
        }

        if (labeled == 0) {
            throw new EvalNotReadyException("本次评测集里没有任何带金标（expected_refs）的用例，"
                    + "无法计算召回率。请为用例填写金标文档/块 id —— 关键词用例只能算精确率。");
        }

        RetrievalMetrics.Aggregate aggregate = RetrievalMetrics.aggregate(scores);
        double keywordPrecision = keywordOnly == 0 ? 0.0 : keywordPrecisionSum / keywordOnly;
        long avgLatency = outcomes.isEmpty() ? 0 : latencySum / outcomes.size();

        String runId = UUID.randomUUID().toString().replace("-", "").substring(0, 24);
        Report report = new Report(runId, k, audience, kbIds, outcomes.size(),
                labeled, keywordOnly, aggregate, round4(keywordPrecision),
                sparseOnly, avgLatency, outcomes);

        persist(report, triggerBy);
        return report;
    }

    /** 落库。写失败不影响返回 —— 评测结果本身比历史记录重要。 */
    private void persist(Report report, String triggerBy) {
        try {
            RagEvalRun row = new RagEvalRun();
            row.setRunId(report.runId);
            row.setKbIds(joinIds(report.kbIds));
            row.setAudience(report.audience == null ? "all" : report.audience);
            row.setTopK(report.topK);
            row.setCaseTotal(report.caseTotal);
            row.setCaseHit(report.aggregate.caseHit);
            row.setRecall(scale(report.aggregate.recall));
            row.setPrecisionAtK(scale(report.aggregate.precisionAtK));
            row.setMrr(scale(report.aggregate.mrr));
            row.setHitRate(scale(report.aggregate.hitRate));
            row.setAvgLatencyMs((int) Math.min(Integer.MAX_VALUE, report.avgLatencyMs));
            row.setSparseOnly(report.sparseOnly);
            row.setDetailJson(detailJson(report));
            row.setTriggerBy(triggerBy == null || triggerBy.isEmpty() ? "manual" : triggerBy);
            ragEvalRunMapper.save(row);
        } catch (Exception e) {
            log.warn("[RAG-eval] 评测结果落库失败: {}", e.getMessage());
        }
    }

    /** 详情（含逐题明细）。 */
    public RagEvalRun latest(String audience) {
        try {
            return ragEvalRunMapper.latest(audience);
        } catch (Exception e) {
            log.warn("[RAG-eval] 读取最近评测失败: {}", e.getMessage());
            return null;
        }
    }

    public RagEvalRun getByRunId(String runId) {
        if (runId == null || runId.isEmpty()) {
            return null;
        }
        try {
            return ragEvalRunMapper.getByRunId(runId);
        } catch (Exception e) {
            log.warn("[RAG-eval] 读取评测 {} 失败: {}", runId, e.getMessage());
            return null;
        }
    }

    /** 评测历史（不含 detail_json，列表页不需要把大字段拉进内存）。 */
    public List<RagEvalRun> history(String audience, Integer current, Integer size) {
        try {
            List<RagEvalRun> rows = ragEvalRunMapper.query(audience, current, size);
            return rows == null ? Collections.emptyList() : rows;
        } catch (Exception e) {
            log.warn("[RAG-eval] 读取评测历史失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    public Integer historyCount(String audience) {
        try {
            return ragEvalRunMapper.queryCount(audience);
        } catch (Exception e) {
            return 0;
        }
    }

    // ===================================================================
    // 评测集 CRUD
    // ===================================================================

    /** 分页查询评测用例。 */
    public List<RagEvalCase> queryCases(String question, String matchLevel, String audience,
                                        Boolean enabled, Integer current, Integer size) {
        try {
            List<RagEvalCase> rows = ragEvalCaseMapper.query(question, matchLevel, audience,
                    enabled, current, size);
            return rows == null ? Collections.emptyList() : rows;
        } catch (Exception e) {
            log.warn("[RAG-eval] 查询评测用例失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    public Integer countCases(String question, String matchLevel, String audience, Boolean enabled) {
        try {
            return ragEvalCaseMapper.queryCount(question, matchLevel, audience, enabled);
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * 新建用例。
     *
     * @return 校验错误信息；null 表示成功
     */
    public String saveCase(RagEvalCase one) {
        String invalid = validateCase(one);
        if (invalid != null) {
            return invalid;
        }
        ragEvalCaseMapper.save(one);
        return null;
    }

    /** 修改用例。 */
    public String updateCase(RagEvalCase one) {
        if (one == null || one.getId() == null) {
            return "缺少用例 id";
        }
        String invalid = validateCase(one);
        if (invalid != null) {
            return invalid;
        }
        ragEvalCaseMapper.update(one);
        return null;
    }

    public int deleteCase(Integer id) {
        if (id == null) {
            return 0;
        }
        return ragEvalCaseMapper.deleteById(id);
    }

    public RagEvalCase getCase(Integer id) {
        return id == null ? null : ragEvalCaseMapper.getById(id);
    }

    public int countEnabledCases() {
        try {
            return ragEvalCaseMapper.countEnabled();
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * 用例校验。
     *
     * <p>核心规则：<b>金标与关键词至少要有一个</b>。两个都空的用例是「不可评测」的 ——
     * 它会被跑一遍、占用时间，却对结果毫无贡献，而且从列表上看不出来。
     * 在保存时拒绝，比事后发现「评测集里躺着一半废用例」好得多。</p>
     *
     * <p>包级可见（而非 private）：这是用例进入评测集的唯一闸门，判错等于让废用例
     * 悄悄混进去；而它的规则全是纯字符串判定，脱离容器就该被直接锁住。
     * 与 {@code KbSearchService.classify} 同样的理由。</p>
     */
    static String validateCase(RagEvalCase one) {
        if (one == null) {
            return "参数不能为空";
        }
        if (one.getQuestion() == null || one.getQuestion().trim().isEmpty()) {
            return "问题不能为空";
        }
        String level = one.getMatchLevel() == null ? "chunk" : one.getMatchLevel().trim().toLowerCase();
        if (!"chunk".equals(level) && !"doc".equals(level)) {
            return "匹配粒度只能是 chunk（块）或 doc（文档）";
        }
        one.setMatchLevel(level);
        boolean hasRefs = one.getExpectedRefs() != null && !parseRefs(one.getExpectedRefs()).isEmpty();
        boolean hasKeywords = !splitKeywords(one.getExpectedKeywords()).isEmpty();
        if (!hasRefs && !hasKeywords) {
            return "必须至少提供一项：金标引用（expected_refs，可算召回率）或预期关键词"
                    + "（expected_keywords，只能算精确率）。两者都为空则这条用例无法评分。";
        }
        if (hasRefs && one.getExpectedRefs() != null) {
            // 归一化成「英文逗号 + 无空格」，避免同一个金标集因为空格差异看起来不同
            one.setExpectedRefs(joinLongs(parseRefs(one.getExpectedRefs())));
        }
        return null;
    }

    private static String joinLongs(List<Long> ids) {
        StringBuilder sb = new StringBuilder();
        for (Long id : ids) {
            if (id == null) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(id);
        }
        return sb.toString();
    }

    // ===================================================================
    // 报告 → JSON
    // ===================================================================

    /** 报告的 Map 形态（接口直接返回它）。 */
    public static Map<String, Object> toMap(Report report) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("runId", report.runId);
        out.put("topK", report.topK);
        out.put("audience", report.audience);
        out.put("kbIds", report.kbIds);
        out.put("caseTotal", report.caseTotal);
        out.put("labeledTotal", report.labeledTotal);
        out.put("keywordTotal", report.keywordTotal);
        out.put("caseHit", report.aggregate.caseHit);
        out.put("recall", round4(report.aggregate.recall));
        out.put("recallScored", report.aggregate.recallScored);
        out.put("precisionAtK", round4(report.aggregate.precisionAtK));
        out.put("precisionScored", report.aggregate.precisionScored);
        out.put("mrr", round4(report.aggregate.mrr));
        out.put("ndcg", round4(report.aggregate.ndcg));
        out.put("hitRate", round4(report.aggregate.hitRate));
        out.put("keywordPrecision", report.keywordPrecision);
        out.put("sparseOnly", report.sparseOnly);
        out.put("avgLatencyMs", report.avgLatencyMs);
        out.put("cases", caseMaps(report.cases, DETAIL_CASE_MAX));
        if (report.keywordTotal > 0) {
            out.put("note", "召回率 / MRR / nDCG 仅统计 " + report.labeledTotal
                    + " 条金标用例；另有 " + report.keywordTotal
                    + " 条关键词用例只参与精确率（其召回率无定义，未按 0 计入）。");
        }
        return out;
    }

    private String detailJson(Report report) {
        Map<String, Object> detail = toMap(report);
        try {
            String json = JSON.toJSONString(detail);
            return json.length() <= DETAIL_JSON_MAX ? json : json.substring(0, DETAIL_JSON_MAX);
        } catch (Exception e) {
            return null;
        }
    }

    private static List<Map<String, Object>> caseMaps(List<CaseOutcome> cases, int max) {
        List<Map<String, Object>> out = new ArrayList<>();
        int n = 0;
        for (CaseOutcome c : cases) {
            if (n++ >= max) {
                break;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("caseId", c.caseId);
            row.put("question", c.question);
            row.put("matchLevel", c.matchLevel);
            row.put("labeled", c.labeled);
            row.put("retrieved", c.retrieved);
            row.put("golden", c.golden);
            row.put("hitCount", c.hitCount);
            row.put("firstHitRank", c.firstHitRank);
            // NaN 序列化后会变成 null，前端据此显示「—」而不是 0%
            putNum(row, "recall", c.recall);
            putNum(row, "precision", c.precision);
            putNum(row, "reciprocalRank", c.reciprocalRank);
            putNum(row, "ndcg", c.ndcg);
            row.put("path", c.path);
            row.put("latencyMs", c.latencyMs);
            if (c.note != null) {
                row.put("note", c.note);
            }
            out.add(row);
        }
        return out;
    }

    private static void putNum(Map<String, Object> row, String key, double v) {
        row.put(key, Double.isNaN(v) ? null : v);
    }

    // ===================================================================
    // 用例解析
    // ===================================================================

    /**
     * 解析金标引用串。
     *
     * <p>容忍中英文逗号、分号、空格混用 —— 这一列是管理员手填的，
     * 为一个全角逗号让「保存」失败没有任何意义。</p>
     *
     * @return 去重后的 id 列表（按首次出现顺序）
     */
    public static List<Long> parseRefs(String refs) {
        if (refs == null || refs.trim().isEmpty()) {
            return Collections.emptyList();
        }
        Set<Long> ids = new LinkedHashSet<>();
        for (String part : refs.split("[,，;；\\s]+")) {
            if (part == null || part.trim().isEmpty()) {
                continue;
            }
            try {
                ids.add(Long.parseLong(part.trim()));
            } catch (NumberFormatException e) {
                // 非数字的片段直接忽略：金标串里混进说明文字是常见的手滑，
                // 为它整条用例失败不如忽略后继续
            }
        }
        return new ArrayList<>(ids);
    }

    /** 解析逗号分隔的知识库 id。 */
    public static List<Integer> parseKbIds(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return Collections.emptyList();
        }
        Set<Integer> ids = new LinkedHashSet<>();
        for (String part : raw.split("[,，;；\\s]+")) {
            if (part == null || part.trim().isEmpty()) {
                continue;
            }
            try {
                ids.add(Integer.parseInt(part.trim()));
            } catch (NumberFormatException ignored) {
                // 同上
            }
        }
        return new ArrayList<>(ids);
    }

    /** 预期关键词：逗号/空格分隔。 */
    public static List<String> splitKeywords(String raw) {
        List<String> out = new ArrayList<>();
        if (raw == null || raw.trim().isEmpty()) {
            return out;
        }
        for (String part : raw.split("[,，;；\\s]+")) {
            String s = part.trim();
            if (!s.isEmpty()) {
                out.add(s);
            }
        }
        return out;
    }

    /** 召回结果的 id 序列（保留名次、去重）。docLevel 时折算为文档 id。 */
    private static List<Long> collectIds(List<KbSearchService.KbHit> hits, boolean docLevel) {
        List<Long> ids = new ArrayList<>();
        if (hits == null) {
            return ids;
        }
        Set<Long> seen = new LinkedHashSet<>();
        for (KbSearchService.KbHit hit : hits) {
            Long id = docLevel ? hit.getDocId() : hit.getChunkId();
            if (id != null && seen.add(id)) {
                ids.add(id);
            }
        }
        return ids;
    }

    private static boolean containsAny(String content, List<String> keywords) {
        if (content == null || content.isEmpty()) {
            return false;
        }
        for (String kw : keywords) {
            if (content.contains(kw)) {
                return true;
            }
        }
        return false;
    }

    private static String joinIds(List<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Integer id : ids) {
            if (id == null) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(id);
        }
        return sb.toString();
    }

    private static BigDecimal scale(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(v).setScale(4, RoundingMode.HALF_UP);
    }

    private static double round4(double v) {
        return Double.isNaN(v) ? v : Math.round(v * 10000.0) / 10000.0;
    }
}
