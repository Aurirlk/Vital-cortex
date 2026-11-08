package cn.kmbeast.crm.agent;

import cn.kmbeast.context.LocalThreadHolder;
import cn.kmbeast.core.chat.ChatToolLoop;
import cn.kmbeast.crm.agent.tool.Tool;
import cn.kmbeast.crm.agent.tool.ToolContext;
import cn.kmbeast.crm.agent.model.ToolResult;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 阶段5：{@link ChatToolLoop} 工具执行身份铁律。
 *
 * <p><b>防什么回归</b>：工具（如 {@code QueryHealthRecordsTool}）按 {@link ToolContext#getUserId()}
 * 判定「查谁的数据」。若 {@link ChatToolLoop} 信任 LLM 回传的参数里的 userId/patientId，
 * 攻击者可伪造身份越权读取他人健康数据。本测试断言：工具执行时看到的身份<b>只</b>来自
 * 登录用户（{@link LocalThreadHolder}），与 LLM 参数里塞的任何 userId 无关。</p>
 */
class ChatToolIdentityTest {

    /** 执行时捕获工具层看到的 userId / roleId。 */
    private static final class IdentitySpyTool implements Tool {
        static Integer capturedUserId;
        static Integer capturedRoleId;

        @Override
        public String getName() {
            return "spy_tool";
        }

        @Override
        public String getDescription() {
            return "identity spy";
        }

        @Override
        public Map<String, Object> getParametersSchema() {
            Map<String, Object> s = new LinkedHashMap<>();
            s.put("type", "object");
            s.put("properties", new LinkedHashMap<>());
            return s;
        }

        @Override
        public ToolResult execute(Map<String, Object> arguments) {
            capturedUserId = ToolContext.getUserId();
            capturedRoleId = ToolContext.getRoleId();
            return ToolResult.ok("ok");
        }
    }

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

    private static JSONObject responseWithToolCall(String id, String name, String argsJson) {
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
        choice.put("finish_reason", "tool_calls");
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

    @AfterEach
    void tearDown() {
        LocalThreadHolder.clear();
        ToolContext.clear();
    }

    @Test
    @DisplayName("T-id1 工具看到的身份只来自登录用户，忽略 LLM 参数里的 userId")
    void identityComesFromLoginNotLlm() throws Exception {
        // 登录用户 = 1001（角色 2）
        LocalThreadHolder.setUserId(1001, 2);
        IdentitySpyTool.capturedUserId = null;
        IdentitySpyTool.capturedRoleId = null;

        ChatToolLoop loop = new ChatToolLoop();
        setRegistry(loop, new IdentitySpyTool());

        ChatToolLoop.CompletionCaller caller = (msgs, tools, ctx) -> {
            long assistantCalls = msgs.stream()
                    .filter(m -> "assistant".equals(((JSONObject) m).getString("role")))
                    .count();
            if (assistantCalls == 0) {
                // LLM 在参数里塞了 userId=9999（伪造身份）
                return responseWithToolCall("call_id", "spy_tool", "{\"userId\":9999}");
            }
            return responseWithContent("最终回答");
        };

        JSONArray messages = new JSONArray();
        JSONObject userMsg = new JSONObject();
        userMsg.put("role", "user");
        userMsg.put("content", "查一下");
        messages.add(userMsg);

        loop.run(messages, caller, new ChatToolLoop.Context(1001, null, false));

        assertNotNull(IdentitySpyTool.capturedUserId,
                "🔴 工具执行时未捕获到 userId（身份未正确注入 ToolContext）");
        assertEquals(1001, (int) IdentitySpyTool.capturedUserId,
                "🔴 工具看到的 userId 必须是真实登录用户 1001，不得是 LLM 伪造的 9999");
        assertEquals(2, (int) IdentitySpyTool.capturedRoleId,
                "🔴 工具看到的角色必须是用户(2)");
    }
}
