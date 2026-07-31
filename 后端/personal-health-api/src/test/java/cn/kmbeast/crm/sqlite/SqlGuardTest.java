package cn.kmbeast.crm.sqlite;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SqlGuard 只读守卫与租户隔离单测。
 * 覆盖：注释绕过、多语句、危险关键字、子查询、租户手机号匹配。
 */
class SqlGuardTest {

    @Test
    void allowSimpleSelect() {
        assertDoesNotThrow(() -> SqlGuard.validateReadOnly("SELECT * FROM chat_history WHERE phone_number = '13800000000'"));
    }

    @Test
    void rejectInsert() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> SqlGuard.validateReadOnly("INSERT INTO chat_history VALUES (1,'x')"));
        assertNotNull(e.getMessage());
    }

    @Test
    void rejectDropWithCommentSplit() {
        // 注释拆分关键词绕过：DR/**/OP 会被剥注释后变成 DROP
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> SqlGuard.validateReadOnly("SELECT * FROM chat_history DR/**/OP TABLE x"));
        assertTrue(e.getMessage().contains("forbidden"));
    }

    @Test
    void rejectMultiStatement() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> SqlGuard.validateReadOnly("SELECT 1; DROP TABLE chat_history"));
        assertTrue(e.getMessage().contains("multi-statement"));
    }

    @Test
    void rejectUpdateHiddenInString() {
        // 字符串字面量里的 UPDATE 不应误伤
        assertDoesNotThrow(() -> SqlGuard.validateReadOnly(
                "SELECT * FROM chat_history WHERE content LIKE '%update%' AND phone_number='13800000000'"));
    }

    @Test
    void tenantIsolationRequiresOwnPhone() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> SqlGuard.enforceTenantIsolation(
                        "SELECT * FROM chat_history", "13800000000"));
        assertTrue(e.getMessage().contains("phone_number"));
    }

    @Test
    void tenantIsolationPassWithOwnPhone() {
        assertDoesNotThrow(() -> SqlGuard.enforceTenantIsolation(
                "SELECT * FROM chat_history WHERE phone_number = '13800000000'", "13800000000"));
    }

    @Test
    void tenantIsolationRejectOtherPhone() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> SqlGuard.enforceTenantIsolation(
                        "SELECT * FROM chat_history WHERE phone_number = '13911112222'", "13800000000"));
        assertTrue(e.getMessage().contains("your own phone"));
    }

    @Test
    void tenantIsolationRejectSubquery() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> SqlGuard.enforceTenantIsolation(
                        "SELECT * FROM chat_history WHERE id IN (SELECT id FROM chat_history) AND phone_number='13800000000'",
                        "13800000000"));
        assertTrue(e.getMessage().contains("subqueries"));
    }

    @Test
    void nonChatTableNoTenantNeeded() {
        // 不碰 chat_history 的表不需要租户过滤
        assertDoesNotThrow(() -> SqlGuard.enforceTenantIsolation("SELECT 1", null));
    }
}
