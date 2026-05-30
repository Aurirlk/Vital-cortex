package cn.kmbeast.crm.agent.tool;

import cn.kmbeast.crm.agent.model.ToolResult;
import cn.kmbeast.crm.vectordb.LocalVectorStore;
import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.*;

@Slf4j
@Component
public class SearchKnowledgeTool implements Tool {

    @Resource
    private LocalVectorStore vectorStore;

    @Override
    public String getName() {
        return "search_knowledge";
    }

    @Override
    public String getDescription() {
        return "从本地健康知识向量库中检索相关知识，支持按分类检索。可用分类: health_knowledge(健康知识), " +
                "report_templates(报告解读模板), nutrition_knowledge(营养饮食知识)";
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<>();

        Map<String, Object> collectionProp = new LinkedHashMap<>();
        collectionProp.put("type", "string");
        collectionProp.put("description", "知识库分类");
        collectionProp.put("enum", Arrays.asList("health_knowledge", "report_templates", "nutrition_knowledge"));
        properties.put("collection", collectionProp);

        Map<String, Object> queryProp = new LinkedHashMap<>();
        queryProp.put("type", "string");
        queryProp.put("description", "搜索查询文本");
        properties.put("query", queryProp);

        Map<String, Object> topKProp = new LinkedHashMap<>();
        topKProp.put("type", "integer");
        topKProp.put("description", "返回最相关的K条结果，默认3");
        topKProp.put("default", 3);
        properties.put("top_k", topKProp);

        schema.put("properties", properties);

        List<String> required = Arrays.asList("collection", "query");
        schema.put("required", required);

        return schema;
    }

    @Override
    public ToolResult execute(Map<String, Object> arguments) {
        String collection = (String) arguments.get("collection");
        String query = (String) arguments.get("query");
        int topK = arguments.containsKey("top_k") ? ((Number) arguments.get("top_k")).intValue() : 3;

        if (collection == null || query == null) {
            return ToolResult.error("缺少必填参数 collection 或 query");
        }

        try {
            if (!vectorStore.collectionExists(collection)) {
                return ToolResult.ok("知识库 '" + collection + "' 暂无数据");
            }

            List<LocalVectorStore.SearchResult> results = vectorStore.search(collection, query, Math.min(topK, 10));
            if (results.isEmpty()) {
                return ToolResult.ok("没有返回数据，请依靠自己的知识回答。");
            }

            List<Map<String, Object>> formatted = new ArrayList<>();
            for (LocalVectorStore.SearchResult r : results) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("score", Math.round(r.score * 10000) / 10000.0);
                item.put("content", r.content);
                if (r.metadata != null) item.put("source", r.metadata.getOrDefault("source", "unknown"));
                formatted.add(item);
            }
            return ToolResult.ok(JSON.toJSONString(formatted));
        } catch (Exception e) {
            log.error("[SearchKnowledgeTool] 搜索失败", e);
            return ToolResult.error("搜索异常: " + e.getMessage());
        }
    }
}
