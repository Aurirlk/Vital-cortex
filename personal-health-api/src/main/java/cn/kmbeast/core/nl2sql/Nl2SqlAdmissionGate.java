package cn.kmbeast.core.nl2sql;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * NL2SQL 二值准入门禁（阶段 6 · P2-5，2026-11-09）。
 *
 * <p><b>修的是什么</b>：改造前 {@code DataAgentPipeline} 步骤 1 只做一个布尔判断
 * {@code classification.contains("闲聊")} —— 只要模型输出里不含「闲聊」二字，
 * 就无条件 {@code result.setIntent("DATA_QUERY")} 进十步流水线。模型被提示词注入
 * 后吐出任何怪标签、超时返回空串、或 JSON 解析失败，全部被当成「数据分析请求」放行，
 * 等于把 SQL 生成链路暴露给不可信的模型输出。本门禁把这一步改成<b>严格白名单三态</b>。</p>
 *
 * <p><b>三态结论</b>（{@link Decision}）：
 * <ul>
 *   <li>{@code ENTER}：精确命中 {@link #LABEL_DATA}，允许进入 SQL 生成；</li>
 *   <li>{@code BLOCK_CHITCHAT}：精确命中 {@link #LABEL_CHITCHAT}，走闲聊回复，不查库；</li>
 *   <li>{@code BLOCK_UNSAFE}：非白名单标签 / 解析失败 / 超时 / 异常 / 规则命中注入指令，
 *       <b>fail-closed 拒绝进入流水线</b>。</li>
 * </ul>
 *
 * <p><b>为什么不做 6 标签硬分支</b>：意图识别（D2 的 {@code IntentResolver} /
 * {@code core.agent.Resolution}）与「是否放行到 tool」应当解耦，复合标签是反模式。
 * 本门禁只回答一个是非题，D2 的两个类<b>零改动</b>。</p>
 *
 * <p><b>不新增 LLM 调用</b>：本类不持有任何 LLM 客户端，是纯判定器（可脱离 Spring 单测）。
 * 它消费 {@code DataAgentPipeline} 步骤 1 <b>本来就有</b>的那一次 {@code llm.chat} 的原始输出
 * （封装为 {@link Resolution}）；规则先行命中时，Pipeline 会连这一次调用也跳过。</p>
 *
 * <p>⚠️ 与「敏感词过滤」无关：下面的规则只识别<b>试图覆盖系统指令</b>的输入，
 * 目的是 fail-closed 保护 SQL 链路，不是内容审核，也不建词表。</p>
 */
@Slf4j
@Component
public class Nl2SqlAdmissionGate {

    public enum Decision {
        /** 放行进入 NL2SQL 十步流水线。 */
        ENTER,
        /** 闲聊/无关指令：直接回复，不查库。 */
        BLOCK_CHITCHAT,
        /** 判定不可信：fail-closed 拒绝执行。 */
        BLOCK_UNSAFE
    }

    /** 两个合法标签（与 resources/ai-prompts/intent-recognition.txt 严格一致）。 */
    public static final String LABEL_DATA = "《可能的数据分析请求》";
    public static final String LABEL_CHITCHAT = "《闲聊或无关指令》";

    /** 从 LLM 原始文本里兜底抠出 JSON 对象（与 DataAgentPipeline.parseObject 同口径）。 */
    private static final Pattern JSON_OBJECT_PATTERN = Pattern.compile("\\{[\\s\\S]*}");

    /**
     * 规则先行命中的「指令覆盖」模式：这类输入要求模型改变身份/规则或泄漏提示词，
     * 无论模型最终吐出什么标签都不该进入 SQL 生成，直接 fail-closed。
     *
     * <p>刻意保持极小集合且只匹配「祈使式覆盖指令」，不匹配任何业务术语，
     * 避免把正常的数据问题（例如「列出忽略随访的患者」）误挡。</p>
     */
    private static final Pattern[] OVERRIDE_PATTERNS = {
            Pattern.compile("ignore\\s+(all\\s+)?(previous|prior|above)\\s+(instructions?|prompts?|rules?)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(忽略|无视|忘掉)(之前|上面|以上|前述|所有)?的?(指令|规则|提示词|要求|设定)"),
            Pattern.compile("(输出|打印|显示|泄露|告诉我).{0,8}(系统提示词|system\\s*prompt|内部指令)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(act\\s+as|pretend\\s+to\\s+be|you\\s+are\\s+now)\\s+(a\\s+)?(developer|admin|root|system)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(drop|truncate|alter)\\s+(table|database)", Pattern.CASE_INSENSITIVE),
    };

    /** 纯问候/身份询问：规则即可判定为闲聊，无需浪费一次 LLM。 */
    private static final Pattern[] GREETING_PATTERNS = {
            Pattern.compile("^\\s*(你好|您好|hi|hello|hey|在吗|在不在|早上好|下午好|晚上好)[\\s!！.。,，?？]*$", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^\\s*(谢谢|多谢|感谢|thanks|thank you|好的|收到|ok|okay)[\\s!！.。,，?？]*$", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^\\s*(你是谁|你叫什么|你能做什么|你是做什么的)[\\s?？]*$"),
    };

    /**
     * 一次 LLM 二分类的观测结果。
     *
     * <p><b>注意命名</b>：这是本门禁内部的轻量观测值，<b>不是</b>
     * {@code cn.kmbeast.core.agent.Resolution}（D2 的 6 标签意图结论）。
     * 两者互不依赖，D2 的类完全没改。</p>
     */
    public static final class Resolution {
        private final String raw;
        private final boolean failed;
        private final boolean skipped;

        private Resolution(String raw, boolean failed, boolean skipped) {
            this.raw = raw;
            this.failed = failed;
            this.skipped = skipped;
        }

        /** LLM 正常返回（原样文本，可能是非法 JSON）。 */
        public static Resolution of(String rawLlmOutput) {
            return new Resolution(rawLlmOutput, false, false);
        }

        /** LLM 超时/抛异常。 */
        public static Resolution failure() {
            return new Resolution(null, true, false);
        }

        /** 规则先行已决断，本次根本没调 LLM。 */
        public static Resolution skipped() {
            return new Resolution(null, false, true);
        }

        public String raw() {
            return raw;
        }

        public boolean failed() {
            return failed;
        }

        public boolean isSkipped() {
            return skipped;
        }

        /** 解析出的 {@code classification} 原始值；解析失败返回 null。 */
        public String classification() {
            JSONObject json = parse();
            return json == null ? null : json.getString("classification");
        }

        /** 闲聊回复文案（仅 BLOCK_CHITCHAT 时消费）；无内容返回空串。 */
        public String chitchatReply() {
            JSONObject json = parse();
            if (json == null) {
                return "";
            }
            String response = json.getString("response");
            return response == null ? "" : response.trim();
        }

        private JSONObject parse() {
            if (raw == null || raw.trim().isEmpty()) {
                return null;
            }
            String text = Nl2SqlLlmClient.stripFence(raw).trim();
            try {
                return JSON.parseObject(text);
            } catch (Exception ignored) {
                Matcher m = JSON_OBJECT_PATTERN.matcher(text);
                if (m.find()) {
                    try {
                        return JSON.parseObject(m.group());
                    } catch (Exception ignored2) {
                        return null;
                    }
                }
                return null;
            }
        }
    }

    /**
     * 组合入口：规则先行 → 规则不决断时消费已有 LLM 输出做白名单二分类。
     *
     * @param question   用户原始提问
     * @param resolution 本次 LLM 观测结果（{@link Resolution#skipped()} 表示规则命中未调 LLM）
     * @return 非 null 的三态结论
     */
    public Decision admit(String question, Resolution resolution) {
        Decision byRule = admitByRule(question);
        if (byRule != null) {
            return byRule;
        }
        return admitByLlm(resolution);
    }

    /**
     * 规则先行。
     *
     * @return 已决断的结论；<b>null 表示规则不决断</b>，需要 LLM 二分类兜底
     */
    public Decision admitByRule(String question) {
        if (question == null || question.trim().isEmpty()) {
            return Decision.BLOCK_UNSAFE;
        }
        String normalized = question.trim().toLowerCase(Locale.ROOT);
        for (Pattern p : OVERRIDE_PATTERNS) {
            if (p.matcher(question).find() || p.matcher(normalized).find()) {
                log.warn("[Nl2SqlGate] 规则命中指令覆盖尝试，fail-closed 拒绝");
                return Decision.BLOCK_UNSAFE;
            }
        }
        for (Pattern p : GREETING_PATTERNS) {
            if (p.matcher(question.trim()).find()) {
                return Decision.BLOCK_CHITCHAT;
            }
        }
        return null;
    }

    /**
     * LLM 二分类白名单判定（不发起任何 LLM 调用）。
     *
     * <p>只有<b>逐字相等</b>的两个标签被接受。缺书名号、近义词、多余文本、
     * 解析失败、超时一律 {@link Decision#BLOCK_UNSAFE}。</p>
     */
    public Decision admitByLlm(Resolution resolution) {
        if (resolution == null || resolution.failed() || resolution.isSkipped()) {
            return Decision.BLOCK_UNSAFE;
        }
        String label = resolution.classification();
        if (label == null) {
            log.warn("[Nl2SqlGate] LLM 输出无法解析为 JSON，fail-closed");
            return Decision.BLOCK_UNSAFE;
        }
        String trimmed = label.trim();
        if (LABEL_DATA.equals(trimmed)) {
            return Decision.ENTER;
        }
        if (LABEL_CHITCHAT.equals(trimmed)) {
            return Decision.BLOCK_CHITCHAT;
        }
        log.warn("[Nl2SqlGate] LLM 返回非白名单标签，fail-closed: label={}",
                trimmed.length() > 80 ? trimmed.substring(0, 80) : trimmed);
        return Decision.BLOCK_UNSAFE;
    }
}
