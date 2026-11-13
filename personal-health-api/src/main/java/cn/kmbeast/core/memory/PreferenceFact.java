package cn.kmbeast.core.memory;

import com.alibaba.fastjson2.JSONObject;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Set;

/**
 * 一条结构化记忆事实（阶段 8 / D3 改写）。
 *
 * <p>与旧方案的区别：旧方案把用户填写的自由文本 {@code initial_prompt} 直接拼进
 * system prompt（提示词注入反模式），且没有按事实删除/过期/命名空间。
 * 这里每条事实都是<b>结构化字段</b>，渲染时只输出 {@code key: value} bullets，
 * 绝不把任意自由文本当成指令注入。</p>
 *
 * <p>全部字段用 String/基本类型，落库为 {@code user.ai_memory_facts} /
 * {@code hospital_doctor.ai_memory_facts} 的 JSON 数组。</p>
 */
public record PreferenceFact(
        String key,
        String value,
        String kind,
        double confidence,
        String source,
        String createdAt,
        String updatedAt,
        String expiresAt
) {

    /** kind 白名单：与 {@link MemoryPoisoningValidator} 校验口径一致。 */
    public static final Set<String> KINDS = Set.of("preference", "constraint", "goal", "context");

    /** 用户显式写入的事实：置信度恒为 1.0，来源 explicit。 */
    public static PreferenceFact explicit(String key, String value, String kind) {
        String now = Instant.now().toString();
        return new PreferenceFact(key, value, kind, 1.0, "explicit", now, now, null);
    }

    /** 同 key 覆盖时保留创建时间、刷新更新时间。 */
    public PreferenceFact touch() {
        return new PreferenceFact(key, value, kind, confidence, source,
                createdAt, Instant.now().toString(), expiresAt);
    }

    /** 过期判断：expiresAt 为空表示永不过期；解析不了视为已过期（fail-closed）。 */
    public boolean isExpired(Instant now) {
        if (expiresAt == null || expiresAt.isBlank()) {
            return false;
        }
        try {
            return !Instant.parse(expiresAt).isAfter(now == null ? Instant.now() : now);
        } catch (DateTimeParseException e) {
            return true;
        }
    }

    public JSONObject toJson() {
        JSONObject o = new JSONObject();
        o.put("key", key);
        o.put("value", value);
        o.put("kind", kind);
        o.put("confidence", confidence);
        o.put("source", source);
        o.put("createdAt", createdAt);
        o.put("updatedAt", updatedAt);
        if (expiresAt != null) {
            o.put("expiresAt", expiresAt);
        }
        return o;
    }

    /** 解析一条事实；缺 key/value 或类型异常返回 null（调用方负责整体拒绝）。 */
    public static PreferenceFact fromJson(JSONObject o) {
        if (o == null) {
            return null;
        }
        String key = o.getString("key");
        String value = o.getString("value");
        if (key == null || value == null) {
            return null;
        }
        double conf;
        try {
            conf = o.getDoubleValue("confidence");
        } catch (Exception e) {
            return null;
        }
        return new PreferenceFact(
                key,
                value,
                o.getString("kind") == null ? "preference" : o.getString("kind"),
                conf,
                o.getString("source") == null ? "explicit" : o.getString("source"),
                o.getString("createdAt"),
                o.getString("updatedAt"),
                o.getString("expiresAt"));
    }
}
