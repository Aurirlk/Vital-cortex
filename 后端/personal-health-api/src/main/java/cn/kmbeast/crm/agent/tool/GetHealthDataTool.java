package cn.kmbeast.crm.agent.tool;

import cn.kmbeast.crm.agent.model.ToolResult;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * 获取用户健康数据工具
 * 从JSON文件读取健康指标数据
 */
@Slf4j
@Component
public class GetHealthDataTool implements Tool {

    @Value("${crm.health-data-dir:${HEALTH_DATA_DIR:./ai_data/health}}")
    private String healthDataDir;

    @Override
    public String getName() {
        return "get_health_data";
    }

    @Override
    public String getDescription() {
        return "获取用户的健康档案数据，包含健康指标（血压、血糖、血脂等）、异常指标、最近健康记录。" +
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
            return ToolResult.ok("无法确定用户身份，请询问用户是否已登录。");
        }

        try {
            // 从JSON文件读取健康数据
            JSONObject healthData = loadHealthDataFromJson(userId);
            if (healthData == null) {
                return ToolResult.ok("暂无健康数据记录。请告知用户需要先在\"健康数据\"页面录入健康指标。");
            }

            switch (queryType) {
                case "profile":
                    return ToolResult.ok(formatHealthProfile(healthData));
                case "recent":
                    int days = arguments.containsKey("days") ? ((Number) arguments.get("days")).intValue() : 30;
                    days = Math.max(1, Math.min(days, 365));
                    return ToolResult.ok(formatRecentRecords(healthData, days));
                case "abnormal":
                    return ToolResult.ok(formatAbnormalIndicators(healthData));
                default:
                    return ToolResult.error("未知的查询类型: " + queryType);
            }
        } catch (Exception e) {
            log.error("[GetHealthDataTool] 健康数据查询失败", e);
            return ToolResult.error("健康数据查询异常: " + e.getMessage());
        }
    }

    /**
     * 从JSON文件加载用户健康数据
     */
    private JSONObject loadHealthDataFromJson(Integer userId) {
        try {
            // AG-15 整改：原实现用 vectorCacheDir.replace("vector_cache","ai_data")
            // 字符串替换推导路径，配置名一变就静默失效。
            // 改为独立配置 crm.health-data-dir（可环境变量 HEALTH_DATA_DIR 注入）。
            Path filePath = Paths.get(healthDataDir, "user_" + userId + ".json");
            
            if (!Files.exists(filePath)) {
                log.info("[GetHealthDataTool] 健康数据文件不存在: {}", filePath);
                return null;
            }

            String content = new String(Files.readAllBytes(filePath), StandardCharsets.UTF_8);
            return JSON.parseObject(content);
        } catch (Exception e) {
            log.error("[GetHealthDataTool] 读取健康数据JSON失败: userId={}", userId, e);
            return null;
        }
    }

    /**
     * 格式化健康档案摘要
     */
    private String formatHealthProfile(JSONObject data) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== 用户健康档案 ===\n");
        sb.append("用户: ").append(data.getString("userName")).append("\n");
        sb.append("数据更新时间: ").append(data.getString("exportTime")).append("\n\n");

        JSONArray records = data.getJSONArray("records");
        if (records == null || records.isEmpty()) {
            sb.append("暂无健康指标记录\n");
            return sb.toString();
        }

        // 按模型分组，只显示最新记录
        Map<String, JSONObject> latestRecords = new LinkedHashMap<>();
        for (int i = 0; i < records.size(); i++) {
            JSONObject record = records.getJSONObject(i);
            String modelName = record.getString("modelName");
            if (!latestRecords.containsKey(modelName)) {
                latestRecords.put(modelName, record);
            }
        }

        sb.append("健康指标（最新值）:\n");
        for (Map.Entry<String, JSONObject> entry : latestRecords.entrySet()) {
            JSONObject record = entry.getValue();
            sb.append("- ").append(entry.getKey()).append(": ")
              .append(record.getString("value")).append(" ").append(record.getString("unit"));
            
            String normalRange = record.getString("normalRange");
            if (normalRange != null && !normalRange.isEmpty()) {
                sb.append(" (正常范围: ").append(normalRange).append(")");
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    /**
     * 格式化最近记录
     *
     * <p>AG-03 整改：原实现中 days 参数只是拼进了标题文案，
     * 实际没有按日期过滤，也没有按时间排序——"最近N天"名存实亡。
     * 现按 recordTime 倒序排列，并只保留 days 天内的记录。
     */
    private String formatRecentRecords(JSONObject data, int days) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== 最近").append(days).append("天健康记录 ===\n");
        sb.append("用户: ").append(data.getString("userName")).append("\n\n");

        JSONArray records = data.getJSONArray("records");
        if (records == null || records.isEmpty()) {
            sb.append("暂无健康记录\n");
            return sb.toString();
        }

        // 截止时间 = 今天 00:00 往前推 days 天
        Calendar cutoff = Calendar.getInstance();
        cutoff.add(Calendar.DAY_OF_YEAR, -days);
        cutoff.set(Calendar.HOUR_OF_DAY, 0);
        cutoff.set(Calendar.MINUTE, 0);
        cutoff.set(Calendar.SECOND, 0);
        cutoff.set(Calendar.MILLISECOND, 0);
        long cutoffMillis = cutoff.getTimeInMillis();

        List<JSONObject> recentRecords = new ArrayList<>();
        for (int i = 0; i < records.size(); i++) {
            JSONObject record = records.getJSONObject(i);
            String recordTime = record.getString("recordTime");
            if (recordTime == null) {
                continue;
            }
            long ts = parseRecordTime(recordTime);
            if (ts >= cutoffMillis) {
                recentRecords.add(record);
            }
        }

        // 按时间倒序（新的在前）
        recentRecords.sort((a, b) ->
                Long.compare(parseRecordTime(b.getString("recordTime")),
                        parseRecordTime(a.getString("recordTime"))));

        if (recentRecords.isEmpty()) {
            sb.append("最近").append(days).append("天内没有健康记录\n");
            return sb.toString();
        }

        int count = 0;
        for (JSONObject record : recentRecords) {
            if (count >= 20) break; // 最多显示20条
            sb.append("- ").append(record.getString("recordTime"))
              .append(" | ").append(record.getString("modelName"))
              .append(": ").append(record.getString("value")).append(" ").append(record.getString("unit"))
              .append("\n");
            count++;
        }

        return sb.toString();
    }

    /**
     * 解析记录时间字符串为毫秒时间戳。
     * 兼容 "yyyy-MM-dd HH:mm:ss"、"yyyy-MM-dd HH:mm"、"yyyy-MM-dd"。
     * 解析失败返回 0（视为最早，排最后）。
     */
    private long parseRecordTime(String recordTime) {
        if (recordTime == null || recordTime.trim().isEmpty()) {
            return 0L;
        }
        String normalized = recordTime.trim().replace('T', ' ');
        java.text.SimpleDateFormat[] formats = {
                new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss"),
                new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm"),
                new java.text.SimpleDateFormat("yyyy-MM-dd")
        };
        for (java.text.SimpleDateFormat fmt : formats) {
            try {
                return fmt.parse(normalized).getTime();
            } catch (java.text.ParseException ignored) {
                // 尝试下一种格式
            }
        }
        log.warn("[GetHealthDataTool] 无法解析记录时间: {}", recordTime);
        return 0L;
    }

    /**
     * 格式化异常指标
     */
    private String formatAbnormalIndicators(JSONObject data) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== 异常指标分析 ===\n");
        sb.append("用户: ").append(data.getString("userName")).append("\n\n");

        JSONArray records = data.getJSONArray("records");
        if (records == null || records.isEmpty()) {
            sb.append("暂无健康数据，无法分析异常指标\n");
            return sb.toString();
        }

        // 按模型分组，检查最新值是否异常
        Map<String, JSONObject> latestRecords = new LinkedHashMap<>();
        for (int i = 0; i < records.size(); i++) {
            JSONObject record = records.getJSONObject(i);
            String modelName = record.getString("modelName");
            if (!latestRecords.containsKey(modelName)) {
                latestRecords.put(modelName, record);
            }
        }

        List<String> abnormalList = new ArrayList<>();
        for (Map.Entry<String, JSONObject> entry : latestRecords.entrySet()) {
            JSONObject record = entry.getValue();
            String normalRange = record.getString("normalRange");
            String value = record.getString("value");
            
            if (normalRange != null && !normalRange.isEmpty() && value != null) {
                try {
                    String[] range = normalRange.split(",");
                    if (range.length == 2) {
                        double min = Double.parseDouble(range[0].trim());
                        double max = Double.parseDouble(range[1].trim());
                        double val = Double.parseDouble(value);
                        
                        if (val < min || val > max) {
                            String status = val < min ? "偏低" : "偏高";
                            abnormalList.add(entry.getKey() + ": " + value + " " + 
                                record.getString("unit") + " (" + status + ", 正常范围: " + normalRange + ")");
                        }
                    }
                } catch (NumberFormatException ignored) {
                    // 无法解析数值，跳过
                }
            }
        }

        if (abnormalList.isEmpty()) {
            sb.append("所有指标均在正常范围内\n");
        } else {
            sb.append("发现 ").append(abnormalList.size()).append(" 项异常指标:\n");
            for (String abnormal : abnormalList) {
                sb.append("- ").append(abnormal).append("\n");
            }
            sb.append("\n建议：请关注以上异常指标，必要时咨询医生。");
        }

        return sb.toString();
    }
}
