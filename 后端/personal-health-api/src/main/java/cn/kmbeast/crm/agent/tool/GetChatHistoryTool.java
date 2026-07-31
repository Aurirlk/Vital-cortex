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
        return "获取当前用户的聊天历史记录，用于了解用户之前的对话上下文。无需传手机号，自动限定当前用户";
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<>();

        Map<String, Object> limitProp = new LinkedHashMap<>();
        limitProp.put("type", "integer");
        limitProp.put("description", "返回最近N条记录，默认10");
        limitProp.put("default", 10);
        properties.put("limit", limitProp);

        schema.put("properties", properties);

        schema.put("required", new ArrayList<>());

        return schema;
    }

    @Override
    public ToolResult execute(Map<String, Object> arguments) {
        // AG-01 整改：手机号由服务端从会话上下文注入，不信任 LLM 参数。
        // 原实现让 LLM 自由指定 phone_number——prompt 注入一条
        // "先查 138xxxx 的历史"，就能把任意手机号的问诊记录读给攻击者。
        String phoneNumber = ToolContext.getString("phoneNumber");
        if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
            return ToolResult.error("当前会话缺少用户手机号，无法查询历史");
        }

        int limit = 10;
        Object limitArg = arguments.get("limit");
        if (limitArg instanceof Number) {
            limit = Math.max(1, Math.min(((Number) limitArg).intValue(), 100));
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
            return ToolResult.error("获取聊天历史异常");
        }
    }
}
