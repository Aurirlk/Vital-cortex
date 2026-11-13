package cn.kmbeast.controller;

import cn.kmbeast.aop.Protector;
import cn.kmbeast.context.LocalThreadHolder;
import cn.kmbeast.crm.agent.StreamingReActAgent;
import cn.kmbeast.crm.agent.tool.ToolContext;
import cn.kmbeast.crm.dto.CrmChatRequest;
import cn.kmbeast.crm.dto.CrmChatResponse;
import cn.kmbeast.crm.sqlite.SqliteChatHistoryService;
import cn.kmbeast.crm.workflow.SeaChatWorkflow;
import cn.kmbeast.core.agent.Audience;
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

    /**
     * 会话历史落库。与同步链路（{@link SeaChatWorkflow#processChat}）共用同一份存储，
     * 因此 {@code /user/chat/history} 能读到流式链路产生的记录。
     */
    @Resource
    private SqliteChatHistoryService chatHistoryService;

    /**
     * 仅用于把角色编码换成中文名回给前端（路由本身在 SeaChatWorkflow 里）。
     *
     * <p>阶段 1 起不再直连 {@code AgentCoordinator}：它的
     * {@code getAgentRole(key)} 查不到时<b>返回「全能助手」</b>，
     * 而管理员新建的智能体它根本不认识 —— 结果是一个叫「睡眠顾问」的智能体
     * 在回执里显示成「全能助手」。改走编排器的 {@code agentName()}，
     * 注册表优先，见 {@link cn.kmbeast.core.agent.orchestrator.AgentOrchestrator#agentName}。</p>
     */
    @Resource
    private cn.kmbeast.core.agent.orchestrator.AgentOrchestrator agentOrchestrator;

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
        //
        // 阶段 3（多端解耦）：显式声明这是用户端。本类所有链路都把调用方身份标成
        // roleId=2（ToolContext.setIdentity），即用户端频道 —— 路由的端过滤必须与
        // 这个身份判定同口径，否则会出现「工具层当你是用户、路由层却把医生端 Agent
        // 派给你」的割裂。
        CrmChatResponse response = seaChatWorkflow.processChat(crmRequest, userId, Audience.USER);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("reply", response.getReply());
        result.put("sessionId", response.getSessionId());
        result.put("toolsUsed", response.getToolsUsed());
        result.put("isNewUser", response.getIsNewUser());
        // 回传本轮识别到的角色与追问话术，便于前端做引导展示
        result.put("agentType", response.getAgentType());
        result.put("agentName", agentOrchestrator.agentName(response.getAgentType()));
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

        String query = request.getMessage();
        // 空问题必须在设 SSE 头之前挡掉：设完头再 return，客户端拿到的是
        // 「Content-Type: text/event-stream + 0 字节」，与卡住无法区分（本方法上面
        // 处理「用户不存在」用的就是这个写法，口径保持一致）。
        if (query == null || query.trim().isEmpty()) {
            response.setContentType("application/json");
            try {
                response.getWriter().write("{\"code\":400,\"message\":\"请输入您的问题\"}");
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

        // 拼装必须在落库之前：buildInitialMessages 会读历史、并按「历史是否为空」判定新用户。
        // 先落库会让当前这句既从历史里读回来、又被显式加一遍（提示词里出现两遍），
        // 同时 isNewUser 永远为 false，新用户欢迎语再也进不去。
        List<Map<String, String>> messages = seaChatWorkflow.buildInitialMessages(crmRequest);

        // 2026-10-12 修复：用户提问此前从不落库，导致多轮上下文、历史记录、
        // 会话统计与审计追溯全部归零（同链路的 CrmChatController:91 是存的）。
        // 失败只告警，不阻断回答。
        if (!chatHistoryService.saveMessage(phoneNumber, sessionId, "user", query, null, null)) {
            log.warn("[UserChat] 用户消息保存失败: phone={}, session={}", phoneNumber, sessionId);
        }

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
            // 阶段 3：端同样要传 —— 同步链路已经传了，这里漏传会让同一个用户在
            // 「打字发送」和「流式发送」两条路径上被分到不同的 Agent。
            ChatRouting routing = seaChatWorkflow.route(userId, query, Audience.USER);

            // ⚠️ 不能用 Map.of()：guidance 在档案完整时为 null，而 Map.of 不接受 null value，
            // 会直接抛 NPE 把整个 SSE 流打断（general_assistant 关键词表为空，
            // 绝大多数普通问句都会命中它 → 必然踩中）。这里显式允许 null。
            Map<String, Object> agentInfo = new LinkedHashMap<>();
            agentInfo.put("agentType", routing.getAgentType());
            agentInfo.put("agentName", agentOrchestrator.agentName(routing.getAgentType()));
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

            // 2026-10-12 修复：助手回复此前同样不落库（answerCollector 全程只写不读）。
            // 口径对齐 CrmChatController:153 —— 有内容才存，失败只告警不打断 SSE。
            if (answerCollector.length() > 0) {
                Map<String, Object> metadata = new LinkedHashMap<>();
                metadata.put("session_id", sessionId);
                metadata.put("agent_type", routing.getAgentType());
                if (!chatHistoryService.saveMessage(phoneNumber, sessionId, "assistant",
                        answerCollector.toString(), null, metadata)) {
                    log.warn("[UserChat] AI回答保存失败: phone={}, session={}", phoneNumber, sessionId);
                }
            }

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
