package cn.kmbeast.core.graph;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 医学词表 —— 阶段 0-B / B4 的人工知识底座。
 *
 * <p><b>为什么需要它</b>：项目现有数据<b>撑不起</b>医学知识图谱 ——
 * 疾病只有 9 个提及（patient_profile.chronic_diseases）、症状 3 条、就诊 3 条。
 * 而 {@link GraphRAG#extractEntities(String)} 的实体匹配完全依赖图谱里已有哪些
 * {@code name}，词表为空则图谱只能返回它那 10 个写死的兜底词。
 *
 * <p>本类读取 {@code classpath:ai-data/seed-triples.json}（人工对齐真实语料后整理），
 * 提供两样东西：
 * <ol>
 *   <li>{@link #payload()} —— 可直接导入的节点与边（疾病/症状/药品/分类 + 关系）</li>
 *   <li>{@link #matchIn(String)} —— 文本命中词表项，供药品适应症解析与文章实体抽取复用</li>
 * </ol>
 *
 * <p>⚠️ 匹配策略是<b>最长优先</b>：先按名称长度降序匹配，避免
 * "2型糖尿病" 被短词 "糖尿病" 抢先命中而丢失细分类型。
 */
@Slf4j
@Component
public class MedicalVocabulary {

    private static final String SEED_PATH = "ai-data/seed-triples.json";

    /** 名称 → 节点元数据 */
    private final Map<String, GraphNode> byName = new LinkedHashMap<>();

    /** 别名 → 规范名 */
    private final Map<String, String> aliasToName = new LinkedHashMap<>();

    /** 匹配用：按名称长度降序（长词优先） */
    private final List<GraphNode> matchOrder = new ArrayList<>();

    private volatile GraphPayload cachedPayload;

    @PostConstruct
    public void init() {
        load();
    }

    public synchronized void load() {
        byName.clear();
        aliasToName.clear();
        matchOrder.clear();
        try (InputStream in = new ClassPathResource(SEED_PATH).getInputStream()) {
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            JSONObject root = JSON.parseObject(text);

            JSONArray nodes = root.getJSONArray("nodes");
            if (nodes != null) {
                for (int i = 0; i < nodes.size(); i++) {
                    JSONObject n = nodes.getJSONObject(i);
                    GraphSchema.Label label = LocalTripleStore.parseLabel(n.getString("label"));
                    String name = n.getString("name");
                    if (label == null || name == null || name.isEmpty()) {
                        log.warn("[MedicalVocabulary] 跳过非法节点定义: {}", n);
                        continue;
                    }
                    List<String> alias = new ArrayList<>();
                    JSONArray arr = n.getJSONArray("alias");
                    if (arr != null) {
                        for (int j = 0; j < arr.size(); j++) {
                            String a = arr.getString(j);
                            if (a != null && !a.isEmpty()) {
                                alias.add(a);
                            }
                        }
                    }
                    GraphNode node = new GraphNode(name, label, n.getString("description"),
                            alias, null, GraphSources.SEED);
                    byName.put(name, node);
                    for (String a : alias) {
                        aliasToName.putIfAbsent(a, name);
                    }
                }
            }

            matchOrder.addAll(byName.values());
            matchOrder.sort((a, b) -> b.getName().length() - a.getName().length());
            log.info("[MedicalVocabulary] 已加载词表 {} 项（来自 {}）", byName.size(), SEED_PATH);
        } catch (Exception e) {
            log.error("[MedicalVocabulary] 词表加载失败，图谱将只能依赖兜底词: {}", e.getMessage(), e);
        }
    }

    /**
     * 词表全量 payload（构造一次后缓存）。
     */
    public GraphPayload payload() {
        GraphPayload cached = cachedPayload;
        if (cached != null) {
            return copyOf(cached);
        }
        GraphPayload p = new GraphPayload();
        p.addNodes(byName.values());
        try (InputStream in = new ClassPathResource(SEED_PATH).getInputStream()) {
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            JSONObject root = JSON.parseObject(text);
            JSONArray edges = root.getJSONArray("edges");
            if (edges != null) {
                for (int i = 0; i < edges.size(); i++) {
                    JSONObject e = edges.getJSONObject(i);
                    GraphSchema.Label sLabel = LocalTripleStore.parseLabel(e.getString("subjectLabel"));
                    GraphSchema.Label oLabel = LocalTripleStore.parseLabel(e.getString("objectLabel"));
                    GraphSchema.Relation rel = LocalTripleStore.parseRelation(e.getString("relation"));
                    if (sLabel == null || oLabel == null || rel == null) {
                        continue;
                    }
                    p.addEdge(Triple.of(e.getString("subject"), sLabel, rel,
                            e.getString("object"), oLabel, GraphSources.SEED));
                }
            }
        } catch (Exception ex) {
            log.error("[MedicalVocabulary] 词表关系读取失败: {}", ex.getMessage(), ex);
        }
        p.resolveEndpoints();
        cachedPayload = p;
        return copyOf(p);
    }

    /**
     * 返回副本：调用方会往 payload 里继续加东西，不能污染缓存。
     */
    private GraphPayload copyOf(GraphPayload p) {
        GraphPayload copy = new GraphPayload();
        copy.addNodes(p.getNodes());
        copy.addEdges(p.getEdges());
        return copy;
    }

    // ==================== 查询 ====================

    /**
     * 文本命中词表项（长词优先，命中后跳过被包含的短词）。
     *
     * @param text 待匹配文本
     * @return 命中项列表（不重复）
     */
    public List<GraphNode> matchIn(String text) {
        if (text == null || text.isEmpty()) {
            return Collections.emptyList();
        }
        List<GraphNode> hit = new ArrayList<>();
        // 记录已覆盖的字符区间，避免 "糖尿病" 在 "2型糖尿病" 命中后重复计入
        List<int[]> covered = new ArrayList<>();
        for (GraphNode node : matchOrder) {
            int idx = indexOfCovered(text, node.getName(), covered);
            if (idx < 0) {
                continue;
            }
            hit.add(node);
            covered.add(new int[]{idx, idx + node.getName().length()});
        }
        return hit;
    }

    /**
     * 在文本中查找 {@code term}，且该位置未被已命中项覆盖。
     */
    private int indexOfCovered(String text, String term, List<int[]> covered) {
        int from = 0;
        while (true) {
            int idx = text.indexOf(term, from);
            if (idx < 0) {
                return -1;
            }
            boolean overlapped = false;
            for (int[] c : covered) {
                if (idx < c[1] && idx + term.length() > c[0]) {
                    overlapped = true;
                    break;
                }
            }
            if (!overlapped) {
                return idx;
            }
            from = idx + 1;
        }
    }

    /**
     * 文本命中<b>别名</b>时的规范名列表（如 "血压高" → "高血压"）。
     *
     * <p><b>为什么单独要有这个方法</b>：图谱里只存规范名 {@code name}，
     * 而 {@link GraphRAG#extractEntities} 做的是"节点名包含匹配"，
     * 于是用户说"我血压高"时匹配不到 {@code 高血压} —— 别名写了等于没写。
     *
     * <p>与 {@link #matchIn} 的区别：这里只看别名、不看规范名
     * （规范名已由图谱实体列表覆盖），避免同一词被计入两次。
     */
    public List<String> matchAliases(String text) {
        if (text == null || text.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> hit = new ArrayList<>();
        List<int[]> covered = new ArrayList<>();
        // 长别名优先，避免"血压高"抢掉"血压偏高"这类更精确的表达
        List<Map.Entry<String, String>> sorted = new ArrayList<>(aliasToName.entrySet());
        sorted.sort((a, b) -> b.getKey().length() - a.getKey().length());
        for (Map.Entry<String, String> e : sorted) {
            int idx = indexOfCovered(text, e.getKey(), covered);
            if (idx < 0) {
                continue;
            }
            String canonical = e.getValue();
            if (!hit.contains(canonical)) {
                hit.add(canonical);
            }
            covered.add(new int[]{idx, idx + e.getKey().length()});
        }
        return hit;
    }

    /**
     * 别名 → 规范名（如 "血压高" → "高血压"）。
     * 未收录返回原串。
     */
    public String canonical(String name) {
        if (name == null) {
            return null;
        }
        String trimmed = name.trim();
        return aliasToName.getOrDefault(trimmed, trimmed);
    }

    public Set<String> diseaseNames() {
        return namesOf(GraphSchema.Label.DISEASE);
    }

    public Set<String> symptomNames() {
        return namesOf(GraphSchema.Label.SYMPTOM);
    }

    public Set<String> drugNames() {
        return namesOf(GraphSchema.Label.DRUG);
    }

    private Set<String> namesOf(GraphSchema.Label label) {
        Set<String> out = new LinkedHashSet<>();
        for (GraphNode n : byName.values()) {
            if (n.getLabel() == label) {
                out.add(n.getName());
            }
        }
        return out;
    }

    public int size() {
        return byName.size();
    }

    /**
     * 词表是否覆盖了 {@link GraphRAG} 的兜底 10 个词。
     * 供启动自检与单测使用 —— 词表退化会让 GraphRAG 静默回到硬编码词表。
     */
    public boolean coversFallbackTerms(List<String> fallbackTerms) {
        if (fallbackTerms == null) {
            return true;
        }
        for (String t : fallbackTerms) {
            if (!byName.containsKey(t) && !aliasToName.containsKey(t)) {
                return false;
            }
        }
        return true;
    }
}
