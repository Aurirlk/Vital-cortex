package cn.kmbeast.core.graph;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
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
     * 获取相关知识上下文
     */
    public String getRelatedContext(String text) {
        List<String> entities = graphRAG.extractEntities(text);
        StringBuilder context = new StringBuilder();
        for (String entity : entities) {
            String knowledge = graphRAG.queryRelatedKnowledge(entity);
            if (!knowledge.isEmpty()) {
                context.append(knowledge);
            }
        }
        return context.toString();
    }
}
