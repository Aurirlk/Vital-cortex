package cn.kmbeast.core.nl2sql;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 阶段 4 · 受控 NL2SQL 安全门禁测试（迁移方案 §六「20 条恶意 SQL 拦截测试」前置条件）。
 *
 * <p>两个被测类均为纯静态方法，零 Mock、零 Spring 上下文。</p>
 */
class Nl2SqlSecurityTest {

    // ---------- MySqlAiSqlGuard：20 条恶意 SQL 必须全部拦截 ----------

    private static final String[] MALICIOUS_SQL = {
            "DELETE FROM user_health",                                  // 1 DML
            "DROP TABLE user_health",                                   // 2 DDL
            "UPDATE user_health SET value = 1",                         // 3 DML
            "INSERT INTO user_health VALUES (1)",                       // 4 DML
            "SELECT * FROM user_health; DROP TABLE user_health",        // 5 多语句
            "SELECT * FROM user_health WHERE 1=1 -- x",                 // 6 注释绕过
            "SELECT * FROM user_health /* c */",                        // 7 注释绕过
            "SELECT * FROM information_schema.tables",                  // 8 系统库
            "SELECT * FROM mysql.user",                                 // 9 系统库
            "SELECT * FROM performance_schema.events",                  // 10 系统库
            "SELECT * FROM sys.config",                                 // 11 系统库
            "SELECT * FROM user",                                       // 12 黑名单表
            "SELECT * FROM role",                                       // 13 黑名单表
            "SELECT * FROM permission",                                 // 14 黑名单表
            "SELECT * FROM system_config",                              // 15 黑名单表
            "SELECT * FROM audit_log",                                  // 16 黑名单表
            "SELECT * FROM user_health UNION SELECT * FROM user",       // 17 联合注入
            "SELECT * FROM user_health INTO OUTFILE '/tmp/x'",          // 18 写文件
            "SELECT SLEEP(10)",                                         // 19 危险函数
            "TRUNCATE TABLE user_health",                               // 20 DDL
    };

    @Test
    @DisplayName("20 条恶意 SQL 必须全部被 MySqlAiSqlGuard 拦截")
    void guardRejectsAllMaliciousSql() {
        for (String sql : MALICIOUS_SQL) {
            assertFalse(MySqlAiSqlGuard.validate(sql), "应被拦截: " + sql);
        }
    }

    @Test
    @DisplayName("assertSafe 对恶意 SQL 抛 Nl2SqlSecurityException")
    void guardThrowsOnMalicious() {
        assertThrows(MySqlAiSqlGuard.Nl2SqlSecurityException.class,
                () -> MySqlAiSqlGuard.assertSafe("DELETE FROM user_health"));
    }

    @Test
    @DisplayName("合法只读查询应放行（含 WITH...SELECT）")
    void guardAcceptsLegitSelect() {
        assertTrue(MySqlAiSqlGuard.validate("SELECT value FROM user_health WHERE metric = 'bp'"));
        assertTrue(MySqlAiSqlGuard.validate("SELECT * FROM user_health"));
        assertTrue(MySqlAiSqlGuard.validate(
                "WITH t AS (SELECT value FROM user_health) SELECT * FROM t"));
    }

    @Test
    @DisplayName("空值与非 SELECT 开头应被拒绝")
    void guardRejectsBlankAndNonSelect() {
        assertFalse(MySqlAiSqlGuard.validate(null));
        assertFalse(MySqlAiSqlGuard.validate("   "));
        assertFalse(MySqlAiSqlGuard.validate("SHOW TABLES"));
    }

    @Test
    @DisplayName("列名包含敏感词前缀不应误伤（user_id ≠ user）")
    void guardDoesNotFalsePositiveOnColumnPrefix() {
        // user_id 是完整标识符，不应被当作黑名单表 user 拦截
        assertTrue(MySqlAiSqlGuard.validate("SELECT user_id, value FROM user_health"));
    }

    // ---------- TenantSqlRewriter：删除而非校验 ----------

    @Test
    @DisplayName("租户谓词被中和，并无条件追加租户绑定")
    void rewriterNeutralizesAndAppends() {
        String sql = "SELECT * FROM user_health WHERE user_id = 3 AND metric = 'bp'";
        String out = TenantSqlRewriter.rewrite(sql, "user_id").getSql();
        assertEquals("SELECT * FROM user_health WHERE (1=1 AND metric = 'bp') "
                + "AND user_id = :__tenantUserId", out);
    }

    @Test
    @DisplayName("OR 绕过：整个 WHERE 被括起来，租户条件不被 OR 短路")
    void rewriterWrapsWhereToDefeatOrEvasion() {
        String sql = "SELECT * FROM user_health WHERE user_id = 1 OR 1=1";
        String out = TenantSqlRewriter.rewrite(sql, "user_id").getSql();
        // 关键：括号保证 (1=1 OR 1=1) AND 租户条件，而非 1=1 OR (1=1 AND 租户条件)
        assertTrue(out.contains("WHERE (1=1 OR 1=1) AND user_id = :__tenantUserId"),
                "实际: " + out);
    }

    @Test
    @DisplayName("无 WHERE 子句时插入完整租户 WHERE（保持 ORDER BY/LIMIT 位置正确）")
    void rewriterAppendsWhereWhenAbsent() {
        String sql = "SELECT * FROM user_health ORDER BY id LIMIT 10";
        String out = TenantSqlRewriter.rewrite(sql, "user_id").getSql();
        assertEquals("SELECT * FROM user_health WHERE user_id = :__tenantUserId ORDER BY id LIMIT 10", out);
    }

    @Test
    @DisplayName("无租户列的表不做租户过滤（但仍中和租户谓词）")
    void rewriterNoopWhenNoTenantColumn() {
        String sql = "SELECT * FROM mall_product WHERE user_id = 9";
        TenantSqlRewriter.Rewritten r = TenantSqlRewriter.rewrite(sql, null);
        assertFalse(r.isTenantApplied());
        assertEquals("SELECT * FROM mall_product WHERE 1=1", r.getSql());
    }

    @Test
    @DisplayName("doctor_id / patient_id 谓词同样被中和")
    void rewriterNeutralizesAllTenantColumns() {
        String out = TenantSqlRewriter.rewrite(
                "SELECT * FROM visit_record WHERE doctor_id = 3001 AND patient_id = 7", "user_id").getSql();
        assertTrue(out.contains("1=1"), "实际: " + out);
        assertTrue(out.contains("AND user_id = :__tenantUserId"), "实际: " + out);
    }

    // ---------- 主表提取 ----------

    @Test
    @DisplayName("extractPrimaryTable 能取到 FROM 后的表名（含反引号）")
    void extractPrimaryTable() {
        assertEquals("user_health", MySqlAiSqlGuard.extractPrimaryTable("SELECT * FROM user_health WHERE 1=1"));
        assertEquals("user_health", MySqlAiSqlGuard.extractPrimaryTable("SELECT * FROM `user_health`"));
        assertNull(MySqlAiSqlGuard.extractPrimaryTable(null));
        assertNull(MySqlAiSqlGuard.extractPrimaryTable("SELECT 1"));
    }

    // ---------- 2026-10-07 加固：JOIN 逐表租户覆盖 ----------

    @Test
    @DisplayName("extractTableRefs 解析 FROM/JOIN 及别名，且不把 SQL 关键字当别名")
    void extractTableRefsParsesJoins() {
        List<MySqlAiSqlGuard.TableRef> refs = MySqlAiSqlGuard.extractTableRefs(
                "SELECT a.id FROM user_health a JOIN visit_record v ON a.id = v.patient_id WHERE a.metric = 'bp'");
        assertEquals(2, refs.size());
        assertEquals("user_health", refs.get(0).getTable());
        assertEquals("a", refs.get(0).getQualifier());
        assertEquals("visit_record", refs.get(1).getTable());
        assertEquals("v", refs.get(1).getQualifier());
        // 无别名，且紧随的关键字（WHERE）不得被当作别名
        List<MySqlAiSqlGuard.TableRef> one = MySqlAiSqlGuard.extractTableRefs("SELECT * FROM user_health WHERE 1=1");
        assertEquals(1, one.size());
        assertEquals("user_health", one.get(0).getQualifier());
    }

    @Test
    @DisplayName("hasSubqueryOrCte 识别子查询与 CTE（保守档位必须拒绝）")
    void detectsSubqueryAndCte() {
        assertTrue(MySqlAiSqlGuard.hasSubqueryOrCte(
                "SELECT * FROM news WHERE id IN (SELECT id FROM user_health)"));
        assertTrue(MySqlAiSqlGuard.hasSubqueryOrCte(
                "WITH t AS (SELECT id FROM user_health) SELECT * FROM t"));
        assertFalse(MySqlAiSqlGuard.hasSubqueryOrCte(
                "SELECT a.id FROM user_health a JOIN visit_record v ON a.id = v.patient_id"));
        assertFalse(MySqlAiSqlGuard.hasSubqueryOrCte("SELECT COUNT(*) FROM user_health"));
    }

    @Test
    @DisplayName("rewriteAll：JOIN 两侧租户列各自限定，带表前缀的谓词被整体中和")
    void rewriteAllQualifiesEachJoinSide() {
        String sql = "SELECT a.value, v.diagnosis FROM user_health a "
                + "JOIN visit_record v ON a.id = v.patient_id WHERE a.user_id = 9";
        TenantSqlRewriter.Rewritten r = TenantSqlRewriter.rewriteAll(sql,
                Arrays.asList("a.user_id", "v.patient_id"));
        assertTrue(r.isTenantApplied());
        String out = r.getSql();
        assertFalse(out.contains("a.user_id = 9"), "带前缀谓词必须被中和，实际: " + out);
        assertTrue(out.contains("(1=1) AND a.user_id = :__tenantUserId AND v.patient_id = :__tenantUserId"),
                "实际: " + out);
    }

    @Test
    @DisplayName("rewriteAll：空清单时仅中和、不追加（所涉表均无租户列）")
    void rewriteAllNoopOnEmptyColumns() {
        TenantSqlRewriter.Rewritten r = TenantSqlRewriter.rewriteAll(
                "SELECT * FROM mall_product WHERE user_id = 9", Collections.emptyList());
        assertFalse(r.isTenantApplied());
        assertEquals("SELECT * FROM mall_product WHERE 1=1", r.getSql());
    }

    @Test
    @DisplayName("rewriteAll：无 WHERE 时新建 WHERE 并按顺序追加全部租户列")
    void rewriteAllInsertsWhereWhenAbsent() {
        TenantSqlRewriter.Rewritten r = TenantSqlRewriter.rewriteAll(
                "SELECT a.id FROM user_health a JOIN visit_record v ON a.id = v.patient_id ORDER BY a.id",
                Arrays.asList("a.user_id", "v.patient_id"));
        assertEquals("SELECT a.id FROM user_health a JOIN visit_record v ON a.id = v.patient_id "
                + "WHERE a.user_id = :__tenantUserId AND v.patient_id = :__tenantUserId ORDER BY a.id",
                r.getSql());
    }

    // ---------- 2026-10-07 二次加固：库名前缀 / 隐式逗号连接 ----------

    @Test
    @DisplayName("extractTableRefs 容忍库名前缀：FROM db.t 只取 t 作为表名")
    void extractTableRefsToleratesSchemaPrefix() {
        List<MySqlAiSqlGuard.TableRef> refs = MySqlAiSqlGuard.extractTableRefs(
                "SELECT * FROM personal_health.user_health WHERE 1=1");
        assertEquals(1, refs.size());
        assertEquals("user_health", refs.get(0).getTable(), "库名前缀不得被当成表名");
        assertEquals("user_health", refs.get(0).getQualifier());
    }

    @Test
    @DisplayName("hasImplicitCommaJoin：识别 FROM a, b，但不误伤 SELECT 列表与 IN (1,2)")
    void detectsImplicitCommaJoinOnly() {
        assertTrue(MySqlAiSqlGuard.hasImplicitCommaJoin(
                "SELECT m.name, h.value FROM mall_product m, user_health h WHERE m.id = h.user_id"));
        assertTrue(MySqlAiSqlGuard.hasImplicitCommaJoin("SELECT * FROM mall_product, user_health"));
        // 以下都不得误判
        assertFalse(MySqlAiSqlGuard.hasImplicitCommaJoin("SELECT a, b, c FROM user_health"));
        assertFalse(MySqlAiSqlGuard.hasImplicitCommaJoin(
                "SELECT * FROM user_health WHERE metric IN ('bp','hr')"));
        assertFalse(MySqlAiSqlGuard.hasImplicitCommaJoin(
                "SELECT * FROM mall_product m JOIN user_health h ON m.id = h.user_id AND m.x IN (1,2)"));
        assertFalse(MySqlAiSqlGuard.hasImplicitCommaJoin("SELECT * FROM user_health GROUP BY a, b"));
        assertFalse(MySqlAiSqlGuard.hasImplicitCommaJoin(null));
    }
}
