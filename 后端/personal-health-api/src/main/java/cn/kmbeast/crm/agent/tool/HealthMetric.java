package cn.kmbeast.crm.agent.tool;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 健康指标白名单（阶段 2，2026-10-07）。
 *
 * <p><b>核心安全设计</b>：指标名<b>不允许</b>由 LLM 任意传入后再拼 SQL。
 * 本枚举穷举了 {@code health_model_config} 里 12 个全局指标的<b>规范名（dbName）</b>
 * 与常见口语说法（aliases）。工具只接受能在此枚举中解析出来的指标，
 * 解析不到一律拒绝 —— LLM 连"查询哪张表的哪个 name"都无法决定，
 * 更不可能注入，从源头消除 SQL 注入面（见迁移方案"第 1 层：参数化查询"）。
 *
 * <p>单位与正常范围从 {@code health_model_config} 实时联表取出，<b>不在这里硬编码</b>，
 * 避免与数据库漂移。
 */
public enum HealthMetric {

    SYSTOLIC("收缩压", "收缩压", "高压", "收缩", "血压"),
    DIASTOLIC("舒张压", "舒张压", "低压", "舒张", "血压"),
    FASTING_GLUCOSE("空腹血糖", "空腹血糖", "空腹血糖值", "空血", "血糖"),
    POSTPRANDIAL_GLUCOSE("餐后血糖", "餐后血糖", "餐后", "血糖"),
    BMI("体重指数", "体重指数", "BMI", "bmi", "体质指数", "肥胖指数"),
    HEART_RATE("心率", "心率", "脉搏", "心律", "心跳", "脉率"),
    TOTAL_CHOLESTEROL("总胆固醇", "总胆固醇", "总胆", "胆固醇", "血脂"),
    TRIGLYCERIDE("甘油三酯", "甘油三酯", "甘油三脂", "中性脂肪", "血脂", "甘油三酯偏高"),
    HDL("高密度脂蛋白", "高密度脂蛋白", "高密度", "好胆固醇", "胆固醇", "血脂"),
    LDL("低密度脂蛋白", "低密度脂蛋白", "低密度", "坏胆固醇", "胆固醇", "血脂"),
    HEIGHT("身高", "身高", "个头", "高矮"),
    WEIGHT("体重", "体重", "重量", "斤两"),

    ;

    /** 数据库 {@code health_model_config.name} 的规范值 */
    private final String dbName;
    /** 含 dbName 在内的全部可识别说法（小写化存储） */
    private final Set<String> aliases;

    HealthMetric(String dbName, String... aliases) {
        this.dbName = dbName;
        this.aliases = new LinkedHashSet<>();
        for (String a : aliases) {
            this.aliases.add(a.toLowerCase());
        }
    }

    public String dbName() {
        return dbName;
    }

    public Set<String> aliases() {
        return aliases;
    }

    /**
     * 把用户的口语说法解析为一个或多个指标。
     *
     * <p>解析优先级：
     * <ol>
     *   <li>精确命中某指标的别名（大小写不敏感）</li>
     *   <li>dbName 与说法互为包含（如"血压"命中"收缩压/舒张压"，"血脂"命中四个脂类指标）</li>
     * </ol>
     *
     * <p>一个说法可能解析出多个指标（血压→收缩压+舒张压），调用方按指标分组返回即可。
     *
     * @return 解析到的指标集合；无法解析则为空
     */
    public static Set<HealthMetric> resolve(String phrase) {
        Set<HealthMetric> out = new LinkedHashSet<>();
        if (phrase == null || phrase.trim().isEmpty()) {
            return out;
        }
        String p = phrase.trim().toLowerCase();

        // 1) 精确别名匹配
        for (HealthMetric m : values()) {
            if (m.aliases.contains(p)) {
                out.add(m);
            }
        }
        if (!out.isEmpty()) {
            return out;
        }

        // 2) 包含匹配（双向）
        for (HealthMetric m : values()) {
            String db = m.dbName.toLowerCase();
            if (db.contains(p) || p.contains(db)) {
                out.add(m);
            }
        }
        return out;
    }

    /** 是否支持该指标（用于拒绝白名单外的任意指标名） */
    public static boolean isSupported(String phrase) {
        return !resolve(phrase).isEmpty();
    }

    /** 调试 / 文档用：全部规范名 */
    public static List<String> allDbNames() {
        return Arrays.stream(values()).map(HealthMetric::dbName).toList();
    }
}
