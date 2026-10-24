package cn.kmbeast.Interceptor;

import cn.kmbeast.core.auth.AuthSessionManager;
import cn.kmbeast.pojo.em.RoleEnum;
import cn.kmbeast.utils.JwtUtil;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * fail-closed 拒绝时的文案区分（bug.md §7.4 第 2 条，2026-10-25 定：加「系统繁忙」）。
 *
 * <p><b>被锁住的症状</b>：账号状态一律「读不出即拒绝」之后，拦截器只有一个出口
 * {@code writeAccountDisabled}，于是 Redis 与 MySQL 同时不可用的那几分钟里，
 * <b>正常用户收到的是「账号已被禁用，请联系管理员」</b> —— 拒绝是对的（fail-closed 不动），
 * 但这句话让人以为账号被封了、要去联系管理员，而实际上稍后重试就好。
 *
 * <p>这里把两侧都钉死：真锁定（LOCKED）仍说「已被禁用」，读不出（UNKNOWN）改说「系统繁忙」，
 * 且两者都仍是 HTTP 401、都仍然拒绝放行 —— 只改文案，不放松安全取向。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("鉴权拒绝文案：被封禁 vs 系统读不出状态")
class JwtInterceptorFailClosedMessageTest {

    /** context-path + 用户端路径（role=2 可访问） */
    private static final String USER_URI = "/api/personal-health/v1.0/user/profile";

    private JwtInterceptor interceptor;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private AuthSessionManager authSessionManager;

    @BeforeEach
    void setUp() {
        interceptor = new JwtInterceptor();
        ReflectionTestUtils.setField(interceptor, "jwtUtil", jwtUtil);
        ReflectionTestUtils.setField(interceptor, "authSessionManager", authSessionManager);
    }

    /** 带 token 的请求，token 解析出 id + role。 */
    private MockHttpServletRequest request(String uri, Integer userId, Integer roleId) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        request.addHeader("token", "a-token");
        Claims claims = mock(Claims.class);
        when(claims.get("id", Integer.class)).thenReturn(userId);
        when(claims.get("role", Integer.class)).thenReturn(roleId);
        when(jwtUtil.fromToken("a-token")).thenReturn(claims);
        return request;
    }

    private boolean run(MockHttpServletRequest request, MockHttpServletResponse response) {
        try {
            return interceptor.preHandle(request, response, new Object());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String text(MockHttpServletResponse response) {
        try {
            return response.getContentAsString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ==================== 真被封禁 ====================

    @Test
    @DisplayName("账号确认锁定（LOCKED）：401 + 业务码 4010 +「账号已被禁用」")
    void lockedAccountGetsDisabledMessage() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(authSessionManager.accountState(7)).thenReturn(AuthSessionManager.AccountState.LOCKED);

        assertFalse(run(request(USER_URI, 7, RoleEnum.USER.getRole()), response));

        assertEquals(401, response.getStatus());
        String body = text(response);
        assertTrue(body.contains("4010"), body);
        assertTrue(body.contains("账号已被禁用"), body);
        assertFalse(body.contains("系统繁忙"), "真被封禁的人不该看到「稍后重试」，重试不会让他解封");
    }

    // ==================== 只是读不出状态 ====================

    @Test
    @DisplayName("状态读不出（UNKNOWN）：401 + 业务码 4011 +「系统繁忙，请稍后重试」")
    void unknownStateGetsBusyMessage() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(authSessionManager.accountState(7)).thenReturn(AuthSessionManager.AccountState.UNKNOWN);

        assertFalse(run(request(USER_URI, 7, RoleEnum.USER.getRole()), response),
                "🔴 UNKNOWN 仍必须拒绝 —— 只改文案，不放松 fail-closed");

        assertEquals(401, response.getStatus());
        String body = text(response);
        assertTrue(body.contains("4011"), body);
        assertTrue(body.contains("系统繁忙，请稍后重试"), body);
        assertFalse(body.contains("禁用"),
                "🔴 库抖动的几分钟里对正常用户说「账号已被禁用，请联系管理员」是冤枉：" + body);
    }

    @Test
    @DisplayName("UNKNOWN 不查会话版本：既然已经拒绝，不该再打扰 Redis")
    void unknownSkipsVersionCheck() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(authSessionManager.accountState(7)).thenReturn(AuthSessionManager.AccountState.UNKNOWN);

        run(request(USER_URI, 7, RoleEnum.USER.getRole()), response);

        verify(authSessionManager, never()).getVersion(any());
    }

    // ==================== 正常放行不受影响 ====================

    @Test
    @DisplayName("NORMAL：照常放行，不写任何拒绝体")
    void normalAccountPasses() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(authSessionManager.accountState(7)).thenReturn(AuthSessionManager.AccountState.NORMAL);
        when(authSessionManager.getVersion(7)).thenReturn(null);

        assertTrue(run(request(USER_URI, 7, RoleEnum.USER.getRole()), response));

        assertEquals("", text(response));
    }

    @Test
    @DisplayName("医生（role=3）根本不查账号状态：医生不在 user 表，查了永远查不到")
    void doctorSkipsAccountStateLookup() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockHttpServletRequest request = request(
                "/api/personal-health/v1.0/doctor/home", 3001, RoleEnum.DOCTOR.getRole());
        when(authSessionManager.getVersion(3001)).thenReturn(null);

        assertTrue(run(request, response));

        verify(authSessionManager, never()).accountState(any());
    }
}
