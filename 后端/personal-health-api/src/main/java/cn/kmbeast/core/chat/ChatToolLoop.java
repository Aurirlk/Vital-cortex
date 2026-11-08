package cn.kmbeast.core.chat;

import cn.kmbeast.context.LocalThreadHolder;
import cn.kmbeast.core.nl2sql.DataAgentConfigHolder;
import cn.kmbeast.crm.agent.ToolArgsValidator;
import cn.kmbeast.crm.agent.ToolRegistry;
import cn.kmbeast.crm.agent.model.ToolResult;
import cn.kmbeast.crm.agent.tool.Tool;
import cn.kmbeast.crm.agent.tool.ToolContext;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * AI 问诊（{@code /ai/chat}）工具调用循环（2026-11-08 阶段5，Q6 默认开启）。
 *
 * <p><b>职责</b>：在聊天消息数组上反复调用 LLM，直到模型不再请求工具或达到轮次上限，
 * 把 assistant（含 {@code tool_calls}）与 tool 结果消息回写进消息数组，供后续（流式或非流式）
 * 生成最终自然语言回答。复用 {@link ToolRegistry} 下发白名单工具、{@link ToolArgsValidator}
 * 校验入参；输出 {@code role:"tool"} 消息的 {@code content} 恒为 {@code String}
 * （对齐 {@code ToolMessageComplianceTest} 的 OpenAI 协议合规要求）。</p>
 *
 * <p><b>身份铁律</b>：工具执行的身份<b>只</b>取自 {@link LocalThreadHolder#getUserId()}
 * （= JWT 登录用户），工作线程内写入 {@link ToolContext#setIdentity(Integer, Integer)}
 * （角色固定为 2=用户），<b>绝不</b>信任 LLM 回传的 userId/patientId/doctorId。
 * 工具层获取「我是谁」的唯一可信来源是 {@link ToolContext}，本类负责在调用工具前正确注入。</p>
 *
 * <p><b>fail-closed</b>：未知工具名、参数解析失败、参数校验失败、工具执行异常，一律降级为
 * 一条文本内容回填（不抛异常、不中断循环），由模型自行决定如何向用户解释，避免 500。</p>
 */
@Slf4j
@Component
public class ChatToolLoop {

    @Autowired(required = false)
    private ToolRegistry toolRegistry;

    @Autowired(required = false)
    private DataAgentConfigHolder agentConfig;

    /** 真正的 LLM 非流式调用（由 {@code AiServiceImpl} 注入，便于单测替换）。 */
    @FunctionalInterface
    public interface CompletionCaller {
        /**
         * 调用一次 LLM 非流式补全。
         *
         * @param messages 当前（已含历史 + 上一轮工具消息）的消息数组
         * @param tools    本轮下发的工具定义（白名单过滤后）
         * @param ctx      请求上下文
         * @return 完整的 OpenAI 响应 JSON（含 {@code choices[0].message}，可能含 {@code tool_calls}）
         */
        JSONObject call(JSONArray messages, List<Map<String, Object>> tools, Context ctx) throws IOException;
    }

    /** 工具轮上下文（不携带模型/密钥/温度——那些由 {@link CompletionCaller} 闭包捕获）。 */
    public static final class Context {
        /** 当前登录用户 ID（来自 JWT，仅用于审计/日志，工具身份仍走 {@link ToolContext}）。 */
        public final Integer userId;
        /** 工具白名单；{@code null} = 全量（向后兼容默认）。 */
        public final Set<String> allowedToolNames;
        /** 是否下发 {@code function.strict=true}（国产网关支持不一致，默认 false）。 */
        public final boolean toolStrict;

        public Context(Integer userId, Set<String> allowedToolNames, boolean toolStrict) {
            this.userId = userId;
            this.allowedToolNames = allowedToolNames;
            this.toolStrict = toolStrict;
        }
    }

    /** 工具轮是否启用（默认 true；{@code false} 一键关停整条路径）。 */
    public boolean isToolsEnabled() {
        return agentConfig == null || agentConfig.isChatToolsEnabled();
    }

    /** 工具轮最大轮数（默认 3；fail-closed 防模型无限要工具）。 */
    public int getMaxRounds() {
        return agentConfig == null ? 3 : agentConfig.getChatMaxToolRounds();
    }

    /**
     * 执行工具轮循环。
     *
     * @param messages 初始消息数组（<b>不会被修改</b>，内部深拷贝）
     * @param caller   非流式 LLM 调用
     * @param ctx      上下文
     * @return 追加了 assistant(tool_calls) 与 tool 消息后的消息数组
     */
    public JSONArray run(JSONArray messages, CompletionCaller caller, Context ctx) throws IOException {
        if (!isToolsEnabled()) {
            log.info("[ChatToolLoop] 工具调用开关关闭，跳过工具轮（等价于纯文本问诊）");
            return clone(messages);
        }
        List<Map<String, Object>> tools = (toolRegistry != null)
                ? toolRegistry.buildToolsArray(ctx.allowedToolNames, ctx.toolStrict)
                : Collections.emptyList();
        if (tools.isEmpty()) {
            log.info("[ChatToolLoop] 无可用工具，跳过工具轮");
            return clone(messages);
        }

        int maxRounds = getMaxRounds();
        JSONArray working = clone(messages);
        for (int round = 0; round < maxRounds; round++) {
            JSONObject response = caller.call(working, tools, ctx);
            JSONObject assistantMsg = extractAssistantMessage(response);
            if (assistantMsg == null) {
                break;
            }
            working.add(assistantMsg);

            JSONArray toolCalls = assistantMsg.getJSONArray("tool_calls");
            if (toolCalls == null || toolCalls.isEmpty()) {
                break; // 模型不再请求工具 → 本轮即最终回答，结束
            }
            List<JSONObject> toolMessages = executeToolCalls(toolCalls, ctx.userId);
            working.addAll(toolMessages);
        }
        return working;
    }

    /** 从完整响应里取出 assistant 消息体（保留 role/content/tool_calls）。 */
    private JSONObject extractAssistantMessage(JSONObject response) {
        if (response == null) {
            return null;
        }
        JSONArray choices = response.getJSONArray("choices");
        if (choices == null || choices.isEmpty()) {
            return null;
        }
        return choices.getJSONObject(0).getJSONObject("message");
    }

    /** 逐个执行工具调用并构造 {@code role:"tool"} 消息。 */
    private List<JSONObject> executeToolCalls(JSONArray toolCalls, Integer userId) {
        List<JSONObject> out = new ArrayList<>(toolCalls.size());
        for (int i = 0; i < toolCalls.size(); i++) {
            JSONObject raw = toolCalls.getJSONObject(i);
            String id = raw.getString("id");
            JSONObject function = raw.getJSONObject("function");
            String name = function != null ? function.getString("name") : null;
            String argsJson = function != null ? function.getString("arguments") : null;

            ToolResult result = executeOne(id, name, argsJson, userId);
            JSONObject toolMsg = new JSONObject();
            toolMsg.put("role", "tool");
            toolMsg.put("tool_call_id", id);
            // 对齐 ToolMessageComplianceTest：content 必须是 String，null 归一为 ""
            toolMsg.put("content", result.getContent() == null ? "" : result.getContent());
            out.add(toolMsg);
        }
        return out;
    }

    /**
     * 执行单个工具（fail-closed + 身份铁律）。
     *
     * <p>🔴 身份铁律：工具的 userId 只取 {@link LocalThreadHolder#getUserId()}（JWT 登录用户），
     * 绝不读 LLM 传入的参数。即便 LLM 在工具参数里塞了 {@code userId/patientId/doctorId}，
     * 也一律忽略。</p>
     */
    private ToolResult executeOne(String callId, String name, String argsJson, Integer userId) {
        Tool tool = (toolRegistry != null) ? toolRegistry.get(name) : null;
        if (tool == null) {
            log.warn("[ChatToolLoop] 未知工具（fail-closed）: callId={}, name={}", callId, name);
            return ToolResult.ok("工具不存在或已停用: " + name);
        }
        Map<String, Object> args;
        try {
            args = parseArgs(argsJson);
        } catch (Exception e) {
            log.warn("[ChatToolLoop] 工具参数解析失败: callId={}, name={}, err={}", callId, name, e.getMessage());
            return ToolResult.ok("工具参数格式错误");
        }
        String validationError = ToolArgsValidator.validate(tool, args);
        if (validationError != null) {
            log.warn("[ChatToolLoop] 工具参数校验失败: callId={}, name={}, err={}", callId, name, validationError);
            return ToolResult.ok(validationError);
        }

        // 身份铁律：只认登录用户（角色固定为用户=2），绝不信任 LLM 传入的身份
        Integer realUserId = LocalThreadHolder.getUserId();
        if (realUserId == null) {
            realUserId = userId; // 最终兜底（如未注入 LocalThreadHolder 的非 Web 调用）
        }
        ToolContext.set(ToolContext.KEY_USER_ID, realUserId);
        ToolContext.setIdentity(2, null);
        try {
            log.info("[ChatToolLoop] 执行工具: callId={}, name={}, 执行身份(userId={}, role=2)",
                    callId, name, realUserId);
            return tool.execute(args);
        } catch (Exception e) {
            log.error("[ChatToolLoop] 工具执行异常: name={}, err={}", name, e.getMessage());
            return ToolResult.ok("工具执行失败: " + e.getMessage());
        } finally {
            ToolContext.clear();
        }
    }

    private Map<String, Object> parseArgs(String argsJson) {
        if (argsJson == null || argsJson.trim().isEmpty()) {
            return new LinkedHashMap<>();
        }
        Object parsed = JSON.parse(argsJson.trim());
        if (parsed instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) parsed;
            return map;
        }
        return new LinkedHashMap<>();
    }

    /** 深拷贝消息数组（仅追加、不改既有元素，故结构共享亦可，但保险起见走 JSON 往返）。 */
    private JSONArray clone(JSONArray messages) {
        if (messages == null) {
            return new JSONArray();
        }
        return JSON.parseArray(messages.toJSONString());
    }
}
