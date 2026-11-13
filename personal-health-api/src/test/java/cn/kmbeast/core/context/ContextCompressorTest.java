package cn.kmbeast.core.context;

import cn.kmbeast.pojo.entity.AiChatRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** 阶段 9：压缩器五步编排、稳定前缀、结构化摘要与窗口守卫。 */
@DisplayName("上下文压缩器")
class ContextCompressorTest {

    private static AiChatRecord rec(String role, String content) {
        return AiChatRecord.builder().role(role).content(content).build();
    }

    @Test
    @DisplayName("稳定前缀不参与裁剪，且顺序固定 system → tools → few-shot")
    void stablePrefixIsNeverTrimmed() {
        List<PromptCachePrefix.Segment> prefix = PromptCachePrefix.build(
                "SYS", List.of("tool_a", "tool_b"), List.of("示例一"));
        assertEquals(4, prefix.size());
        assertEquals("system", prefix.get(0).role());
        assertEquals("SYS", prefix.get(0).content());
        assertTrue(prefix.get(1).content().startsWith("[TOOL]"));
        assertTrue(prefix.get(3).content().startsWith("[FEW_SHOT]"));
        for (PromptCachePrefix.Segment s : prefix) {
            assertFalse(PromptCachePrefix.participatesInTrimming(s), "稳定前缀不得进入裁剪");
        }
        assertTrue(PromptCachePrefix.samePrefix(prefix, prefix));
    }

    @Test
    @DisplayName("前缀被改动时 samePrefix 能检出（缓存失效判据）")
    void detectsPrefixChange() {
        List<PromptCachePrefix.Segment> a = PromptCachePrefix.build("SYS", null, null);
        List<PromptCachePrefix.Segment> b = PromptCachePrefix.build("SYS_CHANGED", null, null);
        assertFalse(PromptCachePrefix.samePrefix(a, b));
        assertNotEquals(PromptCachePrefix.fingerprint(a), PromptCachePrefix.fingerprint(b));
    }

    @Test
    @DisplayName("压缩结果携带精确计数模式与统计")
    void resultCarriesCounterMode() {
        List<AiChatRecord> h = List.of(rec("user", "血压趋势"), rec("assistant", "整体平稳"));
        ContextCompressor.CompressedResult r = new ContextCompressor().compress(
                h, ContextCompressor.Budget.legacy());
        assertEquals(2, r.getKept());
        assertEquals(0, r.getDropped());
        assertTrue(r.getCounterMode().startsWith("tokenizer:"),
                "离线仓有 jtokkit，实际模式: " + r.getCounterMode());
        assertFalse(r.isSummaryInjected());
        assertFalse(r.isPositionGateApplied(), "legacy 等价预算关闭位置门禁");
    }

    @Test
    @DisplayName("精确 token 预算开启时会真的回收历史")
    void tokenBudgetActuallyTrims() {
        List<AiChatRecord> h = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            h.add(rec(i % 2 == 0 ? "user" : "assistant",
                    "这是一段用于测试精确 token 预算的较长中文历史内容 ".repeat(10)));
        }
        ContextCompressor.Budget tight = new ContextCompressor.Budget(10, 100000, 0, 60, 131072, false);
        ContextCompressor.Budget loose = new ContextCompressor.Budget(10, 100000, 0, 0, 131072, false);
        ContextCompressor c = new ContextCompressor();
        int keptTight = c.compress(h, tight).getKept();
        int keptLoose = c.compress(h, loose).getKept();
        assertTrue(keptTight < keptLoose, "紧预算应比不设预算保留更少历史");
        assertTrue(keptTight >= 1, "至少保留最后一条，不能清空");
    }

    @Test
    @DisplayName("结构化摘要整批 fail-closed：超长词触发消毒拒绝时不得产生摘要")
    void summaryIsFailClosed() {
        List<AiChatRecord> h = new ArrayList<>();
        // 用超长连续中文词触发「值长上限」拒绝（与 FORBIDDEN 指令词无关），
        // 使本用例只依赖长度校验这一个实现点，反向探针可精确归因。
        String oversizeWord = "血压".repeat(60);
        for (int i = 0; i < 24; i++) {
            h.add(rec("user", oversizeWord));
        }
        ContextCompressor.Budget b = new ContextCompressor.Budget(5, 4000, 5, 0, 0, false);
        ContextCompressor.CompressedResult r = new ContextCompressor().compress(h, b);
        assertFalse(r.isSummaryInjected(), "防投毒校验失败时整批拒绝，不注入任何摘要");
        assertEquals(10, r.getSegments().size(), "只保留历史窗口，无摘要条");
    }

    @Test
    @DisplayName("不引入历史存储：guardWindow 只裁剪本次请求自带的窗口")
    void guardWindowHasNoStorage() {
        ContextCompressor c = new ContextCompressor();
        List<String> window = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            window.add("消息 " + i);
        }
        List<String> guarded = c.guardWindow(window, s -> s);
        assertTrue(guarded.size() <= 20, "条数上限应生效");
        assertTrue(guarded.contains("消息 29"), "必须保留最新消息");
        assertFalse(guarded.contains("消息 0"), "最旧消息应被裁掉");
        assertEquals(30, window.size(), "不得修改调用方传入的列表（无隐式历史存储）");
    }

    @Test
    @DisplayName("guardWindow 裁剪后关键医疗事实不得丢失（位置门禁的顺序断言另见 PositionAwareEvalGateTest）")
    void guardWindowKeepsCriticalFacts() {
        ContextCompressor c = new ContextCompressor();
        List<String> window = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            window.add(i == 5 ? "建议服用 10mg 降压药" : "普通消息 " + i);
        }
        List<String> guarded = c.guardWindow(window, s -> s);
        assertTrue(guarded.contains("建议服用 10mg 降压药"), "关键事实不得丢失");
        assertTrue(guarded.size() <= 12);
        // 位置门禁的「中部不得残留关键事实」由 PositionAwareEvalGateTest 单独锁死，
        // 避免两处断言同一个实现点导致反向探针无法精确归因。
    }
}
