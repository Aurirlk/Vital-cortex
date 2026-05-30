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
public class ExecuteSqlTool implements Tool {

    @Resource
    private SqliteChatHistoryService chatHistoryService;

    @Override
    public String getName() {
        return "execute_sql";
    }

    @Override
    public String getDescription() {
        return "在本地 SQLite 数据库中执行只读 SQL 查询(SELECT)。chat_history 表结构: " +
                "id(INTEGER), phone_number(TEXT), session_id(TEXT), role(TEXT), content(TEXT), " +
                "intent_code(INTEGER), metadata(TEXT), created_at(DATETIME)。" +
                "可用于查询用户的聊天历史、统计信息等";
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<>();

        Map<String, Object> sqlProp = new LinkedHashMap<>();
        sqlProp.put("type", "string");
        sqlProp.put("description", "只读 SQL SELECT 语句，禁止 UPDATE/DELETE/INSERT/DROP/CREATE");
        properties.put("sql", sqlProp);

        schema.put("properties", properties);

        List<String> required = Arrays.asList("sql");
        schema.put("required", required);

        return schema;
    }

    @Override
    public ToolResult execute(Map<String, Object> arguments) {
        String sql = (String) arguments.get("sql");
        if (sql == null || sql.trim().isEmpty()) {
            return ToolResult.error("SQL语句不能为空");
        }

        try {
            List<Map<String, Object>> results = chatHistoryService.executeQuery(sql);
            if (results.isEmpty()) {
                return ToolResult.ok("查询结果为空");
            }
            return ToolResult.ok(JSON.toJSONString(results));
        } catch (IllegalArgumentException e) {
            return ToolResult.error(e.getMessage());
        } catch (Exception e) {
            log.error("[ExecuteSqlTool] SQL执行失败", e);
            return ToolResult.error("SQL执行失败: " + e.getMessage());
        }
    }
}
