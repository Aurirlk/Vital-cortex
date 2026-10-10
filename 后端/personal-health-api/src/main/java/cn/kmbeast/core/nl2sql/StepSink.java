package cn.kmbeast.core.nl2sql;

/**
 * 流水线步骤事件接收器。
 *
 * <p>SSE 链路把它接到 {@code PrintWriter}；同步链路接一个收集器；单测接一个 List。
 * 用回调而非 {@code SseEmitter}，是为了让流水线本身与传输层解耦（可单测）。</p>
 *
 * <p>实现必须是<b>线程内</b>同步的：本流水线在单个请求线程内顺序执行，
 * 不做线程池切换（一旦切线程，{@code LocalThreadHolder} 身份会丢失）。</p>
 */
@FunctionalInterface
public interface StepSink {

    /** 空实现，供同步链路与测试使用。 */
    StepSink NOOP = event -> {
    };

    void emit(AgentStepEvent event);
}
