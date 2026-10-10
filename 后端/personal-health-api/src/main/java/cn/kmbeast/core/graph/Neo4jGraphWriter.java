package cn.kmbeast.core.graph;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Neo4j 写入器 —— 把 {@link GraphPayload} 幂等地写进图库。
 *
 * <p><b>2026-10-07 新增（阶段 0-B）</b>。这是项目里<b>第一个</b>真正能写 Neo4j 的类
 * （{@link Neo4jClient} 此前只有 readTransaction）。
 *
 * <h3>幂等设计</h3>
 * <p>全部用 {@code MERGE ... ON CREATE SET ... ON MATCH SET ...}，
 * 不使用 {@code CREATE} —— 阶段 0-B 的验收标准是
 * "连跑两次 {@code /graph/import/all}，{@code /graph/stats} 的节点数与关系数完全不变"。
 *
 * <h3>标签不能参数化</h3>
 * <p>Cypher <b>不支持</b> {@code MERGE (n:$label {name:$name})} ——
 * 标签与关系类型必须是语句文本的一部分。因此这里按 (label, relation) 分组后
 * <b>拼接标签字符串</b>。
 *
 * <p>🔴 这带来注入风险，故标签来源被严格约束为
 * {@link GraphSchema.Label#code()} / {@link GraphSchema.Relation#code()} 枚举值
 * （编译期穷举的字符串常量，不含任何外部输入）。
 * <b>绝不允许</b>把来自请求参数或数据库文本的串拼进标签位。
 */
@Slf4j
@Component
public class Neo4jGraphWriter {

    /** 单批写入的最大条数 —— 一批过大容易触发 Neo4j 事务超时 */
    private static final int BATCH_SIZE = 500;

    @Resource
    private Neo4jClient neo4jClient;

    /**
     * 应用 Schema（约束）。失败不中断（见 {@link Neo4jClient#applySchema}）。
     */
    public int applySchema() {
        return neo4jClient.applySchema(GraphSchema.constraintStatements());
    }

    /**
     * 幂等写入节点（按标签分组批量 UNWIND）。
     *
     * @param batch 批次号，写入 {@code importBatch} 属性便于按批回滚
     * @return 写入的属性变更计数
     */
    public int writeNodes(List<GraphNode> nodes, String batch) {
        if (nodes == null || nodes.isEmpty()) {
            return 0;
        }
        Map<GraphSchema.Label, List<GraphNode>> grouped = new LinkedHashMap<>();
        for (GraphNode n : nodes) {
            if (n == null || !n.isValid()) {
                continue;
            }
            grouped.computeIfAbsent(n.getLabel(), k -> new ArrayList<>()).add(n);
        }

        int total = 0;
        for (Map.Entry<GraphSchema.Label, List<GraphNode>> e : grouped.entrySet()) {
            String label = e.getKey().code();
            List<GraphNode> list = e.getValue();
            for (int from = 0; from < list.size(); from += BATCH_SIZE) {
                List<GraphNode> slice = list.subList(from, Math.min(from + BATCH_SIZE, list.size()));
                List<Map<String, Object>> rows = new ArrayList<>(slice.size());
                for (GraphNode n : slice) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("name", n.getName());
                    row.put("description", n.getDescription());
                    row.put("alias", n.getAlias());
                    row.put("source", n.getSource());
                    row.put("importBatch", batch);
                    // 业务属性（unit / valueRange / price …）直接摊平到节点上
                    row.putAll(n.getProps());
                    rows.add(row);
                }
                String cypher = "UNWIND $rows AS row "
                        + "MERGE (n:" + label + " {name: row.name}) "
                        + "ON CREATE SET n.description = row.description, "
                        + "              n.alias = row.alias, "
                        + "              n.source = row.source, "
                        + "              n.importBatch = row.importBatch, "
                        + "              n += row.propsExtra "
                        + "ON MATCH SET  n.description = CASE WHEN row.description IS NULL OR row.description = '' "
                        + "                                   THEN n.description ELSE row.description END, "
                        + "              n.source = row.source, "
                        + "              n.importBatch = row.importBatch, "
                        + "              n += row.propsExtra";
                // row 里除保留键外的都进 propsExtra，避免污染保留属性
                List<Map<String, Object>> payload = new ArrayList<>(rows.size());
                for (Map<String, Object> r : rows) {
                    Map<String, Object> extra = new LinkedHashMap<>();
                    for (Map.Entry<String, Object> en : r.entrySet()) {
                        if (!RESERVED_NODE_KEYS.contains(en.getKey())) {
                            extra.put(en.getKey(), en.getValue());
                        }
                    }
                    Map<String, Object> wrapped = new LinkedHashMap<>();
                    wrapped.put("name", r.get("name"));
                    wrapped.put("description", r.get("description"));
                    wrapped.put("alias", r.get("alias"));
                    wrapped.put("source", r.get("source"));
                    wrapped.put("importBatch", r.get("importBatch"));
                    wrapped.put("propsExtra", extra);
                    payload.add(wrapped);
                }
                total += neo4jClient.writeBatch(cypher, Map.of("rows", payload));
            }
            log.info("[Neo4jGraphWriter] 节点 :{} 写入 {} 个", label, list.size());
        }
        return total;
    }

    /** 节点保留属性键，不进 propsExtra */
    private static final List<String> RESERVED_NODE_KEYS =
            List.of("name", "description", "alias", "source", "importBatch", "propsExtra");

    /**
     * 幂等写入关系。
     *
     * <p>按 {@code 主体标签-关系-客体标签} 三元组分组，因为
     * <b>标签与关系类型都不能参数化</b>（Cypher 限制），只能拼进语句文本。
     * 拼接量是 13×15×13 的上界、实际远小于此，且全部来自枚举常量。
     *
     * @return 写入的关系/属性变更计数
     */
    public int writeEdges(List<Triple> edges, String batch) {
        if (edges == null || edges.isEmpty()) {
            return 0;
        }
        Map<String, List<Triple>> grouped = new LinkedHashMap<>();
        for (Triple t : edges) {
            if (t == null || !t.isValid()) {
                continue;
            }
            String gk = t.getSubjectLabel().code() + "|" + t.getRelation().code()
                    + "|" + t.getObjectLabel().code();
            grouped.computeIfAbsent(gk, k -> new ArrayList<>()).add(t);
        }

        int total = 0;
        for (List<Triple> list : grouped.values()) {
            Triple sample = list.get(0);
            String cypher = "UNWIND $rows AS row "
                    + "MERGE (a:" + sample.getSubjectLabel().code() + " {name: row.subject}) "
                    + "MERGE (b:" + sample.getObjectLabel().code() + " {name: row.object}) "
                    + "MERGE (a)-[r:" + sample.getRelation().code() + "]->(b) "
                    + "SET r.source = row.source, r.importBatch = row.batch, r += row.props";
            for (int from = 0; from < list.size(); from += BATCH_SIZE) {
                List<Triple> slice = list.subList(from, Math.min(from + BATCH_SIZE, list.size()));
                List<Map<String, Object>> rows = new ArrayList<>(slice.size());
                for (Triple t : slice) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("subject", t.getSubject());
                    row.put("object", t.getObject());
                    row.put("source", t.getSource());
                    row.put("batch", batch);
                    row.put("props", new LinkedHashMap<>(t.getProps()));
                    rows.add(row);
                }
                total += neo4jClient.writeBatch(cypher, Map.of("rows", rows));
            }
        }
        log.info("[Neo4jGraphWriter] 关系写入 {} 条（{} 个标签组合）", edges.size(), grouped.size());
        return total;
    }
}
