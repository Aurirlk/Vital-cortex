package cn.kmbeast.crm.agent.tool;

import cn.kmbeast.crm.agent.model.ToolResult;
import cn.kmbeast.crm.sqlite.SqliteChatHistoryService;
import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.*;

@Slf4j
@Component
public class GetChatHistoryTool implements Tool {

    @Resource
    private SqliteChatHistoryService chatHistoryService;

    @Override
    public String getName() {
        return "get_chat_history";
    }

    @Override
    public String getDescription() {
        return "获取指定手机号用户的聊天历史记录，用于了解用户之前的对话上下文";
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<>();

        Map<String, Object> phoneProp = new LinkedHashMap<>();
        phoneProp.put("type", "string");
        phoneProp.put("description", "用户的手机号码");
        properties.put("phone_number", phoneProp);

        Map<String, Object> limitProp = new LinkedHashMap<>();
        limitProp.put("type", "integer");
        limitProp.put("description", "返回最近N条记录，默认10");
        limitProp.put("default", 10);
        properties.put("limit", limitProp);

        schema.put("properties", properties);

        List<String> required = Arrays.asList("phone_number");
        schema.put("required", required);

        return schema;
    }

    @Override
    public ToolResult execute(Map<String, Object> arguments) {
        String phoneNumber = (String) arguments.get("phone_number");
        int limit = arguments.containsKey("limit") ? ((Number) arguments.get("limit")).intValue() : 10;

        if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
            return ToolResult.error("手机号不能为空");
        }

        try {
            List<Map<String, Object>> history = chatHistoryService.getHistory(phoneNumber, limit);
            if (history.isEmpty()) {
                return ToolResult.ok("该用户暂无历史记录");
            }

            List<Map<String, Object>> simplified = new ArrayList<>();
            for (Map<String, Object> record : history) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("role", record.get("role"));
                item.put("content", record.get("content"));
                item.put("time", record.get("created_at"));
                simplified.add(item);
            }
            return ToolResult.ok(JSON.toJSONString(simplified));
        } catch (Exception e) {
            log.error("[GetChatHistoryTool] 获取历史失败", e);
            return ToolResult.error("获取聊天历史异常: " + e.getMessage());
        }
    }
}
