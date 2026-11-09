package cn.kmbeast.core.memory;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 阶段 8：user / doctor 两套记忆命名空间隔离。
 *
 * <p>医生的 {@code hospital_doctor.id}（3001+）与 {@code user.id} 是两套 id 空间，
 * 即使数值相同也必须读写不同的表、命中不同的缓存键。
 * 本测试同时锁死「不建 hospital_doctor_ai_profile 新表」这条铁律。</p>
 */
@DisplayName("记忆命名空间隔离")
class MemoryNamespaceIsolationTest {

    private static String read(Path p) throws Exception {
        return new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
    }

    /** 逐级上溯定位仓库根（含 Data/02_迁移脚本 的那一级），与 MapperTableConsistencyTest 同口径。 */
    private static Path repoRoot() {
        Path p = Paths.get("").toAbsolutePath();
        while (p != null) {
            if (Files.isDirectory(p.resolve(Paths.get("Data", "02_迁移脚本")))) {
                return p;
            }
            p = p.getParent();
        }
        return Paths.get("").toAbsolutePath();
    }

    private static Path migration() {
        return repoRoot().resolve(Paths.get("Data", "02_迁移脚本", "12_user_ai_memory_columns.sql"));
    }

    @Test
    @DisplayName("两个命名空间映射到两张不同的身份表")
    void namespacesMapToDifferentTables() {
        assertEquals("user", StructuredMemoryService.Namespace.USER.table());
        assertEquals("hospital_doctor", StructuredMemoryService.Namespace.DOCTOR.table());
        assertEquals(2, StructuredMemoryService.Namespace.values().length);
    }

    @Test
    @DisplayName("🔴 同一个数值 id 在两个命名空间下读的是不同的表")
    void sameIdHitsDifferentTables() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), any(Object[].class)))
                .thenReturn(List.of(Map.of("ai_memory_facts", "[]")));
        StructuredMemoryService svc = new StructuredMemoryService() {
            @Override
            protected JdbcTemplate jdbc() {
                return jdbc;
            }
        };

        svc.load(StructuredMemoryService.Namespace.USER, 3001);
        svc.load(StructuredMemoryService.Namespace.DOCTOR, 3001);

        ArgumentCaptor<String> sqls = ArgumentCaptor.forClass(String.class);
        verify(jdbc, times(2)).queryForList(sqls.capture(), any(Object[].class));
        assertTrue(sqls.getAllValues().get(0).contains("FROM user WHERE id = ?"));
        assertTrue(sqls.getAllValues().get(1).contains("FROM hospital_doctor WHERE id = ?"));
    }

    @Test
    @DisplayName("🔴 缓存键包含命名空间，同 id 不同命名空间不串数据")
    void cacheKeyIncludesNamespace() throws Exception {
        String src = read(Paths.get("src", "main", "java", "cn", "kmbeast",
                "core", "memory", "StructuredMemoryService.java"));
        assertTrue(src.contains("ns.name() + \":\" + ownerId"),
                "缓存键必须由 namespace + ownerId 组成");

        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), any(Object[].class)))
                .thenReturn(List.of(Map.of("ai_memory_facts",
                        "[{\"key\":\"a\",\"value\":\"甲\",\"kind\":\"preference\",\"confidence\":1.0}]")));
        StructuredMemoryService svc = new StructuredMemoryService() {
            @Override
            protected JdbcTemplate jdbc() {
                return jdbc;
            }
        };
        assertEquals(1, svc.load(StructuredMemoryService.Namespace.USER, 3001).size());
        assertEquals(1, svc.load(StructuredMemoryService.Namespace.DOCTOR, 3001).size());
        // 两次都真正查库（缓存未跨命名空间命中）
        verify(jdbc, times(2)).queryForList(anyString(), any(Object[].class));
    }

    @Test
    @DisplayName("🔴 不建 hospital_doctor_ai_profile：全仓源码与迁移脚本都不应出现该表")
    void noExtraProfileTable() throws Exception {
        String[] files = {
                "src/main/java/cn/kmbeast/core/memory/StructuredMemoryService.java",
                "src/main/java/cn/kmbeast/core/memory/MemoryApiSupport.java",
                "src/main/java/cn/kmbeast/controller/DoctorMemoryController.java",
        };
        for (String f : files) {
            assertFalse(read(Paths.get(f)).contains("hospital_doctor_ai_profile"),
                    f + " 不应引用已废弃的画像新表");
        }
        Path migration = migration();
        assertTrue(Files.exists(migration), "阶段 8 DDL 脚本必须存在: " + migration);
        assertFalse(read(migration).contains("CREATE TABLE"), "阶段 8 只允许加列，不允许建新表");
    }

    @Test
    @DisplayName("DDL 只给两张身份表各加 3 列")
    void ddlOnlyAddsSixColumns() throws Exception {
        String sql = read(migration());
        for (String col : new String[]{"ai_memory_facts", "ai_memory_enabled", "ai_memory_opt_out"}) {
            assertTrue(sql.contains(col), "DDL 应包含 " + col);
        }
        assertTrue(sql.contains("TABLE_NAME='user'"));
        assertTrue(sql.contains("TABLE_NAME='hospital_doctor'"));
    }
}
