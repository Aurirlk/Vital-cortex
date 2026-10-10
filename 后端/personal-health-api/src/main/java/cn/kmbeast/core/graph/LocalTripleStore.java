package cn.kmbeast.core.graph;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 本地三元组存储 —— Neo4j 不可用时的兜底图谱。
 *
 * <p><b>2026-10-07 新增（阶段 0-B / B2）</b>。
 *
 * <h3>为什么必须有它</h3>
 * <p>本机 Neo4j <b>长期未启动</b>（无 docker 镜像、无本地安装），而
 * {@code cache/knowledge_graph/} 至今是空目录。若图谱能力完全绑死 Neo4j，
 * 则"阶段 0-B 建图"在本机环境下产出的成果为 0，后续阶段 1（图谱 Tool）、
 * 阶段 3（意图路由到 GRAPH）全部无法验证。
 *
 * <p>本类的定位是<b>成果不丢的保险</b>：无论 Neo4j 是否可用，抽取出的三元组
 * 都会先落 {@code cache/knowledge_graph/triples.json}，并构建内存邻接表供检索。
 * Neo4j 起来后再灌一次即可，无需重跑抽取。
 *
 * <h3>能力</h3>
 * <ul>
 *   <li>{@link #upsert(Collection)} 幂等写入（按 {@link Triple#key()} 去重）</li>
 *   <li>{@link #neighbors(String, int)} 一跳出边检索 —— 与
 *       {@link GraphRAG#queryRelatedKnowledge(String)} 的语义对齐</li>
 *   <li>{@link #allNames()} 全量实体名 —— 与 {@code MATCH (n) RETURN n.name} 对齐</li>
 *   <li>{@link #stats()} 统计</li>
 *   <li>落盘 / 启动自动加载</li>
 * </ul>
 *
 * <p>⚠️ 内存结构全部用并发容器：本 Bean 是单例，导入端点与聊天链路会并发访问。
 */
@Slf4j
@Component
public class LocalTripleStore {

    /** 落盘文件（相对项目运行目录） */
    private static final String DEFAULT_FILE = "cache/knowledge_graph/triples.json";

    @Value("${graph.local.file:" + DEFAULT_FILE + "}")
    private String filePath;

    /** 幂等键 → 三元组。key 见 {@link Triple#key()} */
    private final Map<String, Triple> triples = new ConcurrentHashMap<>();

    /** 实体名 → 出边三元组（一跳检索用） */
    private final Map<String, List<Triple>> outEdges = new ConcurrentHashMap<>();

    /** 实体名 → 入边三元组 */
    private final Map<String, List<Triple>> inEdges = new ConcurrentHashMap<>();

    /** 实体名 → 标签（写盘时保留，加载时恢复） */
    private final Map<String, String> nodeLabels = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        load();
    }

    // ==================== 写 ====================

    /**
     * 幂等写入一批三元组。
     *
     * <p>同一 {@link Triple#key()} 已存在时<b>覆盖属性</b>（保留最新 value/time），
     * 但节点数/边数不变 —— 这是"连跑两次导入，统计不变"验收标准的本地侧保证。
     *
     * @return 本次实际新增的条数（重复计入 0）
     */
    public int upsert(Collection<Triple> batch) {
        if (batch == null || batch.isEmpty()) {
            return 0;
        }
        int added = 0;
        for (Triple t : batch) {
            if (t == null || !t.isValid()) {
                continue;
            }
            String key = t.key();
            Triple prev = triples.put(key, t);
            if (prev == null) {
                added++;
                index(t);
            } else {
                // 已存在：属性更新，索引结构不变（边已经索引过）
                replaceInList(outEdges.get(t.getSubject()), t);
                replaceInList(inEdges.get(t.getObject()), t);
            }
            nodeLabels.putIfAbsent(t.getSubject(), t.getSubjectLabel().code());
            nodeLabels.putIfAbsent(t.getObject(), t.getObjectLabel().code());
        }
        return added;
    }

    private void index(Triple t) {
        outEdges.computeIfAbsent(t.getSubject(), k -> Collections.synchronizedList(new ArrayList<>()))
                .add(t);
        inEdges.computeIfAbsent(t.getObject(), k -> Collections.synchronizedList(new ArrayList<>()))
                .add(t);
    }

    /**
     * 原地替换列表中同 key 的旧三元组（属性更新场景）。
     * 直接改 list 元素而非 remove+add，避免遍历期并发修改。
     */
    private void replaceInList(List<Triple> list, Triple t) {
        if (list == null) {
            return;
        }
        synchronized (list) {
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i).key().equals(t.key())) {
                    list.set(i, t);
                    return;
                }
            }
            list.add(t);
        }
    }

    // ==================== 读 ====================

    /**
     * 一跳出边（subject → ?），与 Neo4j 侧 {@code (n)-[r]->(m)} 语义一致。
     *
     * @param name 实体名
     * @param limit 最大返回条数
     */
    public List<Triple> neighbors(String name, int limit) {
        if (name == null || name.trim().isEmpty()) {
            return Collections.emptyList();
        }
        List<Triple> edges = outEdges.get(name.trim());
        if (edges == null || edges.isEmpty()) {
            return Collections.emptyList();
        }
        synchronized (edges) {
            int n = Math.min(limit <= 0 ? edges.size() : limit, edges.size());
            return new ArrayList<>(edges.subList(0, n));
        }
    }

    /**
     * 无向一跳（出边 + 入边）。供 {@link GraphRAG} 兜底使用 ——
     * 用户问"高血压吃什么药"时，"高血压"在 {@code TREATED_BY} 里是<b>主体</b>，
     * 只查出边才能命中；而"氨氯地平治什么"时药是<b>客体</b>，需要查入边。
     */
    public List<Triple> related(String name, int limit) {
        if (name == null || name.trim().isEmpty()) {
            return Collections.emptyList();
        }
        String key = name.trim();
        List<Triple> out = new ArrayList<>();
        List<Triple> outs = outEdges.get(key);
        if (outs != null) {
            synchronized (outs) {
                out.addAll(outs);
            }
        }
        List<Triple> ins = inEdges.get(key);
        if (ins != null) {
            synchronized (ins) {
                out.addAll(ins);
            }
        }
        int n = limit <= 0 ? out.size() : Math.min(limit, out.size());
        return new ArrayList<>(out.subList(0, n));
    }

    /**
     * 全量实体名（等价于 {@code MATCH (n) RETURN n.name}）。
     * 返回不可变副本 —— 调用方（GraphRAG）会缓存并长期持有。
     */
    public List<String> allNames() {
        return Collections.unmodifiableList(new ArrayList<>(nodeLabels.keySet()));
    }

    public int size() {
        return triples.size();
    }

    /**
     * 节点数（按 name 去重）。
     */
    public int nodeCount() {
        return nodeLabels.size();
    }

    /**
     * 统计信息，结构与 {@code /graph/stats} 的 {@code graph} 段对齐。
     */
    public Map<String, Object> stats() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("nodeCount", nodeLabels.size());
        out.put("relationshipCount", triples.size());

        Map<String, Integer> labelCount = new LinkedHashMap<>();
        for (String label : nodeLabels.values()) {
            labelCount.merge(label, 1, Integer::sum);
        }
        List<Map<String, Object>> labels = new ArrayList<>();
        labelCount.entrySet().stream()
                .sorted((a, b) -> b.getValue() - a.getValue())
                .forEach(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("label", e.getKey());
                    m.put("count", e.getValue());
                    labels.add(m);
                });

        Map<String, Integer> relCount = new LinkedHashMap<>();
        for (Triple t : triples.values()) {
            relCount.merge(t.getRelation().code(), 1, Integer::sum);
        }
        List<Map<String, Object>> relTypes = new ArrayList<>();
        relCount.entrySet().stream()
                .sorted((a, b) -> b.getValue() - a.getValue())
                .forEach(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("type", e.getKey());
                    m.put("count", e.getValue());
                    relTypes.add(m);
                });

        out.put("labels", labels);
        out.put("relationshipTypes", relTypes);
        return out;
    }

    // ==================== 持久化 ====================

    /**
     * 落盘。写临时文件再原子替换 —— 直接覆盖会在写一半崩溃时留下损坏的 JSON，
     * 而本文件是"抽取成果"的唯一本地副本，不能冒这个险。
     */
    public synchronized void save() {
        try {
            Path path = Paths.get(filePath);
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            JSONObject root = new JSONObject();
            root.put("updatedAt", java.time.LocalDateTime.now().toString());
            root.put("tripleCount", triples.size());
            root.put("nodeCount", nodeLabels.size());

            JSONArray nodes = new JSONArray();
            for (Map.Entry<String, String> e : nodeLabels.entrySet()) {
                JSONObject n = new JSONObject();
                n.put("name", e.getKey());
                n.put("label", e.getValue());
                nodes.add(n);
            }
            root.put("nodes", nodes);

            JSONArray edges = new JSONArray();
            for (Triple t : triples.values()) {
                JSONObject e = new JSONObject();
                e.put("subject", t.getSubject());
                e.put("subjectLabel", t.getSubjectLabel().code());
                e.put("relation", t.getRelation().code());
                e.put("object", t.getObject());
                e.put("objectLabel", t.getObjectLabel().code());
                if (!t.getProps().isEmpty()) {
                    e.put("props", new JSONObject(new LinkedHashMap<>(t.getProps())));
                }
                e.put("source", t.getSource());
                edges.add(e);
            }
            root.put("edges", edges);

            File tmp = new File(filePath + ".tmp");
            Files.writeString(tmp.toPath(), root.toString(), StandardCharsets.UTF_8);
            File target = new File(filePath);
            if (target.exists() && !target.delete()) {
                log.warn("[LocalTripleStore] 旧文件删除失败，改为直接覆盖: {}", filePath);
            }
            if (!tmp.renameTo(target)) {
                // Windows 下 renameTo 对已存在目标会失败，兜底用 Files.move
                Files.move(tmp.toPath(), target.toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            log.info("[LocalTripleStore] 已落盘 {} 节点 / {} 三元组 → {}",
                    nodeLabels.size(), triples.size(), filePath);
        } catch (Exception e) {
            log.error("[LocalTripleStore] 落盘失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 启动时从磁盘恢复。文件不存在 / 损坏时降级为空图谱，<b>不抛异常</b>
     * —— 首次部署本来就没有这个文件。
     */
    public synchronized void load() {
        try {
            File f = new File(filePath);
            if (!f.exists() || f.length() == 0) {
                log.info("[LocalTripleStore] 未找到本地三元组文件（{}），以空图谱启动", filePath);
                return;
            }
            String text = Files.readString(f.toPath(), StandardCharsets.UTF_8);
            JSONObject root = JSON.parseObject(text);

            JSONArray nodes = root.getJSONArray("nodes");
            if (nodes != null) {
                for (int i = 0; i < nodes.size(); i++) {
                    JSONObject n = nodes.getJSONObject(i);
                    String name = n.getString("name");
                    String label = n.getString("label");
                    if (name != null && label != null) {
                        nodeLabels.put(name, label);
                    }
                }
            }

            JSONArray edges = root.getJSONArray("edges");
            if (edges != null) {
                for (int i = 0; i < edges.size(); i++) {
                    JSONObject e = edges.getJSONObject(i);
                    Triple t = fromJson(e);
                    if (t != null) {
                        triples.put(t.key(), t);
                        index(t);
                    }
                }
            }
            log.info("[LocalTripleStore] 已从 {} 加载 {} 节点 / {} 三元组",
                    filePath, nodeLabels.size(), triples.size());
        } catch (Exception e) {
            log.error("[LocalTripleStore] 加载失败，以空图谱启动: {}", e.getMessage(), e);
        }
    }

    /**
     * JSON → Triple。任一字段缺失/标签不可识别则返回 null 跳过
     * —— 手工改坏了 triples.json 不该让应用起不来。
     */
    private Triple fromJson(JSONObject e) {
        try {
            String subject = e.getString("subject");
            String object = e.getString("object");
            String relation = e.getString("relation");
            GraphSchema.Label sLabel = parseLabel(e.getString("subjectLabel"));
            GraphSchema.Label oLabel = parseLabel(e.getString("objectLabel"));
            GraphSchema.Relation rel = parseRelation(relation);
            if (sLabel == null || oLabel == null || rel == null) {
                return null;
            }
            Map<String, Object> props = new LinkedHashMap<>();
            JSONObject p = e.getJSONObject("props");
            if (p != null) {
                for (String k : p.keySet()) {
                    props.put(k, p.get(k));
                }
            }
            return new Triple(subject, sLabel, rel, object, oLabel, props, e.getString("source"));
        } catch (Exception ex) {
            log.warn("[LocalTripleStore] 跳过一条无法解析的三元组: {}", e);
            return null;
        }
    }

    /**
     * 标签字符串 → 枚举。找不到返回 null —— 不允许猜测，
     * 猜错会把节点挂到错误标签下，后续检索永远查不到。
     */
    static GraphSchema.Label parseLabel(String code) {
        if (code == null) {
            return null;
        }
        for (GraphSchema.Label l : GraphSchema.Label.values()) {
            if (l.code().equalsIgnoreCase(code)) {
                return l;
            }
        }
        return null;
    }

    static GraphSchema.Relation parseRelation(String code) {
        if (code == null) {
            return null;
        }
        for (GraphSchema.Relation r : GraphSchema.Relation.values()) {
            if (r.code().equalsIgnoreCase(code)) {
                return r;
            }
        }
        return null;
    }

    /**
     * 清空（仅供测试与"重建图谱"场景）。
     */
    public synchronized void clear() {
        triples.clear();
        outEdges.clear();
        inEdges.clear();
        nodeLabels.clear();
    }

    /**
     * 当前文件绝对路径（测试与运维排查用）。
     */
    public String getFilePath() {
        return filePath;
    }

    /**
     * 覆盖落盘路径（包内可见）。
     *
     * <p>存在理由：单测需要把图谱写到 {@code @TempDir} 而不是默认的
     * {@code cache/knowledge_graph/}，否则会污染开发机的真实图谱数据。
     * 生产路径仍由 {@code graph.local.file} 配置注入，不受影响。
     */
    void setFilePath(String path) {
        this.filePath = path;
    }

    /**
     * 按来源统计三元组数量（{@code /graph/stats} 的 sources 段）。
     */
    public Map<String, Integer> countBySource() {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (Triple t : triples.values()) {
            String s = t.getSource() == null ? "unknown" : t.getSource();
            out.merge(s, 1, Integer::sum);
        }
        return out;
    }

    /**
     * 全量出边（测试用快照）。
     */
    public Set<String> indexedSubjects() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(outEdges.keySet()));
    }
}
