package cn.kmbeast.crm.agent.tool;

import cn.kmbeast.crm.agent.model.ToolResult;
import cn.kmbeast.pojo.api.Result;
import cn.kmbeast.service.AiHealthDataService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.*;

@Slf4j
@Component
public class GetHealthDataTool implements Tool {

    @Resource
    private AiHealthDataService aiHealthDataService;

    @Override
    public String getName() {
        return "get_health_data";
    }

    @Override
    public String getDescription() {
        return "获取用户的健康档案数据，包含基本健康指标、异常指标、最近健康记录。" +
                "可用查询类型: profile(健康档案摘要), recent(最近N天记录), abnormal(异常指标列表)";
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<>();

        Map<String, Object> typeProp = new LinkedHashMap<>();
        typeProp.put("type", "string");
        typeProp.put("description", "查询类型");
        typeProp.put("enum", Arrays.asList("profile", "recent", "abnormal"));
        properties.put("query_type", typeProp);

        Map<String, Object> daysProp = new LinkedHashMap<>();
        daysProp.put("type", "integer");
        daysProp.put("description", "查询最近多少天的记录(仅query_type=recent时有效)，默认30");
        daysProp.put("default", 30);
        properties.put("days", daysProp);

        schema.put("properties", properties);

        List<String> required = Arrays.asList("query_type");
        schema.put("required", required);

        return schema;
    }

    @Override
    public ToolResult execute(Map<String, Object> arguments) {
        String queryType = (String) arguments.get("query_type");
        if (queryType == null) {
            return ToolResult.error("缺少必填参数 query_type");
        }

        Integer userId = ToolContext.getInt("userId");
        if (userId == null) {
            return ToolResult.ok("无法确定用户身份，请询问用户是否已注册。用户标识: " + ToolContext.getString("phoneNumber"));
        }

        try {
            switch (queryType) {
                case "profile": {
                    Result<Map<String, Object>> result = aiHealthDataService.getUserHealthProfile(userId);
                    if (result.getCode() == 200 && result.getData() != null) {
                        return ToolResult.ok(formatHealthProfile(result.getData()));
                    }
                    return ToolResult.ok("暂无健康档案数据");
                }
                case "recent": {
                    int days = arguments.containsKey("days")
                            ? ((Number) arguments.get("days")).intValue() : 30;
                    Result<Map<String, Object>> result = aiHealthDataService.getRecentHealthRecords(userId, days);
                    if (result.getCode() == 200 && result.getData() != null) {
                        return ToolResult.ok(formatRecentRecords(result.getData()));
                    }
                    return ToolResult.ok("最近" + days + "天暂无健康记录");
                }
                case "abnormal": {
                    Result<Map<String, Object>> result = aiHealthDataService.getAbnormalIndicators(userId);
                    if (result.getCode() == 200 && result.getData() != null) {
                        return ToolResult.ok(formatAbnormal(result.getData()));
                    }
                    return ToolResult.ok("暂未检测到异常指标");
                }
                default:
                    return ToolResult.error("未知的查询类型: " + queryType);
            }
        } catch (Exception e) {
            log.error("[GetHealthDataTool] 健康数据查询失败", e);
            return ToolResult.error("健康数据查询异常: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private String formatHealthProfile(Map<String, Object> data) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== 用户健康档案 ===\n");

        Map<String, Object> userInfo = (Map<String, Object>) data.get("用户信息");
        if (userInfo != null) {
            sb.append("用户名: ").append(userInfo.getOrDefault("用户名", "未知")).append("\n");
        }

        Map<String, Object> summary = (Map<String, Object>) data.get("健康指标摘要");
        if (summary != null && !summary.isEmpty()) {
            sb.append("健康指标:\n");
            for (Map.Entry<String, Object> entry : summary.entrySet()) {
                Map<String, Object> indicator = (Map<String, Object>) entry.getValue();
                sb.append("  - ").append(entry.getKey()).append(": ")
                        .append(indicator.getOrDefault("最新值", "无数据"))
                        .append(" [").append(indicator.getOrDefault("状态", "未知")).append("]\n");
            }
        }

        if (sb.length() == "=== 用户健康档案 ===\n".length()) {
            sb.append("暂无健康数据记录\n");
        }
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private String formatRecentRecords(Map<String, Object> data) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== 最近健康记录 ===\n");

        List<Map<String, Object>> records = (List<Map<String, Object>>) data.get("records");
        if (records == null) records = (List<Map<String, Object>>) data.get("健康记录");

        if (records != null && !records.isEmpty()) {
            for (int i = 0; i < Math.min(records.size(), 10); i++) {
                Map<String, Object> record = records.get(i);
                sb.append("- ").append(record.getOrDefault("记录时间", "未知时间"))
                        .append(": ").append(record.getOrDefault("指标信息", "")).append("\n");
            }
        } else {
            sb.append("暂无健康记录\n");
        }
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private String formatAbnormal(Map<String, Object> data) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== 异常指标 ===\n");

        List<Map<String, Object>> abnormalList = (List<Map<String, Object>>) data.get("异常指标列表");
        if (abnormalList == null) abnormalList = (List<Map<String, Object>>) data.get("abnormalList");

        if (abnormalList != null && !abnormalList.isEmpty()) {
            for (Map<String, Object> item : abnormalList) {
                sb.append("- ").append(item.getOrDefault("指标", "未知"))
                        .append(": ").append(item.getOrDefault("最新值", "无数据"))
                        .append(" [").append(item.getOrDefault("状态", "未知")).append("]\n");
            }
        } else {
            sb.append("暂未检测到异常指标\n");
        }
        return sb.toString();
    }
}
