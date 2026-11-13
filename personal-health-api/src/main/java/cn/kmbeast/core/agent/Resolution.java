package cn.kmbeast.core.agent;

import cn.kmbeast.crm.agent.IntentRouter;

import java.util.Map;

/**
 * 一次意图识别的最终结论（P2-2 统一入口 {@link IntentResolver} 的返回值）。
 *
 * <p>把「规则 / LLM / 兜底」三种来源归一成一个结构，调用方（{@code AgentCoordinator}、
 * 对话控制器）只消费本对象，不再关心意图是怎么算出来的。</p>
 */
public class Resolution {

    /** 结论来源：规则命中 / LLM 兜底命中 / 双重失败回落 */
    public enum Source {
        RULE, LLM, FALLBACK
    }

    private final IntentRouter.IntentHint hint;
    private final Source source;
    private final String llmRaw;

    private Resolution(IntentRouter.IntentHint hint, Source source, String llmRaw) {
        this.hint = hint;
        this.source = source;
        this.llmRaw = llmRaw;
    }

    public static Resolution fromRule(IntentRouter.IntentHint hint) {
        return new Resolution(hint, Source.RULE, null);
    }

    public static Resolution fromLlm(IntentRouter.IntentHint hint, String llmRaw) {
        return new Resolution(hint, Source.LLM, llmRaw);
    }

    public static Resolution fallback(IntentRouter.IntentHint hint, String llmRaw) {
        return new Resolution(hint, Source.FALLBACK, llmRaw);
    }

    /** 意图枚举（HEALTH_RECORDS / GRAPH / DRUG / KNOWLEDGE / DATA_QUERY / CHAT） */
    public IntentRouter.Intent intent() {
        return hint == null ? IntentRouter.Intent.CHAT : hint.getIntent();
    }

    public IntentRouter.Confidence confidence() {
        return hint == null ? IntentRouter.Confidence.LOW : hint.getConfidence();
    }

    /** 承载意图倾向的 hint（含 hintText / targetTool / suggestedArgs），供 prompt 引导与 fail-safe 使用 */
    public IntentRouter.IntentHint hint() {
        return hint;
    }

    public Source source() {
        return source;
    }

    /** LLM 兜底命中的原始标签（用于审计/排查）；规则与回落为 null */
    public String llmRaw() {
        return llmRaw;
    }

    /** 是否「高置信度 + 有数据工具落点」（供四层 fail-safe 复用） */
    public boolean isHighConfidenceDataIntent() {
        return hint != null && hint.isHighConfidenceDataIntent();
    }

    /** 仅当本结论确实来自 LLM 兜底时才为 true */
    public boolean isFromLlm() {
        return source == Source.LLM;
    }

    @Override
    public String toString() {
        return "Resolution{intent=" + intent() + ", source=" + source
                + ", targetTool=" + (hint == null ? null : hint.getTargetTool()) + "}";
    }
}
