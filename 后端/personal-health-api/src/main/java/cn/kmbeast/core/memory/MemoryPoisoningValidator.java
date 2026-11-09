package cn.kmbeast.core.memory;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 记忆投毒防护（阶段 8 / D3 改写）。
 *
 * <p><b>为什么需要整体 fail-closed</b>：记忆最终会以 bullets 形式进入 system prompt。
 * 只要放进来一条含「忽略之前的指令」或角色标记的事实，就等于给用户开了一个
 * 持久化的提示词注入后门。因此校验以<b>整批</b>为单位：任何一条不合格，
 * 整批拒绝写入，不做「过滤掉坏的那条再存其余」的部分接受。</p>
 *
 * <p>⚠️ 与「敏感词过滤」无关：这不是内容审核词表，而是结构化数据 + prompt 注入面的
 * 硬约束（项目已于 2026-11-08 正式取消敏感词过滤，本类不是它的复活）。</p>
 */
public final class MemoryPoisoningValidator {

    /** 单用户事实条数上限。 */
    public static final int MAX_FACTS = 50;
    /** 单条 value 字符上限。 */
    public static final int MAX_VALUE_CHARS = 200;
    /** 事实最长有效期（天）。 */
    public static final int MAX_TTL_DAYS = 365;

    private static final Pattern KEY_PATTERN = Pattern.compile("^[a-z0-9_]{1,32}$");

    /** 明确的指令覆盖/角色扮演模式：命中即整批拒绝。 */
    private static final String[] FORBIDDEN = {
            "ignore previous", "ignore all previous", "ignore the above",
            "忽略之前", "忽略上述", "无视以上", "忘掉前面的",
            "you are now", "act as", "pretend to be", "system prompt",
            "系统提示词", "你是系统", "你现在是", "role:", "assistant:", "system:",
    };

    private MemoryPoisoningValidator() {
    }

    /** 校验结果：{@code ok=false} 时 reason 说明第一条违规原因（不泄露完整 value）。 */
    public record Result(boolean ok, String reason) {
        public static Result pass() {
            return new Result(true, null);
        }
        public static Result reject(String reason) {
            return new Result(false, reason);
        }
    }

    /** 批量校验（含条数上限）。 */
    public static Result validate(List<PreferenceFact> facts) {
        if (facts == null) {
            return Result.reject("事实列表为 null");
        }
        if (facts.size() > MAX_FACTS) {
            return Result.reject("事实条数超过上限 " + MAX_FACTS);
        }
        for (PreferenceFact f : facts) {
            Result one = validateOne(f);
            if (!one.ok()) {
                return one;
            }
        }
        return Result.pass();
    }

    /** 单条校验。 */
    public static Result validateOne(PreferenceFact f) {
        if (f == null) {
            return Result.reject("存在空事实");
        }
        if (f.key() == null || !KEY_PATTERN.matcher(f.key()).matches()) {
            return Result.reject("key 必须匹配 ^[a-z0-9_]{1,32}$");
        }
        String value = f.value();
        if (value == null || value.isBlank()) {
            return Result.reject("value 不能为空");
        }
        if (value.length() > MAX_VALUE_CHARS) {
            return Result.reject("value 超过 " + MAX_VALUE_CHARS + " 字符");
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isISOControl(c)) {
                return Result.reject("value 含控制字符/换行");
            }
            if (c == '<' || c == '>' || c == '{' || c == '}') {
                return Result.reject("value 含尖括号或花括号（疑似注入载荷）");
            }
        }
        String lower = value.toLowerCase(Locale.ROOT);
        for (String bad : FORBIDDEN) {
            if (lower.contains(bad)) {
                return Result.reject("value 含指令覆盖/角色标记");
            }
        }
        if (f.kind() == null || !PreferenceFact.KINDS.contains(f.kind())) {
            return Result.reject("kind 不在白名单内");
        }
        if (Double.isNaN(f.confidence()) || f.confidence() < 0.0 || f.confidence() > 1.0) {
            return Result.reject("confidence 必须落在 0..1");
        }
        if (f.expiresAt() != null && !f.expiresAt().isBlank()) {
            try {
                Instant exp = Instant.parse(f.expiresAt());
                Instant now = Instant.now();
                if (!exp.isAfter(now)) {
                    return Result.reject("expiresAt 已过期");
                }
                if (exp.isAfter(now.plus(MAX_TTL_DAYS, ChronoUnit.DAYS))) {
                    return Result.reject("expiresAt 超过 " + MAX_TTL_DAYS + " 天上限");
                }
            } catch (DateTimeParseException e) {
                return Result.reject("expiresAt 不是合法 ISO-8601 Instant");
            }
        }
        return Result.pass();
    }
}
