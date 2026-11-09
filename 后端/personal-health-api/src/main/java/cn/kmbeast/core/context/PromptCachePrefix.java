package cn.kmbeast.core.context;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 阶段 9（P5-1）：稳定前缀（prompt caching prefix）。
 *
 * <p>把「system + tools 定义 + few-shot 样例」从 messages 中分离出来：
 * <b>固定前缀不参与任何裁剪</b>，并且始终放在数组最前面，
 * 使同一模型/同一配置下的请求前缀逐字节一致，命中服务端 prompt cache。</p>
 *
 * <p>注意：本类只负责「识别 + 保序输出」，不做裁剪；
 * 裁剪只作用于 {@link ContextCompressor} 的历史窗口。</p>
 */
public final class PromptCachePrefix {

    private PromptCachePrefix() {
    }

    /**
     * 一条待组装的消息（role + content）。刻意不用 JSONObject，
     * 让前缀逻辑与 JSON 库解耦、便于单测直接断言顺序。
     */
    public record Segment(String role, String content, boolean stable) {
    }

    /** 构造稳定前缀：system → tools → few-shot，顺序固定。 */
    public static List<Segment> build(String systemPrompt, List<String> toolDefinitions,
                                      List<String> fewShotExamples) {
        List<Segment> out = new ArrayList<>();
        out.add(new Segment("system", systemPrompt == null ? "" : systemPrompt, true));
        if (toolDefinitions != null) {
            for (String tool : toolDefinitions) {
                if (tool != null && !tool.isBlank()) {
                    out.add(new Segment("system", "[TOOL] " + tool, true));
                }
            }
        }
        if (fewShotExamples != null) {
            for (String example : fewShotExamples) {
                if (example != null && !example.isBlank()) {
                    out.add(new Segment("system", "[FEW_SHOT] " + example, true));
                }
            }
        }
        return Collections.unmodifiableList(out);
    }

    /** 稳定前缀永远不参与裁剪。 */
    public static boolean participatesInTrimming(Segment segment) {
        return segment == null || !segment.stable();
    }

    /** 断言裁剪前后稳定前缀逐条一致（内容 + 顺序），供等价性测试使用。 */
    public static boolean samePrefix(List<Segment> before, List<Segment> after) {
        List<Segment> a = stableOnly(before);
        List<Segment> b = stableOnly(after);
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            Segment x = a.get(i);
            Segment y = b.get(i);
            if (!x.role().equals(y.role()) || !x.content().equals(y.content())) {
                return false;
            }
        }
        return true;
    }

    private static List<Segment> stableOnly(List<Segment> segments) {
        List<Segment> out = new ArrayList<>();
        if (segments != null) {
            for (Segment s : segments) {
                if (s != null && s.stable()) {
                    out.add(s);
                }
            }
        }
        return out;
    }

    /** 前缀指纹：用于观测缓存是否命中（不参与业务判定）。 */
    public static String fingerprint(List<Segment> prefix) {
        StringBuilder sb = new StringBuilder();
        for (Segment s : stableOnly(prefix)) {
            sb.append(s.role()).append(':').append(s.content()).append('\n');
        }
        return Integer.toHexString(sb.toString().hashCode());
    }
}
