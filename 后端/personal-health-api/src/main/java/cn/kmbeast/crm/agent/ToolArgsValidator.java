package cn.kmbeast.crm.agent;

import cn.kmbeast.crm.agent.tool.Tool;

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
        if (requiredObj instanceof List) {
            for (Object r : (List<?>) requiredObj) {
                if (r != null && !arguments.containsKey(r.toString())) {
                    return "缺少必填参数: " + r;
                }
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
}
