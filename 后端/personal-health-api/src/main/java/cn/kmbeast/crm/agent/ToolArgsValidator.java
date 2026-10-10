package cn.kmbeast.crm.agent;

import cn.kmbeast.crm.agent.tool.Tool;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * AG-16：工具入参 schema 校验。
 *
 * <p>原实现各 Tool 手写 schema（LinkedHashMap）但从未校验，
 * LLM 返回的 arguments 直接强转，类型错即 ClassCastException。
 * 本工具按 schema 的 type 声明做轻量校验：
 * string / integer / number / boolean / array / object，缺失必填项也报错。
 */
public final class ToolArgsValidator {

    private ToolArgsValidator() {
    }

    /**
     * 校验参数。
     *
     * @param tool      工具（提供 schema）
     * @param arguments LLM 传入的参数
     * @return null 表示通过；否则返回错误信息
     */
    public static String validate(Tool tool, Map<String, Object> arguments) {
        if (arguments == null) {
            return "参数不能为空";
        }
        Map<String, Object> schema = tool.getParametersSchema();
        if (schema == null) {
            return null;
        }
        Object propertiesObj = schema.get("properties");
        if (!(propertiesObj instanceof Map)) {
            return null;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) propertiesObj;

        // 必填项检查
        Object requiredObj = schema.get("required");
        for (String r : normalizeRequired(requiredObj)) {
            if (!arguments.containsKey(r)) {
                return "缺少必填参数: " + r;
            }
        }

        // 类型检查
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            String key = entry.getKey();
            Object value = arguments.get(key);
            if (value == null) {
                continue; // 可选项缺失不校验
            }
            if (!(entry.getValue() instanceof Map)) {
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> prop = (Map<String, Object>) entry.getValue();
            Object type = prop.get("type");
            if (type == null) {
                continue;
            }
            String typeError = checkType(key, value, type.toString());
            if (typeError != null) {
                return typeError;
            }
        }
        return null;
    }

    private static String checkType(String key, Object value, String expectedType) {
        switch (expectedType) {
            case "string":
                if (!(value instanceof String)) return "参数 " + key + " 应为字符串";
                return null;
            case "integer":
                if (value instanceof Integer || value instanceof Long) return null;
                if (value instanceof Number) return null; // 数字均可接受
                return "参数 " + key + " 应为整数";
            case "number":
                if (value instanceof Number) return null;
                return "参数 " + key + " 应为数字";
            case "boolean":
                if (value instanceof Boolean) return null;
                return "参数 " + key + " 应为布尔值";
            case "array":
                if (value instanceof List) return null;
                return "参数 " + key + " 应为数组";
            case "object":
                if (value instanceof Map) return null;
                return "参数 " + key + " 应为对象";
            default:
                return null; // 未知类型不拦截
        }
    }

    /**
     * 归一化 schema 里的 {@code required} 字段，兼容三种写法。
     *
     * <p><b>2026-10-07 修复</b>：原实现只认 {@code instanceof List<?>}，
     * 而 {@code SearchDrugTool} 传的是 {@code new String[]{"keyword"}} ——
     * 数组不是 List，判断恒为 false，**必填校验被静默跳过、不报任何错**。
     * （wire 格式没问题，fastjson2 会把数组序列化成 JSON 数组；坏的是本地校验。）</p>
     *
     * <p>支持：{@code List<?>} / {@code Object[]}（含 {@code String[]}）/ 裸 {@code String}。
     * 空 {@code required}（空 List / 空数组）一律返回空列表 —— 语义上等于「无必填」。</p>
     *
     * @param requiredObj schema 里的 required 值，可为任意形态
     * @return 归一化后的参数名列表，永不为 null
     */
    private static List<String> normalizeRequired(Object requiredObj) {
        List<String> out = new ArrayList<>(4);
        if (requiredObj == null) {
            return out;
        }
        if (requiredObj instanceof List<?> list) {
            for (Object r : list) {
                if (r != null && !r.toString().trim().isEmpty()) {
                    out.add(r.toString().trim());
                }
            }
            return out;
        }
        if (requiredObj instanceof Object[] arr) {
            for (Object r : arr) {
                if (r != null && !r.toString().trim().isEmpty()) {
                    out.add(r.toString().trim());
                }
            }
            return out;
        }
        // 裸字符串（JSON Schema 允许 required: "a"，虽然少见）
        String s = requiredObj.toString().trim();
        if (!s.isEmpty()) {
            out.add(s);
        }
        return out;
    }
}
