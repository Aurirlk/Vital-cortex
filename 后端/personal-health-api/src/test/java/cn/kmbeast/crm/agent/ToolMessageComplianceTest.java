package cn.kmbeast.crm.agent;

import cn.kmbeast.crm.agent.model.ReActResponse;
import cn.kmbeast.crm.agent.model.ToolCall;
import cn.kmbeast.crm.agent.model.ToolResult;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T1：{@code role:"tool"} 消息的 OpenAI 协议合规性。
 *
 * <p><b>这条测试防的是什么回归</b>：OpenAI 规范要求 tool 消息的 {@code content} 是
 * {@code String | array of content parts}。改造前项目会把工具返回的 JSON
 * <b>解析成 JSONObject 直接塞进 content</b> —— DeepSeek 容忍，但换 vLLM / 新版网关
 * 直接判 400，而这类问题只在切换厂商时才暴露，排查成本极高。</p>
 *
 * <p>因此必须有一层「content 的运行时类型断言」，而不是靠 code review 盯着。</p>
 */
class ToolMessageComplianceTest {

    /** 最小可用的 agent 桩：只暴露需要测的两个 protected 方法。 */
    private static final class StubAgent extends BaseReActAgent {
        @Override
        protected ReActResponse doRun(List<Map<String, String>> userMessages) {
            throw new UnsupportedOperationException("测试桩不执行主循环");
        }

        Map<String, Object> buildMessages() {
            return new LinkedHashMap<>();
        }
    }

    @Test
    @DisplayName("T1.1 工具返回 JSON 字符串时，content 必须原样保持 String（不得被解析成对象）")
    void toolContentStaysString() {
        StubAgent agent = new StubAgent();
        ToolCall tc = ToolCall.builder().id("call_1").name("search_drug")
                .arguments(new LinkedHashMap<>()).build();
        // 工具真实的返回形态：content 是 JSON **字符串**
        String jsonText = "{\"name\":\"布洛芬\",\"price\":12.5}";
        ToolResult result = ToolResult.ok(jsonText,
                JSON.parseObject(jsonText));

        List<Map<String, Object>> messages = new ArrayList<>();
        agent.addToolResultMessage(messages, tc, result);

        assertEquals(1, messages.size());
        Map<String, Object> msg = messages.get(0);
        assertEquals("tool", msg.get("role"));
        assertEquals("call_1", msg.get("tool_call_id"));

        Object content = msg.get("content");
        assertTrue(content instanceof String,
                "🔴 content 必须是 String，实际是 " + (content == null ? "null" : content.getClass().getName())
                        + " —— 这会被 vLLM / 严格网关判 400");
        assertEquals(jsonText, content);

        // 结构化数据确实保留在 ToolResult 上（供日志/审计用），但不在 messages 里
        assertNotNull(result.getStructuredContent(), "structuredContent 应保留供日志/审计消费");
        assertFalse(JSON.toJSONString(messages).contains("\"price\":12.5"),
                "🔴 结构化数据不得泄漏进 messages（应只以字符串形态出现）");
    }

    @Test
    @DisplayName("T1.2 content 为 null 时归一为空串（OpenAI 不接受 null content）")
    void nullContentBecomesEmptyString() {
        StubAgent agent = new StubAgent();
        ToolCall tc = ToolCall.builder().id("call_x").name("t").build();
        ToolResult result = new ToolResult(true, null, 0);

        List<Map<String, Object>> messages = new ArrayList<>();
        agent.addToolResultMessage(messages, tc, result);

        Object content = messages.get(0).get("content");
        assertNotNull(content, "🔴 content 为 null 会被 OpenAI 判 400，必须归一为 \"\"");
        assertEquals("", content);
    }

    @Test
    @DisplayName("T1.3 result 为 null 时不抛 NPE，content 仍为空串")
    void nullResultIsSafe() {
        StubAgent agent = new StubAgent();
        ToolCall tc = ToolCall.builder().id("call_n").name("t").build();
        List<Map<String, Object>> messages = new ArrayList<>();
        // 并行执行超时时理论上不会传 null，但这层必须兜住：
        // 此前 NPE 无 try 包裹会冒泡成 500
        agent.addToolResultMessage(messages, tc, null);
        assertEquals(1, messages.size());
        assertEquals("", messages.get(0).get("content"));
    }

    @Test
    @DisplayName("T1.4 tc 为 null 时跳过（流式分片可能出现空槽位）")
    void nullToolCallSkipped() {
        StubAgent agent = new StubAgent();
        List<Map<String, Object>> messages = new ArrayList<>();
        agent.addToolResultMessage(messages, null, ToolResult.ok("x"));
        assertTrue(messages.isEmpty(), "tc 为 null 不应产生消息");
    }

    @Test
    @DisplayName("T1.5 assistant 工具调用消息保留模型同轮文本（content 不再硬编码空串）")
    void assistantMessageKeepsModelText() {
        StubAgent agent = new StubAgent();
        List<ToolCall> calls = List.of(
                ToolCall.builder().id("c1").name("search_drug").build());

        List<Map<String, Object>> messages = new ArrayList<>();
        agent.addAssistantToolCallMessage(messages, calls, "好的，我来查一下。");

        JSONObject asJson = JSON.parseObject(JSON.toJSONString(messages.get(0)));
        assertEquals("assistant", asJson.getString("role"));
        assertEquals("好的，我来查一下。", asJson.getString("content"),
                "🔴 模型同轮文本被丢弃会导致下一轮看不到自己说过的话（且 token 白花）");
        assertEquals(1, asJson.getJSONArray("tool_calls").size());
        assertEquals("search_drug", asJson.getJSONArray("tool_calls")
                .getJSONObject(0).getJSONObject("function").getString("name"));
    }

    @Test
    @DisplayName("T1.6 同轮文本为空白时 content 置 null（而非空串），仍是合法 OpenAI 形状")
    void blankModelTextBecomesNull() {
        StubAgent agent = new StubAgent();
        List<Map<String, Object>> messages = new ArrayList<>();
        agent.addAssistantToolCallMessage(messages,
                List.of(ToolCall.builder().id("c1").name("t").build()), "   ");
        assertNull(messages.get(0).get("content"));
    }

    @Test
    @DisplayName("T1.7 tool_calls 列表中含 null 元素时被跳过，不产出 null 项")
    void nullToolCallInListSkipped() {
        StubAgent agent = new StubAgent();
        List<ToolCall> calls = new ArrayList<>();
        calls.add(null);
        calls.add(ToolCall.builder().id("c2").name("real_tool").build());
        List<Map<String, Object>> out = agent.buildToolCallsForMessage(calls);
        assertEquals(1, out.size());
        assertEquals("real_tool", ((Map<?, ?>) out.get(0).get("function")).get("name"));
    }
}