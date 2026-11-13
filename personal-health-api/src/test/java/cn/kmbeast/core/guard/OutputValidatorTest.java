package cn.kmbeast.core.guard;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AI 输出合规门单元测试（2026-11-08 新增）。
 *
 * <p><b>为什么现在才补</b>：{@link OutputValidator} 在 {@code AiServiceImpl} 的两条对话链路
 * （{@code /ai/chat} 与流式）里都会调用，命中后会往回答尾部追加「遵医嘱」提示。
 * 但此前这个类<b>零测试覆盖</b> —— 它挂了也不会有人知道，只会表现为「AI 回答里出现剂量却
 * 没有安全提示」，属于医疗场景的合规风险。
 *
 * <p>核心回归点：实现曾用 {@code String.matches(".*" + p + ".*")}，而 {@code .}
 * <b>不匹配换行符</b>，导致多行回答里从第二行起的剂量<b>全部漏检</b>。
 */
@DisplayName("AI 输出合规门（剂量/免责）")
class OutputValidatorTest {

    private final OutputValidator validator = new OutputValidator();

    // ========== 剂量检测：单行 ==========

    @Test
    @DisplayName("单行英文剂量单位应命中")
    void singleLineDoseShouldBeDetected() {
        assertTrue(validator.containsSensitiveInfo("建议每次服用 500mg"));
        assertTrue(validator.containsSensitiveInfo("每日 10ml"));
    }

    @Test
    @DisplayName("数字与单位间有空格也应命中（500mg 与 500 mg 都是常见写法）")
    void doseWithSpaceShouldBeDetected() {
        assertTrue(validator.containsSensitiveInfo("建议每次服用 500 mg"),
                "真实模型输出常写成「500 mg」，若只认紧贴写法就是漏检");
    }

    @Test
    @DisplayName("中文剂量写法应命中")
    void chineseDoseShouldBeDetected() {
        assertTrue(validator.containsSensitiveInfo("每次 2 片，一日三次"));
        assertTrue(validator.containsSensitiveInfo("每次服用 500 毫克"));
    }

    // ========== 剂量检测：多行（历史 bug 的正面回归） ==========

    @Test
    @DisplayName("多行回答中第二行出现剂量必须命中 —— 曾经的漏检点")
    void doseOnSecondLineMustBeDetected() {
        String multiLine = "您的情况建议先观察：\n"
                + "1. 多休息\n"
                + "2. 如症状持续，可服用布洛芬 400mg\n";
        assertTrue(validator.containsSensitiveInfo(multiLine),
                "'.' 不匹配换行 → 多行输出里第二行起的剂量全部漏检，等于没做合规兜底");
    }

    @Test
    @DisplayName("CRLF 换行的多行回答同样要命中（Windows/前端换行）")
    void doseWithCrlfMustBeDetected() {
        assertTrue(validator.containsSensitiveInfo("建议如下：\r\n每日 20ml\r\n"));
    }

    // ========== 不应误伤 ==========

    @Test
    @DisplayName("不含剂量时不得命中（避免给正常回答挂无关提示）")
    void plainAnswerShouldNotBeDetected() {
        assertFalse(validator.containsSensitiveInfo("建议保持规律作息，多喝水，适量运动。"));
        assertFalse(validator.containsSensitiveInfo("血压 120/80 属于正常范围"));
        assertFalse(validator.containsSensitiveInfo(""));
        assertFalse(validator.containsSensitiveInfo(null), "null 不得抛异常（历史实现直接 NPE 风险点）");
    }

    // ========== 免责声明判定 ==========

    @Test
    @DisplayName("出现医疗行为关键词应要求免责声明")
    void medicalKeywordShouldNeedDisclaimer() {
        assertTrue(validator.needDisclaimer("根据您的描述，初步诊断可能是胃炎"));
        assertTrue(validator.needDisclaimer("这个处方需要调整"));
        assertTrue(validator.needDisclaimer("建议考虑手术治疗"));
    }

    @Test
    @DisplayName("纯生活建议不需要免责声明")
    void lifestyleAdviceShouldNotNeedDisclaimer() {
        assertFalse(validator.needDisclaimer("多喝水，早点休息。"));
        assertFalse(validator.needDisclaimer(null));
    }

    @Test
    @DisplayName("多行文本第二行的医疗关键词也要触发免责")
    void medicalKeywordOnSecondLineShouldNeedDisclaimer() {
        assertTrue(validator.needDisclaimer("先说明一下：\n以上不构成处方，具体用药请看医生"));
    }
}
