package cn.kmbeast.core.context;

import cn.kmbeast.pojo.entity.AiChatRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 阶段 9（P5-1）：统一的上下文压缩器（D4 之三改写后的唯一实现）。
 *
 * <p>取代 {@code AiServiceImpl} 里私有的 {@code appendCompressedHistory} +
 * {@code summarizeDroppedHistory}。串起五步：</p>
 * <ol>
 *   <li><b>稳定前缀分离</b>：system / tools / few-shot 由 {@link PromptCachePrefix} 标记，
 *       <b>不参与任何裁剪</b>（{@link PromptCachePrefix#participatesInTrimming}）。</li>
 *   <li><b>条数截断</b>：保留最近 {@code rounds*2} 条，语义与旧实现逐行一致。</li>
 *   <li><b>字符预算</b>：从最旧整条丢弃，语义与旧实现逐行一致。</li>
 *   <li><b>精确 token 预算</b>：用 {@link ExactTokenCounter}（真实 BPE）逐条回收，
 *       上限由 {@link TaskContextBudget} 钳制到「模型窗口 50%」以内。</li>
 *   <li><b>结构化摘要 + 位置门禁</b>：被丢弃历史经
 *       {@link StructuredSummarySanitizer} 消毒为 JSON 摘要（整批 fail-closed），
 *       保留窗口经 {@link PositionAwareEvalGate} 把中部关键医疗事实提到尾部。</li>
 * </ol>
 *
 * <p><b>零回归约束</b>：默认参数 {@code rounds=10 / maxChars=4000 / summaryThreshold=0}
 * 与旧实现一一映射；{@code compress()} 产出的历史段条数、顺序、role、content
 * 由 {@code ContextCompressionParityTest} 逐条与旧算法比对。</p>
 *
 * <p><b>不引入历史存储</b>：ReAct / NL2SQL 链路仍是单轮，本类只提供
 * {@link #guardWindow(List)} 做条数/字符上限与位置门禁。</p>
 */
@Slf4j
@Component
public class ContextCompressor {

    public static final int DEFAULT_ROUNDS = 10;
    public static final int DEFAULT_MAX_CHARS = 4000;
    public static final int DEFAULT_SUMMARY_THRESHOLD = 0;

    @Autowired(required = false)
    private ExactTokenCounter tokenCounter;

    @Autowired(required = false)
    private cn.kmbeast.core.nl2sql.DataAgentConfigHolder configHolder;

    /** 压缩预算（纯值对象，未装配配置源时用代码默认值）。 */
    public record Budget(int rounds, int maxChars, int summaryThreshold,
                         int maxTokens, int modelWindow, boolean positionGateEnabled) {

        public static Budget defaults() {
            return new Budget(DEFAULT_ROUNDS, DEFAULT_MAX_CHARS, DEFAULT_SUMMARY_THRESHOLD,
                    TaskContextBudget.DEFAULT_MAX_TOKENS, TaskContextBudget.DEFAULT_MODEL_WINDOW, true);
        }

        /** 旧实现的等价预算（关闭 token 预算与位置门禁，用于等价性比对）。 */
        public static Budget legacy() {
            return new Budget(DEFAULT_ROUNDS, DEFAULT_MAX_CHARS, DEFAULT_SUMMARY_THRESHOLD,
                    0, 0, false);
        }
    }

    /** 压缩结果。segments 即旧 {@code appendCompressedHistory} 追加的那一段。 */
    public static final class CompressedResult {
        private final List<PromptCachePrefix.Segment> segments;
        private final int dropped;
        private final int kept;
        private final int estimatedTokens;
        private final boolean exactTokens;
        private final String counterMode;
        private final boolean positionGateApplied;
        private final boolean summaryInjected;

        CompressedResult(List<PromptCachePrefix.Segment> segments, int dropped, int kept,
                         int estimatedTokens, boolean exactTokens, String counterMode,
                         boolean positionGateApplied, boolean summaryInjected) {
            this.segments = segments;
            this.dropped = dropped;
            this.kept = kept;
            this.estimatedTokens = estimatedTokens;
            this.exactTokens = exactTokens;
            this.counterMode = counterMode;
            this.positionGateApplied = positionGateApplied;
            this.summaryInjected = summaryInjected;
        }

        public List<PromptCachePrefix.Segment> getSegments() {
            return segments;
        }

        public int getDropped() {
            return dropped;
        }

        public int getKept() {
            return kept;
        }

        public int getEstimatedTokens() {
            return estimatedTokens;
        }

        public boolean isExactTokens() {
            return exactTokens;
        }

        public String getCounterMode() {
            return counterMode;
        }

        public boolean isPositionGateApplied() {
            return positionGateApplied;
        }

        public boolean isSummaryInjected() {
            return summaryInjected;
        }
    }

    /** 从在线配置装配预算；未装配时回落代码默认值，保证单测与降级路径可用。 */
    public Budget budgetFromConfig(TaskContextBudget.Task task) {
        int rounds = DEFAULT_ROUNDS;
        int maxChars = DEFAULT_MAX_CHARS;
        int summaryThreshold = DEFAULT_SUMMARY_THRESHOLD;
        int maxTokens = TaskContextBudget.DEFAULT_MAX_TOKENS;
        int modelWindow = TaskContextBudget.DEFAULT_MODEL_WINDOW;
        boolean gate = true;
        try {
            if (configHolder != null) {
                rounds = configHolder.getContextRounds();
                maxChars = configHolder.getContextMaxChars();
                summaryThreshold = configHolder.getContextSummaryThreshold();
                maxTokens = configHolder.getContextMaxTokens();
                gate = configHolder.isContextPositionGateEnabled();
            }
        } catch (Exception e) {
            log.debug("[Context] 读取压缩配置失败，使用默认值: {}", e.getMessage());
        }
        TaskContextBudget taskBudget = new TaskContextBudget(modelWindow, maxTokens, gate);
        return new Budget(rounds, maxChars, summaryThreshold,
                taskBudget.forTask(task), modelWindow, gate);
    }

    /**
     * 压缩历史，返回可直接追加到 messages 的段（摘要 + 保留历史）。
     *
     * @param history 完整历史（按时间升序）
     * @param budget  预算；null 时用默认预算
     */
    public CompressedResult compress(List<AiChatRecord> history, Budget budget) {
        Budget b = budget == null ? Budget.defaults() : budget;
        List<PromptCachePrefix.Segment> out = new ArrayList<>();
        if (history == null || history.isEmpty() || b.rounds() <= 0) {
            return new CompressedResult(out, 0, 0, 0, counter() != null && counter().isExact(),
                    counterMode(), false, false);
        }

        ExactTokenCounter counter = counter();
        boolean exact = counter != null && counter.isExact();

        int maxMessages = b.rounds() * 2;
        int startIdx = Math.max(0, history.size() - maxMessages);
        int dropped = startIdx;

        // ---- 步骤 2：字符预算（与旧实现逐行一致，从最旧整条丢弃） ----
        long used = 0L;
        int keepFrom = startIdx;
        for (int i = startIdx; i < history.size(); i++) {
            AiChatRecord r = history.get(i);
            if (r == null) {
                continue;
            }
            int cost = r.getContent() == null ? 8 : r.getContent().length();
            if (used + cost > b.maxChars() && i > startIdx) {
                keepFrom = i;
                break;
            }
            used += cost;
        }

        // ---- 步骤 4：精确 token 预算（只在开启时进一步回收） ----
        int tokenLimit = b.maxTokens();
        if (tokenLimit > 0 && counter != null) {
            int total = 0;
            for (int i = keepFrom; i < history.size(); i++) {
                AiChatRecord r = history.get(i);
                if (r != null) {
                    total += counter.count(r.getContent() == null ? "" : r.getContent()) + 4;
                }
            }
            while (total > tokenLimit && keepFrom < history.size() - 1) {
                AiChatRecord r = history.get(keepFrom);
                if (r != null) {
                    total -= counter.count(r.getContent() == null ? "" : r.getContent()) + 4;
                }
                keepFrom++;
            }
        }

        // ---- 步骤 5a：结构化摘要（整批 fail-closed） ----
        boolean summaryInjected = false;
        if (dropped > 0 && b.summaryThreshold() > 0 && dropped >= b.summaryThreshold()) {
            String json = StructuredSummarySanitizer.summarize(history.subList(0, dropped));
            if (json != null) {
                out.add(new PromptCachePrefix.Segment("system",
                        "[早期对话结构化摘要] " + json, false));
                summaryInjected = true;
            }
        }

        // ---- 步骤 5b：保留窗口 + 位置门禁 ----
        List<PromptCachePrefix.Segment> window = new ArrayList<>();
        for (int i = keepFrom; i < history.size(); i++) {
            AiChatRecord r = history.get(i);
            if (r == null) {
                continue;
            }
            window.add(new PromptCachePrefix.Segment(r.getRole(),
                    r.getContent() == null ? "" : r.getContent(), false));
        }
        boolean gateApplied = b.positionGateEnabled() && !window.isEmpty();
        List<PromptCachePrefix.Segment> arranged = PositionAwareEvalGate.apply(
                window, gateApplied, PromptCachePrefix.Segment::content);

        // 稳定前缀不参与裁剪：这里只做断言式保护，防止未来有人把前缀塞进 window
        for (PromptCachePrefix.Segment s : arranged) {
            if (!PromptCachePrefix.participatesInTrimming(s)) {
                log.warn("[Context] 稳定前缀误入裁剪窗口，已跳过");
                continue;
            }
            out.add(s);
        }

        int estimatedTokens = 0;
        if (counter != null) {
            for (PromptCachePrefix.Segment s : out) {
                estimatedTokens += counter.count(s.content()) + 4;
            }
        }
        if (log.isDebugEnabled()) {
            log.debug("[Context] 压缩: 历史 {} 条 → 保留 {} 条, 丢弃 {} 条, {} token ({})",
                    history.size(), window.size(), history.size() - keepFrom,
                    estimatedTokens, counterMode());
        }
        return new CompressedResult(out, dropped, window.size(), estimatedTokens,
                exact, counterMode(), gateApplied, summaryInjected);
    }

    /**
     * 供 ReAct / NL2SQL 单轮链路使用：只做条数 + 字符上限与位置门禁，
     * <b>不引入任何历史存储</b>。输入是本次请求自带的消息窗口。
     */
    public <T> List<T> guardWindow(List<T> window, java.util.function.Function<T, String> textOf) {
        if (window == null || window.isEmpty()) {
            return window == null ? List.of() : window;
        }
        Budget b = budgetFromConfig(TaskContextBudget.Task.REACT);
        int maxMessages = Math.max(0, b.rounds() * 2);
        List<T> trimmed = window.size() > maxMessages
                ? new ArrayList<>(window.subList(window.size() - maxMessages, window.size()))
                : new ArrayList<>(window);
        long used = 0L;
        int keepFrom = 0;
        for (int i = 0; i < trimmed.size(); i++) {
            String text = textOf == null ? null : textOf.apply(trimmed.get(i));
            int cost = text == null ? 8 : text.length();
            if (used + cost > b.maxChars() && i > 0) {
                keepFrom = i;
                break;
            }
            used += cost;
        }
        if (keepFrom > 0) {
            trimmed = new ArrayList<>(trimmed.subList(keepFrom, trimmed.size()));
        }
        return PositionAwareEvalGate.apply(trimmed, b.positionGateEnabled(), textOf);
    }

    private ExactTokenCounter counter() {
        if (tokenCounter != null) {
            return tokenCounter;
        }
        return FALLBACK_COUNTER;
    }

    private String counterMode() {
        ExactTokenCounter c = counter();
        return c == null ? "unavailable" : c.mode();
    }

    /** 单测 / 未装配 Spring 时的兜底计数器。 */
    private static final ExactTokenCounter FALLBACK_COUNTER = new ExactTokenCounter();
}
