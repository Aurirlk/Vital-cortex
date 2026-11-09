package cn.kmbeast.controller;

import cn.kmbeast.aop.Protector;
import cn.kmbeast.context.LocalThreadHolder;
import cn.kmbeast.core.agent.Audience;
import cn.kmbeast.core.agent.ChatRouting;
import cn.kmbeast.core.agent.orchestrator.AgentOrchestrator;
import cn.kmbeast.crm.agent.StreamingReActAgent;
import cn.kmbeast.crm.agent.tool.ToolContext;
import cn.kmbeast.crm.dto.CrmChatRequest;
import cn.kmbeast.crm.dto.CrmChatResponse;
import cn.kmbeast.crm.sqlite.SqliteChatHistoryService;
import cn.kmbeast.crm.workflow.SeaChatWorkflow;
import cn.kmbeast.pojo.api.ApiResult;
import cn.kmbeast.pojo.api.Result;
import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.fastjson2.JSONObject;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 医生端 ReAct 对话接口（阶段 6，2026-11-09）。
 *
 * <p><b>补的是什么缺口</b>：{@code SeaChatWorkflow#route} 早已支持
 * {@link Audience#DOCTOR}，{@code DataQueryTool} 的医生分支（patientId + 接诊授权重查）
 * 也早已写好，但全仓没有任何 {@code setIdentity(3, ...)} 的调用方 ——
 * 医生端只有 {@code /doctor/data-agent}（NL2SQL 十步流水线），
 * 没有一条「带工具调用的日常问诊对话」链路。本 Controller 把 ReAct 链路接到医生端。</p>
 *
 * <p><b>身份铁律</b>：{@code doctorId} 只取 {@link LocalThreadHolder#getUserId()}
 * （医生登录后 JWT 里的 {@code hospital_doctor.id}，3001+ 段位），
 * 由 {@code ToolContext.setIdentity(3, doctorId)} 注入，工具层据此走医生轨；
 * <b>绝不</b>接受请求体里的 doctorId/patientId 作为身份。
 * 同时 {@code ToolContext.applyHttpMetaFromRequest()} 补齐审计字段（P1-3 范式）。
 * 请求结束 {@code finally ToolContext.clear()} 防 Tomcat 线程复用串号。</p>
 *
 * <p><b>隔离</b>：{@code /doctor/**} 由 {@code core/auth/DoctorIsolation} 保证仅医生可进，
 * {@code @Protector(role="医生")} 二次把关（与 {@link DoctorDataAgentController} 同口径）。
 * 端点本身不接收任何 userId 入参。</p>
 *
 * <p><b>为什么同步链路也要改 SeaChatWorkflow</b>：{@code processChat} 原先无条件
 * {@code setIdentity(2, null)}，会把这里注入的医生身份覆盖成用户。已改为按 audience 判定，
 * 用户端行为不变。</p>
 */
@Slf4j
@RestController
@RequestMapping("/doctor/chat")
public class DoctorChatController {

    @Resource
    private SeaChatWorkflow seaChatWorkflow;

    @Resource
    private StreamingReActAgent streamingReActAgent;

    @Resource
    private SqliteChatHistoryService chatHistoryService;

    @Resource
    private AgentOrchestrator agentOrchestrator;

    /**
     * 同步对话。
     *
     * <p>复用 {@link UserChatController} 的同步范式，但 audience 传
     * {@link Audience#DOCTOR}，身份写成 (3, doctorId)。</p>
     */
    @Protector(role = "医生")
    @SentinelResource(value = "ai:doctorChat",
            blockHandler = "doctorChatBlocked", blockHandlerClass = DoctorChatController.class)
    @PostMapping
    public Result<Map<String, Object>> chat(@RequestBody DoctorChatRequest request) {
        Integer doctorId = LocalThreadHolder.getUserId();
        if (doctorId == null) {
            return ApiResult.error("未登录，无法使用 AI 助手");
        }
        String query = request == null ? null : request.getMessage();
        if (query == null || query.trim().isEmpty()) {
            return ApiResult.error("请输入您的问题");
        }

        String historyKey = historyKey(doctorId);
        String sessionId = sessionIdOf(request);

        // 身份只认 JWT：doctorId 来自 ThreadLocal，角色固定 3=医生。
        ToolContext.setPhoneAndUserId(historyKey, doctorId);
        ToolContext.setIdentity(3, doctorId);
        ToolContext.applyHttpMetaFromRequest();
        try {
            CrmChatRequest crmRequest = new CrmChatRequest();
            crmRequest.setPhoneNumber(historyKey);
            crmRequest.setQuery(query);
            crmRequest.setSessionId(sessionId);

            CrmChatResponse response = seaChatWorkflow.processChat(crmRequest, doctorId, Audience.DOCTOR);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("reply", response.getReply());
            result.put("sessionId", response.getSessionId());
            result.put("toolsUsed", response.getToolsUsed());
            result.put("isNewUser", response.getIsNewUser());
            result.put("agentType", response.getAgentType());
            result.put("agentName", agentOrchestrator.agentName(response.getAgentType()));
            result.put("guidance", response.getGuidance());
            return ApiResult.success(result);
        } finally {
            ToolContext.clear();
        }
    }

    /**
     * 流式对话（SSE）。事件协议与 {@code /user/chat/stream} 一致：
     * {@code agent_info} / {@code answer_chunk} / {@code session_info} / {@code error}。
     */
    @Protector(role = "医生")
    @SentinelResource(value = "ai:doctorChatStream",
            blockHandler = "doctorChatStreamBlocked", blockHandlerClass = DoctorChatController.class)
    @PostMapping("/stream")
    public void chatStream(@RequestBody DoctorChatRequest request,
                           HttpServletResponse response) {
        Integer doctorId = LocalThreadHolder.getUserId();
        if (doctorId == null) {
            writeJsonError(response, "{\"code\":401,\"message\":\"未登录，无法使用 AI 助手\"}");
            return;
        }
        String query = request == null ? null : request.getMessage();
        // 空问题必须在设 SSE 头之前挡掉，否则客户端拿到「text/event-stream + 0 字节」，
        // 与卡住无法区分（与 UserChatController 同口径）。
        if (query == null || query.trim().isEmpty()) {
            writeJsonError(response, "{\"code\":400,\"message\":\"请输入您的问题\"}");
            return;
        }

        response.setContentType("text/event-stream");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("Connection", "keep-alive");
        response.setHeader("X-Accel-Buffering", "no");

        String historyKey = historyKey(doctorId);
        String sessionId = sessionIdOf(request);

        ToolContext.setPhoneAndUserId(historyKey, doctorId);
        ToolContext.setIdentity(3, doctorId);
        ToolContext.applyHttpMetaFromRequest();

        try {
            PrintWriter writer = response.getWriter();
            StringBuilder answerCollector = new StringBuilder();

            StreamingReActAgent.StreamCallback callback = (eventName, jsonData) -> {
                if ("answer_chunk".equals(eventName)) {
                    try {
                        JSONObject json = JSONObject.parseObject(jsonData);
                        String content = json.getString("content");
                        if (content != null) {
                            answerCollector.append(content);
                        }
                    } catch (Exception ignored) {
                        // 非 JSON 的 chunk 直接转发，不阻断 SSE
                    }
                }
                try {
                    writer.write("event: " + eventName + "\n");
                    writer.write("data: " + jsonData + "\n\n");
                    writer.flush();
                } catch (Exception e) {
                    log.error("[DoctorChat] SSE 写入失败", e);
                }
            };

            CrmChatRequest crmRequest = new CrmChatRequest();
            crmRequest.setPhoneNumber(historyKey);
            crmRequest.setQuery(query);
            crmRequest.setSessionId(sessionId);

            // 拼装必须在落库之前（buildInitialMessages 会按历史是否为空判定新用户）。
            List<Map<String, String>> messages = seaChatWorkflow.buildInitialMessages(crmRequest);
            if (!chatHistoryService.saveMessage(historyKey, sessionId, "user", query, null, null)) {
                log.warn("[DoctorChat] 医生消息保存失败: doctor={}, session={}", doctorId, sessionId);
            }

            ChatRouting routing = seaChatWorkflow.route(doctorId, query, Audience.DOCTOR);
            // 不用 Map.of()：guidance/slots 可能为 null，Map.of 不接受 null value。
            Map<String, Object> agentInfo = new LinkedHashMap<>();
            agentInfo.put("agentType", routing.getAgentType());
            agentInfo.put("agentName", agentOrchestrator.agentName(routing.getAgentType()));
            agentInfo.put("extractedSlots", routing.getExtractedSlots());
            callback.onEvent("agent_info", JSONObject.toJSONString(agentInfo));

            streamingReActAgent.runWithContextStreaming(messages, callback,
                    routing.getAgentType(), routing.getGuidance(), routing.getIntentHint());

            Map<String, Object> sessionInfo = new LinkedHashMap<>();
            sessionInfo.put("sessionId", sessionId);
            sessionInfo.put("doctorId", doctorId);
            writer.write("event: session_info\n");
            writer.write("data: " + JSONObject.toJSONString(sessionInfo) + "\n\n");
            writer.flush();

            if (answerCollector.length() > 0) {
                Map<String, Object> metadata = new LinkedHashMap<>();
                metadata.put("session_id", sessionId);
                metadata.put("agent_type", routing.getAgentType());
                if (!chatHistoryService.saveMessage(historyKey, sessionId, "assistant",
                        answerCollector.toString(), null, metadata)) {
                    log.warn("[DoctorChat] AI 回答保存失败: doctor={}, session={}", doctorId, sessionId);
                }
            }
            writer.close();
        } catch (Exception e) {
            log.error("[DoctorChat] 流式响应异常", e);
            try {
                PrintWriter writer = response.getWriter();
                writer.write("event: error\n");
                writer.write("data: {\"message\":\"AI 助手暂时不可用，请稍后重试\"}\n\n");
                writer.flush();
            } catch (Exception ignored) {
                // 响应已提交则放弃
            }
        } finally {
            ToolContext.clear();
        }
    }

    /** Sentinel 限流兜底（同步）。参数必须与被保护方法一致 + BlockException。 */
    public static Result<Map<String, Object>> doctorChatBlocked(DoctorChatRequest request,
                                                                 BlockException ex) {
        log.warn("[Sentinel] 医生端 AI 对话触发限流");
        return ApiResult.error("AI 服务繁忙，请稍后再试");
    }

    /** Sentinel 限流兜底（流式）：以 SSE error 事件告知前端，保持流式协议。 */
    public static void doctorChatStreamBlocked(DoctorChatRequest request,
                                               HttpServletResponse response,
                                               BlockException ex) {
        log.warn("[Sentinel] 医生端 AI 流式对话触发限流");
        try {
            response.setContentType("text/event-stream");
            response.setCharacterEncoding("UTF-8");
            PrintWriter w = response.getWriter();
            w.write("event: error\n");
            w.write("data: {\"message\":\"AI 服务繁忙，请稍后再试\"}\n\n");
            w.flush();
        } catch (Exception e) {
            log.warn("[Sentinel] 医生端流式限流兜底写入失败: {}", e.getMessage());
        }
    }

    /**
     * 医生端在 SQLite {@code chat_history} 里的独立命名空间。
     *
     * <p>用户端用 {@code user_account}（登录名/手机号），医生端若也用裸 id
     * 会与用户端撞号（历史串在一起）。加 {@code doctor:} 前缀彻底分开，
     * 且医生没有 {@code user} 表记录，本来就查不到 user_account。</p>
     */
    private static String historyKey(Integer doctorId) {
        return "doctor:" + doctorId;
    }

    private static String sessionIdOf(DoctorChatRequest request) {
        String sessionId = request == null ? null : request.getSessionId();
        if (sessionId == null || sessionId.trim().isEmpty()) {
            return UUID.randomUUID().toString();
        }
        return sessionId;
    }

    private static void writeJsonError(HttpServletResponse response, String body) {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        try {
            response.getWriter().write(body);
        } catch (IOException ignored) {
            // 响应已提交则放弃
        }
    }

    /** 聊天请求 DTO（不接收 doctorId / patientId —— 身份只从 ThreadLocal 取）。 */
    @Data
    public static class DoctorChatRequest {
        private String message;
        private String sessionId;
    }
}
