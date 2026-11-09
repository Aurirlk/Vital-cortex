package cn.kmbeast.core.memory;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 阶段 8：记忆删除权 / 越权写入防线。
 *
 * <p>端点只从 {@code LocalThreadHolder.getUserId()} 取主体，且所有读写 SQL
 * 恒带 {@code WHERE id = ?}。反向验证锚点：临时去掉这个过滤，本测试必须真红。</p>
 */
@DisplayName("记忆删除权与身份边界")
class MemoryDeletionRightsTest {

    private static final String FACTS_JSON =
            "[{\"key\":\"diet\",\"value\":\"少盐\",\"kind\":\"preference\",\"confidence\":1.0}]";

    private static String read(Path p) throws Exception {
        return new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
    }

    /** 覆写 jdbc() 的测试替身；load 返回一条事实，update 记录调用参数。 */
    private static StructuredMemoryService serviceWith(JdbcTemplate jdbc) {
        when(jdbc.queryForList(anyString(), any(Object[].class)))
                .thenReturn(List.of(Map.of("ai_memory_facts", FACTS_JSON)));
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        return new StructuredMemoryService() {
            @Override
            protected JdbcTemplate jdbc() {
                return jdbc;
            }
        };
    }

    @Test
    @DisplayName("🔴 删除单条事实的 SQL 带 WHERE id = ?，且主体参数就是当前登录者")
    void deleteUsesOwnerIdFilter() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        StructuredMemoryService svc = serviceWith(jdbc);

        assertTrue(svc.delete(StructuredMemoryService.Namespace.USER, 1001, "diet"));

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).update(sql.capture(), args.capture());
        assertTrue(sql.getValue().contains("WHERE id = ?"),
                "没有主体过滤就可能删到别人记忆，实际 SQL: " + sql.getValue());
        assertEquals(1001, args.getValue()[1], "第二个参数必须是主体 id");
    }

    @Test
    @DisplayName("🔴 清空记忆同样带 WHERE id = ?")
    void clearUsesOwnerIdFilter() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        StructuredMemoryService svc = serviceWith(jdbc);

        assertTrue(svc.clear(StructuredMemoryService.Namespace.USER, 2002));

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).update(sql.capture(), args.capture());
        assertTrue(sql.getValue().contains("WHERE id = ?"));
        assertEquals(2002, args.getValue()[1]);
    }

    @Test
    @DisplayName("🔴 开关写入也带 WHERE id = ?")
    void switchUsesOwnerIdFilter() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        StructuredMemoryService svc = serviceWith(jdbc);

        assertTrue(svc.setSwitch(StructuredMemoryService.Namespace.DOCTOR, 3001, false, true));

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).update(sql.capture(), args.capture());
        assertTrue(sql.getValue().contains("WHERE id = ?"));
        assertEquals(3001, args.getValue()[2]);
    }

    @Test
    @DisplayName("🔴 服务层所有读写 SQL 都必须带主体过滤（静态兜底）")
    void allSqlHasOwnerFilter() throws Exception {
        String src = read(Paths.get("src", "main", "java", "cn", "kmbeast",
                "core", "memory", "StructuredMemoryService.java"));
        int selects = count(src, "SELECT ");
        int updates = count(src, "UPDATE ");
        assertTrue(selects >= 3, "应有多处带主体的读取");
        assertTrue(updates >= 3, "应有多处带主体的写入");
        // 每条 SQL 语句模板都必须包含 WHERE id = ?
        assertEquals(count(src, "WHERE id = ?"), selects + updates,
                "SELECT/UPDATE 数量与 WHERE id = ? 数量必须一一对应，"
                        + "否则说明某条 SQL 漏了主体过滤");
    }

    @Test
    @DisplayName("用户端 / 医生端 Controller 都不接收 userId 参数")
    void controllersDoNotAcceptUserId() throws Exception {
        for (String name : new String[]{"UserMemoryController", "DoctorMemoryController"}) {
            Class<?> clazz = Class.forName("cn.kmbeast.controller." + name);
            for (Method m : clazz.getDeclaredMethods()) {
                for (Class<?> p : m.getParameterTypes()) {
                    assertTrue(p == String.class || p.getName().endsWith("JSONObject"),
                            name + "." + m.getName() + " 出现了身份类入参: " + p.getName());
                }
            }
            String src = read(Paths.get("src", "main", "java", "cn", "kmbeast",
                    "controller", name + ".java"));
            assertFalse(src.contains("@RequestParam"), name + " 不应从请求参数取身份");
            assertTrue(src.contains("LocalThreadHolder.getUserId()"),
                    name + " 必须只从 ThreadLocal 取身份");
        }
    }

    @Test
    @DisplayName("端点不提供 initial_prompt / systemPrompt 自由文本入口")
    void noFreeTextPromptEndpoint() throws Exception {
        String src = read(Paths.get("src", "main", "java", "cn", "kmbeast",
                "core", "memory", "MemoryApiSupport.java"));
        assertTrue(src.contains("FORBIDDEN_FIELDS"));
        assertTrue(src.contains("initialPrompt"));
        assertTrue(src.contains("systemPrompt"));
        assertTrue(src.contains("整单拒绝") || src.contains("整批未保存"));
    }

    private static int count(String src, String needle) {
        int n = 0;
        int i = 0;
        while ((i = src.indexOf(needle, i)) >= 0) {
            n++;
            i += needle.length();
        }
        return n;
    }
}
