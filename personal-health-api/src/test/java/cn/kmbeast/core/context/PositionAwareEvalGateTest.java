package cn.kmbeast.core.context;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** 阶段 9：位置感知 eval 门禁（中部衰减区不放关键医疗事实）。 */
@DisplayName("位置感知 eval 门禁")
class PositionAwareEvalGateTest {

    @Test
    @DisplayName("中部衰减区定义为 [25%, 75%)")
    void middleZoneBoundaries() {
        assertFalse(PositionAwareEvalGate.inMiddle(0, 10));
        assertTrue(PositionAwareEvalGate.inMiddle(3, 10));
        assertTrue(PositionAwareEvalGate.inMiddle(6, 10));
        assertFalse(PositionAwareEvalGate.inMiddle(9, 10));
        assertFalse(PositionAwareEvalGate.inMiddle(0, 1), "只有 1~2 条时不存在中部衰减区");
    }

    @Test
    @DisplayName("剂量 / 诊断结论识别为关键医疗事实（含多行）")
    void detectsCriticalFacts() {
        assertTrue(PositionAwareEvalGate.isCriticalMedicalFact("建议服用 10mg 降压药"));
        assertTrue(PositionAwareEvalGate.isCriticalMedicalFact("第一行\n结论：血压偏高"));
        assertFalse(PositionAwareEvalGate.isCriticalMedicalFact("今天天气不错"));
        assertFalse(PositionAwareEvalGate.isCriticalMedicalFact(null));
    }

    @Test
    @DisplayName("开关关闭时原样返回（零回归兼容路径）")
    void disabledGateKeepsOriginalOrder() {
        List<String> in = List.of("a", "血压 120/80", "b", "c");
        assertEquals(in, PositionAwareEvalGate.apply(in, false, s -> s));
    }

    @Test
    @DisplayName("门禁开启后条数不变、内容不丢")
    void enabledGatePreservesSizeAndContent() {
        List<String> in = List.of(
                "开头问候", "最近睡眠如何", "建议服用 10mg 降压药", "中期闲聊",
                "血糖 6.1mmol", "尾部追问", "最后一条");
        List<String> out = PositionAwareEvalGate.apply(in, true, s -> s);
        assertEquals(in.size(), out.size(), "位置门禁只调整顺序，绝不能丢消息");
        for (String s : in) {
            assertTrue(out.contains(s), "不得丢失: " + s);
        }
    }

    @Test
    @DisplayName("countMiddleCritical 能指出未开启门禁时的违规条数")
    void countsUnguardedViolations() {
        List<String> in = List.of("a", "b", "建议服用 10mg 降压药", "d", "e", "f");
        assertTrue(PositionAwareEvalGate.countMiddleCritical(in, s -> s) >= 1);
    }

    /**
     * 反向验证专用：唯一严格断言「关键事实必须离开中部并落到尾部」的用例。
     * 探针把 apply 的输出顺序打乱（reverse）时，本类应恰好 1 例真红。
     */
    @Test
    @DisplayName("🔴 门禁开启后关键事实必须离开中部并落到窗口尾部")
    void criticalFactMustLandInTail() {
        List<String> in = List.of(
                "开头", "闲聊一", "每日2次 5mg", "闲聊二",
                "结论：需复查", "闲聊三", "尾部");
        List<String> out = PositionAwareEvalGate.apply(in, true, s -> s);
        int tailStart = Math.max(0, out.size() - 2);
        assertTrue(out.subList(tailStart, out.size()).contains("每日2次 5mg"),
                "中部关键事实应整条提升到窗口尾部，实际: " + out);
        assertTrue(out.indexOf("尾部") < out.indexOf("每日2次 5mg"),
                "原有尾部消息仍排在关键事实之前，实际: " + out);
        assertEquals(0, PositionAwareEvalGate.countMiddleCritical(out, s -> s));
    }
}
