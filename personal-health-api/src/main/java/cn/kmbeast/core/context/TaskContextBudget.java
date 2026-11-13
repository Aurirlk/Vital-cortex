package cn.kmbeast.core.context;

/**
 * 阶段 9（P5-1）：按任务划分的上下文预算。
 *
 * <p><b>硬约束</b>：任何任务的预算都不得超过「模型窗口 × 50%」——
 * 剩下的一半必须留给模型输出与工具结果。管理员在 {@code system_config}
 * 配的 {@code context-max-tokens} 只是上限的再收窄，不能放大。</p>
 *
 * <p>四个任务的窗口系数（相对 {@code ceiling}）：chat 100% / react 80% /
 * nl2sql 60% / report 50%。理由：NL2SQL 与报告链路的 system prompt 里
 * 已经携带 schema、术语 evidence 等长固定前缀，历史可用空间必须更小。</p>
 *
 * <p>本类是<b>纯值对象</b>，不读 Spring 配置、不查库，单测可直接 new。
 * 配置装配由 {@link ContextCompressor} 完成。</p>
 */
public final class TaskContextBudget {

    public enum Task {
        CHAT(1.00d),
        REACT(0.80d),
        NL2SQL(0.60d),
        REPORT(0.50d);

        private final double factor;

        Task(double factor) {
            this.factor = factor;
        }

        public double factor() {
            return factor;
        }
    }

    /** 模型窗口占比上限（不可调，安全红线）。 */
    public static final double MAX_WINDOW_RATIO = 0.50d;
    public static final int DEFAULT_MAX_TOKENS = 4096;
    public static final int DEFAULT_MODEL_WINDOW = 131072;
    public static final int MIN_BUDGET = 256;

    private final int modelWindow;
    private final int ceiling;
    private final boolean positionGateEnabled;

    public TaskContextBudget(int modelWindow, int configuredMaxTokens, boolean positionGateEnabled) {
        this.modelWindow = modelWindow > 0 ? modelWindow : DEFAULT_MODEL_WINDOW;
        int half = (int) Math.floor(this.modelWindow * MAX_WINDOW_RATIO);
        int requested = configuredMaxTokens > 0 ? configuredMaxTokens : DEFAULT_MAX_TOKENS;
        // 钳制：requested 不能超过模型窗口 50%；下限保证不会把上下文压成 0
        this.ceiling = Math.max(MIN_BUDGET, Math.min(requested, half));
        this.positionGateEnabled = positionGateEnabled;
    }

    public static TaskContextBudget defaults() {
        return new TaskContextBudget(DEFAULT_MODEL_WINDOW, DEFAULT_MAX_TOKENS, true);
    }

    public int getModelWindow() {
        return modelWindow;
    }

    public int getCeiling() {
        return ceiling;
    }

    public boolean isPositionGateEnabled() {
        return positionGateEnabled;
    }

    /** 某任务可用的历史 token 上限。 */
    public int forTask(Task task) {
        if (task == null) {
            return ceiling;
        }
        return Math.max(MIN_BUDGET, (int) Math.floor(ceiling * task.factor()));
    }

    /** 是否守住了「≤ 模型窗口 50%」红线（供测试与日志断言）。 */
    public boolean withinWindowHalf() {
        return ceiling <= (int) Math.floor(modelWindow * MAX_WINDOW_RATIO);
    }

    /** 把任意请求值钳制进本任务预算，防止调用方传入放大值。 */
    public int clamp(int requested, Task task) {
        int limit = forTask(task);
        if (requested <= 0) {
            return limit;
        }
        return Math.min(requested, limit);
    }
}
