package cn.kmbeast.core.agent;

import cn.kmbeast.context.LocalThreadHolder;
import cn.kmbeast.core.nl2sql.DataAgentConfigHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 阶段 4：memory 三项配置（memory-max-chars / memory-max-users / memory-enabled）接线验证。
 *
 * <p><b>背景</b>：改前 {@code BaseReActAgent}、{@code DataAgentPipeline}、{@code AgentMemoryService}
 * 三处上限分别是 {@code static final 800 / 400 / 1000}，管理员在 {@code system_config} 配的
 * {@code memory-max-chars} / {@code memory-max-users} / {@code memory-enabled} 改了也不生效
 * （仅 Holder 的 {@code effective()} 展示用）。本测试锁死「改配置后行为真变」。</p>
 */
@DisplayName("memory 三项配置接线（阶段 4）")
class MemoryConfigWiringTest {

    /** 测试用配置桩：把三项值钉死，覆盖 Holder 的三个 getter。 */
    static final class StubHolder extends DataAgentConfigHolder {
        private final int maxChars;
        private final int maxUsers;
        private final boolean enabled;

        StubHolder(int maxChars, int maxUsers, boolean enabled) {
            this.maxChars = maxChars;
            this.maxUsers = maxUsers;
            this.enabled = enabled;
        }

        @Override
        public int getMemoryMaxChars() {
            return maxChars;
        }

        @Override
        public int getMemoryMaxUsers() {
            return maxUsers;
        }

        @Override
        public boolean isMemoryEnabled() {
            return enabled;
        }
    }

    /** 计数型记忆服务桩：覆写 getUserPreferences，记录调用次数（用于「零磁盘读」断言）。 */
    static final class CountingMemoryService extends AgentMemoryService {
        int calls = 0;
        private final Map<String, String> prefs;

        CountingMemoryService(Map<String, String> prefs) {
            this.prefs = prefs;
        }

        @Override
        public Map<String, String> getUserPreferences(Integer userId) {
            calls++;
            return prefs == null ? new LinkedHashMap<>() : new LinkedHashMap<>(prefs);
        }
    }

    /** 沿类层级查找字段并设置（BaseReActAgent 的字段在父类）。 */
    private static void setField(Object target, String name, Object value) throws Exception {
        Field f = null;
        Class<?> c = target.getClass();
        while (c != null && f == null) {
            try {
                f = c.getDeclaredField(name);
            } catch (NoSuchFieldException e) {
                c = c.getSuperclass();
            }
        }
        if (f == null) {
            throw new AssertionError("字段不存在: " + name);
        }
        f.setAccessible(true);
        f.set(target, value);
    }

    /** 反射调用 BaseReActAgent.buildMemorySection()。 */
    private static String buildSection(Object agent) throws Exception {
        Method m = Class.forName("cn.kmbeast.crm.agent.BaseReActAgent")
                .getDeclaredMethod("buildMemorySection");
        m.setAccessible(true);
        return (String) m.invoke(agent);
    }

    @AfterEach
    void clearThread() {
        LocalThreadHolder.clear();
    }

    // ===================== 断言 1：ReAct 链路截断按 memory-max-chars =====================

    @Test
    @DisplayName("ReAct：偏好段按 memory-max-chars(120) 截断，超出的被省略")
    void reactTruncatesAtConfiguredChars() throws Exception {
        Object agent = new cn.kmbeast.crm.agent.ReActAgent();
        setField(agent, "configHolder", new StubHolder(120, 1000, true));

        Map<String, String> prefs = new LinkedHashMap<>();
        for (int i = 1; i <= 5; i++) {
            prefs.put("k" + i, "v".repeat(40));
        }
        setField(agent, "memoryService", new CountingMemoryService(prefs));

        LocalThreadHolder.setUserId(1, 2);
        String section = buildSection(agent);

        assertNotNull(section, "启用且非空偏好应注入偏好段");
        assertTrue(section.contains("另有 3 条"),
                "120 字符上限下 5 条只放得下 2 条，应省略 3 条，实际: " + section);
    }

    // ===================== 断言 2：NL2SQL 链路截断按 memory-max-chars =====================

    @Test
    @DisplayName("NL2SQL：偏好段按 memory-max-chars(120) 截断")
    void nl2sqlTruncatesAtConfiguredChars() throws Exception {
        Object pipeline = new cn.kmbeast.core.nl2sql.DataAgentPipeline();
        setField(pipeline, "config", new StubHolder(120, 1000, true));

        Map<String, String> prefs = new LinkedHashMap<>();
        for (int i = 1; i <= 5; i++) {
            prefs.put("k" + i, "v".repeat(40));
        }
        setField(pipeline, "memoryService", new CountingMemoryService(prefs));

        Method m = cn.kmbeast.core.nl2sql.DataAgentPipeline.class
                .getDeclaredMethod("safeMemory", Long.class);
        m.setAccessible(true);
        String section = (String) m.invoke(pipeline, 1L);

        assertNotNull(section, "NL2SQL 偏好段不应为 null");
        assertTrue(section.contains("另有 3 条"),
                "120 字符上限下应省略 3 条，实际: " + section);
    }

    // ===================== 断言 3：memory-enabled=false 时零磁盘读 =====================

    @Test
    @DisplayName("关闭开关：两链路都不读偏好（零磁盘读）")
    void disabledMeansZeroDiskRead() throws Exception {
        // ReAct 侧
        Object agent = new cn.kmbeast.crm.agent.ReActAgent();
        setField(agent, "configHolder", new StubHolder(800, 1000, false));
        CountingMemoryService mem = new CountingMemoryService(Map.of("diet", "少盐"));
        setField(agent, "memoryService", mem);
        LocalThreadHolder.setUserId(1, 2);
        String reactSection = buildSection(agent);
        assertNull(reactSection, "关闭记忆开关时不应注入偏好段");
        assertEquals(0, mem.calls, "关闭开关时 ReAct 不应读取偏好（零磁盘读）");

        // NL2SQL 侧
        Object pipeline = new cn.kmbeast.core.nl2sql.DataAgentPipeline();
        setField(pipeline, "config", new StubHolder(800, 1000, false));
        CountingMemoryService mem2 = new CountingMemoryService(Map.of("diet", "少盐"));
        setField(pipeline, "memoryService", mem2);
        Method m = cn.kmbeast.core.nl2sql.DataAgentPipeline.class
                .getDeclaredMethod("safeMemory", Long.class);
        m.setAccessible(true);
        String nl2sqlSection = (String) m.invoke(pipeline, 1L);
        assertEquals("", nl2sqlSection, "关闭记忆开关时 safeMemory 应返回空串");
        assertEquals(0, mem2.calls, "关闭开关时 NL2SQL 不应读取偏好（零磁盘读）");
    }

    // ===================== 断言 4：memory-max-users 控制缓存上限与淘汰 =====================

    @Test
    @DisplayName("memory-max-users(12)：缓存上限=12，第 13 个用户被 LRU 淘汰")
    void maxUsersControlsCacheBoundAndEviction() throws Exception {
        AgentMemoryService service = new AgentMemoryService();
        setField(service, "configHolder", new StubHolder(800, 12, true));
        // 手动触发 @PostConstruct（单测不走 Spring 生命周期）
        service.initCache();

        Map<String, Object> stats = service.getCacheStats();
        assertEquals(12, stats.get("maxCachedUsers"), "缓存上限应来自 memory-max-users=12");

        // 校验实际内部缓存实例：塞 13 个用户，第 13 个触发淘汰，容量恒为 12
        Field cacheField = AgentMemoryService.class.getDeclaredField("userPreferences");
        cacheField.setAccessible(true);
        AgentMemoryService.BoundedPreferenceCache cache =
                (AgentMemoryService.BoundedPreferenceCache) cacheField.get(service);
        for (int i = 1; i <= 13; i++) {
            Map<String, String> p = new LinkedHashMap<>();
            p.put("k", "v" + i);
            cache.put(i, p);
        }
        assertEquals(12, cache.size(), "塞入 13 个用户后容量必须仍为 12");
        assertNull(cache.get(1), "最早写入的用户 1 应被 LRU 淘汰");
    }

    // ===================== 反向验证：配置源未装配时回落代码默认值 =====================

    @Test
    @DisplayName("无配置源：回落默认 800/400/1000，行为不崩")
    void fallsBackWhenNoConfigHolder() throws Exception {
        // ReAct：configHolder 为 null → 回落 800，2 条放得下、不会因上限为 0 而全删
        Object agent = new cn.kmbeast.crm.agent.ReActAgent();
        // 不设置 configHolder（保持 null）
        Map<String, String> prefs = new LinkedHashMap<>();
        for (int i = 1; i <= 5; i++) {
            prefs.put("k" + i, "v".repeat(40));
        }
        setField(agent, "memoryService", new CountingMemoryService(prefs));
        LocalThreadHolder.setUserId(1, 2);
        String reactSection = buildSection(agent);
        assertNotNull(reactSection, "无配置源时应回落默认上限并正常注入");
        assertFalse(reactSection.contains("另有 3 条"),
                "回落默认 800 时 5 条（约 225 字符）应全部放得下，不应省略");

        // AgentMemoryService：configHolder 为 null → initCache 维持默认 1000
        AgentMemoryService service = new AgentMemoryService();
        service.initCache();
        assertEquals(1000, service.getCacheStats().get("maxCachedUsers"),
                "无配置源时缓存上限应回落默认 1000");
    }
}
