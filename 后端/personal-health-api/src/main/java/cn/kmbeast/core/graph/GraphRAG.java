package cn.kmbeast.core.graph;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * GraphRAG - 基于知识图谱的 RAG。
 *
 * <p><b>2026-10-07 增强（阶段 0-B）</b>：接入 {@link LocalTripleStore} 兜底。
 *
 * <p>原实现只有一条路：{@code Neo4jClient.isConnected() ? 查 Neo4j : 返回空}。
 * 而本机 Neo4j <b>长期未启动</b>，于是这条"双路召回"里的图谱路<b>从来没生效过</b> ——
 * 表现为功能存在、实际零贡献，且不报错、不打日志，极难发现。
 *
 * <p>现在改为三级：
 * <ol>
 *   <li>Neo4j 可用 → 查 Neo4j（真实图谱）</li>
 *   <li>Neo4j 不可用 → 查 {@link LocalTripleStore}（导入端点落盘的 triples.json）</li>
 *   <li>两者都空 → 回退固定医学词表（只保证"能召回实体名"，但不带关系）</li>
 * </ol>
 */
@Slf4j
@Component
public class GraphRAG {

    /** 图谱实体列表缓存 TTL（毫秒） */
    private static final long ENTITY_CACHE_TTL_MS = 5 * 60 * 1000L;

    /** 单实体召回的关系条数上限 */
    private static final int MAX_RELATIONS_PER_ENTITY = 8;

    @Resource
    private Neo4jClient neo4jClient;

    @Resource
    private LocalTripleStore localTripleStore;

    /**
     * 词表（可为空 —— 单测里可能不注入）。
     *
     * <p>用途：图谱节点只存规范名，用户口语里说的是别名
     * （"血压高" vs {@code 高血压}），不做别名归并这些节点就永远命中不到。
     */
    @Resource
    private MedicalVocabulary vocabulary;

    /** 图谱未连接或实体未命中时的兜底医学词表 */
    private static final List<String> FALLBACK_TERMS = Arrays.asList(
            "高血压", "糖尿病", "心脏病", "感冒", "发烧", "咳嗽", "头痛", "哮喘", "胃炎", "失眠"
    );

    /** 图谱实体名缓存（避免每次提取都全表查询） */
    private volatile List<String> entityCache = null;
    private volatile long entityCacheTime = 0L;

    /**
     * 兜底词表（供 {@code /graph/stats} 自检：词表是否覆盖了它）。
     */
    public static List<String> fallbackTerms() {
        return Collections.unmodifiableList(FALLBACK_TERMS);
    }

    /**
     * 从文本中提取实体。
     *
     * <p>优先从知识图谱加载真实实体列表做包含匹配（图谱里有什么实体就匹配什么，
     * 而非写死词表）；图谱未连接或实体为空时回退固定医学词表。
     */
    public List<String> extractEntities(String text) {
        if (text == null || text.isEmpty()) return new ArrayList<>();
        List<String> entities = new ArrayList<>();

        // 1) 图谱实体匹配（优先）：真实实体名，命中即有效实体
        List<String> graphEntities = loadGraphEntities();
        for (String name : graphEntities) {
            if (name != null && !name.isEmpty() && text.contains(name)) {
                entities.add(name);
            }
        }

        // 2) 别名归并：口语说法 → 图谱规范名。
        //    图谱只存规范名，没有这一步"血压高"这类说法会全部漏掉。
        if (vocabulary != null) {
            for (String canonical : vocabulary.matchAliases(text)) {
                if (!entities.contains(canonical)) {
                    entities.add(canonical);
                }
            }
        }

        // 3) 固定医学词表兜底（图谱为空时保证基本能力）
        for (String term : FALLBACK_TERMS) {
            if (text.contains(term) && !entities.contains(term)) {
                entities.add(term);
            }
        }
        return entities;
    }

    /**
     * 从图谱加载全部实体名（带 5 分钟缓存）。
     *
     * <p>Neo4j 优先，不可用时读 {@link LocalTripleStore}。
     * 两者都空时返回空列表 —— 调用方自然降级到固定词表，不抛错。
     */
    private List<String> loadGraphEntities() {
        long now = System.currentTimeMillis();
        List<String> cached = entityCache;
        if (cached != null && now - entityCacheTime < ENTITY_CACHE_TTL_MS) {
            return cached;
        }
        List<String> names = new ArrayList<>();
        try {
            if (neo4jClient.isConnected()) {
                List<java.util.Map<String, Object>> rows = neo4jClient.query(
                        "MATCH (n) RETURN n.name AS name", java.util.Map.of());
                for (java.util.Map<String, Object> row : rows) {
                    if (row.get("name") != null) {
                        names.add(String.valueOf(row.get("name")));
                    }
                }
            }
            if (names.isEmpty()) {
                // 🔴 新增兜底：Neo4j 未启动（本机常态）时读本地三元组
                names.addAll(localTripleStore.allNames());
            }
        } catch (Exception e) {
            log.warn("[GraphRAG] 加载图谱实体失败: {}", e.getMessage());
        }

        if (!names.isEmpty()) {
            entityCache = names;
            entityCacheTime = now;
            log.info("[GraphRAG] 已加载知识图谱实体 {} 个", names.size());
        }
        return names;
    }

    /**
     * 查询某实体的相关知识（一跳关系，带关系类型别名）。
     *
     * <p>Neo4j 不可用或查询为空时降级到 {@link LocalTripleStore}；
     * 仍为空则返回空串 —— 双路召回中该路自然降级。
     */
    public String queryRelatedKnowledge(String entity) {
        StringBuilder knowledge = new StringBuilder();

        // 1) Neo4j
        if (neo4jClient.isConnected()) {
            List<java.util.Map<String, Object>> results = neo4jClient.query(
                    "MATCH (n)-[r]->(m) WHERE n.name = $name " +
                            "RETURN n.name AS subject, type(r) AS relation, m.name AS object LIMIT 8",
                    java.util.Map.of("name", entity)
            );
            for (java.util.Map<String, Object> row : results) {
                knowledge.append(String.format("%s - %s - %s\n",
                        row.get("subject"), row.get("relation"), row.get("object")));
            }
            if (knowledge.length() > 0) {
                return knowledge.toString();
            }
        }

        // 2) 本地三元组兜底（无向一跳，见 LocalTripleStore.related 的说明）
        List<Triple> related = localTripleStore.related(entity, MAX_RELATIONS_PER_ENTITY);
        for (Triple t : related) {
            knowledge.append(String.format("%s - %s - %s",
                    t.getSubject(), t.getRelation().code(), t.getObject()));
            Object value = t.getProps().get("value");
            if (value != null) {
                Object unit = t.getProps().get("unit");
                knowledge.append(" [").append(value)
                        .append(unit == null ? "" : " " + unit).append("]");
            }
            knowledge.append("\n");
        }
        return knowledge.toString();
    }
}
