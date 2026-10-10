package cn.kmbeast.core.agent;

import cn.kmbeast.context.LocalThreadHolder;
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
 * 用户偏好（长期记忆）注入 prompt 的行为测试。
 *
 * <p><b>背景</b>：{@code AgentMemoryService} 的治理早已到位（有界 LRU + 定时清理 +
 * 不可变副本），但此前<b>全仓只有 {@code AgentController} 的 3 个 REST 端点调用它</b>，
 * ReAct 与 NL2SQL 两条 Agent 链路都不读 → 「用户偏好记忆」可通过 API 读写，
 * 却<b>永远不会进入 prompt，对用户完全不可见</b>。2026-10-07 把它接进 prompt，
 * 本测试锁住接入后的行为契约。</p>
 */
class AgentMemoryInjectionTest {

    @AfterEach
    void clearThread() {
        LocalThreadHolder.clear();
    }

    /** 复刻 {@code BaseReActAgent.buildMemorySection()} 的可见行为（通过反射调用真实方法）。 */
    private String buildSection(Object agent, Integer userId) throws Exception {
        Class<?> base = Class.forName("cn.kmbeast.crm.agent.BaseReActAgent");
        Method m = base.getDeclaredMethod("buildMemorySection");
        m.setAccessible(true);
        if (userId != null) {
            // 第二个参数是 userRole（1=管理员 2=用户 3=医生），记忆注入只看 userId
            LocalThreadHolder.setUserId(userId, 2);
        }
        return (String) m.invoke(agent);
    }

    /** 构造一个只注入 memoryService 的 BaseReActAgent 匿名子类。 */
    private Object newAgent(AgentMemoryService memoryService) throws Exception {
        Class<?> base = Class.forName("cn.kmbeast.crm.agent.BaseReActAgent");
        // 动态子类化在单测里不便（构造器可能有参数），改用直接对 BaseReActAgent
        // 的一个具体子类操作 —— ReActAgent 的无参构造即可。
        Object agent = new cn.kmbeast.crm.agent.ReActAgent();
        Field f = null;
        Class<?> c = base;
        while (c != null && f == null) {
            try {
                f = c.getDeclaredField("memoryService");
            } catch (NoSuchFieldException e) {
                c = c.getSuperclass();
            }
        }
        assertNotNull(f, "BaseReActAgent 应有 memoryService 字段");
        f.setAccessible(true);
        f.set(agent, memoryService);
        return agent;
    }

    /** 桩：直接返回预设偏好，不碰磁盘。 */
    private static AgentMemoryService stubMemory(Map<String, String> prefs) {
        return new AgentMemoryService() {
            @Override
            public Map<String, String> getUserPreferences(Integer userId) {
                return prefs;
            }

            @Override
            public void evictUser(Integer userId) {
            }

            @Override
            public Map<String, Object> getCacheStats() {
                return new LinkedHashMap<>();
            }
        };
    }

    @Test
    @DisplayName("有偏好时注入到 prompt，且标注为「用户偏好（长期记忆）」")
    void injectsPreferencesWhenPresent() throws Exception {
        Map<String, String> prefs = new LinkedHashMap<>();
        prefs.put("饮食偏好", "素食");
        prefs.put("运动习惯", "每周三次慢跑");
        Object agent = newAgent(stubMemory(prefs));

        String section = buildSection(agent, 1001);
        assertNotNull(section, "有偏好时应注入");
        assertTrue(section.contains("用户偏好"), "应标注为用户偏好段: " + section);
        assertTrue(section.contains("素食"), "应包含偏好值: " + section);
        assertTrue(section.contains("慢跑"), "应包含第二条偏好: " + section);
        // 格式：前导两个换行（便于拼接到 system prompt）
        assertTrue(section.startsWith("\n\n"), "应以两个换行开头便于拼接: " + section.substring(0, 4));
    }

    @Test
    @DisplayName("🔴 取不到登录身份时不注入 —— 绝不能把别人的偏好带进来")
    void skipsWhenNoIdentity() throws Exception {
        Map<String, String> prefs = new LinkedHashMap<>();
        prefs.put("饮食偏好", "素食");
        Object agent = newAgent(stubMemory(prefs));

        String section = buildSection(agent, null);
        assertNull(section, "未登录时必须返回 null（而非空串），表示「不注入」");
    }

    @Test
    @DisplayName("偏好为空时不产生任何 prompt 噪音")
    void skipsWhenEmpty() throws Exception {
        Object agent = newAgent(stubMemory(new LinkedHashMap<>()));
        assertNull(buildSection(agent, 1002));

        agent = newAgent(stubMemory(null));
        assertNull(buildSection(agent, 1003), "桩返回 null 时也应安全跳过");
    }

    @Test
    @DisplayName("未注入 memoryService 时跳过（单测场景不 NPE）")
    void skipsWhenServiceAbsent() throws Exception {
        Object agent = newAgent(null);
        assertNull(buildSection(agent, 1004), "memoryService 为 null 时应返回 null");
    }

    @Test
    @DisplayName("记忆读取抛异常时降级为不注入，绝不影响对话")
    void toleratesServiceFailure() throws Exception {
        Object agent = newAgent(new AgentMemoryService() {
            @Override
            public Map<String, String> getUserPreferences(Integer userId) {
                throw new IllegalStateException("磁盘读取失败");
            }

            @Override
            public void evictUser(Integer userId) {
            }

            @Override
            public Map<String, Object> getCacheStats() {
                return new LinkedHashMap<>();
            }
        });
        String section = buildSection(agent, 1005);
        assertNull(section, "记忆服务异常时必须降级为不注入");
    }

    @Test
    @DisplayName("空 key / 空 value / 空白偏好被跳过，不产生空行噪音")
    void filtersBlankEntries() throws Exception {
        Map<String, String> prefs = new LinkedHashMap<>();
        prefs.put("", "有key没值");
        prefs.put("   ", "空白 key");
        prefs.put("有效键", null);
        prefs.put("另一个空值", "   ");
        prefs.put("真实偏好", "无糖");
        Object agent = newAgent(stubMemory(prefs));

        String section = buildSection(agent, 1006);
        assertNotNull(section);
        assertTrue(section.contains("无糖"), "有效偏好应保留: " + section);
        assertFalse(section.contains("有key没值"), "空 key 的条目应被跳过");
        assertFalse(section.contains("无糖\n-"), "不应产生连续多个条目行: " + section);
    }

    @Test
    @DisplayName("超长偏好被截断到单条上限，不撑爆 prompt")
    void truncatesLongValue() throws Exception {
        StringBuilder huge = new StringBuilder();
        for (int i = 0; i < 500; i++) {
            huge.append('长');
        }
        Map<String, String> prefs = new LinkedHashMap<>();
        prefs.put("长文本偏好", huge.toString());
        Object agent = newAgent(stubMemory(prefs));

        String section = buildSection(agent, 1007);
        assertNotNull(section);
        assertTrue(section.contains("…"), "超长值应以省略号结尾: " + section.length());
        assertTrue(section.length() < 1200, "整段不应过长，实际 " + section.length());
    }

    @Test
    @DisplayName("偏好条数超限时保留最新若干条并标注已省略")
    void keepsNewestWhenOverBudget() throws Exception {
        Map<String, String> prefs = new LinkedHashMap<>();
        // LinkedHashMap 保持插入序：先插入的是旧偏好
        for (int i = 1; i <= 200; i++) {
            prefs.put("偏好" + i, "值" + i);
        }
        Object agent = newAgent(stubMemory(prefs));

        String section = buildSection(agent, 1008);
        assertNotNull(section);
        assertTrue(section.contains("条较早的偏好未列出"),
                "超预算时应标注省略条数: " + section.substring(0, Math.min(120, section.length())));
        assertTrue(section.length() <= 1100, "整段应受 MEMORY_SECTION_MAX_CHARS 约束，实际 " + section.length());
    }

    @Test
    @DisplayName("最新偏好优先保留（倒序遍历）")
    void keepsNewestFirst() throws Exception {
        Map<String, String> prefs = new LinkedHashMap<>();
        prefs.put("旧偏好", "很旧的写法");
        prefs.put("新偏好", "很新的写法");
        Object agent = newAgent(stubMemory(prefs));

        String section = buildSection(agent, 1009);
        assertNotNull(section);
        int newIdx = section.indexOf("很新的写法");
        int oldIdx = section.indexOf("很旧的写法");
        assertTrue(newIdx >= 0, "新偏好应存在: " + section);
        assertTrue(oldIdx >= 0, "旧偏好也应存在（未超预算）: " + section);
        assertTrue(newIdx < oldIdx, "新偏好应排在旧偏好之前（最新优先）");
    }

    @Test
    @DisplayName("身份隔离：不同 userId 读到各自偏好，不串号")
    void isolatesBetweenUsers() throws Exception {
        AgentMemoryService svc = new AgentMemoryService() {
            @Override
            public Map<String, String> getUserPreferences(Integer userId) {
                Map<String, String> m = new LinkedHashMap<>();
                m.put("userId", String.valueOf(userId));
                return m;
            }

            @Override
            public void evictUser(Integer userId) {
            }

            @Override
            public Map<String, Object> getCacheStats() {
                return new LinkedHashMap<>();
            }
        };
        Object agent = newAgent(svc);

        assertTrue(buildSection(agent, 2001).contains("2001"), "应含 A 用户 id");
        assertTrue(buildSection(agent, 2002).contains("2002"), "应含 B 用户 id");
        String aSection = buildSection(agent, 2001);
        assertFalse(aSection.contains("2002"), "A 用户不应看到 B 用户的偏好: " + aSection);
    }

    @Test
    @DisplayName("注入段必须明确「冲突时以当次要求为准」，避免记忆压过当次指令")
    void statesConflictRule() throws Exception {
        Map<String, String> prefs = new LinkedHashMap<>();
        prefs.put("饮食偏好", "素食");
        Object agent = newAgent(stubMemory(prefs));

        String section = buildSection(agent, 1010);
        assertNotNull(section);
        assertTrue(section.contains("以当次要求为准"),
                "必须声明冲突处理规则，避免陈旧记忆压过用户当次明确要求: " + section);
    }

    @Test
    @DisplayName("AgentMemoryService 的类注释不得声称未实现的「长期记忆/语义记忆」")
    void memoryServiceCommentIsHonest() throws Exception {
        // 该服务的类注释曾声称提供「长期记忆、语义记忆、用户偏好记忆」三种，
        // 但 loadFromDisk 只读 preferences 一个 key —— 另两种不存在实现（注释虚标）。
        // 本测试锁定注释与实现一致。
        String src = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(
                "src", "main", "java", "cn", "kmbeast", "core", "agent", "AgentMemoryService.java")),
                java.nio.charset.StandardCharsets.UTF_8);

        // 只要注释提到「语义记忆」或「长期记忆」，其所在段落必须说明这两项当前未实现。
        // 取整个类注释头部（第一个 */ 之前）作为判定范围 —— 那正是做宣称的地方。
        int headEnd = src.indexOf("*/");
        assertTrue(headEnd > 0, "应存在类注释");
        String javadoc = src.substring(0, headEnd);
        for (String claim : new String[]{"语义记忆", "长期记忆"}) {
            int idx = javadoc.indexOf(claim);
            if (idx < 0) {
                continue;
            }
            // 命中位置之后 400 字符内必须有明确的否定/限定说明
            int from = idx;
            int to = Math.min(javadoc.length(), idx + 400);
            String after = javadoc.substring(from, to);
            boolean honest = after.contains("不存在实现") || after.contains("未实现")
                    || after.contains("不含") || after.contains("只读") || after.contains("仅");
            assertTrue(honest,
                    "类注释提到「" + claim + "」时必须在其后明确说明当前未实现"
                            + "（实际只有用户偏好一种）。实际上下文: " + after);
        }
        assertEquals(1, countKeys(src), "loadFromDisk 只应读取 preferences 一个记忆分区");
    }

    /** 统计 readJson 的记忆分区参数出现次数（应恒为 1，即只有 preferences）。 */
    private static int countKeys(String src) {
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("readJson\\(\\s*\"([a-z_]+)\"").matcher(src);
        java.util.Set<String> keys = new java.util.LinkedHashSet<>();
        while (m.find()) {
            keys.add(m.group(1));
        }
        return keys.size();
    }
}