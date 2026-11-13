package cn.kmbeast.crm.agent;

import cn.kmbeast.crm.agent.tool.Tool;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T3：{@code ToolArgsValidator} 对 {@code required} 三种形态的兼容性。
 *
 * <p><b>背景是一个真实的静默失效</b>：{@code SearchDrugTool} 传
 * {@code required: new String[]{"keyword"}}，而校验器只认 {@code instanceof List<?>}，
 * 于是<b>必填校验永远不命中、缺 keyword 也不报错</b>，
 * 工具内部拿到 {@code null} 再 NPE，报错点离根因十万八千里。
 * 而且它在 wire 上完全正常（fastjson2 会把数组序列化成 JSON 数组），
 * <b>只有本地校验会坏</b>，靠看接口报文永远发现不了。</p>
 */
class ToolArgsValidatorRequiredArrayTest {

    /** 构造一个 schema 完全由测试掌控的匿名工具。 */
    private static Tool toolWithRequired(Object required) {
        return new Tool() {
            @Override public String getName() { return "t"; }
            @Override public String getDescription() { return "d"; }
            @Override public Map<String, Object> getParametersSchema() {
                Map<String, Object> props = new LinkedHashMap<>();
                props.put("keyword", Map.of("type", "string"));
                props.put("limit", Map.of("type", "integer"));
                Map<String, Object> schema = new LinkedHashMap<>();
                schema.put("type", "object");
                schema.put("properties", props);
                schema.put("required", required);
                return schema;
            }
            @Override public cn.kmbeast.crm.agent.model.ToolResult execute(Map<String, Object> a) {
                return null;
            }
        };
    }

    @Test
    @DisplayName("T3.1 String[] 形态的 required 能检出缺参（P0-2 修复的核心）")
    void stringArrayRequiredIsChecked() {
        Tool tool = toolWithRequired(new String[]{"keyword"});
        String err = ToolArgsValidator.validate(tool, new LinkedHashMap<>());
        assertNotNull(err,
                "🔴 String[] 形态的 required 未被识别 —— 必填校验被静默跳过（SearchDrugTool 的原始 bug）");
        assertTrue(err.contains("keyword"), "错误信息应指出缺哪个参数，实际: " + err);

        // 参数齐了应通过
        Map<String, Object> ok = new LinkedHashMap<>();
        ok.put("keyword", "布洛芬");
        assertNull(ToolArgsValidator.validate(tool, ok));
    }

    @Test
    @DisplayName("T3.2 List 形态的 required 正常工作")
    void listRequiredIsChecked() {
        Tool tool = toolWithRequired(List.of("keyword"));
        String err = ToolArgsValidator.validate(tool, new LinkedHashMap<>());
        assertNotNull(err);
        assertTrue(err.contains("keyword"));
    }

    @Test
    @DisplayName("T3.3 裸 String 形态的 required 也能识别（JSON Schema 允许）")
    void bareStringRequiredIsChecked() {
        Tool tool = toolWithRequired("keyword");
        String err = ToolArgsValidator.validate(tool, new LinkedHashMap<>());
        assertNotNull(err, "裸 String 的 required 应被归一为单元素列表");
        assertTrue(err.contains("keyword"));
    }

    @Test
    @DisplayName("T3.4 Object[] 形态（泛型数组）也能识别")
    void objectArrayRequiredIsChecked() {
        Tool tool = toolWithRequired(new Object[]{"keyword"});
        String err = ToolArgsValidator.validate(tool, new LinkedHashMap<>());
        assertNotNull(err);
        assertTrue(err.contains("keyword"));
    }

    @Test
    @DisplayName("T3.5 空 required（空 List / 空数组 / null）语义为「无必填」，不得误报")
    void emptyRequiredMeansNone() {
        assertNull(ToolArgsValidator.validate(toolWithRequired(List.of()), new LinkedHashMap<>()));
        assertNull(ToolArgsValidator.validate(toolWithRequired(new String[0]), new LinkedHashMap<>()));
        assertNull(ToolArgsValidator.validate(toolWithRequired(null), new LinkedHashMap<>()));
        assertNull(ToolArgsValidator.validate(toolWithRequired(""), new LinkedHashMap<>()));
        // 含空白项也应被跳过，不能把 "  " 当成参数名
        assertNull(ToolArgsValidator.validate(
                toolWithRequired(Arrays.asList("  ", "")), new LinkedHashMap<>()));
    }

    @Test
    @DisplayName("T3.6 多必填项按顺序检出第一个缺失项")
    void multipleRequiredReportsFirst() {
        Tool tool = toolWithRequired(new String[]{"keyword", "limit"});
        Map<String, Object> onlyOne = new LinkedHashMap<>();
        onlyOne.put("keyword", "x");
        String err = ToolArgsValidator.validate(tool, onlyOne);
        assertNotNull(err);
        assertTrue(err.contains("limit"), "应报告第二个缺失项，实际: " + err);
    }

    @Test
    @DisplayName("T3.7 arguments 为 null 时报错（而不是 NPE）")
    void nullArgumentsIsReported() {
        String err = ToolArgsValidator.validate(toolWithRequired(List.of("keyword")), null);
        assertNotNull(err);
        assertEquals("参数不能为空", err);
    }
}