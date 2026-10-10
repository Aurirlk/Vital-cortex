package cn.kmbeast.crm.agent.tool;

import cn.kmbeast.core.graph.GraphSchema;
import cn.kmbeast.core.graph.LocalTripleStore;
import cn.kmbeast.core.graph.MedicalVocabulary;
import cn.kmbeast.core.graph.Neo4jClient;
import cn.kmbeast.core.graph.Triple;
import cn.kmbeast.crm.agent.model.ToolResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 知识图谱检索工具（阶段 1，2026-10-07）。
 *
 * <p><b>定位</b>：让 AI 能把「高血压吃什么药」「布洛芬治什么」「糖尿病有哪些症状」
 * 这类<b>关系型</b>问题落到图谱上，而不是靠模型记忆编造。
 *
 * <h3>为什么值得单独做一个 Tool</h3>
 * <p>{@code search_knowledge} 检索的是<b>文章全文</b>（向量召回），回答的是"有没有相关资料"；
 * 图谱回答的是"A 和 B 之间是什么关系"。两者互补，不能互相替代。
 * 阶段 3 的意图路由会把「关系类提问」路由到 GRAPH，本工具就是那条路的落点。
 *
 * <h3>三条硬约束</h3>
 * <ol>
 *   <li><b>limit 硬钳 50</b> —— LLM 常传 9999，会把几百条边塞进上下文挤爆 prompt。</li>
 *   <li><b>2 秒时间预算</b>，超预算立即<b>返回已收集到的部分结果</b>。</li>
 *   <li><b>降级返回 {@code ToolResult.ok()}</b> 而非 error —— 见 {@link #execute} 注释。</li>
 * </ol>
 *
 * <p>⚠️ <b>为什么不用 Future/线程池做超时</b>：{@code BaseReActAgent.executeTool} 已把工具
 * 丢进 {@code toolExecutor} 线程池执行，{@code ToolContext}（身份）是在提交前捕获、
 * 在池线程里恢复的。若本工具再开一层线程，身份 ThreadLocal 会再次丢失，
 * 违反「医生端身份只能从上下文取」的铁律。因此这里用<b>协作式 deadline 检查</b>：
 * 图谱检索主体是内存遍历（{@link LocalTripleStore}），可在循环内检查预算；
 * 唯一的外部调用（Neo4j）本身受驱动超时保护。
 */
@Slf4j
@Component
public class QueryGraphTool implements Tool {

    /** 返回三元组条数上限。LLM 传 9999 也压到这个值。 */
    private static final int MAX_LIMIT = 50;

    /** 默认返回条数 */
    private static final int DEFAULT_LIMIT = 20;

    /** 最大跳数。图谱小，2 跳足够；再深会引入大量噪声边。 */
    private static final int MAX_DEPTH = 2;

    /** 时间预算（毫秒）。超时即降级，绝不让一次图谱查询拖垮整轮对话。 */
    private static final long TIME_BUDGET_MS = 2000L;

    /** 实体名模糊匹配时最多枚举的候选节点数 */
    private static final int FUZZY_SCAN_CAP = 5000;

    @Resource
    private Neo4jClient neo4jClient;

    @Resource
    private LocalTripleStore localTripleStore;

    /** 词表（别名归并用）。单测里可能不注入，故所有使用处判空。 */
    @Resource
    private MedicalVocabulary vocabulary;

    @Override
    public String getName() {
        return "query_knowledge_graph";
    }

    @Override
    public String getDescription() {
        return "查询医疗知识图谱，获取疾病、症状、药品、指标之间的关联关系。" +
                "适用于「高血压吃什么药」「布洛芬治什么病」「糖尿病有哪些症状」这类关系型提问。" +
                "返回「主体 - 关系 - 客体」三元组列表。";
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<>();

        Map<String, Object> entityProp = new LinkedHashMap<>();
        entityProp.put("type", "string");
        entityProp.put("description", "要查询的实体名称，如：高血压、糖尿病、布洛芬缓释胶囊、收缩压");
        properties.put("entity", entityProp);

        Map<String, Object> relationProp = new LinkedHashMap<>();
        relationProp.put("type", "string");
        relationProp.put("description", "关系类型过滤（可选）。留空表示不过滤");
        List<String> relationCodes = new ArrayList<>();
        for (GraphSchema.Relation r : GraphSchema.Relation.values()) {
            relationCodes.add(r.code());
        }
        relationProp.put("enum", relationCodes);
        properties.put("relation", relationProp);

        Map<String, Object> depthProp = new LinkedHashMap<>();
        depthProp.put("type", "integer");
        depthProp.put("description", "检索跳数：1=直接关联（默认），2=两跳关联");
        depthProp.put("enum", List.of(1, 2));
        depthProp.put("default", 1);
        properties.put("depth", depthProp);

        Map<String, Object> limitProp = new LinkedHashMap<>();
        limitProp.put("type", "integer");
        limitProp.put("description", "返回三元组条数上限，默认20，最大50");
        limitProp.put("default", DEFAULT_LIMIT);
        properties.put("limit", limitProp);

        schema.put("properties", properties);
        schema.put("required", Collections.singletonList("entity"));
        return schema;
    }

    @Override
    public ToolResult execute(Map<String, Object> arguments) {
        if (arguments == null) {
            return ToolResult.error("缺少参数");
        }
        String entity = str(arguments.get("entity"));
        if (entity == null || entity.trim().isEmpty()) {
            return ToolResult.error("缺少必填参数 entity");
        }
        entity = entity.trim();

        int limit = clampLimit(arguments.get("limit"));
        int depth = clampDepth(arguments.get("depth"));
        String relationFilter = normalizeRelation(arguments.get("relation"));
        if (arguments.get("relation") != null && relationFilter == null) {
            return ToolResult.error("未知的关系类型: " + arguments.get("relation"));
        }

        long deadline = System.currentTimeMillis() + TIME_BUDGET_MS;

        try {
            // 1) 实体解析：规范名 → 别名 → 包含匹配
            String resolved = resolveEntity(entity);
            if (resolved == null) {
                // 图谱里没有这个实体。这不是错误 —— 图谱只覆盖本项目真实数据涉及的概念，
                // 查不到是常态。返回 ok 让模型自己接话，而不是触发 ReAct 重试。
                log.info("[QueryGraphTool] 图谱未命中实体: {}", entity);
                return ToolResult.ok("知识图谱中没有找到与「" + entity + "」相关的实体。" +
                        "可以尝试使用 search_knowledge 检索健康文章，或直接基于医学常识回答。");
            }

            // 2) 检索
            List<Triple> triples;
            boolean timedOut = false;
            if (neo4jClient != null && neo4jClient.isHealthy()) {
                triples = queryNeo4j(resolved, relationFilter, depth, limit);
            } else {
                triples = queryLocal(resolved, relationFilter, depth, limit, deadline);
                timedOut = System.currentTimeMillis() > deadline;
            }

            if (triples.isEmpty()) {
                return ToolResult.ok("知识图谱中存在实体「" + resolved + "」，但当前没有检索到关联关系。");
            }

            StringBuilder sb = new StringBuilder();
            if (!resolved.equals(entity)) {
                sb.append("（已将「").append(entity).append("」归一化为「").append(resolved).append("」）\n");
            }
            sb.append("=== 知识图谱检索结果 ===\n");
            sb.append("实体: ").append(resolved).append("\n");
            sb.append("共 ").append(triples.size()).append(" 条关联：\n\n");
            for (Triple t : triples) {
                sb.append("- ").append(t.getSubject())
                        .append(" —[").append(t.getRelation().code()).append("]→ ")
                        .append(t.getObject());
                Object value = t.getProps() == null ? null : t.getProps().get("value");
                if (value != null) {
                    Object unit = t.getProps().get("unit");
                    sb.append("  [").append(value)
                            .append(unit == null ? "" : " " + unit).append("]");
                }
                sb.append("\n");
            }
            if (timedOut) {
                sb.append("\n（检索已达时间预算，结果为部分数据）\n");
            }
            return ToolResult.ok(sb.toString());
        } catch (Exception e) {
            // 🔴 降级而非报错：ToolResult.error 会让 ReAct 循环判定"工具失败"并重试，
            // 重试对一个"本来就没数据的图谱"没有任何意义，只会多烧一轮 token。
            // 图谱是增强项，不是主链路，查不到应当静默降级。
            log.warn("[QueryGraphTool] 图谱检索异常，降级返回空结果: entity={}, msg={}",
                    entity, e.getMessage());
            return ToolResult.ok("知识图谱暂时不可用，请基于其他工具或医学常识回答。");
        }
    }

    // ==================== 参数钳制 ====================

    /** limit 钳制到 [1, 50]。null / 非数字 / 负数 / 极大值统一处理。 */
    static int clampLimit(Object raw) {
        int limit = DEFAULT_LIMIT;
        if (raw instanceof Number n) {
            limit = n.intValue();
        } else if (raw instanceof String s) {
            try {
                limit = Integer.parseInt(s.trim());
            } catch (NumberFormatException ignored) {
                limit = DEFAULT_LIMIT;
            }
        }
        if (limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    /** depth 钳制到 [1, 2] */
    static int clampDepth(Object raw) {
        int depth = 1;
        if (raw instanceof Number n) {
            depth = n.intValue();
        } else if (raw instanceof String s) {
            try {
                depth = Integer.parseInt(s.trim());
            } catch (NumberFormatException ignored) {
                depth = 1;
            }
        }
        return Math.max(1, Math.min(depth, MAX_DEPTH));
    }

    /**
     * 关系类型归一化（大小写不敏感）。
     *
     * @return 规范的关系 code；无法识别时返回 null（调用方应拒绝，而不是静默忽略过滤条件）
     */
    static String normalizeRelation(Object raw) {
        if (raw == null) {
            return null;
        }
        String s = String.valueOf(raw).trim();
        if (s.isEmpty()) {
            return null;
        }
        for (GraphSchema.Relation r : GraphSchema.Relation.values()) {
            if (r.code().equalsIgnoreCase(s)) {
                return r.code();
            }
        }
        return null;
    }

    // ==================== 实体解析 ====================

    /**
     * 把用户说法解析为图谱里的节点名，三级递进：
     * <ol>
     *   <li>精确命中节点名</li>
     *   <li>词表别名（"血压高" → "高血压"）</li>
     *   <li>包含匹配（图谱名是用户说法的子串，或反之）</li>
     * </ol>
     *
     * @return 解析出的节点名；无法解析返回 null
     */
    String resolveEntity(String entity) {
        Set<String> names = nodeNames();
        if (names.contains(entity)) {
            return entity;
        }
        // 别名归并：图谱只存规范名，口语说法必须靠词表接上
        if (vocabulary != null) {
            for (String canonical : vocabulary.matchAliases(entity)) {
                if (names.contains(canonical)) {
                    return canonical;
                }
            }
        }
        // 包含匹配：优先取"被包含"的最长节点名，避免"感冒"命中"感冒灵"这类反向误配
        String best = null;
        int bestLen = 0;
        int scanned = 0;
        for (String name : names) {
            if (++scanned > FUZZY_SCAN_CAP) {
                break;
            }
            if (name.length() <= bestLen) {
                continue;
            }
            if (entity.contains(name) || name.contains(entity)) {
                best = name;
                bestLen = name.length();
            }
        }
        return best;
    }

    /**
     * 图谱全量节点名。
     * Neo4j 健康时优先读 Neo4j，否则读本地三元组（本机常态）。
     */
    private Set<String> nodeNames() {
        Set<String> names = new LinkedHashSet<>();
        try {
            if (neo4jClient != null && neo4jClient.isHealthy()) {
                List<Map<String, Object>> rows = neo4jClient.query(
                        "MATCH (n) RETURN n.name AS name", Map.of());
                for (Map<String, Object> row : rows) {
                    Object v = row.get("name");
                    if (v != null) {
                        names.add(String.valueOf(v));
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[QueryGraphTool] Neo4j 节点名读取失败，改用本地图: {}", e.getMessage());
        }
        if (names.isEmpty() && localTripleStore != null) {
            names.addAll(localTripleStore.allNames());
        }
        return names;
    }

    // ==================== 检索 ====================

    /**
     * Neo4j 侧检索（1/2 跳）。
     *
     * <p>🔴 <b>Cypher 的关系类型不可参数化</b>，只能拼字符串。这里拼的只有
     * {@code relationFilter} —— 它已经过 {@link #normalizeRelation} 校验，
     * 必然是 {@link GraphSchema.Relation} 的枚举 code，不接受任何外部原始输入。
     */
    private List<Triple> queryNeo4j(String entity, String relationFilter, int depth, int limit) {
        // 🔴 rel 只可能是 GraphSchema.Relation 的 code（已过 normalizeRelation 校验）或空串。
        //    绝不拼接任何未经枚举校验的外部输入。
        String rel = relationFilter == null ? "" : ":" + relationFilter;
        String cypher = depth >= 2
                // 变长路径：type(rels[size-1]) 取路径上最后一段关系，语义与本地 BFS 的"第 d 跳"一致
                ? "MATCH p = (n {name: $name})-[" + rel + "*1..2]->(m) " +
                  "WITH n, m, relationships(p) AS rels " +
                  "RETURN n.name AS s, labels(n)[0] AS sl, type(rels[size(rels)-1]) AS rel, " +
                  "m.name AS o, labels(m)[0] AS ol LIMIT " + limit
                : "MATCH (n {name: $name})-[" + rel + "]->(m) " +
                  "RETURN n.name AS s, labels(n)[0] AS sl, type(r) AS rel, " +
                  "m.name AS o, labels(m)[0] AS ol LIMIT " + limit;

        List<Triple> out = new ArrayList<>();
        List<Map<String, Object>> rows = neo4jClient.query(cypher, Map.of("name", entity));
        for (Map<String, Object> row : rows) {
            String s = str(row.get("s"));
            String o = str(row.get("o"));
            if (s == null || o == null) {
                continue;
            }
            GraphSchema.Relation relation = GraphSchema.Relation.of(str(row.get("rel"))).orElse(null);
            if (relation == null) {
                continue;
            }
            // 标签不可识别时退化为 DISEASE 之外的中性选择是不安全的，这里直接跳过该条
            GraphSchema.Label sl = GraphSchema.Label.of(str(row.get("sl"))).orElse(null);
            GraphSchema.Label ol = GraphSchema.Label.of(str(row.get("ol"))).orElse(null);
            if (sl == null || ol == null) {
                continue;
            }
            out.add(new Triple(s, sl, relation, o, ol, Map.of(), "neo4j"));
        }
        return out;
    }

    /**
     * 本地三元组检索（BFS，1/2 跳），带 deadline 协作式检查。
     */
    private List<Triple> queryLocal(String entity, String relationFilter, int depth, int limit,
                                    long deadline) {
        if (localTripleStore == null) {
            return List.of();
        }
        List<Triple> out = new ArrayList<>();
        Set<String> seenKeys = new LinkedHashSet<>();
        Set<String> visited = new LinkedHashSet<>();

        List<String> frontier = List.of(entity);
        visited.add(entity);

        for (int d = 1; d <= depth && !frontier.isEmpty(); d++) {
            List<String> next = new ArrayList<>();
            for (String node : frontier) {
                if (System.currentTimeMillis() > deadline) {
                    return out;
                }
                List<Triple> edges = localTripleStore.related(node, MAX_LIMIT);
                for (Triple t : edges) {
                    if (out.size() >= limit) {
                        return out;
                    }
                    if (relationFilter != null && !relationFilter.equalsIgnoreCase(t.getRelation().code())) {
                        continue;
                    }
                    if (!seenKeys.add(t.key())) {
                        continue;
                    }
                    out.add(t);
                    String other = t.getSubject().equals(node) ? t.getObject() : t.getSubject();
                    if (!visited.contains(other)) {
                        visited.add(other);
                        next.add(other);
                    }
                }
            }
            frontier = next;
        }
        return out;
    }

    private String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
