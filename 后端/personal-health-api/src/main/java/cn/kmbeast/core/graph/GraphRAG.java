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

    @Resource
    private Neo4jClient neo4jClient;

    /**
     * 从文本中提取实体
     */
    public List<String> extractEntities(String text) {
        // 简单实现：基于关键词提取
        List<String> entities = new ArrayList<>();
        String[] medicalTerms = {"高血压", "糖尿病", "心脏病", "感冒", "发烧", "咳嗽", "头痛"};
        for (String term : medicalTerms) {
            if (text.contains(term)) {
                entities.add(term);
            }
        }
        return entities;
    }

    /**
     * 查询相关知识
     */
    public String queryRelatedKnowledge(String entity) {
        if (!neo4jClient.isConnected()) {
            return "";
        }
        List<Map<String, Object>> results = neo4jClient.query(
                "MATCH (n)-[r]->(m) WHERE n.name CONTAINS $name RETURN n.name, type(r), m.name LIMIT 5",
                Map.of("name", entity)
        );
        StringBuilder knowledge = new StringBuilder();
        for (Map<String, Object> row : results) {
            knowledge.append(String.format("%s - %s - %s\n",
                    row.get("n.name"), row.get("type(r)"), row.get("m.name")));
        }
        return knowledge.toString();
    }
}
