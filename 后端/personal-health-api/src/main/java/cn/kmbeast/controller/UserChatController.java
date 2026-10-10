package cn.kmbeast.controller;

import cn.kmbeast.aop.Protector;
import cn.kmbeast.context.LocalThreadHolder;
import cn.kmbeast.crm.agent.StreamingReActAgent;
import cn.kmbeast.crm.agent.tool.ToolContext;
import cn.kmbeast.crm.dto.CrmChatRequest;
import cn.kmbeast.crm.dto.CrmChatResponse;
import cn.kmbeast.crm.workflow.SeaChatWorkflow;
import cn.kmbeast.core.agent.ChatRouting;
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

    /** 2026-10-05：仅用于把角色编码换成中文名回给前端（路由本身在 SeaChatWorkflow 里） */
    @Resource
    private cn.kmbeast.core.agent.AgentCoordinator agentCoordinator;

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

        // 2026-10-05：意图路由与数据引导统一在 SeaChatWorkflow 内部执行，
        // 由真正执行对话的 ReActAgent 自己 set 角色 → run → finally 清理。
        //
        // 上一版这里把 roleHint 设在了 streamingReActAgent 上，但 processChat 内部跑的
        // 是另一个 ReActAgent bean，角色指令从未进过 prompt —— 同步链路的 6 角色路由
        // 是彻底空转的。根因是「上下文靠调用方手动传递」，漏一处就失效，
        // 所以改由执行方自己开入口，Controller 只负责取结果回显。
        //
        // ⚠️ 必须把 JWT 里的 userId 传进去：手机号存的是 user_account（登录名），
        // 而 user.phone 字段在库里大量为 NULL，工作流按 phone 反查必然查不到，
        // 会导致槽位不落库、get_health_data 工具罢工。
        CrmChatResponse response = seaChatWorkflow.processChat(crmRequest, userId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("reply", response.getReply());
        result.put("sessionId", response.getSessionId());
        result.put("toolsUsed", response.getToolsUsed());
        result.put("isNewUser", response.getIsNewUser());
        // 回传本轮识别到的角色与追问话术，便于前端做引导展示
        result.put("agentType", response.getAgentType());
        result.put("agentName", agentCoordinator.getAgentRole(response.getAgentType()).getName());
        result.put("guidance", response.getGuidance());

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
        // 阶段 2：标注调用方为用户（roleId=2），供双轨健康记录查询工具判定身份
        ToolContext.setIdentity(2, null);
        // 2026-10-07 P1-3：补齐工具层审计的 HTTP 上下文（客户端 IP / UA / 方法 / URL），
        // 供跨线程透传后在工具内写入 audit_log。
        ToolContext.applyHttpMetaFromRequest();

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

            // 2026-10-05：与同步链路共用同一个 route()，避免「同步走医生、流式走默认」。
            // route() 内部做：意图识别 → 槽位抽取 → 落库 → 生成缺失项追问话术。
            ChatRouting routing = seaChatWorkflow.route(userId, query);

            // ⚠️ 不能用 Map.of()：guidance 在档案完整时为 null，而 Map.of 不接受 null value，
            // 会直接抛 NPE 把整个 SSE 流打断（general_assistant 关键词表为空，
            // 绝大多数普通问句都会命中它 → 必然踩中）。这里显式允许 null。
            Map<String, Object> agentInfo = new LinkedHashMap<>();
            agentInfo.put("agentType", routing.getAgentType());
            agentInfo.put("agentName", agentCoordinator.getAgentRole(routing.getAgentType()).getName());
            agentInfo.put("extractedSlots", routing.getExtractedSlots());
            callback.onEvent("agent_info", JSONObject.toJSONString(agentInfo));

            // runWithContextStreaming：set 角色与引导 → 跑流式 ReAct → finally 必清 ThreadLocal，
            // 不用再靠调用方自己记得 clear（Tomcat 线程复用会串号）。
            streamingReActAgent.runWithContextStreaming(messages, callback,
                    routing.getAgentType(), routing.getGuidance(), routing.getIntentHint());
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
            // 角色/引导的 ThreadLocal 已由 runWithContextStreaming 的 finally 清理，
            // 这里不再重复调用——重复清理不会出错，但会让人误以为外层才是唯一的保障。
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
