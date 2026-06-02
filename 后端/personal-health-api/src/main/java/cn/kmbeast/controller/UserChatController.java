package cn.kmbeast.controller;

import cn.kmbeast.aop.Protector;
import cn.kmbeast.context.LocalThreadHolder;
import cn.kmbeast.crm.agent.StreamingReActAgent;
import cn.kmbeast.crm.agent.tool.ToolContext;
import cn.kmbeast.crm.dto.CrmChatRequest;
import cn.kmbeast.crm.dto.CrmChatResponse;
import cn.kmbeast.crm.workflow.SeaChatWorkflow;
import cn.kmbeast.mapper.UserMapper;
import cn.kmbeast.pojo.entity.User;
import cn.kmbeast.pojo.api.ApiResult;
import cn.kmbeast.pojo.api.Result;
import cn.kmbeast.pojo.vo.MessageVO;
import cn.kmbeast.service.MessageService;
import com.alibaba.fastjson2.JSONObject;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

/**
 * 用户端聊天接口（JWT认证）
 */
@Slf4j
@RestController
@RequestMapping("/user/chat")
public class UserChatController {

    @Resource
    private SeaChatWorkflow seaChatWorkflow;

    @Resource
    private StreamingReActAgent streamingReActAgent;

    @Resource
    private UserMapper userMapper;

    /**
     * 同步聊天
     */
    @Protector
    @PostMapping
    public Result<Map<String, Object>> chat(@RequestBody UserChatRequest request) {
        Integer userId = LocalThreadHolder.getUserId();
        User user = userMapper.getByActive(User.builder().id(userId).build());
        if (user == null) {
            return ApiResult.error("用户不存在");
        }

        // 构建CRM请求
        CrmChatRequest crmRequest = new CrmChatRequest();
        crmRequest.setPhoneNumber(user.getUserAccount());
        crmRequest.setQuery(request.getMessage());
        crmRequest.setSessionId(request.getSessionId());

        // 调用CRM工作流
        CrmChatResponse response = seaChatWorkflow.processChat(crmRequest);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("reply", response.getReply());
        result.put("sessionId", response.getSessionId());
        result.put("toolsUsed", response.getToolsUsed());
        result.put("isNewUser", response.getIsNewUser());

        return ApiResult.success(result);
    }

    /**
     * 流式聊天（SSE）
     */
    @Protector
    @PostMapping("/stream")
    public void chatStream(@RequestBody UserChatRequest request,
                           HttpServletResponse response) {
        Integer userId = LocalThreadHolder.getUserId();
        User user = userMapper.getByActive(User.builder().id(userId).build());
        if (user == null) {
            response.setContentType("application/json");
            try {
                response.getWriter().write("{\"code\":400,\"message\":\"用户不存在\"}");
            } catch (IOException ignored) {}
            return;
        }

        // 设置SSE响应头
        response.setContentType("text/event-stream");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("Connection", "keep-alive");
        response.setHeader("X-Accel-Buffering", "no");

        String phoneNumber = user.getUserAccount();
        String query = request.getMessage();

        if (query == null || query.trim().isEmpty()) {
            return;
        }

        // 生成会话ID
        String sessionId = request.getSessionId();
        if (sessionId == null || sessionId.trim().isEmpty()) {
            sessionId = UUID.randomUUID().toString();
        }

        // 设置工具上下文
        ToolContext.setPhoneAndUserId(phoneNumber, userId);

        // 构建消息
        CrmChatRequest crmRequest = new CrmChatRequest();
        crmRequest.setPhoneNumber(phoneNumber);
        crmRequest.setQuery(query);
        crmRequest.setSessionId(sessionId);

        List<Map<String, String>> messages = seaChatWorkflow.buildInitialMessages(crmRequest);

        StringBuilder answerCollector = new StringBuilder();

        try {
            PrintWriter writer = response.getWriter();

            StreamingReActAgent.StreamCallback callback = (eventName, jsonData) -> {
                if ("answer_chunk".equals(eventName)) {
                    JSONObject json = JSONObject.parseObject(jsonData);
                    String content = json.getString("content");
                    if (content != null) {
                        answerCollector.append(content);
                    }
                }
                try {
                    writer.write("event: " + eventName + "\n");
                    writer.write("data: " + jsonData + "\n\n");
                    writer.flush();
                } catch (Exception e) {
                    log.error("[UserChat] SSE写入失败", e);
                }
            };

            streamingReActAgent.runStreaming(messages, callback);

            // 发送会话信息
            Map<String, Object> sessionInfo = new LinkedHashMap<>();
            sessionInfo.put("sessionId", sessionId);
            sessionInfo.put("userId", userId);
            writer.write("event: session_info\n");
            writer.write("data: " + JSONObject.toJSONString(sessionInfo) + "\n\n");
            writer.flush();

            writer.close();
        } catch (Exception e) {
            log.error("[UserChat] 流式响应异常", e);
        } finally {
            ToolContext.clear();
        }
    }

    /**
     * 获取聊天历史
     */
    @Protector
    @GetMapping("/history")
    public Result<List<Map<String, Object>>> getHistory() {
        Integer userId = LocalThreadHolder.getUserId();
        User user = userMapper.getByActive(User.builder().id(userId).build());
        if (user == null) {
            return ApiResult.error("用户不存在");
        }
        List<Map<String, Object>> history = seaChatWorkflow.getHistory(user.getUserAccount());
        return ApiResult.success(history);
    }

    /**
     * 聊天请求DTO
     */
    @Data
    public static class UserChatRequest {
        private String message;
        private String sessionId;
    }
}
