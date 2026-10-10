package cn.kmbeast.core.graph;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 知识图谱导入编排。
 *
 * <p><b>2026-10-07 新增（阶段 0-B / B3）</b>。补齐 {@code neo4j_import.sh} 调用了、
 * 但代码中<b>完全不存在</b>的 5 个端点背后的服务。
 *
 * <h3>双写策略</h3>
 * <pre>
 *   抽取（规则，零 LLM）
 *     ├→ Neo4j（可用时）：MERGE 幂等写
 *     └→ LocalTripleStore（总是写）：Neo4j 长期未启动时的兜底，成果不丢
 * </pre>
 *
 * <p>⚠️ <b>本地兜底无条件写入</b>：本机 Neo4j 实际长期不可用，若只在"Neo4j 挂了"时
 * 才写本地，那本机一次导入都留不下东西。本地写入同时也是 Neo4j 恢复前的检索数据源。
 *
 * <h3>幂等验收</h3>
 * <p>连跑两次 {@code /graph/import/all}，{@code /graph/stats} 的
 * nodeCount / relationshipCount 必须完全不变。
 */
@Slf4j
@Service
public class GraphImportService {

    @Resource
    private GraphExtractor extractor;

    @Resource
    private MedicalVocabulary vocabulary;

    @Resource
    private Neo4jClient neo4jClient;

    @Resource
    private Neo4jGraphWriter neo4jGraphWriter;

    @Resource
    private LocalTripleStore localTripleStore;

    /**
     * 导入基础数据 = 人工词表 + 指标字典 + 患者档案 + 指标测量 + 就诊记录。
     *
     * <p>这是"患者健康画像图"的主体（约 250 节点 / 220 关系的确定性部分）。
     */
    public Map<String, Object> importBase() {
        GraphPayload p = new GraphPayload();
        p.merge(vocabulary.payload());
        p.merge(extractor.extractIndicators());
        p.merge(extractor.extractPatientProfiles());
        p.merge(extractor.extractMeasurements());
        p.merge(extractor.extractVisits());
        return commit(p, "base");
    }

    /**
     * 导入药品数据 = drugs.json + mall_product 药品类商品。
     */
    public Map<String, Object> importDrugs() {
        GraphPayload p = new GraphPayload();
        p.merge(extractor.extractDrugs());
        return commit(p, "drugs");
    }

    /**
     * 从知识文章抽取实体（词表命中，零 LLM）。
     */
    public Map<String, Object> importArticles() {
        GraphPayload p = new GraphPayload();
        p.merge(extractor.extractArticles());
        return commit(p, "articles");
    }

    /**
     * 一键全量导入（推荐）。三个子步骤全部幂等，可重复执行。
     */
    public Map<String, Object> importAll() {
        GraphPayload p = new GraphPayload();
        p.merge(vocabulary.payload());
        p.merge(extractor.extractIndicators());
        p.merge(extractor.extractPatientProfiles());
        p.merge(extractor.extractMeasurements());
        p.merge(extractor.extractVisits());
        p.merge(extractor.extractDrugs());
        p.merge(extractor.extractArticles());
        return commit(p, "all");
    }

    // ==================== 核心提交 ====================

    /**
     * 统一的提交流程：补端点 → 写 Neo4j（若可用）→ 无条件写本地 → 落盘 → 出报告。
     */
    private Map<String, Object> commit(GraphPayload payload, String scope) {
        long start = System.currentTimeMillis();
        payload.resolveEndpoints();
        String batch = GraphExtractor.newBatchId();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("scope", scope);
        result.put("batch", batch);
        result.put("nodes", payload.nodeCount());
        result.put("edges", payload.edgeCount());

        // ---- 1) Neo4j ----
        boolean neo4jOk = neo4jClient.isHealthy();
        result.put("neo4jConnected", neo4jOk);
        if (neo4jOk) {
            try {
                int schemaApplied = neo4jGraphWriter.applySchema();
                int nodeWrites = neo4jGraphWriter.writeNodes(payload.getNodes(), batch);
                int edgeWrites = neo4jGraphWriter.writeEdges(payload.getEdges(), batch);
                result.put("neo4jSchemaStatements", schemaApplied);
                result.put("neo4jNodeWrites", nodeWrites);
                result.put("neo4jEdgeWrites", edgeWrites);
                result.put("neo4jStatus", "OK");
            } catch (Exception e) {
                log.error("[GraphImport] Neo4j 写入失败，已降级到本地存储: {}", e.getMessage(), e);
                result.put("neo4jStatus", "FAILED");
                result.put("neo4jError", e.getMessage());
                neo4jOk = false;
            }
        } else {
            result.put("neo4jStatus", "UNAVAILABLE");
        }

        // ---- 2) 本地兜底（无条件） ----
        int added = localTripleStore.upsert(payload.getEdges());
        try {
            localTripleStore.save();
            result.put("localStatus", "OK");
        } catch (Exception e) {
            log.error("[GraphImport] 本地三元组落盘失败: {}", e.getMessage(), e);
            result.put("localStatus", "FAILED");
            result.put("localError", e.getMessage());
        }
        result.put("localNewTriples", added);
        result.put("localTotalTriples", localTripleStore.size());
        result.put("localTotalNodes", localTripleStore.nodeCount());

        // ---- 3) 后端与警告 ----
        result.put("backend", neo4jOk ? "neo4j" : "local");
        result.put("warnings", payload.getWarnings());
        result.put("elapsedMs", System.currentTimeMillis() - start);
        return result;
    }

    /**
     * Neo4j 是否可用（供 {@code /graph/health} 探测）。
     */
    public boolean isNeo4jAvailable() {
        return neo4jClient.isHealthy();
    }

    // ==================== 统计 ====================

    /**
     * 图谱统计（{@code /graph/stats}）。
     *
     * <p>刻意把 {@code status} 与 {@code backend} 分开：
     * 前端要能区分「图谱为空」（数据问题）与「图谱挂了」（Neo4j 问题），
     * 否则运维只能看到一个空列表，无从判断。
     */
    public Map<String, Object> stats() {
        long start = System.currentTimeMillis();
        Map<String, Object> out = new LinkedHashMap<>();

        boolean neo4jOk = neo4jClient.isHealthy();
        out.put("status", neo4jOk ? "OK" : "DEGRADED");
        out.put("backend", neo4jOk ? "neo4j" : "local");

        Map<String, Object> neo4jInfo = new LinkedHashMap<>();
        neo4jInfo.put("connected", neo4jOk);
        neo4jInfo.put("degraded", neo4jClient.isDegraded());
        if (neo4jOk) {
            Map<String, Object> s = neo4jClient.stats();
            neo4jInfo.put("nodeCount", s.get("nodeCount"));
            neo4jInfo.put("relationshipCount", s.get("relationshipCount"));
        }
        out.put("neo4j", neo4jInfo);

        Map<String, Object> graph = new LinkedHashMap<>(localTripleStore.stats());
        out.put("graph", graph);

        // 本地按来源统计
        Map<String, Integer> bySource = localTripleStore.countBySource();
        List<Map<String, Object>> sources = new ArrayList<>();
        for (Map.Entry<String, Integer> e : bySource.entrySet()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("source", e.getKey());
            m.put("triples", e.getValue());
            m.put("lastImport", java.time.LocalDateTime.now().toString());
            sources.add(m);
        }
        out.put("sources", sources);

        // 覆盖率：哪些导入跑过、哪些没跑
        List<String> used = new ArrayList<>();
        for (Map.Entry<String, Integer> e : bySource.entrySet()) {
            used.add(sourceToEndpoint(e.getKey()));
        }
        List<String> pending = new ArrayList<>(ALL_ENDPOINTS);
        pending.removeAll(used);
        Map<String, Object> coverage = new LinkedHashMap<>();
        coverage.put("importEndpointsUsed", used);
        coverage.put("pendingEndpoints", pending);
        coverage.put("llmExtractionJobStatus", "NEVER_RUN");
        coverage.put("vocabularySize", vocabulary.size());
        coverage.put("vocabularyCoversFallbackTerms",
                vocabulary.coversFallbackTerms(GraphRAG.fallbackTerms()));
        out.put("coverage", coverage);

        out.put("warnings", collectWarnings());
        out.put("elapsedMs", System.currentTimeMillis() - start);
        return out;
    }

    private static final List<String> ALL_ENDPOINTS = List.of(
            "/graph/import", "/graph/import/drugs", "/graph/import/articles");

    /**
     * 来源 → 对应导入端点（供 coverage 展示）。
     */
    private static String sourceToEndpoint(String source) {
        return switch (source) {
            case GraphSources.DRUGS_JSON, GraphSources.MALL_PRODUCT -> "/graph/import/drugs";
            case GraphSources.NEWS -> "/graph/import/articles";
            default -> "/graph/import";
        };
    }

    /**
     * 汇总当前已知问题。
     */
    private List<Map<String, Object>> collectWarnings() {
        List<Map<String, Object>> warnings = new ArrayList<>();
        if (!neo4jClient.isHealthy()) {
            Map<String, Object> w = new LinkedHashMap<>();
            w.put("type", "NEO4J_UNAVAILABLE");
            w.put("detail", "Neo4j 未连接，图谱数据仅保存在本地三元组存储，检索走本地兜底");
            warnings.add(w);
        }
        if (localTripleStore.size() == 0) {
            Map<String, Object> w = new LinkedHashMap<>();
            w.put("type", "GRAPH_EMPTY");
            w.put("detail", "图谱为空，请先调用 /graph/import/all");
            warnings.add(w);
        }
        if (!vocabulary.coversFallbackTerms(GraphRAG.fallbackTerms())) {
            Map<String, Object> w = new LinkedHashMap<>();
            w.put("type", "VOCABULARY_DEGRADED");
            w.put("detail", "疾病词表未覆盖 GraphRAG 兜底词，实体抽取能力下降");
            warnings.add(w);
        }
        return warnings;
    }

    /**
     * 清空图谱（管理员重建用）。
     *
     * <p>⚠️ 清空本地存储即删除 {@code cache/knowledge_graph/triples.json} 的内容。
     * Neo4j 侧只清本 Schema 定义的标签，不动库里其它数据。
     */
    public Map<String, Object> clear() {
        Map<String, Object> out = new LinkedHashMap<>();
        int before = localTripleStore.size();
        localTripleStore.clear();
        localTripleStore.save();
        out.put("localCleared", before);
        if (neo4jClient.isHealthy()) {
            int removed = 0;
            try {
                for (String label : GraphSchema.allLabelCodes()) {
                    neo4jClient.write("MATCH (n:" + label + ") DETACH DELETE n", Map.of());
                    removed++;
                }
            } catch (Exception e) {
                log.error("[GraphImport] Neo4j 清空失败: {}", e.getMessage());
                out.put("neo4jError", e.getMessage());
            }
            out.put("neo4jLabelsCleared", removed);
        } else {
            out.put("neo4jLabelsCleared", 0);
        }
        return out;
    }
}
