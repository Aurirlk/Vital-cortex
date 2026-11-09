package cn.kmbeast.mapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 阶段 8：结构化记忆 6 列的实体 / Mapper / DDL / 结构基线一致性。
 *
 * <p>重点防的是两个曾真实发生过的坑：</p>
 * <ol>
 *   <li>基线文档手工补录了实库不存在的列 → 一致性测试误绿，真实调用必抛
 *       {@code Unknown column}（2026-11-08 事故）；</li>
 *   <li>Mapper 的 update 若漏写 {@code <if test="xxx != null">}，
 *       用户改一次昵称就会把记忆清空。</li>
 * </ol>
 */
@DisplayName("AI 记忆 6 列一致性")
class UserMemoryColumnsTest {

    private static final String[] FIELDS = {"aiMemoryFacts", "aiMemoryEnabled", "aiMemoryOptOut"};
    private static final String[] COLUMNS = {"ai_memory_facts", "ai_memory_enabled", "ai_memory_opt_out"};

    private static String read(Path p) throws Exception {
        return new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
    }

    private static Path repoRoot() {
        Path p = Paths.get("").toAbsolutePath();
        while (p != null) {
            if (Files.isDirectory(p.resolve(Paths.get("Data", "04_结构基线")))) {
                return p;
            }
            p = p.getParent();
        }
        return Paths.get("").toAbsolutePath();
    }

    private static String entity(String name) throws Exception {
        return read(Paths.get("src", "main", "java", "cn", "kmbeast", "pojo", "entity", name + ".java"));
    }

    private static String mapper(String name) throws Exception {
        return read(Paths.get("src", "main", "resources", "mapper", name + ".xml"));
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

    @Test
    @DisplayName("User / HospitalDoctor 各有 3 个 aiMemory* 字段")
    void entitiesHaveThreeFields() throws Exception {
        for (String name : new String[]{"User", "HospitalDoctor"}) {
            String src = entity(name);
            for (String f : FIELDS) {
                assertTrue(src.contains("private " + typeOf(f) + " " + f + ";"),
                        name + " 缺少字段 " + f);
            }
        }
    }

    private static String typeOf(String field) {
        return "aiMemoryFacts".equals(field) ? "String" : "Boolean";
    }

    @Test
    @DisplayName("🔴 UserMapper 的 update / updateSettings 三列都必须有 <if> 保护")
    void userMapperUpdatesAreGuarded() throws Exception {
        String src = mapper("UserMapper");
        for (String f : FIELDS) {
            String guard = "<if test=\"" + f + " != null\">";
            assertEquals(2, count(src, guard),
                    "UserMapper 的 update 与 updateSettings 各需一个 " + guard);
        }
        // resultMap 也要能读出来
        for (String col : COLUMNS) {
            assertTrue(src.contains("<result column=\"" + col + "\""),
                    "BaseResultMap 缺少 " + col);
        }
    }

    @Test
    @DisplayName("🔴 HospitalDoctorMapper 的 update 三列必须有 <if> 保护")
    void doctorMapperUpdateIsGuarded() throws Exception {
        String src = mapper("HospitalDoctorMapper");
        for (String f : FIELDS) {
            assertTrue(src.contains("<if test=\"" + f + " != null\">"),
                    "HospitalDoctorMapper.update 缺少 " + f + " 的 <if> 保护");
        }
    }

    @Test
    @DisplayName("🔴 两份结构基线镜像都含 6 个新列，且字节一致")
    void baselineMirrorsContainSixColumns() throws Exception {
        Path root = repoRoot();
        Path a = root.resolve(Paths.get("docs", "数据表结构基线-20261003.md"));
        Path b = root.resolve(Paths.get("Data", "04_结构基线", "数据表结构基线-20261003.md"));
        assertTrue(Files.exists(a) && Files.exists(b), "两份基线镜像都必须存在");
        String sa = read(a);
        String sb = read(b);
        assertEquals(sa, sb, "两份结构基线必须字节一致（改表后要跑 gen_schema_doc.py）");
        for (String col : COLUMNS) {
            assertTrue(sa.contains(col), "基线缺少列 " + col);
        }
        assertFalse(sa.contains("hospital_doctor_ai_profile"),
                "旧方案的新表已废弃，基线里不得出现");
    }

    @Test
    @DisplayName("DDL 幂等脚本只加列不建表，且部署链已挂载")
    void ddlIsIdempotentAndMounted() throws Exception {
        Path root = repoRoot();
        String sql = read(root.resolve(Paths.get("Data", "02_迁移脚本",
                "12_user_ai_memory_columns.sql")));
        assertFalse(sql.toLowerCase().contains("create table"), "阶段 8 不建新表");
        assertTrue(sql.contains("information_schema.COLUMNS"), "幂等守卫必须用 information_schema");
        assertTrue(sql.contains("PREPARE _s FROM @_sql"), "幂等守卫必须 PREPARE/EXECUTE");
        for (String col : COLUMNS) {
            assertTrue(sql.contains(col), "DDL 缺少 " + col);
        }
        String compose = read(root.resolve("docker-compose.yml"));
        assertTrue(compose.contains("17-migrate-12-ai-memory.sql"),
                "docker-compose 必须挂载 12 号迁移脚本");
    }

    @Test
    @DisplayName("两份全新部署 DDL 都带 3 个新列")
    void initDdlIncludesColumns() throws Exception {
        Path root = repoRoot();
        for (String file : new String[]{"init_database.sql", "appointment_schema.sql"}) {
            String ddl = read(root.resolve(Paths.get("Data", "01_建库建表", file)));
            for (String col : COLUMNS) {
                assertTrue(ddl.contains(col), file + " 缺少 " + col);
            }
        }
    }
}
