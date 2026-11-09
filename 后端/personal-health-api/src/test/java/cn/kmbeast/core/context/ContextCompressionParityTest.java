package cn.kmbeast.core.context;

import cn.kmbeast.pojo.entity.AiChatRecord;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 阶段 9 零回归契约：新压缩器与旧压缩算法在等价预算下，
 * 产出的历史段<b>条数 / 顺序 / role / content 逐条一致</b>。
 *
 * <p>唯一的有意变更是 {@code system} 稳定前缀从「历史之后」移到「最前」
 * （prompt caching），该变化由 {@link #systemPrefixIsAtFront()} 单独断言，
 * 不算历史段回归。</p>
 */
@DisplayName("上下文压缩等价性（零回归）")
class ContextCompressionParityTest {

    private static AiChatRecord rec(String role, String content) {
        return AiChatRecord.builder().role(role).content(content).build();
    }

    /** 旧 {@code AiServiceImpl.appendCompressedHistory} 的逐行复刻（作为 oracle）。 */
    private static List<PromptCachePrefix.Segment> legacyOracle(List<AiChatRecord> history,
                                                                int rounds, int maxChars,
                                                                int summaryThreshold) {
        List<PromptCachePrefix.Segment> out = new ArrayList<>();
        if (history == null || history.isEmpty() || rounds <= 0) {
            return out;
        }
        int startIdx = Math.max(0, history.size() - rounds * 2);
        int dropped = startIdx;
        long used = 0L;
        int keepFrom = startIdx;
        for (int i = startIdx; i < history.size(); i++) {
            AiChatRecord r = history.get(i);
            if (r == null) {
                continue;
            }
            int cost = r.getContent() == null ? 8 : r.getContent().length();
            if (used + cost > maxChars && i > startIdx) {
                keepFrom = i;
                break;
            }
            used += cost;
        }
        if (dropped > 0 && summaryThreshold > 0 && dropped >= summaryThreshold) {
            out.add(new PromptCachePrefix.Segment("system", "[早期对话摘要]", false));
        }
        for (int i = keepFrom; i < history.size(); i++) {
            AiChatRecord r = history.get(i);
            if (r == null) {
                continue;
            }
            out.add(new PromptCachePrefix.Segment(r.getRole(),
                    r.getContent() == null ? "" : r.getContent(), false));
        }
        return out;
    }

    private static void assertSame(List<PromptCachePrefix.Segment> expected,
                                   List<PromptCachePrefix.Segment> actual, String caseName) {
        assertEquals(expected.size(), actual.size(), caseName + " 条数不一致");
        for (int i = 0; i < expected.size(); i++) {
            assertEquals(expected.get(i).role(), actual.get(i).role(), caseName + " 第 " + i + " 条 role");
            assertEquals(expected.get(i).content(), actual.get(i).content(),
                    caseName + " 第 " + i + " 条 content（顺序/内容必须零回归）");
        }
    }

    @Test
    @DisplayName("空历史 / rounds=0 → 不携带任何历史")
    void emptyAndDisabled() {
        ContextCompressor c = new ContextCompressor();
        assertEquals(0, c.compress(null, ContextCompressor.Budget.legacy()).getSegments().size());
        assertEquals(0, c.compress(List.of(), ContextCompressor.Budget.legacy()).getSegments().size());
        ContextCompressor.Budget zero = new ContextCompressor.Budget(0, 4000, 0, 0, 0, false);
        assertEquals(0, c.compress(List.of(rec("user", "你好")), zero).getSegments().size());
    }

    @Test
    @DisplayName("少量历史：逐条一致")
    void smallHistory() {
        List<AiChatRecord> h = List.of(
                rec("user", "我最近睡眠怎么样"), rec("assistant", "整体平稳"),
                rec("user", "血压呢"), rec("assistant", "120/80 左右"));
        ContextCompressor c = new ContextCompressor();
        assertSame(legacyOracle(h, 10, 4000, 0),
                c.compress(h, ContextCompressor.Budget.legacy()).getSegments(), "少量历史");
    }

    @Test
    @DisplayName("超过条数上限：只保留最近 N 条，顺序不变")
    void overMessageLimit() {
        List<AiChatRecord> h = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            h.add(rec(i % 2 == 0 ? "user" : "assistant", "第 " + i + " 轮内容"));
        }
        ContextCompressor.Budget b = new ContextCompressor.Budget(5, 4000, 0, 0, 0, false);
        List<PromptCachePrefix.Segment> actual = new ContextCompressor().compress(h, b).getSegments();
        assertEquals(10, actual.size());
        assertSame(legacyOracle(h, 5, 4000, 0), actual, "超条数");
    }

    @Test
    @DisplayName("超过字符预算：从最旧整条丢弃，顺序不变")
    void overCharBudget() {
        List<AiChatRecord> h = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            h.add(rec(i % 2 == 0 ? "user" : "assistant", "内容".repeat(120)));
        }
        ContextCompressor.Budget b = new ContextCompressor.Budget(10, 600, 0, 0, 0, false);
        List<PromptCachePrefix.Segment> actual = new ContextCompressor().compress(h, b).getSegments();
        assertSame(legacyOracle(h, 10, 600, 0), actual, "超字符预算");
        assertTrue(actual.size() < 8, "字符预算必须真的丢掉较早消息");
    }

    @Test
    @DisplayName("含 null 记录 / null content：与旧实现同样跳过或置空")
    void nullRecords() {
        List<AiChatRecord> h = new ArrayList<>();
        h.add(rec("user", "第一句"));
        h.add(null);
        h.add(rec("assistant", null));
        h.add(rec("user", "最后一句"));
        ContextCompressor c = new ContextCompressor();
        assertSame(legacyOracle(h, 10, 4000, 0),
                c.compress(h, ContextCompressor.Budget.legacy()).getSegments(), "含 null");
    }

    @Test
    @DisplayName("摘要开启时条数与历史段顺序仍一致（内容改为消毒后的 JSON）")
    void withSummaryThreshold() {
        List<AiChatRecord> h = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            h.add(rec(i % 2 == 0 ? "user" : "assistant", "血压趋势记录 " + i));
        }
        ContextCompressor.Budget b = new ContextCompressor.Budget(5, 4000, 5, 0, 0, false);
        List<PromptCachePrefix.Segment> actual = new ContextCompressor().compress(h, b).getSegments();
        assertEquals(11, actual.size(), "1 条结构化摘要 + 10 条历史");
        assertEquals("system", actual.get(0).role());
        assertTrue(actual.get(0).content().contains("\"topics\""), "摘要必须是结构化 JSON");
        for (int i = 1; i < actual.size(); i++) {
            assertEquals(legacyOracle(h, 5, 4000, 0).get(i - 1).content(),
                    actual.get(i).content(), "历史段第 " + i + " 条内容不得变");
        }
    }

    @Test
    @DisplayName("完整 messages：system 稳定前缀在最前、历史段居中、当前 user 在末尾")
    void systemPrefixIsAtFront() throws Exception {
        List<AiChatRecord> h = List.of(
                rec("user", "之前的问题"), rec("assistant", "之前的回答"));
        Class<?> clazz = Class.forName("cn.kmbeast.service.impl.AiServiceImpl");
        Object svc = clazz.getDeclaredConstructor().newInstance();
        Method m = clazz.getDeclaredMethod("buildMessagesArray", String.class, String.class,
                List.class, String.class, List.class, boolean.class);
        m.setAccessible(true);
        JSONArray msgs = (JSONArray) m.invoke(svc, "SYS", "CTX", h, "当前提问", null, false);
        assertEquals(4, msgs.size(), "system + 2 条历史 + 当前 user");
        assertEquals("system", msgs.getJSONObject(0).getString("role"));
        assertTrue(String.valueOf(msgs.getJSONObject(0).getString("content")).contains("SYS"));
        assertEquals("user", msgs.getJSONObject(1).getString("role"));
        assertEquals("assistant", msgs.getJSONObject(2).getString("role"));
        assertEquals("user", msgs.getJSONObject(3).getString("role"));
        assertEquals("当前提问", msgs.getJSONObject(3).getString("content"));
        for (int i = 0; i < msgs.size(); i++) {
            JSONObject obj = msgs.getJSONObject(i);
            assertTrue(obj.containsKey("role") && obj.containsKey("content"));
        }
    }
}
