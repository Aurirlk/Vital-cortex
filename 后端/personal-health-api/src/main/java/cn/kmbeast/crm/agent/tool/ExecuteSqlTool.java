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
                "可用于查询当前用户的聊天历史、统计信息等。" +
                "注意：查询 chat_history 时必须带 WHERE phone_number = '用户手机号' 条件";
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<>();

        Map<String, Object> sqlProp = new LinkedHashMap<>();
        sqlProp.put("type", "string");
        sqlProp.put("description", "只读 SQL SELECT 语句，禁止 UPDATE/DELETE/INSERT/DROP/CREATE；" +
                "查 chat_history 必须限定 WHERE phone_number = '用户手机号'");
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
            // SEC-04：LLM 生成的 SQL 必须落在当前会话用户自己的数据上，
            // 由 SqlGuard 强制校验 phone_number 归属，越权/拖库查询直接失败
            String tenantPhone = ToolContext.getString("phoneNumber");
            List<Map<String, Object>> results = chatHistoryService.executeQuery(sql, tenantPhone);
            if (results.isEmpty()) {
                return ToolResult.ok("查询结果为空");
            }
            return ToolResult.ok(JSON.toJSONString(results));
        } catch (IllegalArgumentException e) {
            return ToolResult.error(e.getMessage());
        } catch (Exception e) {
            log.error("[ExecuteSqlTool] SQL执行失败", e);
            return ToolResult.error("SQL执行失败");
        }
    }
}
