package cn.kmbeast.core.auth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 三端隔离规则单元测试（2026-10-04）
 *
 * <p>这是「医生端与用户端完全隔离」的<b>唯一执行点</b>。
 * 规则写错一行，医生就能读到患者健康数据，或普通用户能读他人接诊记录。
 *
 * <p>用例分三类：
 * <ol>
 *   <li>context-path 剥离是否正确（剥离错了前缀就永远匹配不上 → 隔离静默失效）</li>
 *   <li>隔离矩阵是否严格（医生↔用户互拒）</li>
 *   <li>公共端点不能被误伤</li>
 * </ol>
 */
@DisplayName("三端隔离规则")
class DoctorIsolationTest {

    private static final int ADMIN = 1;
    private static final int USER = 2;
    private static final int DOCTOR = 3;
    private static final String CTX = "/api/personal-health/v1.0";

    // ---------------------------------------------------------------- 路径归一化

    @Test
    @DisplayName("剥离 context-path：/api/.../v1.0/doctor/home → /doctor/home")
    void normalizeShouldStripContextPath() {
        assertEquals("/doctor/home",
                DoctorIsolation.normalize(CTX + "/doctor/home"));
        assertEquals("/user/profile",
                DoctorIsolation.normalize(CTX + "/user/profile"));
    }

    @Test
    @DisplayName("带查询串也能正确剥离")
    void normalizeShouldHandleQueryString() {
        assertEquals("/doctor/appointments",
                DoctorIsolation.normalize(CTX + "/doctor/appointments?fromDate=2026-01-01"));
    }

    @Test
    @DisplayName("无 context-path 时原样返回")
    void normalizeShouldPassThroughBarePath() {
        assertEquals("/doctor/home", DoctorIsolation.normalize("/doctor/home"));
    }

    @Test
    @DisplayName("context-path 后面没有子路径时归一为 /")
    void normalizeShouldReturnSlashWhenOnlyContextPath() {
        assertEquals("/", DoctorIsolation.normalize(CTX));
    }

    @Test
    @DisplayName("null / 空串不抛异常")
    void normalizeShouldTolerateNull() {
        assertDoesNotThrow(() -> DoctorIsolation.normalize(null));
        assertEquals("", DoctorIsolation.normalize(null));
    }

    // ---------------------------------------------------------------- 医生端

    @Test
    @DisplayName("医生可访问 /doctor/**")
    void doctorCanAccessDoctorEndpoints() {
        assertTrue(DoctorIsolation.isAllowed(CTX + "/doctor/home", DOCTOR));
        assertTrue(DoctorIsolation.isAllowed(CTX + "/doctor/appointments", DOCTOR));
        assertTrue(DoctorIsolation.isAllowed(CTX + "/doctor/schedules", DOCTOR));
    }

    @Test
    @DisplayName("普通用户不得访问 /doctor/**（防读取他人接诊记录）")
    void userCannotAccessDoctorEndpoints() {
        assertFalse(DoctorIsolation.isAllowed(CTX + "/doctor/overview", USER),
                "用户能读医生接诊数据即为越权");
        assertFalse(DoctorIsolation.isAllowed(CTX + "/doctor/appointments", USER));
    }

    @Test
    @DisplayName("管理员不得访问 /doctor/**")
    void adminCannotAccessDoctorEndpoints() {
        assertFalse(DoctorIsolation.isAllowed(CTX + "/doctor/home", ADMIN),
                "管理端走 /admin，不应经由医生端；即便管理员也不放行以保持职责单一");
    }

    // ---------------------------------------------------------------- 用户端

    @Test
    @DisplayName("医生不得访问 /user/**（医生不是 user 表记录）")
    void doctorCannotAccessUserEndpoints() {
        assertFalse(DoctorIsolation.isAllowed(CTX + "/user/profile", DOCTOR),
                "医生在 user 表中无记录，放行只会读到 null 或他人数据");
        assertFalse(DoctorIsolation.isAllowed(CTX + "/user/order", DOCTOR));
    }

    @Test
    @DisplayName("普通用户可访问 /user/**")
    void userCanAccessUserEndpoints() {
        assertTrue(DoctorIsolation.isAllowed(CTX + "/user/profile", USER));
    }

    @Test
    @DisplayName("管理员可访问 /user/**（管理端需查看用户数据，属既有业务要求）")
    void adminCanAccessUserEndpoints() {
        assertTrue(DoctorIsolation.isAllowed(CTX + "/user/list", ADMIN));
    }

    // ---------------------------------------------------------------- 公共端点

    @Test
    @DisplayName("公共端点三端放行（公告/资讯/商品等）")
    void publicEndpointsAllowedForAllRoles() {
        String[] publicPaths = {
                CTX + "/news/list",
                CTX + "/mall/product/list",
                CTX + "/ai/chat",
                CTX + "/file/getFile",
        };
        for (String p : publicPaths) {
            assertTrue(DoctorIsolation.isAllowed(p, DOCTOR), p + " 应放行医生");
            assertTrue(DoctorIsolation.isAllowed(p, USER), p + " 应放行用户");
            assertTrue(DoctorIsolation.isAllowed(p, ADMIN), p + " 应放行管理员");
        }
    }

    @Test
    @DisplayName("管理端 /admin/** 不在本规则管辖内（由 @Protector(role=管理员) 负责）")
    void adminPathsNotGovernedByIsolation() {
        // 本规则只管 doctor/user 互斥；/admin 由 Protector 注解控制。
        // 此处断言的是「不会因为路径含 admin 而被误判为用户端」
        assertTrue(DoctorIsolation.isAllowed(CTX + "/admin/user/list", ADMIN));
    }

    // ---------------------------------------------------------------- 非法输入

    @Test
    @DisplayName("roleId 为 null 一律拒绝（不因无法判断而放行）")
    void nullRoleShouldBeRejected() {
        assertFalse(DoctorIsolation.isAllowed(CTX + "/doctor/home", null));
        assertFalse(DoctorIsolation.isAllowed(CTX + "/user/profile", null));
    }

    @Test
    @DisplayName("URI 为 null 一律拒绝")
    void nullUriShouldBeRejected() {
        assertFalse(DoctorIsolation.isAllowed(null, DOCTOR));
        assertFalse(DoctorIsolation.isAllowed(null, USER));
    }

    @Test
    @DisplayName("非法角色编码（0/99）访问受限端点被拒")
    void unknownRoleCannotAccessProtectedPaths() {
        assertFalse(DoctorIsolation.isAllowed(CTX + "/doctor/home", 99));
        assertFalse(DoctorIsolation.isAllowed(CTX + "/user/profile", 0),
                "未知角色不能被当作「非医生」而放行进用户端 —— 否定判断的经典漏洞");
        assertFalse(DoctorIsolation.isAllowed(CTX + "/user/profile", -1));
    }

    @Test
    @DisplayName("非法角色编码连公共端点也不放行（身份不可信时全局拒绝）")
    void unknownRoleRejectedEverywhere() {
        assertFalse(DoctorIsolation.isAllowed(CTX + "/news/list", 99),
                "角色编码非法说明 token 已被篡改，不应放行任何端点");
    }

    // ---------------------------------------------------------------- 前缀绕过

    @Test
    @DisplayName("前缀边界：/doctor-admin 不被误判为 /doctor 前缀")
    void similarPrefixShouldNotMatch() {
        String path = CTX + "/doctor-admin/list";
        // 归一化为 /doctor-admin/list，首字母段是 "doctor-admin" ≠ "doctor"
        assertEquals("/doctor-admin/list", DoctorIsolation.normalize(path));
        // 用户应被放行（它不属于医生端，归属由 @Protector 控制）
        assertTrue(DoctorIsolation.isAllowed(path, USER),
                "非 /doctor 前缀的路径不应被误判为医生端");
        // 但医生也不该因此被当成访问「用户端」而受限
        assertTrue(DoctorIsolation.isAllowed(path, DOCTOR));
    }

    @Test
    @DisplayName("前缀边界：/userXxx 同理不被误判为 /user 前缀")
    void similarUserPrefixShouldNotMatch() {
        String path = CTX + "/user-center/profile";
        assertTrue(DoctorIsolation.isAllowed(path, DOCTOR),
                "/user-center 不是 /user 端点，医生可访问");
    }

    @Test
    @DisplayName("精确等于 /doctor（无子路径）也应命中医生端前缀")
    void exactPrefixShouldMatch() {
        assertFalse(DoctorIsolation.isAllowed(CTX + "/doctor", USER));
        assertTrue(DoctorIsolation.isAllowed(CTX + "/doctor", DOCTOR));
    }

    @Test
    @DisplayName("医生访问 /user 前缀的深层路径同样被拒")
    void doctorCannotAccessDeepUserPaths() {
        assertFalse(DoctorIsolation.isAllowed(CTX + "/user/health/record/1", DOCTOR));
        assertFalse(DoctorIsolation.isAllowed(CTX + "/user/mall/order", DOCTOR));
    }
}
