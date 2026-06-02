package cn.kmbeast.crm.agent.tool;

import cn.kmbeast.crm.agent.model.ToolResult;
import cn.kmbeast.crm.dto.WebSearchResult;
import cn.kmbeast.crm.service.WebSearchService;
import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.*;

/**
 * 联网搜索工具
 */
@Slf4j
@Component
public class WebSearchTool implements Tool {

    @Resource
    private WebSearchService webSearchService;

    @Override
    public String getName() {
        return "web_search";
    }

    @Override
    public String getDescription() {
        return "联网搜索获取最新信息。当用户询问最新资讯、实时数据、时事新闻、最新研究成果等问题时使用此工具。" +
                "返回搜索结果摘要和来源链接。";
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<>();

        Map<String, Object> queryProp = new LinkedHashMap<>();
        queryProp.put("type", "string");
        queryProp.put("description", "搜索查询词");
        properties.put("query", queryProp);

        schema.put("properties", properties);
        schema.put("required", Arrays.asList("query"));

        return schema;
    }

    @Override
    public ToolResult execute(Map<String, Object> arguments) {
        String query = (String) arguments.get("query");
        if (query == null || query.trim().isEmpty()) {
            return ToolResult.error("请提供搜索关键词");
        }

        try {
            List<WebSearchResult> results = webSearchService.search(query);
            
            if (results.isEmpty()) {
                Map<String, Object> emptyResult = new HashMap<>();
                emptyResult.put("message", "未找到相关结果");
                emptyResult.put("query", query);
                return ToolResult.ok(JSON.toJSONString(emptyResult));
            }

            // 格式化结果
            StringBuilder sb = new StringBuilder();
            sb.append("搜索结果：\n\n");
            
            for (int i = 0; i < results.size(); i++) {
                WebSearchResult r = results.get(i);
                sb.append(String.format("%d. %s\n", i + 1, r.getTitle()));
                if (r.getSnippet() != null) {
                    sb.append("   ").append(r.getSnippet()).append("\n");
                }
                if (r.getUrl() != null) {
                    sb.append("   来源：").append(r.getUrl()).append("\n");
                }
                sb.append("\n");
            }

            Map<String, Object> result = new HashMap<>();
            result.put("query", query);
            result.put("count", results.size());
            result.put("results", results);
            result.put("formatted", sb.toString());
            
            return ToolResult.ok(JSON.toJSONString(result));
        } catch (Exception e) {
            log.error("[WebSearchTool] 搜索失败: {}", e.getMessage());
            return ToolResult.error("搜索失败: " + e.getMessage());
        }
    }
}
