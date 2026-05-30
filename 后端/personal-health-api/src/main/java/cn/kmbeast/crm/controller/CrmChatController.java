package cn.kmbeast.crm.controller;

import cn.kmbeast.crm.agent.StreamingReActAgent;
import cn.kmbeast.crm.agent.ToolRegistry;
import cn.kmbeast.crm.agent.tool.ToolContext;
import cn.kmbeast.crm.config.CrmConfig;
import cn.kmbeast.crm.dto.CrmChatRequest;
import cn.kmbeast.crm.dto.CrmChatResponse;
import cn.kmbeast.crm.dto.SqlQueryRequest;
import cn.kmbeast.crm.sqlite.SqliteChatHistoryService;
import cn.kmbeast.crm.workflow.SeaChatWorkflow;
import cn.kmbeast.mapper.UserMapper;
import cn.kmbeast.pojo.api.ApiResult;
import cn.kmbeast.pojo.api.Result;
import cn.kmbeast.pojo.entity.User;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

@Slf4j
@RestController
@RequestMapping(value = "/crm")
public class CrmChatController {

    @Resource
    private SeaChatWorkflow seaChatWorkflow;

    @Resource
    private StreamingReActAgent streamingReActAgent;

    @Resource
    private SqliteChatHistoryService chatHistoryService;

    @Resource
    private UserMapper userMapper;

    @Resource
    private ToolRegistry toolRegistry;

    @Resource
    private CrmConfig crmConfig;

    @PostMapping(value = "/chat")
    public Result<CrmChatResponse> chat(@RequestBody CrmChatRequest request,
                                         @RequestHeader(value = "X-CRM-API-Key", required = false) String apiKey) {
        if (!isValidApiKey(apiKey)) {
            return ApiResult.error("无效的API密钥");
        }
        CrmChatResponse response = seaChatWorkflow.processChat(request);
        return ApiResult.success(response);
    }

    @PostMapping(value = "/chat/stream")
    public void chatStream(@RequestBody CrmChatRequest request,
                           HttpServletResponse response,
                           @RequestHeader(value = "X-CRM-API-Key", required = false) String apiKey) {
        if (!isValidApiKey(apiKey)) {
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            try {
                response.getWriter().write("{\"code\":401,\"message\":\"无效的API密钥\"}");
                response.getWriter().flush();
            } catch (IOException ignored) {}
            return;
        }
        response.setContentType("text/event-stream");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("Connection", "keep-alive");
        response.setHeader("X-Accel-Buffering", "no");

        String phoneNumber = request.getPhoneNumber();
        String query = request.getQuery();

        if (phoneNumber == null || phoneNumber.trim().isEmpty() ||
                query == null || query.trim().isEmpty()) {
            return;
        }

        String sessionId;
        if (request.getSessionId() != null && !request.getSessionId().trim().isEmpty()) {
            sessionId = request.getSessionId();
        } else {
            sessionId = UUID.randomUUID().toString();
        }

        chatHistoryService.saveMessage(phoneNumber, sessionId, "user", query, null, null);

        Integer userId = null;
        try {
            User user = userMapper.findByPhone(phoneNumber);
            userId = user != null ? user.getId() : null;
        } catch (Exception ignored) {}
        ToolContext.setPhoneAndUserId(phoneNumber, userId);

        List<Map<String, String>> messages = seaChatWorkflow.buildInitialMessages(request);

        StringBuilder answerCollector = new StringBuilder();

        try {
            PrintWriter writer = response.getWriter();

            StreamingReActAgent.StreamCallback callback = (eventName, jsonData) -> {
                if ("answer_chunk".equals(eventName)) {
                    com.alibaba.fastjson2.JSONObject json =
                            com.alibaba.fastjson2.JSON.parseObject(jsonData);
                    String content = json.getString("content");
                    if (content != null) {
                        answerCollector.append(content);
                    }
                }
                writer.write("event: " + eventName + "\n");
                writer.write("data: " + jsonData + "\n\n");
                writer.flush();
            };

            streamingReActAgent.runStreaming(messages, callback);

            Map<String, Object> sessionInfo = new LinkedHashMap<>();
            sessionInfo.put("sessionId", sessionId);
            sessionInfo.put("phoneNumber", phoneNumber);
            writer.write("event: session_info\n");
            writer.write("data: " + JSONObject.toJSONString(sessionInfo) + "\n\n");
            writer.flush();

            if (answerCollector.length() > 0) {
                Map<String, Object> metadata = new LinkedHashMap<>();
                metadata.put("session_id", sessionId);
                chatHistoryService.saveMessage(phoneNumber, sessionId, "assistant",
                        answerCollector.toString(), null, metadata);
            }

            writer.close();
        } catch (IOException e) {
            log.error("[CRM] 流式响应异常", e);
        } finally {
            ToolContext.clear();
        }
    }

    @GetMapping(value = "/history/{phone}")
    public Result<List<Map<String, Object>>> getHistory(@PathVariable("phone") String phone) {
        List<Map<String, Object>> history = seaChatWorkflow.getHistory(phone);
        return ApiResult.success(history);
    }

    @GetMapping(value = "/sqlite/stats")
    public Result<Map<String, Object>> getSqliteStats(
            @RequestHeader(value = "X-CRM-API-Key", required = false) String apiKey) {
        if (!isValidApiKey(apiKey)) {
            return ApiResult.error("无效的API密钥");
        }
        return ApiResult.success(seaChatWorkflow.getSqliteStats());
    }

    @PostMapping(value = "/sql/query")
    public Result<?> executeSql(@RequestBody SqlQueryRequest request,
                                @RequestHeader(value = "X-CRM-API-Key", required = false) String apiKey) {
        if (!isValidApiKey(apiKey)) {
            return ApiResult.error("无效的API密钥");
        }
        try {
            List<Map<String, Object>> results = seaChatWorkflow.executeSqlQuery(request.getQuery());
            return ApiResult.success(results);
        } catch (IllegalArgumentException e) {
            return ApiResult.error(e.getMessage());
        } catch (Exception e) {
            return ApiResult.error("SQL查询失败: " + e.getMessage());
        }
    }

    @GetMapping(value = "/health")
    public Result<Map<String, Object>> healthCheck() {
        Map<String, Object> health = new LinkedHashMap<>();
        health.put("status", "UP");
        health.put("timestamp", System.currentTimeMillis());

        Map<String, Object> sqlite = new LinkedHashMap<>();
        try {
            sqlite.put("status", "UP");
            sqlite.put("stats", seaChatWorkflow.getSqliteStats());
        } catch (Exception e) {
            sqlite.put("status", "DOWN");
            sqlite.put("error", e.getMessage());
        }
        health.put("sqlite", sqlite);

        Map<String, Object> tools = new LinkedHashMap<>();
        tools.put("count", toolRegistry.getAll().size());
        health.put("tools", tools);

        return ApiResult.success(health);
    }

    @PostMapping(value = "/sqlite/backup")
    public Result<Map<String, Object>> backupSqlite() {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            String dbPath = crmConfig.getSqliteDbPath();
            java.io.File source = new java.io.File(dbPath);
            if (!source.exists()) {
                return ApiResult.error("数据库文件不存在: " + dbPath);
            }
            String timestamp = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss").format(new java.util.Date());
            java.io.File backup = new java.io.File(source.getParent(), "chat_history_backup_" + timestamp + ".db");
            java.nio.file.Files.copy(source.toPath(), backup.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            result.put("success", true);
            result.put("backup_path", backup.getAbsolutePath());
            result.put("backup_size", backup.length());
            log.info("[CRM] SQLite备份完成: {}", backup.getAbsolutePath());
            return ApiResult.success(result);
        } catch (Exception e) {
            log.error("[CRM] SQLite备份失败", e);
            return ApiResult.error("备份失败: " + e.getMessage());
        }
    }

    private boolean isValidApiKey(String apiKey) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            return false;
        }
        String validKey = crmConfig.getCrmApiKey();
        return validKey != null && validKey.equals(apiKey);
    }
}
