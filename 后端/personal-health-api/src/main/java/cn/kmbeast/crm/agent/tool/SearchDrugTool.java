package cn.kmbeast.crm.agent.tool;

import cn.kmbeast.mapper.DrugMapper;
import cn.kmbeast.pojo.vo.DrugVO;
import com.alibaba.fastjson2.JSON;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * CRM 工具：搜索药品信息和价格
 */
@Component
public class SearchDrugTool implements Tool {

    @Resource
    private DrugMapper drugMapper;

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
    public String execute(Map<String, Object> arguments) {
        String keyword = (String) arguments.get("keyword");
        if (keyword == null || keyword.isEmpty()) {
            return JSON.toJSONString(Map.of("error", "请提供药品名称或分类关键词"));
        }
        try {
            List<DrugVO> drugs = drugMapper.searchByName(keyword, 10);
            if (drugs.isEmpty()) {
                return JSON.toJSONString(Map.of("message", "未找到相关药品", "keyword", keyword));
            }
            Map<String, Object> result = new HashMap<>();
            result.put("keyword", keyword);
            result.put("count", drugs.size());
            result.put("drugs", drugs);
            return JSON.toJSONString(result);
        } catch (Exception e) {
            return JSON.toJSONString(Map.of("error", "搜索药品失败: " + e.getMessage()));
        }
    }
}
