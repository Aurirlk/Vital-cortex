package cn.kmbeast.core.context;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingRegistry;
import com.knuddels.jtokkit.api.EncodingType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * 阶段 9（P5-1）：精确 token 计数器。
 *
 * <p>旧 {@code TokenBudgetManager.estimateTextTokens} 用 {@code len*0.6} 粗估，
 * 误差可达 ±30%，且对英文/代码/emoji 完全失准 —— <b>不能用于预算决策</b>。
 * 本类走 jtokkit 1.1.0 的真实 BPE 编码（cl100k_base / o200k_base），
 * 离线仓已具备 jar 与两份 tiktoken 资源，因此<b>不需要走「保守上界」降级方案</b>。</p>
 *
 * <p>防御性设计：若运行环境缺 jar / 编码表加载失败，{@link #isExact()} 返回 false，
 * 计数回落 {@link #conservativeUpperBound(String)}（UTF-8 字节加权乐观上界的保守版本），
 * 并明确标注非 tokenizer 级精确，绝不静默假装精确。</p>
 *
 * <p>{@code usage.prompt_tokens} 事后校验：{@link #recordUsage(int, int)} 只记录偏差供观测，
 * 不参与请求前决策，避免把一次真实响应变成阻断条件。</p>
 */
@Slf4j
@Component
public class ExactTokenCounter {

    public enum EncodingName {
        CL100K("cl100k_base", EncodingType.CL100K_BASE),
        O200K("o200k_base", EncodingType.O200K_BASE);

        private final String label;
        private final EncodingType type;

        EncodingName(String label, EncodingType type) {
            this.label = label;
            this.type = type;
        }

        public String label() {
            return label;
        }

        EncodingType type() {
            return type;
        }
    }

    /** 图片占位 token（与 TokenBudgetManager 同源口径，避免引入 AiConfig 依赖）。 */
    public static final int DEFAULT_TOKENS_PER_IMAGE = 85;

    private final EncodingRegistry registry;
    private volatile EncodingName encodingName;
    private volatile Encoding encoding;
    private volatile long lastDeltaRatioX100 = 0L;

    public ExactTokenCounter() {
        EncodingRegistry r = null;
        try {
            r = Encodings.newLazyEncodingRegistry();
        } catch (Throwable t) {
            log.warn("[Context] jtokkit 不可用，精确计数降级: {}", t.toString());
        }
        this.registry = r;
        use(EncodingName.CL100K);
    }

    /** 切换编码（GPT-4o 及之后用 o200k_base）。加载失败时保持上一个可用编码。 */
    public synchronized void use(EncodingName name) {
        EncodingName target = name == null ? EncodingName.CL100K : name;
        Encoding loaded = null;
        if (registry != null) {
            try {
                loaded = registry.getEncoding(target.type());
            } catch (Throwable t) {
                log.warn("[Context] {} 编码加载失败: {}", target.label(), t.toString());
            }
        }
        if (loaded != null) {
            this.encoding = loaded;
            this.encodingName = target;
        }
    }

    /** 是否为真正的 tokenizer 级精确计数（false = 降级为保守上界）。 */
    public boolean isExact() {
        return encoding != null;
    }

    public EncodingName getEncodingName() {
        return encodingName;
    }

    /** 供文档/日志如实标注，禁止把降级模式写成「精确」。 */
    public String mode() {
        return isExact() ? "tokenizer:" + encodingName.label() : "fallback:utf8-upper-bound";
    }

    /** 统计一条文本的 token 数。 */
    public int count(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        Encoding e = this.encoding;
        if (e != null) {
            try {
                return e.countTokens(text);
            } catch (Throwable t) {
                // jtokkit 对超长文本有保护阈值；不能因为一次异常就让整条链路失败
                log.debug("[Context] BPE 计数异常，回落保守上界: {}", t.toString());
            }
        }
        return conservativeUpperBound(text);
    }

    /**
     * 降级用的<b>保守上界</b>：按 UTF-8 字节数 / 2 上取整。
     *
     * <p>中文 3 字节通常 1~2 token，ASCII 4 字节通常 1 token —— 该式恒不低估，
     * 因此用作预算不会超发；但它不是 tokenizer 级精确，{@link #mode()} 会如实标注。</p>
     */
    public static int conservativeUpperBound(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int bytes = text.getBytes(StandardCharsets.UTF_8).length;
        return Math.max(1, (int) Math.ceil(bytes / 2.0d));
    }

    /** 统计一个 OpenAI 协议 messages 数组（含多模态图片占位）。 */
    public int countMessages(JSONArray messages) {
        return countMessages(messages, DEFAULT_TOKENS_PER_IMAGE);
    }

    public int countMessages(JSONArray messages, int tokensPerImage) {
        if (messages == null || messages.isEmpty()) {
            return 0;
        }
        int total = 0;
        for (int i = 0; i < messages.size(); i++) {
            JSONObject msg = messages.getJSONObject(i);
            if (msg == null) {
                continue;
            }
            Object content = msg.get("content");
            if (content instanceof String s) {
                total += count(s);
            } else if (content instanceof JSONArray parts) {
                for (int j = 0; j < parts.size(); j++) {
                    JSONObject part = parts.getJSONObject(j);
                    if (part == null) {
                        continue;
                    }
                    if ("text".equals(part.getString("type"))) {
                        total += count(part.getString("text"));
                    } else if ("image_url".equals(part.getString("type"))) {
                        total += Math.max(1, tokensPerImage);
                    }
                }
            }
            // role / 分隔符 / message 边界的固定开销（OpenAI 协议经验值）
            total += 4;
        }
        return total;
    }

    /**
     * 事后校验：记录请求前估算与真实 {@code usage.prompt_tokens} 的相对偏差（百分数）。
     * 只做观测，不改变任何已发出的请求。
     */
    public void recordUsage(int estimated, int actualPromptTokens) {
        if (estimated <= 0 || actualPromptTokens < 0) {
            return;
        }
        this.lastDeltaRatioX100 = Math.round(((double) (actualPromptTokens - estimated) / estimated) * 100);
        if (log.isDebugEnabled()) {
            log.debug("[Context] token 事后校验: 估算={} 实际={} 偏差={}% ({})",
                    estimated, actualPromptTokens, lastDeltaRatioX100, mode());
        }
    }

    /** 最近一次事后校验偏差（百分数，可为负）。 */
    public long getLastDeltaRatioX100() {
        return lastDeltaRatioX100;
    }
}
