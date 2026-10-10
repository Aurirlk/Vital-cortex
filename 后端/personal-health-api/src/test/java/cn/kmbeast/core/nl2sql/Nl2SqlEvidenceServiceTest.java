package cn.kmbeast.core.nl2sql;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 术语 Evidence 服务测试。
 *
 * <p>重点是 {@link Nl2SqlEvidenceService#formatRange} —— {@code health_model_config.value_range}
 * 存在<b>两套互不兼容的历史格式</b>：老 5 条是逗号式 {@code "90,140"}，新 7 条是 JSON 式
 * {@code {"min":0,"max":11.1}}。这个方法若只认一种，模型就会拿到一半空白的正常范围，
 * 进而在生成"是否异常"类条件时出错。</p>
 *
 * <p>不连库：只测纯函数。Evidence 的库内读取由 {@code evidence()} 承担，
 * 需要 Spring 容器，不在本测试范围内。</p>
 */
class Nl2SqlEvidenceServiceTest {

    @Test
    @DisplayName("老格式（逗号式）→ 90~140")
    void formatsLegacyCommaRange() {
        assertEquals("90~140", Nl2SqlEvidenceService.formatRange("90,140"));
        assertEquals("3.9~6.1", Nl2SqlEvidenceService.formatRange("3.9,6.1"));
        assertEquals("18.5~24.9", Nl2SqlEvidenceService.formatRange("18.5,24.9"));
    }

    @Test
    @DisplayName("新格式（JSON 式）→ 0~11.1")
    void formatsJsonRange() {
        assertEquals("0~11.1", Nl2SqlEvidenceService.formatRange("{\"min\":0,\"max\":11.1}"));
        assertEquals("0.5~3", Nl2SqlEvidenceService.formatRange("{\"min\":0.5,\"max\":3.0}"),
                "3.0 应去掉无意义的整数尾巴");
        assertEquals("50~250", Nl2SqlEvidenceService.formatRange("{\"min\":50,\"max\":250}"));
    }

    @Test
    @DisplayName("空值 / null / 空白 → 空串（不能给模型塞 null 字面量）")
    void emptyInputYieldsEmptyString() {
        assertEquals("", Nl2SqlEvidenceService.formatRange(null));
        assertEquals("", Nl2SqlEvidenceService.formatRange(""));
        assertEquals("", Nl2SqlEvidenceService.formatRange("   "));
    }

    @Test
    @DisplayName("无法解析的脏数据原样返回（宁可给原文，也不给空）")
    void unparsableRangeFallsBackToRawText() {
        // 缺 max：JSON 解析成功但字段不全 → 原样
        assertEquals("{\"min\":0}", Nl2SqlEvidenceService.formatRange("{\"min\":0}"));
        // 完全不是 JSON → 原样
        assertEquals("待定", Nl2SqlEvidenceService.formatRange("待定"));
        // 单值无逗号 → 原样
        assertEquals("100", Nl2SqlEvidenceService.formatRange("100"));
    }

    @Test
    @DisplayName("带空格的脏数据也能兜住（不抛异常，且两端空白被 trim）")
    void toleratesMessyInput() {
        // 实现里对逗号两端做了 trim，输出统一为 "90~140"
        assertEquals("90~140", Nl2SqlEvidenceService.formatRange(" 90 , 140 "));
        // 坏 JSON 不应抛异常
        Nl2SqlEvidenceService.formatRange("{min:0,max:1}");
        Nl2SqlEvidenceService.formatRange("{{\"min\":");
    }
}
