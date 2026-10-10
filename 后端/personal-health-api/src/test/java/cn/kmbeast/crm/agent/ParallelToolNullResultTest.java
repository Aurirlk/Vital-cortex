package cn.kmbeast.crm.agent;

import cn.kmbeast.crm.agent.model.ReActResponse;
import cn.kmbeast.crm.agent.model.ToolCall;
import cn.kmbeast.crm.agent.model.ToolResult;
import cn.kmbeast.crm.agent.tool.Tool;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T5：并行工具执行的空结果回填（超时 / 线程池拒绝 / 慢工具）。
 *
 * <p><b>防的真实事故</b>：并行执行里 {@code latch.await} 超时后，{@code results[i]}
 * 有槽位仍为 {@code null}；此前 {@code addToolResultMessage} 直接解引用，
 * <b>且该链路无 try 包裹</b> → NPE 冒泡到 Controller 变成 500。
 * 更糟的是此时工具其实<b>可能已经在后台跑完了</b>，用户看到的是「服务器错误」
 * 而非「超时」，排查方向完全被带偏。</p>
 */
class ParallelToolNullResultTest {

    /** 一律超时的假工具（模拟慢 SQL / 慢外部 API）。 */
    private static final class SlowTool implements Tool {
        @Override public String getName() { return "slow_tool"; }
        @Override public String getDescription() { return "永远超时"; }
        @Override public Map<String, Object> getParametersSchema() {
            Map<String, Object> s = new LinkedHashMap<>();
            s.put("type", "object");
            s.put("properties", new LinkedHashMap<>());
            return s;
        }
        @Override public ToolResult execute(Map<String, Object> arguments) {
            try { Thread.sleep(60_000); } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            return ToolResult.ok("不应该执行到这里");
        }
    }

    /** 一个只把 executeToolsParallel 暴露出来的 agent 桩。 */
    private static final class StubAgent extends BaseReActAgent {
        Tool tool;
        int timeoutSeconds = 1;

        @Override
        protected ReActResponse doRun(List<Map<String, String>> userMessages) {
            throw new UnsupportedOperationException("桩不跑主循环");
        }

        @Override
        protected long resolveToolTimeoutSeconds() {
            return timeoutSeconds;
        }

        ToolResult[] run(List<ToolCall> calls, Set<String> keys) {
            return executeToolsParallel(calls, keys, null);
        }
    }

    @Test
    @DisplayName("T5.1 全部工具超时：结果数组元素非 null 且标记失败（不冒 NPE）")
    void timeoutFillsErrorResults() {
        StubAgent agent = new StubAgent();
        agent.tool = new SlowTool();
        setTool(agent, agent.tool);

        List<ToolCall> calls = List.of(
                ToolCall.builder().id("c1").name("slow_tool").arguments(new LinkedHashMap<>()).build(),
                ToolCall.builder().id("c2").name("slow_tool")
                        .arguments(Map.of("k", "v")).build());

        ToolResult[] results = agent.run(calls, new LinkedHashSet<>());

        assertEquals(2, results.length);
        for (int i = 0; i < results.length; i++) {
            assertNotNull(results[i],
                    "🔴 results[" + i + "] 为 null 会在回填时 NPE（此前真实故障）");
            assertFalse(results[i].isSuccess(), "超时结果必须标记失败");
        }
        // 超时提示要带上工具名，便于前端/日志定位
        assertTrue(results[0].getContent().contains("slow_tool"),
                "超时提示应含工具名，实际: " + results[0].getContent());
    }

    @Test
    @DisplayName("T5.2 超时结果回填进 tool 消息不抛异常（端到端串一次 addToolResultMessage）")
    void nullSafeEndToEnd() {
        StubAgent agent = new StubAgent();
        ToolCall tc = ToolCall.builder().id("c1").name("slow_tool")
                .arguments(new LinkedHashMap<>()).build();
        List<Map<String, Object>> messages = new ArrayList<>();
        // 直接把 null 结果传进去 —— 这正是超时场景
        agent.addToolResultMessage(messages, tc, null);
        assertEquals(1, messages.size());
        assertEquals("", messages.get(0).get("content"));
    }

    @Test
    @DisplayName("T5.3 同轮重复调用被去重（不重复执行，第二次回 error）")
    void sameRoundDuplicateSkipped() {
        StubAgent agent = new StubAgent();
        AtomicCounterTool counter = new AtomicCounterTool();
        setTool(agent, counter);

        Map<String, Object> sameArgs = new LinkedHashMap<>();
        sameArgs.put("q", "布洛芬");
        List<ToolCall> calls = List.of(
                ToolCall.builder().id("c1").name("count_tool").arguments(sameArgs).build(),
                ToolCall.builder().id("c2").name("count_tool").arguments(sameArgs).build());

        ToolResult[] results = agent.run(calls, new LinkedHashSet<>());

        assertTrue(results[0].isSuccess());
        assertFalse(results[1].isSuccess(), "同轮重复调用必须被拒绝");
        assertTrue(results[1].getContent().contains("重复"));
        assertEquals(1, counter.count, "🔴 同轮重复调用导致工具被执行两次（会重复写 audit_log）");
    }

    @Test
    @DisplayName("T5.4 跨轮重复调用（有副作用工具）被台账拦截")
    void crossRoundIdempotentLedger() {
        StubAgent agent = new StubAgent();
        // 用真实的有副作用工具名，因为 hasSideEffects 是按名字判定的
        AtomicCounterTool counter = new AtomicCounterTool("query_health_records");
        setTool(agent, counter);

        Set<String> keys = new LinkedHashSet<>();
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("q", "x");

        // 第一轮
        ToolResult[] r1 = agent.run(List.of(ToolCall.builder().id("c1")
                .name("query_health_records").arguments(args).build()), keys);

        // 第二轮：相同调用
        ToolResult[] r2 = agent.run(List.of(ToolCall.builder().id("c2")
                .name("query_health_records").arguments(args).build()), keys);

        assertNotNull(r1[0]);
        assertFalse(r2[0].isSuccess(), "跨轮重复调用应被幂等台账拦截");
    }

    /** 计数工具，用于断言「实际执行了几次」。 */
    private static final class AtomicCounterTool implements Tool {
        int count = 0;
        private final String name;

        AtomicCounterTool() {
            this("count_tool");
        }

        AtomicCounterTool(String name) {
            this.name = name;
        }

        @Override public String getName() { return name; }
        @Override public String getDescription() { return "计数"; }
        @Override public Map<String, Object> getParametersSchema() {
            Map<String, Object> s = new LinkedHashMap<>();
            s.put("type", "object");
            s.put("properties", new LinkedHashMap<>());
            return s;
        }
        @Override public ToolResult execute(Map<String, Object> arguments) {
            count++;
            return ToolResult.ok("ok");
        }
    }

    /** 反射注入私有 tools 字段：先把 ToolRegistry 挂到 agent 上，再塞工具。 */
    @SuppressWarnings("unchecked")
    private static void setTool(BaseReActAgent agent, Tool tool) {
        try {
            ToolRegistry registry = new ToolRegistry();
            Field regField = ToolRegistry.class.getDeclaredField("tools");
            regField.setAccessible(true);
            ((Map<String, Tool>) regField.get(registry)).put(tool.getName(), tool);

            Field agentField = BaseReActAgent.class.getDeclaredField("toolRegistry");
            agentField.setAccessible(true);
            agentField.set(agent, registry);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}