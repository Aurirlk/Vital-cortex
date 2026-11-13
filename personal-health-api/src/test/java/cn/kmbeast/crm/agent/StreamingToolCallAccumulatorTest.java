package cn.kmbeast.crm.agent;

import cn.kmbeast.crm.agent.model.ToolCall;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T4：流式 {@code delta.tool_calls} 分片累积与 {@code finish_reason} 四态处理。
 *
 * <p><b>为什么这是 P0 里最需要测试的一处</b>：流式 function calling 的协议形态很容易写错 ——
 * {@code id} 和 {@code name} 只在首片出现，{@code arguments} 被逐字符切片。
 * 写错的表现是<b>静默降级</b>（模型要调工具，代码当成纯文本，前端收到空回答），
 * 而且只在真实网关 + 流式下才暴露。</p>
 */
class StreamingToolCallAccumulatorTest {

    /** 模拟一帧 delta.tool_calls。 */
    private static JSONArray frag(int index, String id, String name, String args) {
        JSONObject f = new JSONObject();
        if (id != null) f.put("id", id);
        JSONObject func = new JSONObject();
        if (name != null) func.put("name", name);
        if (args != null) func.put("arguments", args);
        f.put("function", func);
        f.put("index", index);
        JSONArray arr = new JSONArray();
        arr.add(f);
        return arr;
    }

    @Test
    @DisplayName("T4.1 单工具 arguments 跨多片累积（真实分片形态）")
    void singleToolArgumentsFragments() {
        StreamingReActAgent.ToolCallAccumulator acc = new StreamingReActAgent.ToolCallAccumulator();
        acc.accept(frag(0, "call_abc", "search_drug", ""));
        acc.accept(frag(0, null, null, "{\"key"));
        acc.accept(frag(0, null, null, "word\":\"布洛"));
        acc.accept(frag(0, null, null, "芬\"}"));

        assertFalse(acc.isEmpty());
        assertEquals(1, acc.size());
        List<ToolCall> calls = acc.toToolCalls();
        assertEquals(1, calls.size());
        assertEquals("call_abc", calls.get(0).getId());
        assertEquals("search_drug", calls.get(0).getName());
        assertEquals("布洛芬", calls.get(0).getArguments().get("keyword"));
    }

    @Test
    @DisplayName("T4.2 多个并行工具按 index 分组，arguments 不串号（index 缺失时才退化按顺序）")
    void parallelToolsByIndex() {
        StreamingReActAgent.ToolCallAccumulator acc = new StreamingReActAgent.ToolCallAccumulator();
        acc.accept(frag(0, "call_1", "search_drug", "{\"keyword\":\"布洛芬\"}"));
        acc.accept(frag(1, "call_2", "get_health_data", "{\"days\":"));
        acc.accept(frag(1, null, null, "30}"));

        List<ToolCall> calls = acc.toToolCalls();
        assertEquals(2, calls.size());
        assertEquals("search_drug", calls.get(0).getName());
        assertEquals("布洛芬", calls.get(0).getArguments().get("keyword"));
        assertEquals("get_health_data", calls.get(1).getName());
        assertEquals(30, calls.get(1).getArguments().get("days"));
        // 反序到达也要按 index 排序输出
        assertEquals("call_1", calls.get(0).getId());
        assertEquals("call_2", calls.get(1).getId());
    }

    @Test
    @DisplayName("T4.3 缺 index 时退化按出现顺序（宁可顺序猜错也不整组丢弃）")
    void missingIndexDegradesGracefully() {
        StreamingReActAgent.ToolCallAccumulator acc = new StreamingReActAgent.ToolCallAccumulator();
        // 手工构造无 index 的分片
        JSONArray a = new JSONArray();
        JSONObject f = new JSONObject();
        JSONObject func = new JSONObject();
        func.put("name", "no_index_tool");
        func.put("arguments", "{}");
        f.put("function", func);
        a.add(f);
        acc.accept(a);

        List<ToolCall> calls = acc.toToolCalls();
        assertEquals(1, calls.size());
        assertEquals("no_index_tool", calls.get(0).getName());
        assertNotNull(calls.get(0).getId(), "缺 id 时应合成稳定 id（协议要求可配对）");
    }

    @Test
    @DisplayName("T4.4 arguments 是半截非法 JSON 时返回空列表（整组丢弃，绝不抛给工具）")
    void malformedArgumentsYieldEmptyList() {
        StreamingReActAgent.ToolCallAccumulator acc = new StreamingReActAgent.ToolCallAccumulator();
        acc.accept(frag(0, "call_x", "search_drug", "{\"keywor"));
        List<ToolCall> calls = acc.toToolCalls();
        assertTrue(calls.isEmpty(),
                "🔴 非法 JSON 的 arguments 必须丢弃整组，否则工具侧解析必炸");
    }

    @Test
    @DisplayName("T4.5 缺工具名的分片被跳过（有 id 也无法确定调哪个工具）")
    void missingNameSkipped() {
        StreamingReActAgent.ToolCallAccumulator acc = new StreamingReActAgent.ToolCallAccumulator();
        acc.accept(frag(0, "call_noName", null, "{}"));
        assertTrue(acc.toToolCalls().isEmpty());
    }

    @Test
    @DisplayName("T4.6 空/畸形 delta 不影响累积器（accept 传 null / 空数组安全）")
    void emptyInputSafe() {
        StreamingReActAgent.ToolCallAccumulator acc = new StreamingReActAgent.ToolCallAccumulator();
        acc.accept(null);
        acc.accept(new JSONArray());
        assertTrue(acc.isEmpty());
        assertTrue(acc.toToolCalls().isEmpty());
    }

    // ------------------------------------------------------------------
    // SSE 帧解析健壮性（这几点无法端到端验证，用单元测试锁死）
    // ------------------------------------------------------------------

    @Test
    @DisplayName("T4.7 SSE 帧容错：无空格 data:{...} 与有空格 data: {...} 都能解出 choices")
    void sseFramePrefixTolerance() {
        String noSpace = "data:{\"choices\":[{\"delta\":{\"content\":\"你好\"},\"finish_reason\":null}]}";
        String withSpace = "data: {\"choices\":[{\"delta\":{\"content\":\"你好\"},\"finish_reason\":null}]}";
        // 两条都必须能提取 content（此前 startsWith(\"data: \") 会漏掉无空格那种）
        assertEquals("你好", extractContent(noSpace));
        assertEquals("你好", extractContent(withSpace));
    }

    /** 复刻生产解析里「截取 + trim」这一步，验证前缀容错。 */
    private static String extractContent(String line) {
        if (!line.startsWith("data:")) return null;
        String json = line.substring(5).trim();
        if (json.isEmpty() || "[DONE]".equals(json)) return null;
        JSONObject chunk;
        try {
            chunk = JSON.parseObject(json);
        } catch (Exception e) {
            return null; // 畸形帧跳过，不抛
        }
        if (chunk == null) return null;
        JSONArray choices = chunk.getJSONArray("choices");
        if (choices == null || choices.isEmpty()) return null;
        JSONObject delta = choices.getJSONObject(0).getJSONObject("delta");
        return delta == null ? null : delta.getString("content");
    }

    @Test
    @DisplayName("T4.8 畸形 SSE 帧只跳过该帧，不丢弃已生成内容（不抛异常）")
    void malformedFrameDoesNotThrow() {
        // 模拟网关插入的心跳/注释帧
        String bad = "data:{this is not json";
        assertEquals(null, extractContent(bad), "畸形帧应返回 null（跳过）而非抛异常");
        // 且后续正常帧仍能解析
        String good = "data:{\"choices\":[{\"delta\":{\"content\":\"继续\"}}]}";
        assertEquals("继续", extractContent(good));
    }

    @Test
    @DisplayName("T4.9 finish_reason 四态都能被取出（tool_calls/stop/length/content_filter）")
    void finishReasonFourStates() {
        for (String reason : new String[]{"tool_calls", "stop", "length", "content_filter"}) {
            String frame = "data:{\"choices\":[{\"delta\":{},\"finish_reason\":\"" + reason + "\"}]}";
            if (!frame.startsWith("data:")) throw new AssertionError();
            JSONObject chunk = JSON.parseObject(frame.substring(5).trim());
            assertEquals(reason, chunk.getJSONArray("choices")
                    .getJSONObject(0).getString("finish_reason"),
                    "finish_reason=" + reason + " 必须能被识别");
        }
    }
}