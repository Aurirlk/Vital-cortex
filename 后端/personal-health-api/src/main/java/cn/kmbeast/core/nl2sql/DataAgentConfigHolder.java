package cn.kmbeast.core.nl2sql;

import cn.kmbeast.core.agent.Audience;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import javax.sql.DataSource;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 数据智能体运行时配置（管理员可在线开关，无需重启）。
 *
 * <p><b>强耦合原则</b>：不新建配置表，复用既有 {@code system_config}
 * （{@code config_group='data_agent'}）。读取带 10s TTL 缓存；写入走「查存在再 insert/update」。</p>
 *
 * <p>2026-10-07 更正：本注释原称「{@code system_config} 未建 group+key 唯一键」，
 * 实际<b>已建</b> {@code UNIQUE KEY uk_group_key (config_group, config_key)}
 * （见 {@code Data/01_建库建表/init_database.sql}，并已用 {@code SHOW INDEX} 在实库确认）。
 * upsert 仍保留「查存在再写」而非依赖 {@code INSERT ... ON DUPLICATE KEY UPDATE}，
 * 是为了兼容可能缺失该唯一键的历史库（迁移前的老库），两者并不冲突。</p>
 *
 * <p>默认值回落到 {@link Nl2SqlProperties}（即 {@code crm.ai.nl2sql.*}），
 * 因此<b>未做任何配置时行为与阶段 4 一致（默认关闭）</b>。</p>
 */
@Slf4j
@Component
public class DataAgentConfigHolder {

    /** 配置分组名。 */
    public static final String GROUP = "data_agent";

    public static final String KEY_ENABLED = "enabled";
    public static final String KEY_REPORT_ENABLED = "report-enabled";
    public static final String KEY_MAX_ROWS = "max-rows";
    public static final String KEY_QUERY_TIMEOUT = "query-timeout";
    /** SQL 方言（当前仅 MySQL；抽象留口，避免再出现"配置项不生效"）。 */
    public static final String KEY_DIALECT = "dialect";
    /** 故障修复重试上限（流水线实际使用的那一个，钳制 0~5）。 */
    public static final String KEY_MAX_FIX_RETRY = "max-fix-retry";
    /** 预设问题（JSON 数组字符串）：用户端。 */
    public static final String KEY_PRESETS_USER = "preset-questions-user";
    /** 预设问题（JSON 数组字符串）：医生端。 */
    public static final String KEY_PRESETS_DOCTOR = "preset-questions-doctor";

    // ==================== 按使用者分层（2026-10-07 新增） ====================
    //
    // 复用 preset-questions-{端} 的 key 后缀范式（项目内唯一已有的「按端分配置」先例），
    // 全部落在 system_config(config_group='data_agent')，**不新建表**。
    //
    // 语义：每个 key 都有「无后缀的全局值」与「带端后缀的覆盖值」两档。
    // 读取时先查带后缀的，未配置则回落全局值，再回落代码默认值。
    // 这样既保证向后兼容（原有 enabled/max-rows 等键继续生效），
    // 又让管理员可以只针对医生端或用户端单独收紧/放宽。

    /**
     * 使用者类型：用户端（角色 2）。
     *
     * <p>取值来自 {@link Audience} —— 这两个常量保留旧名字只是为了不动已有调用点，
     * 值本身不再是本类定义的（同一组字符串在编排器与建表脚本里各出现过一次，
     * 一处漏改就会让某个 Agent 在某些端上凭空消失）。</p>
     */
    public static final String AUDIENCE_USER = Audience.USER;
    /** 使用者类型：医生端（角色 3）。取值同样来自 {@link Audience}。 */
    public static final String AUDIENCE_DOCTOR = Audience.DOCTOR;

    /** 分层开关：数据智能体总开关是否对该端开放（未配置则回落全局 enabled）。 */
    public static final String KEY_ENABLED_AUDIENCE_PREFIX = "enabled-";
    /** 分层：是否自动生成报告（未配置则回落全局 report-enabled）。 */
    public static final String KEY_REPORT_AUDIENCE_PREFIX = "report-enabled-";
    /** 分层：最大返回行数（未配置则回落全局 max-rows）。 */
    public static final String KEY_MAX_ROWS_AUDIENCE_PREFIX = "max-rows-";
    /** 分层：SQL 超时秒数（未配置则回落全局 query-timeout）。 */
    public static final String KEY_QUERY_TIMEOUT_AUDIENCE_PREFIX = "query-timeout-";

    // ---- 三项新能力（2026-10-07）：上下文压缩 / 长短期记忆 / 沙箱 ----

    /**
     * 上下文压缩：送入 prompt 的历史消息条数上限。
     *
     * <p><b>这是真的压缩</b>：{@code AiServiceImpl} 此前把全部历史无上限塞进 prompt，
     * 超预算时 {@code TokenBudgetManager} 只<b>抛异常</b>让用户开新会话。现改为
     * 按本项裁剪（保留最近 N 条），并配合 {@code context-summary} 做滚动摘要。</p>
     *
     * <p>0 表示不携带历史。</p>
     */
    public static final String KEY_CONTEXT_ROUNDS = "context-rounds";
    /**
     * 上下文压缩：历史正文总字符预算。超出时从最旧的消息开始截断
     * （而非从中间截断，避免破坏"最近若干轮"的语义连续性）。
     */
    public static final String KEY_CONTEXT_MAX_CHARS = "context-max-chars";
    /** 上下文压缩：超过该条数后，对更早的历史做滚动摘要（压缩为一段话）。 */
    public static final String KEY_CONTEXT_SUMMARY_THRESHOLD = "context-summary-threshold";

    /**
     * 长短期记忆：是否把用户偏好注入 prompt。
     *
     * <p>2026-10-07 接线前，{@code AgentMemoryService} 只被 REST 层调用，
     * 偏好「可通过 API 读写但对用户不可见」。</p>
     */
    public static final String KEY_MEMORY_ENABLED = "memory-enabled";
    /** 长短期记忆：注入 prompt 的偏好段字符上限（ReAct 链路）。 */
    public static final String KEY_MEMORY_MAX_CHARS = "memory-max-chars";
    /** 长短期记忆：偏好缓存的用户数上限（LRU）。 */
    public static final String KEY_MEMORY_MAX_USERS = "memory-max-users";

    /**
     * 沙箱：单次查询返回结果的字节上限。
     *
     * <p><b>新增维度</b>：此前只有 {@code max-rows}（行数），一个宽表 200 行 × 50 列
     * 仍可能返回数 MB 的 JSON，直接塞进 prompt 会挤爆 token 预算。</p>
     */
    public static final String KEY_SANDBOX_MAX_BYTES = "sandbox-max-bytes";
    /** 沙箱：是否启用词法守卫（{@code MySqlAiSqlGuard}）。生产环境不建议关闭。 */
    public static final String KEY_SANDBOX_GUARD_ENABLED = "sandbox-guard-enabled";
    /** 沙箱：只读数据源是否已配置（前端展示用，防止管理员误以为已隔离）。 */
    public static final String KEY_SANDBOX_READONLY = "sandbox-readonly-configured";

    /** 分层后缀列表（供管理端遍历渲染）。 */
    public static final List<String> AUDIENCES = List.of(AUDIENCE_USER, AUDIENCE_DOCTOR);

    /** 使用者类型的中文名（供管理端界面展示）。 */
    public static String audienceLabel(String audience) {
        if (AUDIENCE_DOCTOR.equals(audience)) {
            return "医生端";
        }
        if (AUDIENCE_USER.equals(audience)) {
            return "用户端";
        }
        return "全局";
    }

    /** 内置兜底预设问题（未配置时使用，保证功能开箱可用）。 */
    public static final List<String> DEFAULT_PRESETS_USER = List.of(
            "我最近的血压趋势如何？",
            "最近 30 天有哪些异常指标？",
            "我的健康指标里哪一项波动最大？",
            "我最近一次的就诊记录是什么？");
    public static final List<String> DEFAULT_PRESETS_DOCTOR = List.of(
            "这位患者最近的血压趋势如何？",
            "最近 30 天有哪些异常指标？",
            "这位患者正在服用哪些药物？",
            "这位患者最近一次就诊的诊断是什么？");

    private static final long TTL_MS = 10_000L;

    /** 修复重试默认值（{@code crm.ai.nl2sql.max-fix-retry}）。 */
    public static final int DEFAULT_MAX_FIX_RETRY = 2;
    /** 修复重试可配置的上限，防止管理员误填造成 LLM 调用放大。 */
    public static final int MAX_FIX_RETRY_CEILING = 5;

    /** 每项配置的说明与默认值，供管理员页面渲染。 */
    private static final Map<String, String[]> SCHEMA = new LinkedHashMap<>();

    static {
        SCHEMA.put(KEY_ENABLED, new String[]{"是否开启数据智能体（受控 NL2SQL）", "boolean"});
        SCHEMA.put(KEY_REPORT_ENABLED, new String[]{"查询结果是否自动生成 Markdown 报告", "boolean"});
        SCHEMA.put(KEY_MAX_ROWS, new String[]{"单次查询最大返回行数", "number"});
        SCHEMA.put(KEY_QUERY_TIMEOUT, new String[]{"单条 SQL 执行超时（秒）", "number"});
        SCHEMA.put(KEY_DIALECT, new String[]{"SQL 方言（当前支持 MySQL）", "string"});
        SCHEMA.put(KEY_MAX_FIX_RETRY, new String[]{"SQL 失败后的修复重试上限（0~5）", "number"});
        SCHEMA.put(KEY_PRESETS_USER, new String[]{"用户端预设问题（JSON 数组，前端以快捷标签展示）", "json-array"});
        SCHEMA.put(KEY_PRESETS_DOCTOR, new String[]{"医生端预设问题（JSON 数组，前端以快捷标签展示）", "json-array"});

        // ---- 按使用者分层（key 后缀范式，2026-10-07）----
        SCHEMA.put(KEY_ENABLED + "-" + AUDIENCE_USER, new String[]{"【用户端】是否开放数据智能体（未配置则用全局值）", "boolean"});
        SCHEMA.put(KEY_ENABLED + "-" + AUDIENCE_DOCTOR, new String[]{"【医生端】是否开放数据智能体（未配置则用全局值）", "boolean"});
        SCHEMA.put(KEY_REPORT_ENABLED + "-" + AUDIENCE_USER, new String[]{"【用户端】是否自动生成报告（未配置则用全局值）", "boolean"});
        SCHEMA.put(KEY_REPORT_ENABLED + "-" + AUDIENCE_DOCTOR, new String[]{"【医生端】是否自动生成报告（未配置则用全局值）", "boolean"});
        SCHEMA.put(KEY_MAX_ROWS + "-" + AUDIENCE_USER, new String[]{"【用户端】单次查询最大返回行数（未配置则用全局值）", "number"});
        SCHEMA.put(KEY_MAX_ROWS + "-" + AUDIENCE_DOCTOR, new String[]{"【医生端】单次查询最大返回行数（未配置则用全局值）", "number"});
        SCHEMA.put(KEY_QUERY_TIMEOUT + "-" + AUDIENCE_USER, new String[]{"【用户端】单条 SQL 超时秒数（未配置则用全局值）", "number"});
        SCHEMA.put(KEY_QUERY_TIMEOUT + "-" + AUDIENCE_DOCTOR, new String[]{"【医生端】单条 SQL 超时秒数（未配置则用全局值）", "number"});

        // ---- 上下文压缩 ----
        SCHEMA.put(KEY_CONTEXT_ROUNDS, new String[]{"上下文压缩：送入 prompt 的历史消息条数上限（0=不携带历史）", "number"});
        SCHEMA.put(KEY_CONTEXT_MAX_CHARS, new String[]{"上下文压缩：历史正文字符总预算（超出从最旧开始截断）", "number"});
        SCHEMA.put(KEY_CONTEXT_SUMMARY_THRESHOLD, new String[]{"上下文压缩：超过该条数后对更早历史做滚动摘要（0=不摘要）", "number"});

        // ---- 长短期记忆 ----
        SCHEMA.put(KEY_MEMORY_ENABLED, new String[]{"长短期记忆：是否把用户偏好注入 prompt", "boolean"});
        SCHEMA.put(KEY_MEMORY_MAX_CHARS, new String[]{"长短期记忆：注入 prompt 的偏好段字符上限", "number"});
        SCHEMA.put(KEY_MEMORY_MAX_USERS, new String[]{"长短期记忆：偏好缓存用户数上限（LRU 淘汰）", "number"});

        // ---- 沙箱 ----
        SCHEMA.put(KEY_SANDBOX_MAX_BYTES, new String[]{"沙箱：单次查询结果字节上限（防止宽表挤爆 token）", "number"});
        SCHEMA.put(KEY_SANDBOX_GUARD_ENABLED, new String[]{"沙箱：是否启用 SQL 词法守卫（生产环境强烈建议开启）", "boolean"});
        SCHEMA.put(KEY_SANDBOX_READONLY, new String[]{"沙箱：只读数据源是否已配置（只读标记，由程序回写）", "boolean"});
    }

    // ==================== 按使用者分层的读取（2026-10-07 新增） ====================

    /**
     * 分层取值：先查「带端后缀」的键，未配置则回落全局键，再回落代码默认值。
     *
     * <p><b>为什么这样设计</b>：项目内已有的「按端分配置」先例是
     * {@code preset-questions-user} / {@code preset-questions-doctor}（key 后缀范式）。
     * 直接沿用同一范式，好处是<b>完全向后兼容</b> —— 管理员原先配的全局 {@code max-rows}
     * 依然生效，只是可以被某个端的覆盖值优先。</p>
     *
     * @param key          全局键（如 {@link #KEY_MAX_ROWS}）
     * @param audience     使用者类型（{@link #AUDIENCE_USER} / {@link #AUDIENCE_DOCTOR}）；
     *                     传 null 或未知值时直接读全局键
     * @param defaultValue 代码默认值
     * @return 分层后的配置值
     */
    private String rawForAudience(String key, String audience, String defaultValue) {
        if (audience != null && AUDIENCES.contains(audience)) {
            String scoped = cache.get(key + "-" + audience);
            if (scoped != null && !scoped.isBlank()) {
                return scoped;
            }
        }
        String global = cache.get(key);
        return global != null && !global.isBlank() ? global : defaultValue;
    }

    /** 该端是否开放数据智能体。 */
    public boolean isEnabledFor(String audience) {
        // ⚠️ 两个坑（都是实测踩出来的）：
        //   ① 不能把 null 传给 defaultValue —— 会一路传到 cache.get(null) 触发 NPE；
        //   ② 不能拿已解析出的字符串去调 asBool(String, boolean) —— 它的第一个形参是
        //      【配置键】不是配置值，那样会拿值当键去查（查不到 → 恒返回默认值）。
        //   故统一用 parseBool(String) 解析已取出的值。
        boolean fallback = properties != null && properties.isEnabled();
        return parseBool(rawForAudience(KEY_ENABLED, audience, String.valueOf(fallback)), fallback);
    }

    /** 该端是否自动生成报告。 */
    public boolean isReportEnabledFor(String audience) {
        return parseBool(rawForAudience(KEY_REPORT_ENABLED, audience, "true"), true);
    }

    /** 该端的单次查询最大返回行数。 */
    public int getMaxRowsFor(String audience) {
        int fallback = properties != null ? properties.getMaxRows() : 200;
        return clamp(parseInt(rawForAudience(KEY_MAX_ROWS, audience, String.valueOf(fallback)), fallback), 1, 2000);
    }

    /** 该端的单条 SQL 执行超时（秒）。 */
    public int getQueryTimeoutFor(String audience) {
        int fallback = properties != null ? properties.getQueryTimeout() : 10;
        return clamp(parseInt(rawForAudience(KEY_QUERY_TIMEOUT, audience, String.valueOf(fallback)), fallback), 1, 60);
    }

    // ==================== 上下文压缩 ====================

    /**
     * 送入 prompt 的历史消息条数上限（<b>一次</b>记 user+assistant 为一轮时按条数计）。
     *
     * @return 条数上限；0 表示不携带历史
     */
    public int getContextRounds() {
        return clamp(asInt(KEY_CONTEXT_ROUNDS, 10), 0, 50);
    }

    /** 历史正文字符总预算，超出时从最旧的消息开始截断。 */
    public int getContextMaxChars() {
        return clamp(asInt(KEY_CONTEXT_MAX_CHARS, 4000), 500, 100000);
    }

    /** 超过该条数后对更早历史做滚动摘要；0 表示不摘要。 */
    public int getContextSummaryThreshold() {
        return clamp(asInt(KEY_CONTEXT_SUMMARY_THRESHOLD, 0), 0, 100);
    }

    // ==================== 长短期记忆 ====================

    /** 是否把用户偏好注入 prompt。 */
    public boolean isMemoryEnabled() {
        return asBool(KEY_MEMORY_ENABLED, true);
    }

    /** 偏好注入段字符上限（ReAct 链路用）。 */
    public int getMemoryMaxChars() {
        return clamp(asInt(KEY_MEMORY_MAX_CHARS, 800), 0, 5000);
    }

    /** 偏好缓存用户数上限（LRU）。 */
    public int getMemoryMaxUsers() {
        return clamp(asInt(KEY_MEMORY_MAX_USERS, 1000), 10, 100000);
    }

    // ==================== 沙箱 ====================

    /**
     * 单次查询结果字节上限。
     *
     * <p>新增维度：{@code max-rows} 只限行数，一个宽表 200 行 × 50 列仍可能数 MB。</p>
     */
    public int getSandboxMaxBytes() {
        return clamp(asInt(KEY_SANDBOX_MAX_BYTES, 512 * 1024), 4096, 16 * 1024 * 1024);
    }

    /** 是否启用 SQL 词法守卫。生产环境不建议关闭。 */
    public boolean isSandboxGuardEnabled() {
        return asBool(KEY_SANDBOX_GUARD_ENABLED, true);
    }

    /** 只读数据源是否已配置（由启动自检回写，供管理端展示）。 */
    public boolean isSandboxReadonlyConfigured() {
        return asBool(KEY_SANDBOX_READONLY, false);
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    @Resource
    private Nl2SqlProperties properties;

    @Resource
    private DataSource dataSource;

    private final Map<String, String> cache = new ConcurrentHashMap<>();
    private volatile long loadedAt = 0L;

    // ---- 读取（带 TTL） ----
    //
    // ⚠️ properties 可能为 null（单测桩只注入 dataSource、或 Spring 装配失败降级），
    //    故下面 4 个旧 getter 也做了 null 保护 —— 此前它们直接 properties.xxx() 会 NPE，
    //    而 snapshot() 会调用它们，管理端一打开配置页就整页报错。

    public boolean isEnabled() {
        return asBool(KEY_ENABLED, properties != null && properties.isEnabled());
    }

    public boolean isReportEnabled() {
        return asBool(KEY_REPORT_ENABLED, true);
    }

    public int getMaxRows() {
        return asInt(KEY_MAX_ROWS, properties != null ? properties.getMaxRows() : 200);
    }

    public int getQueryTimeout() {
        return asInt(KEY_QUERY_TIMEOUT, properties != null ? properties.getQueryTimeout() : 10);
    }

    /**
     * SQL 方言，传给 prompt 做语法约束。
     *
     * <p>2026-10-07 接线：此前 {@code DataAgentPipeline} 有四处<b>硬编码 "MySQL"</b>，
     * 配置项形同虚设。现改为读配置（可在线改，10s 生效）。</p>
     *
     * <p>{@code properties} 可能为 null（单测桩只注入部分字段），故做保护。</p>
     */
    public String getDialect() {
        String def = properties == null ? "MySQL" : properties.getDialect();
        if (def == null || def.trim().isEmpty()) {
            def = "MySQL";
        }
        String v = raw(KEY_DIALECT, def);
        return (v == null || v.trim().isEmpty()) ? def : v.trim();
    }

    /**
     * 故障修复重试上限，钳制在 {@code 0~5}。
     *
     * <p>不使用 {@link #asInt}：它对 0 会回落默认值，无法表达「关闭重试」，
     * 故自己解析并钳制。上限刻意设为 5 —— 每次重试都是一次 LLM 调用，
     * 放任管理员填 10 会变成 11 次调用的成本放大。</p>
     */
    public int getMaxFixRetry() {
        int def = properties == null ? DEFAULT_MAX_FIX_RETRY : properties.getMaxFixRetry();
        try {
            String v = raw(KEY_MAX_FIX_RETRY, String.valueOf(def));
            int n = Integer.parseInt(v == null ? "" : v.trim());
            return Math.max(0, Math.min(MAX_FIX_RETRY_CEILING, n));
        } catch (Exception e) {
            return def;
        }
    }

    /**
     * 预设问题列表（管理员可配；未配置或配置损坏时回落内置默认，保证功能不空）。
     *
     * @param doctor true=医生端，false=用户端
     */
    public List<String> presetQuestions(boolean doctor) {
        String key = doctor ? KEY_PRESETS_DOCTOR : KEY_PRESETS_USER;
        List<String> fallback = doctor ? DEFAULT_PRESETS_DOCTOR : DEFAULT_PRESETS_USER;
        ensureFresh();
        String v = cache.get(key);
        if (v == null || v.trim().isEmpty()) {
            return fallback;
        }
        try {
            List<String> parsed = com.alibaba.fastjson2.JSON.parseArray(v.trim(), String.class);
            List<String> cleaned = new java.util.ArrayList<>();
            for (String s : parsed) {
                if (s != null && !s.trim().isEmpty()) {
                    cleaned.add(s.trim());
                }
            }
            return cleaned.isEmpty() ? fallback : cleaned;
        } catch (Exception e) {
            log.warn("[DataAgentConfig] 预设问题解析失败，回落默认值: {}", e.getMessage());
            return fallback;
        }
    }

    /** 当前生效配置快照（含说明与默认值），供管理员页面展示。 */
    public Map<String, Object> snapshot() {
        ensureFresh();
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, String[]> e : SCHEMA.entrySet()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("key", e.getKey());
            item.put("description", e.getValue()[0]);
            item.put("valueType", e.getValue()[1]);
            item.put("value", raw(e.getKey(), defaultValue(e.getKey())));
            out.put(e.getKey(), item);
        }
        Map<String, Object> effective = new LinkedHashMap<>();
        effective.put(KEY_ENABLED, isEnabled());
        effective.put(KEY_REPORT_ENABLED, isReportEnabled());
        effective.put(KEY_MAX_ROWS, getMaxRows());
        effective.put(KEY_QUERY_TIMEOUT, getQueryTimeout());
        effective.put("presetCountUser", presetQuestions(false).size());
        effective.put("presetCountDoctor", presetQuestions(true).size());

        // ---- 2026-10-07：按使用者分层的「实际生效值」，供管理端并排对照展示 ----
        // 注意：这是【实际生效】值（分层值优先、否则回落全局），与上面 raw 回传的配置值不同。
        for (String audience : AUDIENCES) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("label", audienceLabel(audience));
            row.put("enabled", isEnabledFor(audience));
            row.put("reportEnabled", isReportEnabledFor(audience));
            row.put("maxRows", getMaxRowsFor(audience));
            row.put("queryTimeout", getQueryTimeoutFor(audience));
            row.put("overriddenEnabled", isAudienceKeyConfigured(KEY_ENABLED, audience));
            row.put("overriddenMaxRows", isAudienceKeyConfigured(KEY_MAX_ROWS, audience));
            row.put("overriddenQueryTimeout", isAudienceKeyConfigured(KEY_QUERY_TIMEOUT, audience));
            effective.put(audience, row);
        }

        // ---- 三项新能力的实际生效值 ----
        effective.put("contextRounds", getContextRounds());
        effective.put("contextMaxChars", getContextMaxChars());
        effective.put("contextSummaryThreshold", getContextSummaryThreshold());
        effective.put("memoryEnabled", isMemoryEnabled());
        effective.put("memoryMaxChars", getMemoryMaxChars());
        effective.put("memoryMaxUsers", getMemoryMaxUsers());
        effective.put("sandboxMaxBytes", getSandboxMaxBytes());
        effective.put("sandboxGuardEnabled", isSandboxGuardEnabled());
        effective.put("sandboxReadonly", isSandboxReadonlyConfigured());
        out.put("effective", effective);
        return out;
    }

    /**
     * 该端是否显式配置了覆盖值（未配置时返回 false，管理端据此显示「跟随全局」）。
     *
     * <p>空串视为未配置 —— 与 {@link #rawForAudience} 的判定保持一致。</p>
     */
    public boolean isAudienceKeyConfigured(String key, String audience) {
        if (audience == null || !AUDIENCES.contains(audience)) {
            return false;
        }
        ensureFresh();
        String v = cache.get(key + "-" + audience);
        return v != null && !v.trim().isEmpty();
    }

    private String defaultValue(String key) {
        switch (key) {
            case KEY_ENABLED:
                return String.valueOf(properties != null && properties.isEnabled());
            case KEY_REPORT_ENABLED:
                return "true";
            case KEY_MAX_ROWS:
                return String.valueOf(properties != null ? properties.getMaxRows() : 200);
            case KEY_QUERY_TIMEOUT:
                return String.valueOf(properties != null ? properties.getQueryTimeout() : 10);
            case KEY_DIALECT:
                return properties == null ? "MySQL" : properties.getDialect();
            case KEY_MAX_FIX_RETRY:
                return String.valueOf(properties == null ? DEFAULT_MAX_FIX_RETRY : properties.getMaxFixRetry());
            case KEY_PRESETS_USER:
                return com.alibaba.fastjson2.JSON.toJSONString(DEFAULT_PRESETS_USER);
            case KEY_PRESETS_DOCTOR:
                return com.alibaba.fastjson2.JSON.toJSONString(DEFAULT_PRESETS_DOCTOR);
            // ---- 2026-10-07 新增：分层键的默认值是空串，语义 =「不覆盖，跟随全局」----
            case KEY_CONTEXT_ROUNDS:
                return "10";
            case KEY_CONTEXT_MAX_CHARS:
                return "4000";
            case KEY_CONTEXT_SUMMARY_THRESHOLD:
                return "0";
            case KEY_MEMORY_ENABLED:
                return "true";
            case KEY_MEMORY_MAX_CHARS:
                return "800";
            case KEY_MEMORY_MAX_USERS:
                return "1000";
            case KEY_SANDBOX_MAX_BYTES:
                return String.valueOf(512 * 1024);
            case KEY_SANDBOX_GUARD_ENABLED:
                return "true";
            case KEY_SANDBOX_READONLY:
                return "false";
            default:
                // 分层键（含 enabled-user / max-rows-doctor 等）走这里：空串 = 跟随全局
                return "";
        }
    }

    // ---- 写入（管理员） ----

    /** 批量落库并立即刷新缓存。 */
    public void update(Map<String, String> values) {
        if (values == null || values.isEmpty()) {
            return;
        }
        NamedParameterJdbcTemplate jdbc = new NamedParameterJdbcTemplate(dataSource);
        for (Map.Entry<String, String> e : values.entrySet()) {
            if (!SCHEMA.containsKey(e.getKey())) {
                continue;
            }
            upsert(jdbc, e.getKey(), e.getValue());
        }
        reload();
    }

    private void upsert(NamedParameterJdbcTemplate jdbc, String key, String value) {
        String v = value == null ? "" : value.trim();
        Integer id = jdbc.query(
                "SELECT id FROM system_config WHERE config_group = :g AND config_key = :k LIMIT 1",
                new MapSqlParameterSource().addValue("g", GROUP).addValue("k", key),
                rs -> rs.next() ? rs.getInt(1) : null);
        String[] meta = SCHEMA.get(key);
        if (id == null) {
            jdbc.update("INSERT INTO system_config "
                            + "(config_group, config_key, config_value, description, sensitive, value_type, default_value, create_time, update_time) "
                            + "VALUES (:g, :k, :v, :d, 0, :t, :dv, NOW(), NOW())",
                    new MapSqlParameterSource()
                            .addValue("g", GROUP).addValue("k", key).addValue("v", v)
                            .addValue("d", meta[0]).addValue("t", meta[1]).addValue("dv", defaultValue(key)));
        } else {
            jdbc.update("UPDATE system_config SET config_value = :v, update_time = NOW() WHERE id = :id",
                    new MapSqlParameterSource().addValue("v", v).addValue("id", id));
        }
    }

    /** 强制从库重载。 */
    public void reload() {
        try {
            NamedParameterJdbcTemplate jdbc = new NamedParameterJdbcTemplate(dataSource);
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT config_key, config_value FROM system_config WHERE config_group = :g",
                    new MapSqlParameterSource().addValue("g", GROUP));
            Map<String, String> fresh = new ConcurrentHashMap<>();
            for (Map<String, Object> row : rows) {
                Object k = row.get("config_key");
                Object v = row.get("config_value");
                if (k != null) {
                    fresh.put(String.valueOf(k), v == null ? "" : String.valueOf(v));
                }
            }
            cache.clear();
            cache.putAll(fresh);
            loadedAt = System.currentTimeMillis();
        } catch (Exception e) {
            log.warn("[DataAgentConfig] 读取 system_config 失败，沿用默认配置: {}", e.getMessage());
            loadedAt = System.currentTimeMillis();
        }
    }

    private void ensureFresh() {
        if (System.currentTimeMillis() - loadedAt > TTL_MS) {
            reload();
        }
    }

    private String raw(String key, String def) {
        ensureFresh();
        String v = cache.get(key);
        return v == null || v.trim().isEmpty() ? def : v.trim();
    }

    private boolean asBool(String key, boolean def) {
        String v = raw(key, String.valueOf(def));
        return parseBool(v, def);
    }

    private int asInt(String key, int def) {
        return parseInt(raw(key, String.valueOf(def)), def);
    }

    /**
     * 解析<b>已取出的配置值</b>为布尔。
     *
     * <p>与 {@link #asBool} 分开是因为分层读取拿到的是<b>值</b>而不是键 ——
     * 误用 {@code asBool(值, 默认)} 会把值当键去查，必然查不到而恒返回默认值
     * （这个坑实测踩过一次：分层开关恒为 false）。</p>
     */
    private static boolean parseBool(String v, boolean def) {
        if (v == null || v.trim().isEmpty()) {
            return def;
        }
        String s = v.trim();
        return "true".equalsIgnoreCase(s) || "1".equals(s) || "yes".equalsIgnoreCase(s);
    }

    /**
     * 解析<b>已取出的配置值</b>为整数。
     *
     * <p>注意与旧 {@code asInt} 的语义差异：这里 <b>0 是合法值</b>
     * （如 {@code context-rounds=0} 表示「不携带历史」），而旧的 {@code asInt} 有
     * {@code n > 0 ? n : def} 的判断，会把 0 悄悄改成默认值。</p>
     */
    private static int parseInt(String v, int def) {
        if (v == null || v.trim().isEmpty()) {
            return def;
        }
        try {
            return Integer.parseInt(v.trim());
        } catch (Exception e) {
            return def;
        }
    }
}
