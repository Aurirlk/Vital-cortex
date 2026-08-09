package cn.kmbeast.core.graph;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.*;

/**
 * GraphRAG - 基于知识图谱的 RAG
 */
@Slf4j
@Component
public class GraphRAG {

    /** 图谱实体列表缓存 TTL（毫秒） */
    private static final long ENTITY_CACHE_TTL_MS = 5 * 60 * 1000L;

    @Resource
    private Neo4jClient neo4jClient;

    /** 图谱未连接或实体未命中时的兜底医学词表 */
    private static final List<String> FALLBACK_TERMS = Arrays.asList(
            "高血压", "糖尿病", "心脏病", "感冒", "发烧", "咳嗽", "头痛", "哮喘", "胃炎", "失眠"
    );

    /** 图谱实体名缓存（避免每次提取都全表查询） */
    private volatile List<String> entityCache = null;
    private volatile long entityCacheTime = 0L;

    /**
     * 从文本中提取实体。
     * 优先从知识图谱加载真实实体列表做包含匹配（图谱里有什么实体就匹配什么，
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

        // 2) 固定医学词表兜底（Neo4j 未连接 / 图谱实体未命中时保证基本能力）
        for (String term : FALLBACK_TERMS) {
            if (text.contains(term) && !entities.contains(term)) {
                entities.add(term);
            }
        }
        return entities;
    }

    /**
     * 从 Neo4j 加载全部实体名（带 5 分钟缓存）。
     * 未连接时返回空列表 —— 调用方自然降级，不抛错。
     */
    private List<String> loadGraphEntities() {
        if (!neo4jClient.isConnected()) {
            return Collections.emptyList();
        }
        long now = System.currentTimeMillis();
        List<String> cached = entityCache;
        if (cached != null && now - entityCacheTime < ENTITY_CACHE_TTL_MS) {
            return cached;
        }
        try {
            List<Map<String, Object>> rows = neo4jClient.query(
                    "MATCH (n) RETURN n.name AS name", Map.of());
            List<String> names = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                if (row.get("name") != null) {
                    names.add(String.valueOf(row.get("name")));
                }
            }
            entityCache = names;
            entityCacheTime = now;
            log.info("[GraphRAG] 已加载知识图谱实体 {} 个", names.size());
            return names;
        } catch (Exception e) {
            log.warn("[GraphRAG] 加载图谱实体失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 查询某实体的相关知识（一跳关系，带关系类型别名）。
     * Neo4j 未连接或查询异常时返回空串 —— 双路召回中该路自然降级。
     */
    public String queryRelatedKnowledge(String entity) {
        if (!neo4jClient.isConnected()) {
            return "";
        }
        List<Map<String, Object>> results = neo4jClient.query(
                "MATCH (n)-[r]->(m) WHERE n.name = $name " +
                        "RETURN n.name AS subject, type(r) AS relation, m.name AS object LIMIT 8",
                Map.of("name", entity)
        );
        StringBuilder knowledge = new StringBuilder();
        for (Map<String, Object> row : results) {
            knowledge.append(String.format("%s - %s - %s\n",
                    row.get("subject"), row.get("relation"), row.get("object")));
        }
        return knowledge.toString();
    }
}
