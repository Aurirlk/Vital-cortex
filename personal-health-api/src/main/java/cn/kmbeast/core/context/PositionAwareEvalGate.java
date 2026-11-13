package cn.kmbeast.core.context;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 阶段 9（P5-1）：位置感知 eval 门禁。
 *
 * <p>长上下文存在「中部衰减」（lost-in-the-middle）：模型对开头与结尾的内容
 * 注意力显著高于中部。剂量、诊断结论这类<b>关键医疗事实</b>若落在中部，
 * 容易被忽略或与其它轮次混淆，属于安全风险而不只是效果问题。</p>
 *
 * <p>本门禁把落在中部衰减区的关键事实消息整条移到窗口<b>尾部</b>
 * （保持彼此相对顺序，条数不变），其余消息保持原顺序。
 * 开关关闭时原样返回 —— 这是与旧实现零回归的兼容路径。</p>
 */
public final class PositionAwareEvalGate {

    /** 中部衰减区：索引落在 [25%, 75%) 视为中部。 */
    public static final double MIDDLE_START = 0.25d;
    public static final double MIDDLE_END = 0.75d;

    /** 剂量 / 结论 / 诊断类关键事实（find 而非 matches，兼容多行内容）。 */
    private static final Pattern CRITICAL = Pattern.compile(
            "(?i)(\\d+(\\.\\d+)?\\s*(mg|ml|g|iu|ug|mmol|mmhg|片|粒|毫升|毫克|克|单位|次/日|每日\\d+次))" +
                    "|(诊断|确诊|结论|处方|建议用药|剂量|血压为|血糖为|停[药用])");

    private PositionAwareEvalGate() {
    }

    /** 是否为关键医疗事实。 */
    public static boolean isCriticalMedicalFact(String text) {
        return text != null && CRITICAL.matcher(text).find();
    }

    /** 该索引是否落在中部衰减区。 */
    public static boolean inMiddle(int index, int total) {
        if (total <= 2 || index < 0) {
            return false;
        }
        double p = (double) index / (total - 1);
        return p >= MIDDLE_START && p < MIDDLE_END;
    }

    /**
     * 应用位置门禁。
     *
     * @param segments 历史窗口（不含稳定前缀与当前用户消息）
     * @param enabled  是否开启（关闭时原样返回，保证零回归）
     * @return 处理后的列表；<b>条数与输入恒等</b>，仅局部顺序调整
     */
    public static <T> List<T> apply(List<T> segments, boolean enabled,
                                    java.util.function.Function<T, String> textOf) {
        if (segments == null || segments.isEmpty()) {
            return segments == null ? List.of() : segments;
        }
        if (!enabled) {
            return new ArrayList<>(segments);
        }
        List<T> head = new ArrayList<>();
        List<T> middle = new ArrayList<>();
        List<T> tail = new ArrayList<>();
        int total = segments.size();
        for (int i = 0; i < total; i++) {
            T item = segments.get(i);
            String text = textOf == null ? null : textOf.apply(item);
            if (inMiddle(i, total) && isCriticalMedicalFact(text)) {
                tail.add(item);   // 中部关键事实 → 提升到尾部
            } else if (i < Math.max(1, (int) Math.ceil(total * MIDDLE_START))) {
                head.add(item);
            } else {
                middle.add(item);
            }
        }
        List<T> out = new ArrayList<>(total);
        out.addAll(head);
        out.addAll(middle);
        out.addAll(tail);
        return out;
    }

    /** 门禁开启后，中部衰减区不得残留关键事实；返回违规条数（0 = 合规）。 */
    public static <T> int countMiddleCritical(List<T> segments,
                                              java.util.function.Function<T, String> textOf) {
        if (segments == null || segments.isEmpty()) {
            return 0;
        }
        int violations = 0;
        int total = segments.size();
        for (int i = 0; i < total; i++) {
            String text = textOf == null ? null : textOf.apply(segments.get(i));
            if (inMiddle(i, total) && isCriticalMedicalFact(text)) {
                violations++;
            }
        }
        return violations;
    }
}
