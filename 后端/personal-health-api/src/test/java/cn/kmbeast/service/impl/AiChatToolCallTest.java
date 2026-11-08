package cn.kmbeast.service.impl;

import cn.kmbeast.core.chat.ChatToolLoop;
import cn.kmbeast.crm.agent.ToolRegistry;
import cn.kmbeast.crm.agent.model.ToolResult;
import cn.kmbeast.crm.agent.tool.Tool;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 阶段5：{@link ChatToolLoop} 工具调用机制。
 *
 * <p>用桩 CompletionCaller 替代真实 LLM，断言：① 工具正确下发；② {@code tool_call_id}
 * 严格配对；③ 单轮最大轮数上限（fail-closed）；④ 未知工具 fail-closed（不抛异常、降级文本）；
 * ⑤ 参数校验失败 fail-closed（不执行工具）。</p>
 */
class AiChatToolCallTest {

    /** 记录调用次数、可带 required 校验的假工具。 */
    private static final class FakeTool implements Tool {
        int execCount = 0;
        final String name;
        final boolean requireQ;

        FakeTool(String name, boolean requireQ) {
            this.name = name;
            this.requireQ = requireQ;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getDescription() {
            return "fake tool";
        }

        @Override
        public Map<String, Object> getParametersSchema() {
            Map<String, Object> s = new LinkedHashMap<>();
            s.put("type", "object");
            Map<String, Object> props = new LinkedHashMap<>();
            if (requireQ) {
                Map<String, Object> q = new LinkedHashMap<>();
                q.put("type", "string");
                props.put("q", q);
            }
            s.put("properties", props);
            if (requireQ) {
                s.put("required", new String[]{"q"});
            }
            return s;
        }

        @Override
        public ToolResult execute(Map<String, Object> arguments) {
            execCount++;
            return ToolResult.ok("fake_result_for_" + name);
        }
    }

    /** 把假工具塞进一个 ToolRegistry 并注入到 loop（绕过 Spring，便于单测）。 */
    private static void setRegistry(ChatToolLoop loop, Tool tool) {
        try {
            ToolRegistry registry = new ToolRegistry();
            Field regField = ToolRegistry.class.getDeclaredField("tools");
            regField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<String, Tool> map = (Map<String, Tool>) regField.get(registry);
            map.put(tool.getName(), tool);

            Field loopField = ChatToolLoop.class.getDeclaredField("toolRegistry");
            loopField.setAccessible(true);
            loopField.set(loop, registry);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static JSONObject responseWithToolCall(String id, String name, String argsJson, String finishReason) {
        JSONObject msg = new JSONObject();
        msg.put("role", "assistant");
        msg.put("content", null);
        JSONArray toolCalls = new JSONArray();
        JSONObject tc = new JSONObject();
        tc.put("id", id);
        tc.put("type", "function");
        JSONObject fn = new JSONObject();
        fn.put("name", name);
        fn.put("arguments", argsJson);
        tc.put("function", fn);
        toolCalls.add(tc);
        msg.put("tool_calls", toolCalls);

        JSONObject choice = new JSONObject();
        choice.put("message", msg);
        choice.put("finish_reason", finishReason);
        JSONArray choices = new JSONArray();
        choices.add(choice);
        JSONObject resp = new JSONObject();
        resp.put("choices", choices);
        return resp;
    }

    private static JSONObject responseWithContent(String content) {
        JSONObject msg = new JSONObject();
        msg.put("role", "assistant");
        msg.put("content", content);
        JSONObject choice = new JSONObject();
        choice.put("message", msg);
        choice.put("finish_reason", "stop");
        JSONArray choices = new JSONArray();
        choices.add(choice);
        JSONObject resp = new JSONObject();
        resp.put("choices", choices);
        return resp;
    }

    @Test
    @DisplayName("T1 工具正确下发给 LLM（buildToolsArray 生效）")
    void toolsAreDispatched() throws Exception {
        FakeTool tool = new FakeTool("fake_tool", false);
        ChatToolLoop loop = new ChatToolLoop();
        setRegistry(loop, tool);

        final boolean[] called = {false};
        final List<Map<String, Object>>[] captured = new List[1];
        ChatToolLoop.CompletionCaller caller = (msgs, tools, ctx) -> {
            called[0] = true;
            captured[0] = tools;
            return responseWithContent("最终回答");
        };

        JSONArray messages = new JSONArray();
        JSONObject userMsg = new JSONObject();
        userMsg.put("role", "user");
        userMsg.put("content", "你好");
        messages.add(userMsg);

        loop.run(messages, caller, new ChatToolLoop.Context(1001, null, false));

        assertTrue(called[0], "🔴 CompletionCaller 未被调用");
        assertNotNull(captured[0], "🔴 工具数组未下发");
        assertFalse(captured[0].isEmpty(), "🔴 下发的工具数组为空");
        boolean hasFake = captured[0].stream()
                .anyMatch(t -> "fake_tool".equals(((Map<?, ?>) t.get("function")).get("name")));
        assertTrue(hasFake, "🔴 假工具未出现在下发列表");
    }

    @Test
    @DisplayName("T2 tool_call_id 严格配对：tool 消息的 tool_call_id 与请求一致")
    void toolCallIdStrictlyPaired() throws Exception {
        FakeTool tool = new FakeTool("fake_tool", false);
        ChatToolLoop loop = new ChatToolLoop();
        setRegistry(loop, tool);

        ChatToolLoop.CompletionCaller caller = (msgs, tools, ctx) -> {
            // 第一轮返回工具调用，第二轮返回最终内容
            long assistantCalls = msgs.stream()
                    .filter(m -> "assistant".equals(((JSONObject) m).getString("role")))
                    .count();
            if (assistantCalls == 0) {
                return responseWithToolCall("call_xyz", "fake_tool", "{}", "tool_calls");
            }
            return responseWithContent("最终回答");
        };

        JSONArray messages = new JSONArray();
        JSONObject userMsg = new JSONObject();
        userMsg.put("role", "user");
        userMsg.put("content", "查一下");
        messages.add(userMsg);

        JSONArray result = loop.run(messages, caller, new ChatToolLoop.Context(1001, null, false));

        // 找到 tool 消息，断言配对
        JSONObject toolMsg = null;
        JSONObject finalAssistant = null;
        for (int i = 0; i < result.size(); i++) {
            JSONObject m = result.getJSONObject(i);
            if ("tool".equals(m.getString("role"))) {
                toolMsg = m;
            }
            if ("assistant".equals(m.getString("role")) && m.getString("content") != null) {
                finalAssistant = m;
            }
        }
        assertNotNull(toolMsg, "🔴 未生成 tool 消息");
        assertEquals("call_xyz", toolMsg.getString("tool_call_id"),
                "🔴 tool 消息的 tool_call_id 必须与模型请求的 id 严格配对");
        assertNotNull(finalAssistant, "🔴 最终 assistant 消息缺失");
        assertEquals("最终回答", finalAssistant.getString("content"));
        assertEquals(1, tool.execCount, "🔴 工具应被执行恰好一次");
    }

    @Test
    @DisplayName("T3 单轮最大轮数上限（fail-closed）：模型无限要工具时在第 3 轮停下")
    void maxRoundsCapEnforced() throws Exception {
        FakeTool tool = new FakeTool("fake_tool", false);
        ChatToolLoop loop = new ChatToolLoop();
        setRegistry(loop, tool);

        final int[] invokeCount = {0};
        ChatToolLoop.CompletionCaller caller = (msgs, tools, ctx) -> {
            invokeCount[0]++;
            // 永远返回工具调用 → 模拟模型失控
            return responseWithToolCall("call_" + invokeCount[0], "fake_tool", "{}", "tool_calls");
        };

        JSONArray messages = new JSONArray();
        JSONObject userMsg = new JSONObject();
        userMsg.put("role", "user");
        userMsg.put("content", "查一下");
        messages.add(userMsg);

        loop.run(messages, caller, new ChatToolLoop.Context(1001, null, false));

        // 默认 maxRounds=3 → 恰好调用 3 次后停下（不无限循环）
        assertEquals(3, invokeCount[0], "🔴 工具轮未受 maxRounds=3 上限约束，可能无限循环");
    }

    @Test
    @DisplayName("T4 未知工具 fail-closed：不抛异常、降级为文本、不中断循环")
    void unknownToolFailClosed() throws Exception {
        FakeTool tool = new FakeTool("fake_tool", false);
        ChatToolLoop loop = new ChatToolLoop();
        setRegistry(loop, tool);

        ChatToolLoop.CompletionCaller caller = (msgs, tools, ctx) -> {
            long assistantCalls = msgs.stream()
                    .filter(m -> "assistant".equals(((JSONObject) m).getString("role")))
                    .count();
            if (assistantCalls == 0) {
                return responseWithToolCall("call_u", "nonexistent_tool", "{}", "tool_calls");
            }
            return responseWithContent("最终回答");
        };

        JSONArray messages = new JSONArray();
        JSONObject userMsg = new JSONObject();
        userMsg.put("role", "user");
        userMsg.put("content", "查一下");
        messages.add(userMsg);

        JSONArray result = loop.run(messages, caller, new ChatToolLoop.Context(1001, null, false));

        // 未知工具不应被执行
        assertEquals(0, tool.execCount, "🔴 未知工具不应被执行");
        // 必须有一条 tool 消息描述「不存在/已停用」
        boolean hasUnknownMsg = false;
        for (int i = 0; i < result.size(); i++) {
            JSONObject m = result.getJSONObject(i);
            if ("tool".equals(m.getString("role"))) {
                String content = m.getString("content");
                if (content != null && (content.contains("不存在") || content.contains("已停用"))) {
                    hasUnknownMsg = true;
                }
            }
        }
        assertTrue(hasUnknownMsg, "🔴 未知工具应降级为说明文本回填");
        // 循环应能正常结束并产出最终回答
        boolean hasFinal = result.stream().anyMatch(m ->
                "assistant".equals(((JSONObject) m).getString("role"))
                        && "最终回答".equals(((JSONObject) m).getString("content")));
        assertTrue(hasFinal, "🔴 未知工具不应中断工具轮，必须产出最终回答");
    }

    @Test
    @DisplayName("T5 参数校验失败 fail-closed：缺少必填项时不执行工具")
    void invalidArgsFailClosed() throws Exception {
        FakeTool tool = new FakeTool("fake_tool", true); // 需要必填项 q
        ChatToolLoop loop = new ChatToolLoop();
        setRegistry(loop, tool);

        ChatToolLoop.CompletionCaller caller = (msgs, tools, ctx) -> {
            long assistantCalls = msgs.stream()
                    .filter(m -> "assistant".equals(((JSONObject) m).getString("role")))
                    .count();
            if (assistantCalls == 0) {
                // 故意传空参数（缺少必填项 q）
                return responseWithToolCall("call_v", "fake_tool", "{}", "tool_calls");
            }
            return responseWithContent("最终回答");
        };

        JSONArray messages = new JSONArray();
        JSONObject userMsg = new JSONObject();
        userMsg.put("role", "user");
        userMsg.put("content", "查一下");
        messages.add(userMsg);

        JSONArray result = loop.run(messages, caller, new ChatToolLoop.Context(1001, null, false));

        assertEquals(0, tool.execCount, "🔴 参数校验失败的工具不应被执行");
        boolean hasValidationMsg = false;
        for (int i = 0; i < result.size(); i++) {
            JSONObject m = result.getJSONObject(i);
            if ("tool".equals(m.getString("role"))) {
                String content = m.getString("content");
                if (content != null && content.contains("缺少必填参数")) {
                    hasValidationMsg = true;
                }
            }
        }
        assertTrue(hasValidationMsg, "🔴 参数校验失败应回填说明文本");
    }
}
