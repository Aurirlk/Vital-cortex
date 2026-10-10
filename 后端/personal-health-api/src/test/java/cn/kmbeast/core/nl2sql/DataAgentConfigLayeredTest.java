package cn.kmbeast.core.nl2sql;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 按「使用者」分层读取配置的行为测试。
 *
 * <p><b>背景</b>：2026-10-07 起管理员可按用户端 / 医生端分别管理数据智能体的
 * 开关、报告、行数上限与 SQL 超时。实现复用 {@code preset-questions-user} 的
 * <b>key 后缀范式</b>：每个键有「无后缀全局值」与「带端后缀覆盖值」两档，
 * 读取时先查后缀值，未配置则回落全局值 —— 因此<b>不配置也完全向后兼容</b>。</p>
 *
 * <p><b>本测试锁住三个实现坑</b>（都是本次开发实测踩出来的）：</p>
 * <ol>
 *   <li>分层读取拿到的是<b>配置值</b>而不是键，绝不能拿值去调
 *       {@code asBool(String, boolean)}（其首参是键）—— 否则查不到恒返回默认值；</li>
 *   <li>不能用 {@code null} 作 defaultValue —— 会一路传到
 *       {@code ConcurrentHashMap.get(null)} 触发 NPE；</li>
 *   <li>{@code 0} 必须是合法值（{@code context-rounds=0} 表示「不携带历史」），
 *       旧的 {@code asInt} 有 {@code n > 0 ? n : def} 会把 0 改掉。</li>
 * </ol>
 */
class DataAgentConfigLayeredTest {

    private HikariDataSource noopDs;

    @AfterEach
    void tearDown() {
        if (noopDs != null) {
            noopDs.close();
        }
    }

    /** 构造一个 cache 预置好的 Holder（跳过真实 DB 读取，focus on 分层逻辑）。 */
    private DataAgentConfigHolder holderWith(Map<String, String> preset) throws Exception {
        DataAgentConfigHolder h = new DataAgentConfigHolder();
        // dataSource 给一个不会真连库的占位（reload 走 catch 分支，不影响 cache 注入）
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl("jdbc:mysql://127.0.0.1:1/none?connectTimeout=200");
        ds.setUsername("x");
        ds.setPassword("x");
        noopDs = ds;
        Field dsF = DataAgentConfigHolder.class.getDeclaredField("dataSource");
        dsF.setAccessible(true);
        dsF.set(h, ds);

        @SuppressWarnings("unchecked")
        Map<String, String> cache = (Map<String, String>) fieldOf(DataAgentConfigHolder.class, h, "cache");
        cache.clear();
        cache.putAll(preset);
        // 让 ensureFresh 认为是刚加载过，避免 reload 覆盖 cache
        Field la = DataAgentConfigHolder.class.getDeclaredField("loadedAt");
        la.setAccessible(true);
        la.setLong(h, System.currentTimeMillis());
        return h;
    }

    private static Object fieldOf(Class<?> c, Object target, String name) throws Exception {
        Field f = c.getDeclaredField(name);
        f.setAccessible(true);
        return f.get(target);
    }

    @Test
    @DisplayName("覆盖值优先：医生端 500 行 / 15 秒，全局 200 行 / 10 秒")
    void scopedValueWinsOverGlobal() throws Exception {
        Map<String, String> m = new HashMap<>();
        m.put("enabled", "true");
        m.put("max-rows", "200");
        m.put("max-rows-doctor", "500");
        m.put("query-timeout", "10");
        m.put("query-timeout-doctor", "15");
        DataAgentConfigHolder h = holderWith(m);

        assertEquals(500, h.getMaxRowsFor("doctor"), "医生端应取覆盖值 500");
        assertEquals(15, h.getQueryTimeoutFor("doctor"), "医生端应取覆盖值 15");
        // 未配置覆盖值的用户端回落全局
        assertEquals(200, h.getMaxRowsFor("user"), "用户端未配置覆盖，应回落全局 200");
        assertEquals(10, h.getQueryTimeoutFor("user"), "用户端未配置覆盖，应回落全局 10");
        // audience=null 即读全局
        assertEquals(200, h.getMaxRowsFor(null));
        assertTrue(h.isEnabledFor(null), "全局 enabled=true 应生效 —— 回归：曾因拿值当键查而恒 false");
    }

    @Test
    @DisplayName("🔴 空串视为「未配置」，不覆盖全局值（否则种子里的空串会让开关消失）")
    void blankMeansNotConfigured() throws Exception {
        Map<String, String> m = new HashMap<>();
        m.put("enabled", "true");
        m.put("enabled-user", "");       // 种子脚本写的就是空串
        m.put("max-rows", "200");
        m.put("max-rows-doctor", "");
        DataAgentConfigHolder h = holderWith(m);

        assertTrue(h.isEnabledFor("user"), "空串不应把全局 true 覆盖掉");
        assertEquals(200, h.getMaxRowsFor("doctor"), "空串不应把全局 200 覆盖掉");
        assertFalse(h.isAudienceKeyConfigured("enabled", "user"), "空串不算已覆盖");
        assertFalse(h.isAudienceKeyConfigured("max-rows", "doctor"));
        assertTrue(h.isAudienceKeyConfigured("max-rows", "user") == false);
    }

    @Test
    @DisplayName("覆盖标记正确反映「是否显式配置」")
    void overrideFlagReflectsConfiguration() throws Exception {
        Map<String, String> m = new HashMap<>();
        m.put("enabled", "true");
        m.put("enabled-doctor", "false");     // 显式关闭医生端
        DataAgentConfigHolder h = holderWith(m);

        assertFalse(h.isEnabledFor("doctor"), "医生端被显式关闭，应为 false");
        assertTrue(h.isEnabledFor("user"), "用户端未配置，应跟随全局 true");
        assertTrue(h.isAudienceKeyConfigured("enabled", "doctor"), "显式配了 false 也算已配置");
        assertFalse(h.isAudienceKeyConfigured("enabled", "user"));
    }

    @Test
    @DisplayName("🔴 数值 0 是合法值 —— context-rounds=0 表示不携带历史")
    void zeroIsValidValue() throws Exception {
        Map<String, String> m = new HashMap<>();
        m.put("context-rounds", "0");
        DataAgentConfigHolder h = holderWith(m);

        assertEquals(0, h.getContextRounds(),
                "0 必须被如实保留 —— 旧 asInt 的 n>0 判断会把它改成默认值 10，导致「不携带历史」失效");
    }

    @Test
    @DisplayName("🔴 分层读取不得因 null 默认值触发 ConcurrentHashMap NPE")
    void noNpeOnFallbackPath() throws Exception {
        // cache 全空 → 全部走代码默认值路径（这里最容易踩 cache.get(null)）
        DataAgentConfigHolder h = holderWith(new HashMap<>());

        assertFalse(h.isEnabledFor("user"), "无配置时默认关闭（与 crm.ai.nl2sql.enabled 默认一致）");
        assertFalse(h.isEnabledFor(null));
        assertEquals(200, h.getMaxRowsFor("user"), "应回落到 properties 缺省 200");
        assertEquals(10, h.getQueryTimeoutFor("doctor"));
        assertTrue(h.isReportEnabledFor("user"), "报告默认开启");
    }

    @Test
    @DisplayName("未知 audience 视为不分层，读全局")
    void unknownAudienceFallsBackToGlobal() throws Exception {
        Map<String, String> m = new HashMap<>();
        m.put("max-rows", "333");
        m.put("max-rows-admin", "999");      // 未登记的 audience
        DataAgentConfigHolder h = holderWith(m);

        assertEquals(333, h.getMaxRowsFor("admin"), "未登记的 audience 不应命中覆盖值");
        assertEquals(333, h.getMaxRowsFor("whatever"));
    }

    @Test
    @DisplayName("三项新能力都能读到值，且被钳制在安全范围内")
    void newCapabilitiesAreBounded() throws Exception {
        Map<String, String> m = new HashMap<>();
        m.put("context-rounds", "999");       // 超上限 50
        m.put("context-max-chars", "10");     // 低于下限 500
        m.put("memory-max-chars", "999999");  // 超上限 5000
        m.put("sandbox-max-bytes", "10");     // 低于下限 4096
        DataAgentConfigHolder h = holderWith(m);

        assertEquals(50, h.getContextRounds(), "应钳制到上限 50");
        assertEquals(500, h.getContextMaxChars(), "应钳制到下限 500");
        assertEquals(5000, h.getMemoryMaxChars(), "应钳制到上限 5000");
        assertEquals(4096, h.getSandboxMaxBytes(), "应钳制到下限 4096");
    }

    @Test
    @DisplayName("max-rows / query-timeout 被钳制在业务上限内")
    void limitsAreClamped() throws Exception {
        Map<String, String> m = new HashMap<>();
        m.put("max-rows-doctor", "999999");     // 超 2000
        m.put("query-timeout-doctor", "600");   // 超 60
        DataAgentConfigHolder h = holderWith(m);

        assertEquals(2000, h.getMaxRowsFor("doctor"), "行数上限 2000");
        assertEquals(60, h.getQueryTimeoutFor("doctor"), "超时上限 60 秒");
    }

    @Test
    @DisplayName("布尔值容错：1 / yes / 大写 TRUE 都算真；非法值算假（fail-safe 偏保守）")
    void booleanParsingIsTolerant() throws Exception {
        Map<String, String> m = new HashMap<>();
        m.put("enabled-user", "1");
        m.put("enabled-doctor", "YES");
        m.put("report-enabled-user", "TRUE");
        m.put("report-enabled-doctor", "垃圾值");
        DataAgentConfigHolder h = holderWith(m);

        assertTrue(h.isEnabledFor("user"), "1 应为真");
        assertTrue(h.isEnabledFor("doctor"), "YES 应为真");
        assertTrue(h.isReportEnabledFor("user"), "大写 TRUE 应为真");
        // 已实测确认：非 true/1/yes 的值一律判【假】而非回落默认值。
        // 这是刻意的 fail-safe —— 配置写坏时宁可少生成报告，也不要静默放开。
        assertFalse(h.isReportEnabledFor("doctor"),
                "非法布尔值应判为 false（fail-safe），不是回落默认 true");
    }

    @Test
    @DisplayName("audienceLabel 给中文名，供管理端界面渲染")
    void audienceLabelIsChinese() {
        assertEquals("用户端", DataAgentConfigHolder.audienceLabel("user"));
        assertEquals("医生端", DataAgentConfigHolder.audienceLabel("doctor"));
        assertEquals("全局", DataAgentConfigHolder.audienceLabel(null));
        assertEquals("全局", DataAgentConfigHolder.audienceLabel("unknown"));
    }
}