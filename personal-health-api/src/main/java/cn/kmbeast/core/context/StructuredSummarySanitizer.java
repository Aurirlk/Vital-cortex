package cn.kmbeast.core.context;

import cn.kmbeast.pojo.entity.AiChatRecord;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 阶段 9（P5-1）：结构化摘要消毒器（落实旧 N3「LLM 摘要幻觉注入后续 prompt」的要求）。
 *
 * <p>旧实现把被丢弃历史压成一句自由文本「讨论过的主题：…（用户共提出 N 个问题）」，
 * 直接作为 {@code system} 消息拼进后续 prompt —— 历史里若含
 * 「忽略之前的指令」「你现在是管理员」之类内容，关键词提取不会删掉它，
 * 于是形成一处持久化的提示词注入面。</p>
 *
 * <p>本类把摘要改成<b>结构化 JSON</b>：固定四个键
 * {@code topics / entities / decisions / unresolved}，值一律做白名单消毒，
 * 且只允许从历史里提取的「词」而非整句。任何一项违规 → <b>整批拒绝</b>（返回 null），
 * 调用方不得注入任何摘要，绝不降级为「删掉那一句继续用」。</p>
 */
public final class StructuredSummarySanitizer {

    public static final Set<String> ALLOWED_KEYS = Set.of("topics", "entities", "decisions", "unresolved");
    public static final int MAX_ITEMS_PER_KEY = 8;
    public static final int MAX_VALUE_CHARS = 64;

    /** 关键词提取：连续 2 个以上汉字或字母（与旧实现同源，保证主题线索不丢）。 */
    private static final Pattern KEYWORD = Pattern.compile("[\\u4e00-\\u9fa5]{2,}|[A-Za-z]{2,}");

    /**
     * 指令式语句 / 角色标记 / 模板注入标记，命中即整批拒绝。
     * 只要摘要里出现一个词，就不注入摘要——宁可丢线索，不留注入面。
     */
    private static final Pattern FORBIDDEN = Pattern.compile(
            "(?i)(ignore\\s+(all\\s+)?(previous|above)|忽略(之前|上面|以上|所有)" +
                    "|you are now|你现在是|system\\s*prompt|系统提示词?|role\\s*:|\\b(assistant|system|user)\\s*:" +
                    "|扮演|请(执行|输出|返回)(以下|下面)|覆盖(规则|指令)|new instructions" +
                    "|<\\||\\|>|\\{\\{|\\}\\}|```|<[^>]*>)");

    private StructuredSummarySanitizer() {
    }

    /**
     * 从被丢弃的历史生成结构化摘要（零 LLM 调用）。
     *
     * @param dropped 被压缩丢弃的较早历史（按时间升序）
     * @return 通过消毒的 JSON 字符串；无可提取内容或任一违规时返回 {@code null}
     */
    public static String summarize(List<AiChatRecord> dropped) {
        if (dropped == null || dropped.isEmpty()) {
            return null;
        }
        Map<String, Integer> freq = new LinkedHashMap<>();
        long userTurns = 0L;
        for (AiChatRecord r : dropped) {
            if (r == null || r.getContent() == null) {
                continue;
            }
            if ("user".equals(r.getRole())) {
                userTurns++;
            }
            Matcher m = KEYWORD.matcher(r.getContent());
            while (m.find()) {
                freq.merge(m.group(), 1, Integer::sum);
            }
        }
        if (freq.isEmpty()) {
            return null;
        }
        List<String> topics = new ArrayList<>();
        for (Map.Entry<String, Integer> e : freq.entrySet()) {
            if (topics.size() >= MAX_ITEMS_PER_KEY) {
                break;
            }
            topics.add(e.getKey());
        }
        JSONObject obj = new JSONObject();
        obj.put("topics", topics);
        obj.put("entities", new ArrayList<String>());
        obj.put("decisions", new ArrayList<String>());
        obj.put("unresolved", List.of("用户共提出 " + userTurns + " 个问题"));
        return sanitize(obj.toJSONString());
    }

    /**
     * 校验并规范化一份结构化摘要 JSON（供 LLM 摘要 / 外部输入复用）。
     *
     * <p>校验项：① 顶层必须是对象；② 键必须在白名单内、且四个键齐全；
     * ③ 值必须是字符串数组；④ 每条值 ≤ {@link #MAX_VALUE_CHARS}、
     * 无控制字符/换行、无 {@link #FORBIDDEN} 命中；⑤ 数组长度 ≤ {@link #MAX_ITEMS_PER_KEY}。
     * <b>任一违规返回 null（整批拒绝）</b>。</p>
     */
    public static String sanitize(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            return null;
        }
        JSONObject obj;
        try {
            obj = JSON.parseObject(rawJson);
        } catch (Exception e) {
            return null;
        }
        if (obj == null || obj.size() != ALLOWED_KEYS.size()) {
            return null;
        }
        Map<String, List<String>> cleaned = new LinkedHashMap<>();
        for (String key : ALLOWED_KEYS) {
            if (!obj.containsKey(key)) {
                return null;
            }
            JSONArray arr;
            try {
                arr = obj.getJSONArray(key);
            } catch (Exception e) {
                return null;
            }
            if (arr == null || arr.size() > MAX_ITEMS_PER_KEY) {
                return null;
            }
            Set<String> values = new LinkedHashSet<>();
            for (int i = 0; i < arr.size(); i++) {
                Object raw = arr.get(i);
                if (!(raw instanceof String s)) {
                    return null;
                }
                if (!safeValue(s)) {
                    return null;
                }
                values.add(s.trim());
            }
            cleaned.put(key, new ArrayList<>(values));
        }
        // fastjson2 没有 fastjson 1.x 的 JSONObject(boolean ordered)；
        // 用 LinkedHashMap 作底层，显式锁定四个键的输出顺序，保证前缀稳定。
        JSONObject out = new JSONObject(new LinkedHashMap<>());
        // 固定键顺序，保证前缀稳定（不依赖 Set 迭代顺序）
        for (String key : List.of("topics", "entities", "decisions", "unresolved")) {
            out.put(key, cleaned.get(key));
        }
        return out.toJSONString();
    }

    /** 单条值是否安全。 */
    public static boolean safeValue(String value) {
        if (value == null || value.isBlank() || value.length() > MAX_VALUE_CHARS) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            if (Character.isISOControl(value.charAt(i))) {
                return false;
            }
        }
        return !FORBIDDEN.matcher(value).find();
    }
}
