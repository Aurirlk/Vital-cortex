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
 * 药品搜索工具
 * 从JSON文件读取药品数据
 */
@Slf4j
@Component
public class SearchDrugTool implements Tool {

    @Value("${vector-cache-dir:./vector_cache}")
    private String vectorCacheDir;

    @Override
    public String getName() {
        return "search_drug";
    }

    @Override
    public String getDescription() {
        return "搜索药品信息，包括药品名称、价格、说明、分类等。当用户询问药品、药物、买药、用药等相关问题时使用此工具。";
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        Map<String, Object> properties = new HashMap<>();
        Map<String, Object> keywordProp = new HashMap<>();
        keywordProp.put("type", "string");
        keywordProp.put("description", "药品名称或分类关键词，如：感冒药、布洛芬、维生素");
        properties.put("keyword", keywordProp);
        schema.put("properties", properties);
        schema.put("required", new String[]{"keyword"});
        return schema;
    }

    @Override
    public ToolResult execute(Map<String, Object> arguments) {
        String keyword = (String) arguments.get("keyword");
        if (keyword == null || keyword.isEmpty()) {
            return ToolResult.error("请提供药品名称或分类关键词");
        }

        try {
            // 从JSON文件读取药品数据
            JSONArray drugs = loadDrugsFromJson();
            if (drugs == null || drugs.isEmpty()) {
                return ToolResult.ok("药品数据为空，请联系管理员导入药品数据。");
            }

            // 搜索匹配的药品
            List<JSONObject> matchedDrugs = new ArrayList<>();
            String lowerKeyword = keyword.toLowerCase();
            
            for (int i = 0; i < drugs.size(); i++) {
                JSONObject drug = drugs.getJSONObject(i);
                String name = drug.getString("name");
                String genericName = drug.getString("genericName");
                String category = drug.getString("category");
                String description = drug.getString("description");
                
                if ((name != null && name.toLowerCase().contains(lowerKeyword)) ||
                    (genericName != null && genericName.toLowerCase().contains(lowerKeyword)) ||
                    (category != null && category.toLowerCase().contains(lowerKeyword)) ||
                    (description != null && description.toLowerCase().contains(lowerKeyword))) {
                    matchedDrugs.add(drug);
                }
            }

            if (matchedDrugs.isEmpty()) {
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("message", "未找到与\"" + keyword + "\"相关的药品");
                result.put("keyword", keyword);
                result.put("suggestion", "请尝试其他关键词，如药品名称、分类（感冒药、消化系统等）");
                return ToolResult.ok(JSON.toJSONString(result));
            }

            // 格式化结果
            StringBuilder sb = new StringBuilder();
            sb.append("找到 ").append(matchedDrugs.size()).append(" 种相关药品：\n\n");
            
            int limit = Math.min(matchedDrugs.size(), 10); // 最多返回10个
            for (int i = 0; i < limit; i++) {
                JSONObject drug = matchedDrugs.get(i);
                sb.append("【").append(i + 1).append("】").append(drug.getString("name"));
                if (drug.getString("genericName") != null) {
                    sb.append("（通用名：").append(drug.getString("genericName")).append("）");
                }
                sb.append("\n");
                sb.append("  分类: ").append(drug.getString("category")).append("\n");
                sb.append("  规格: ").append(drug.getString("specification")).append("\n");
                sb.append("  价格: ¥").append(drug.getBigDecimal("price")).append("/").append(drug.getString("unit")).append("\n");
                sb.append("  厂家: ").append(drug.getString("manufacturer")).append("\n");
                sb.append("  类型: ").append(drug.getBoolean("isOtc") == true ? "OTC(非处方药)" : "处方药").append("\n");
                
                String desc = drug.getString("description");
                if (desc != null && desc.length() > 100) {
                    desc = desc.substring(0, 100) + "...";
                }
                sb.append("  功效: ").append(desc).append("\n\n");
            }

            if (matchedDrugs.size() > limit) {
                sb.append("还有 ").append(matchedDrugs.size() - limit).append(" 种药品未显示，请提供更精确的关键词。\n");
            }

            return ToolResult.ok(sb.toString());
        } catch (Exception e) {
            log.error("[SearchDrugTool] 搜索药品失败", e);
            return ToolResult.error("搜索药品失败: " + e.getMessage());
        }
    }

    /**
     * 从JSON文件加载药品数据
     */
    private JSONArray loadDrugsFromJson() {
        try {
            String drugsFile = vectorCacheDir.replace("vector_cache", "ai_data") + File.separator + "drugs.json";
            Path filePath = Paths.get(drugsFile);
            
            if (!Files.exists(filePath)) {
                log.info("[SearchDrugTool] 药品数据文件不存在: {}", filePath);
                return null;
            }

            String content = new String(Files.readAllBytes(filePath), StandardCharsets.UTF_8);
            JSONObject root = JSON.parseObject(content);
            return root.getJSONArray("drugs");
        } catch (Exception e) {
            log.error("[SearchDrugTool] 读取药品数据JSON失败", e);
            return null;
        }
    }
}
