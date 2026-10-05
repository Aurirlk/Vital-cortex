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

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
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

    /** 2026-10-04：意图路由（原为死代码，角色定义形同虚设） */
    @Resource
    private cn.kmbeast.core.agent.AgentCoordinator agentCoordinator;

    /** 2026-10-04：数据引导引擎（抽取健康槽位并生成追问话术） */
    @Resource
    private cn.kmbeast.core.harness.HarnessEngine harnessEngine;

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

        // 2026-10-04：同步路径与流式路径做完全相同的四件事
        //（识别角色 → 槽位抽取 → 落库 → 生成追问指令），保证两条链路行为一致。
        //
        // 上一版注释写的「该链路走 SeaChatWorkflow 内部，不经过 ReActAgent」是错的：
        // SeaChatWorkflow 内部注入的就是 ReActAgent，最终调 reActAgent.run(userMessages)，
        // 而 enhanceSystemPrompt() 正是在 run() 构建初始消息时生效的。
        // 所以在 Controller 设好 roleHint / guidancePrompt 即可，不必改 workflow 内部编排。
        String agentType = agentCoordinator.identifyAgent(request.getMessage());
        streamingReActAgent.setRoleHint(agentType);

        Map<String, String> extracted = harnessEngine.extractSlots(request.getMessage());
        if (!extracted.isEmpty()) {
            harnessEngine.persistSlots(userId, extracted);
        }
        String guidance = harnessEngine.buildGuidancePrompt(harnessEngine.currentUserMissingSlots());
        streamingReActAgent.setGuidancePrompt(guidance);

        // 构建CRM请求
        CrmChatRequest crmRequest = new CrmChatRequest();
        crmRequest.setPhoneNumber(user.getUserAccount());
        crmRequest.setQuery(request.getMessage());
        crmRequest.setSessionId(request.getSessionId());

        // 调用CRM工作流
        CrmChatResponse response;
        try {
            response = seaChatWorkflow.processChat(crmRequest);
        } finally {
            // BaseReActAgent 是单例，roleHint/guidancePrompt 存在 ThreadLocal 中。
            // Tomcat 线程池会复用线程，不清理必然让下一个请求串用上一位的角色——
            // 属于上下文泄露，必须清。
            streamingReActAgent.clearConversationContext();
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("reply", response.getReply());
        result.put("sessionId", response.getSessionId());
        result.put("toolsUsed", response.getToolsUsed());
        result.put("isNewUser", response.getIsNewUser());
        // 回传本轮识别到的角色、新采集到的槽位与追问话术，便于前端做引导展示
        result.put("agentType", agentType);
        result.put("agentName", agentCoordinator.getAgentRole(agentType).getName());
        result.put("extractedSlots", extracted.keySet());
        result.put("guidance", guidance);

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

            // 2026-10-04：把 Agent 路由与数据引导真正接进对话主流程。
            // 此前 AgentCoordinator 的 6 个角色与 HarnessEngine 从未被调用，
            // 所有问题都走同一套 prompt，角色定义形同虚设。
            // 顺序：① 意图识别定角色 → ② 槽位抽取（用户可能已口头提供）→
            //       ③ 落库 → ④ 生成缺失项追问指令 → ⑤ 注入 ReAct。
            String agentType = agentCoordinator.identifyAgent(query);
            streamingReActAgent.setRoleHint(agentType);

            Map<String, String> extracted = harnessEngine.extractSlots(query);
            if (!extracted.isEmpty()) {
                harnessEngine.persistSlots(userId, extracted);
            }
            streamingReActAgent.setGuidancePrompt(
                    harnessEngine.buildGuidancePrompt(harnessEngine.currentUserMissingSlots()));

            callback.onEvent("agent_info", JSONObject.toJSONString(
                    java.util.Map.of("agentType", agentType,
                            "agentName", agentCoordinator.getAgentRole(agentType).getName(),
                            "extractedSlots", extracted.keySet())));

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
            // 2026-10-04：必须清理角色/引导的 ThreadLocal，
            // 否则 Tomcat 线程复用时，下一个请求（可能是另一个用户）
            // 会读到本次遗留的角色指令与追问话术 —— 上下文串号。
            streamingReActAgent.clearConversationContext();
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
