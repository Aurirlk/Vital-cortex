package cn.kmbeast.core.graph;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 一次抽取的产物：节点 + 边 + 警告。
 *
 * <p>各抽取器（种子词表 / patient_profile / user_health / visit_record /
 * drugs.json / mall_product / news）统一返回本对象，由
 * {@code GraphImportService} 汇总后一次性写入 Neo4j 与 {@link LocalTripleStore}。
 *
 * <p><b>去重在这里完成</b>（按 {@link GraphNode#key()} / {@link Triple#key()}），
 * 这样下游拿到的就是干净集合，写入端无需再判重。
 */
public class GraphPayload {

    private final Map<String, GraphNode> nodes = new LinkedHashMap<>();
    private final Map<String, Triple> edges = new LinkedHashMap<>();
    private final Set<String> warnings = new LinkedHashSet<>();

    public void addNode(GraphNode node) {
        if (node == null || !node.isValid()) {
            return;
        }
        // 后到的补齐前者的空描述（例如种子里的空描述 Drug，会被药品导入补上说明书）
        nodes.merge(node.key(), node, (oldVal, newVal) ->
                (oldVal.getDescription() == null || oldVal.getDescription().isEmpty())
                        && !newVal.getDescription().isEmpty() ? newVal : oldVal);
    }

    public void addNodes(Collection<GraphNode> list) {
        if (list == null) {
            return;
        }
        for (GraphNode n : list) {
            addNode(n);
        }
    }

    public void addEdge(Triple t) {
        if (t == null || !t.isValid()) {
            return;
        }
        // 边的两端必须是本 payload 里已知的节点，否则图里会出现"悬空端点"。
        // 注意：这里只做登记，不强制要求端点已存在 —— 抽取器可能先加边后加点，
        // 因此由 {@link #resolveEndpoints()} 在最后统一补建缺失端点。
        edges.put(t.key(), t);
    }

    public void addEdges(Collection<Triple> list) {
        if (list == null) {
            return;
        }
        for (Triple t : list) {
            addEdge(t);
        }
    }

    /**
     * 补建"边引用了但未声明"的端点节点。
     *
     * <p>抽取器常常直接造边（如 {@code visit -[:PRESCRIBED]-> "苯磺酸氨氯地平片"}），
     * 而不一定先造 Drug 节点。若不补齐，Neo4j 侧 MERGE 虽然也会自动建节点，
     * 但建出来的是<b>没有任何 label 之外属性</b>的裸节点，
     * 且 {@link LocalTripleStore} 侧会缺 label 记录 —— 两边行为不一致。
     */
    public void resolveEndpoints() {
        for (Triple t : new ArrayList<>(edges.values())) {
            addNode(GraphNode.of(t.getSubject(), t.getSubjectLabel(), t.getSource()));
            addNode(GraphNode.of(t.getObject(), t.getObjectLabel(), t.getSource()));
        }
    }

    public void warn(String warning) {
        if (warning != null && !warning.isEmpty()) {
            warnings.add(warning);
        }
    }

    /**
     * 合并另一个 payload（不覆盖已有节点描述，见 {@link #addNode}）。
     */
    public void merge(GraphPayload other) {
        if (other == null) {
            return;
        }
        addNodes(other.getNodes());
        addEdges(other.getEdges());
        warnings.addAll(other.getWarnings());
    }

    public List<GraphNode> getNodes() {
        return new ArrayList<>(nodes.values());
    }

    public List<Triple> getEdges() {
        return new ArrayList<>(edges.values());
    }

    public List<String> getWarnings() {
        return new ArrayList<>(warnings);
    }

    public int nodeCount() {
        return nodes.size();
    }

    public int edgeCount() {
        return edges.size();
    }

    @Override
    public String toString() {
        return "GraphPayload{nodes=" + nodes.size() + ", edges=" + edges.size()
                + ", warnings=" + warnings.size() + "}";
    }
}
