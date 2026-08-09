package cn.kmbeast.core.graph;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.*;

/**
 * 知识图谱服务
 */
@Slf4j
@Service
public class KnowledgeGraphService {

    @Resource
    private Neo4jClient neo4jClient;

    @Resource
    private GraphRAG graphRAG;

    /**
     * 查询实体关系
     */
    public List<Map<String, Object>> queryEntityRelations(String entityName) {
        return neo4jClient.query(
                "MATCH (n)-[r]-(m) WHERE n.name = $name RETURN n.name, type(r) as relation, m.name",
                Map.of("name", entityName)
        );
    }

    /**
     * 获取相关知识上下文（GraphRAG 接入主链路入口）。
     * 提取实体 → 查询图谱关系 → 拼装结构化上下文段。
     * Neo4j 未连接或实体为空时返回空串，调用方自然降级。
     */
    public String getRelatedContext(String text) {
        List<String> entities = graphRAG.extractEntities(text);
        if (entities.isEmpty()) {
            return "";
        }
        StringBuilder context = new StringBuilder();
        context.append("\n\n【知识图谱参考（GraphRAG）】\n");
        context.append("以下是从医学知识图谱查询到的实体关系，请作为背景知识参考，结合用户问题给出专业回答：\n\n");
        for (String entity : entities) {
            String knowledge = graphRAG.queryRelatedKnowledge(entity);
            if (!knowledge.isEmpty()) {
                context.append("实体「").append(entity).append("」的关系：\n").append(knowledge).append("\n");
            }
        }
        return context.toString();
    }
}
