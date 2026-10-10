package cn.kmbeast.core.nl2sql;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 流水线单步事件。用于 SSE 推送给前端，支撑「工作流 / 思考过程 / 工具调用」三视图。
 *
 * <p>刻意使用 {@link LinkedHashMap} 承载 payload —— 项目既有约定：{@code Map.of}
 * 不接受 null 值且 {@code get(null)} 直接 NPE，SSE 拼事件时最易踩。</p>
 */
@Data
public class AgentStepEvent {

    /** 状态常量。 */
    public static final String RUNNING = "RUNNING";
    public static final String SUCCESS = "SUCCESS";
    public static final String FAILED = "FAILED";
    public static final String SKIPPED = "SKIPPED";

    /** 步骤序号（1 起）。 */
    private int order;
    /** 步骤枚举名。 */
    private String step;
    /** 中文标题。 */
    private String title;
    /** 归属工具名（用于「工具调用」视图）。 */
    private String tool;
    /** 运行状态。 */
    private String status;
    /** 一句话结果摘要。 */
    private String summary;
    /** 思考过程（模型输出/推理文本，可能较长，前端折叠展示）。 */
    private String thinking;
    /** 结构化附加数据（表名、SQL、行数预览等）。 */
    private Map<String, Object> payload = new LinkedHashMap<>();
    /** 步骤耗时（毫秒）。 */
    private long elapsedMs;
    /** 事件时间戳。 */
    private long ts = System.currentTimeMillis();

    public static AgentStepEvent running(AgentStep step) {
        AgentStepEvent e = new AgentStepEvent();
        e.order = step.getOrder();
        e.step = step.name();
        e.title = step.getTitle();
        e.tool = step.getTool();
        e.status = RUNNING;
        e.summary = step.getDescription();
        return e;
    }

    public static AgentStepEvent success(AgentStep step, long elapsedMs, String summary) {
        AgentStepEvent e = base(step, SUCCESS, elapsedMs, summary);
        return e;
    }

    public static AgentStepEvent failed(AgentStep step, long elapsedMs, String summary) {
        AgentStepEvent e = base(step, FAILED, elapsedMs, summary);
        return e;
    }

    public static AgentStepEvent skipped(AgentStep step, String summary) {
        AgentStepEvent e = base(step, SKIPPED, 0L, summary);
        return e;
    }

    private static AgentStepEvent base(AgentStep step, String status, long elapsedMs, String summary) {
        AgentStepEvent e = new AgentStepEvent();
        e.order = step.getOrder();
        e.step = step.name();
        e.title = step.getTitle();
        e.tool = step.getTool();
        e.status = status;
        e.elapsedMs = elapsedMs;
        e.summary = summary;
        return e;
    }

    public AgentStepEvent withThinking(String thinking) {
        this.thinking = thinking;
        return this;
    }

    public AgentStepEvent withPayload(String key, Object value) {
        this.payload.put(key, value);
        return this;
    }
}
