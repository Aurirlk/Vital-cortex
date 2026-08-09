package cn.kmbeast.crm.agent.tool;

import java.util.HashMap;
import java.util.Map;

public class ToolContext {

    private static final ThreadLocal<Map<String, Object>> context = new ThreadLocal<>();

    public static void set(String key, Object value) {
        Map<String, Object> ctx = context.get();
        if (ctx == null) {
            ctx = new HashMap<>();
            context.set(ctx);
        }
        ctx.put(key, value);
    }

    public static Object get(String key) {
        Map<String, Object> ctx = context.get();
        return ctx != null ? ctx.get(key) : null;
    }

    public static String getString(String key) {
        Object val = get(key);
        return val != null ? val.toString() : null;
    }

    public static Integer getInt(String key) {
        Object val = get(key);
        if (val instanceof Integer integer) return integer;
        if (val instanceof Number number) return number.intValue();
        return null;
    }

    public static void clear() {
        context.remove();
    }

    public static void setPhoneAndUserId(String phoneNumber, Integer userId) {
        set("phoneNumber", phoneNumber);
        set("userId", userId);
    }
}
