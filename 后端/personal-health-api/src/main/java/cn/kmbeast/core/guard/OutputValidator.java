package cn.kmbeast.core.guard;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * 输出验证器
 * 验证 AI 输出的合规性和安全性
 *
 * <h3>2026-11-08 修复：多行输出里的剂量被整体漏检</h3>
 * <p>原实现是 {@code output.matches(".*" + pattern + ".*")}，有两个叠加缺陷：
 * <ol>
 *   <li>{@link String#matches} 是<b>整串匹配</b>，靠两侧 {@code .*} 硬凑成「包含」，
 *       而 {@code .} <b>不匹配换行符</b> —— 多行回答里从第二行起的剂量<b>一条都查不到</b>。
 *       AI 回答普遍是多行的（列表、分段），等于这条合规兜底事实上从未生效。</li>
 *   <li>每调用一次就现拼一次正则串、重新编译，热点路径上纯浪费。</li>
 * </ol>
 * <p>改为：预编译 {@link Pattern} + {@link java.util.regex.Matcher#find()}。
 * {@code find()} 本身就是「任意位置搜索」，不需要 {@code .*}，也就自然跨行。
 *
 * <p>同时补齐了真实写法：{@code 500 mg}（数字与单位间有空格）、{@code 500 毫克}、
 * {@code 每次 2 片} —— 只认 {@code \d+mg} 紧贴写法会漏掉大量真实输出。
 *
 * <p><b>命中后的行为</b>（调用方 {@code AiServiceImpl}）：在回答尾部追加
 * 「具体用药剂量请务必遵医嘱」提示；本类只负责判定，不修改正文、不拦截回答。
 */
@Component
public class OutputValidator {

    /**
     * 剂量类表达。刻意<b>不</b>匹配孤立的 {@code g}／{@code 克}：
     * 「5 克盐」「3 克膳食纤维」属于饮食建议，命中只会给正常回答挂上无关提示，反而降低提示可信度。
     */
    private static final Pattern[] DOSE_PATTERNS = {
            // 500mg / 500 mg / 0.5 ml / 500 毫克 / 20μg
            Pattern.compile("\\d+(?:\\.\\d+)?\\s*(?:mg|ml|μg|ug|毫克|毫升|微克)", Pattern.CASE_INSENSITIVE),
            // 每次 2 片 / 3 粒 / 1 袋
            Pattern.compile("\\d+(?:\\.\\d+)?\\s*(?:片|粒|袋)"),
    };

    /** 需要挂免责声明的医疗行为关键词。 */
    private static final String[] MEDICAL_KEYWORDS = {"诊断", "处方", "用药", "治疗方案", "手术"};

    /**
     * 验证输出是否包含敏感信息（此处指具体药物剂量建议）
     *
     * @param output AI 输出原文，允许为 null
     * @return true 表示出现了剂量类表达，调用方应追加「遵医嘱」提示
     */
    public boolean containsSensitiveInfo(String output) {
        if (output == null || output.isEmpty()) {
            return false;
        }
        for (Pattern pattern : DOSE_PATTERNS) {
            // find() 是子串搜索，天然跨行；不需要（也不应）用 matches + .*
            if (pattern.matcher(output).find()) {
                return true;
            }
        }
        return false;
    }

    /**
     * 验证输出是否需要免责声明
     *
     * @param output AI 输出原文，允许为 null
     * @return true 表示出现了医疗行为关键词，调用方应追加免责声明
     */
    public boolean needDisclaimer(String output) {
        if (output == null || output.isEmpty()) {
            return false;
        }
        for (String keyword : MEDICAL_KEYWORDS) {
            if (output.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}
