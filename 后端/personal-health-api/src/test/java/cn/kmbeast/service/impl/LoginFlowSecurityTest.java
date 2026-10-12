package cn.kmbeast.service.impl;

import cn.kmbeast.core.auth.AuthSessionManager;
import cn.kmbeast.core.auth.LoginAttemptGuard;
import cn.kmbeast.mapper.HospitalDoctorMapper;
import cn.kmbeast.mapper.UserMapper;
import cn.kmbeast.pojo.api.ApiResult;
import cn.kmbeast.pojo.api.Result;
import cn.kmbeast.pojo.dto.request.DoctorLoginDTO;
import cn.kmbeast.pojo.dto.update.UserLoginDTO;
import cn.kmbeast.pojo.entity.HospitalDoctor;
import cn.kmbeast.pojo.entity.User;
import cn.kmbeast.utils.JwtUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 登录链路的防撞库「接线」测试 —— 2026-10-10 新增。
 *
 * <p>与 {@code LoginAttemptGuardTest} 的分工：那边验证<b>计数器本身</b>算得对不对，
 * 这边验证<b>两个登录入口真的用上了它</b>，以及三条容易在重构中悄悄丢掉的契约：
 * <ol>
 *   <li>封禁检查必须发生在<b>查库之前</b>（否则被封禁时还在给攻击者「账号是否存在」的反馈）；</li>
 *   <li>失败提示必须统一（不能区分「账号不存在」与「密码错误」，那是账号枚举接口）；</li>
 *   <li>医生端首次登录的响应<b>不得携带 doctorId / name</b>（这一步还没验过密码）。</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("登录链路（防撞库接线与信息泄漏）")
class LoginFlowSecurityTest {

    private static final String BLOCK_MSG = "登录尝试过于频繁，请 15 分钟后重试";
    private static final String FAIL_MSG = "账号或密码错误";

    // ==================== 用户端 ====================

    @Mock private UserMapper userMapper;
    @Mock private JwtUtil jwtUtil;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AuthSessionManager authSessionManager;
    @Mock private LoginAttemptGuard loginAttemptGuard;
    @InjectMocks private UserServiceImpl userService;

    // ==================== 医生端 ====================

    @Mock private HospitalDoctorMapper doctorMapper;
    @InjectMocks private DoctorAuthServiceImpl doctorAuthService;

    private static Integer okCode() {
        return ApiResult.success().getCode();
    }

    private static Integer errCode() {
        return ApiResult.error("x").getCode();
    }

    private static UserLoginDTO userDto(String account, String pwd) {
        UserLoginDTO dto = new UserLoginDTO();
        dto.setUserAccount(account);
        dto.setUserPwd(pwd);
        return dto;
    }

    private static User user(int id, boolean locked) {
        return User.builder()
                .id(id)
                .userAccount("admin")
                .userPwd("$2a$hash")
                .userRole(2)
                .isLogin(locked)
                .build();
    }

    @Test
    @DisplayName("账号被封禁时：直接拒绝，且不查库（不给攻击者任何反馈信号）")
    void blockedAccountShouldShortCircuitBeforeDbLookup() {
        when(loginAttemptGuard.checkBlocked("admin")).thenReturn(BLOCK_MSG);

        Result<Object> result = userService.login(userDto("admin", "whatever"));

        assertEquals(errCode(), result.getCode());
        assertEquals(BLOCK_MSG, result.getMsg());
        verify(userMapper, never()).getByActive(any(User.class));
        verify(passwordEncoder, never()).matches(anyString(), anyString());
    }

    @Test
    @DisplayName("密码错误：提示必须与「账号不存在」一致，且计入失败次数")
    void wrongPasswordShouldUseGenericMessage() {
        when(loginAttemptGuard.checkBlocked("admin")).thenReturn(null);
        when(userMapper.getByActive(any(User.class))).thenReturn(user(7, false));
        when(passwordEncoder.matches("bad", "$2a$hash")).thenReturn(false);

        Result<Object> result = userService.login(userDto("admin", "bad"));

        assertEquals(FAIL_MSG, result.getMsg(), "「密码错误」与「账号不存在」可区分 = 账号枚举接口");
        verify(loginAttemptGuard).recordFailure("admin");
        verify(loginAttemptGuard, never()).clearOnSuccess(anyString());
    }

    @Test
    @DisplayName("账号不存在：提示与密码错误相同，并同样计数（否则「计不计数」也是枚举信号）")
    void unknownAccountShouldBeIndistinguishable() {
        when(loginAttemptGuard.checkBlocked("ghost")).thenReturn(null);
        when(userMapper.getByActive(any(User.class))).thenReturn(null);

        Result<Object> result = userService.login(userDto("ghost", "x"));

        assertEquals(FAIL_MSG, result.getMsg());
        verify(loginAttemptGuard).recordFailure("ghost");
    }

    @Test
    @DisplayName("登录成功：清空失败计数并签发 token")
    void successShouldClearFailureCounter() {
        when(loginAttemptGuard.checkBlocked("admin")).thenReturn(null);
        when(userMapper.getByActive(any(User.class))).thenReturn(user(7, false));
        when(passwordEncoder.matches("good", "$2a$hash")).thenReturn(true);
        when(authSessionManager.nextVersion(7)).thenReturn(3);
        when(jwtUtil.toToken(7, 2, 3)).thenReturn("tok");

        Result<Object> result = userService.login(userDto("admin", "good"));

        assertEquals(okCode(), result.getCode());
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) result.getData();
        assertEquals("tok", data.get("token"));
        verify(loginAttemptGuard).clearOnSuccess("admin");
    }

    @Test
    @DisplayName("被锁定的账号：报「登录状态异常」，且不因这次请求清掉失败计数")
    void lockedAccountShouldNotClearCounter() {
        when(loginAttemptGuard.checkBlocked("admin")).thenReturn(null);
        when(userMapper.getByActive(any(User.class))).thenReturn(user(7, true));
        when(passwordEncoder.matches("good", "$2a$hash")).thenReturn(true);

        Result<Object> result = userService.login(userDto("admin", "good"));

        assertEquals(errCode(), result.getCode());
        verify(loginAttemptGuard, never()).clearOnSuccess(anyString());
    }

    @Test
    @DisplayName("空账号/空密码：直接拒绝，不计入失败次数（避免被用来刷爆计数）")
    void blankCredentialsShouldBeRejectedEarly() {
        assertEquals(errCode(), userService.login(null).getCode());
        assertEquals(errCode(), userService.login(userDto("  ", "x")).getCode());
        assertEquals(errCode(), userService.login(userDto("admin", null)).getCode());

        verify(loginAttemptGuard, never()).recordFailure(anyString());
    }

    // ==================== 医生端 ====================

    private static DoctorLoginDTO doctorDto(String username, String pwd) {
        DoctorLoginDTO dto = new DoctorLoginDTO();
        dto.setUsername(username);
        dto.setPassword(pwd);
        return dto;
    }

    private static HospitalDoctor doctor(int id, int needInit, String pwd) {
        HospitalDoctor d = new HospitalDoctor();
        d.setId(id);
        d.setUsername("doc1");
        d.setPassword(pwd);
        d.setStatus(1);
        d.setNeedInitPassword(needInit);
        d.setName("张医生");
        return d;
    }

    @Test
    @DisplayName("医生端封禁检查同样前置：被封禁时不查库")
    void doctorBlockedShouldShortCircuitBeforeDbLookup() {
        when(loginAttemptGuard.checkBlocked("doc1")).thenReturn(BLOCK_MSG);

        Result<Object> result = doctorAuthService.login(doctorDto("doc1", "x"));

        assertEquals(errCode(), result.getCode());
        verify(doctorMapper, never()).getByUsername(anyString());
    }

    @Test
    @DisplayName("医生密码错误：统一提示 + 计数（/doctor/login 此前完全没有流控）")
    void doctorWrongPasswordShouldRecordFailure() {
        when(loginAttemptGuard.checkBlocked("doc1")).thenReturn(null);
        when(doctorMapper.getByUsername("doc1")).thenReturn(doctor(33, 0, "$2a$hash"));
        when(passwordEncoder.matches("bad", "$2a$hash")).thenReturn(false);

        Result<Object> result = doctorAuthService.login(doctorDto("doc1", "bad"));

        assertEquals(FAIL_MSG, result.getMsg());
        verify(loginAttemptGuard).recordFailure("doc1");
    }

    @Test
    @DisplayName("首次登录的响应不得携带 doctorId / name —— 这一步还没验过密码")
    void doctorNeedInitResponseMustNotLeakIdentity() {
        when(loginAttemptGuard.checkBlocked("doc1")).thenReturn(null);
        when(doctorMapper.getByUsername("doc1")).thenReturn(doctor(33, 1, null));

        Result<Object> result = doctorAuthService.login(doctorDto("doc1", null));

        assertEquals(okCode(), result.getCode());
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) result.getData();
        assertEquals(Boolean.TRUE, data.get("needInitPassword"));
        assertFalse(data.containsKey("doctorId"),
                "未认证就返回 doctorId，等于对外提供「医生花名册查询」接口");
        assertFalse(data.containsKey("name"), "同上：真实姓名泄漏");
    }

    @Test
    @DisplayName("医生登录成功：清空失败计数")
    void doctorSuccessShouldClearFailureCounter() {
        when(loginAttemptGuard.checkBlocked("doc1")).thenReturn(null);
        when(doctorMapper.getByUsername("doc1")).thenReturn(doctor(33, 0, "$2a$hash"));
        when(passwordEncoder.matches("good", "$2a$hash")).thenReturn(true);
        when(authSessionManager.nextVersion(33)).thenReturn(1);
        when(jwtUtil.toToken(33, 3, 1)).thenReturn("dtok");

        Result<Object> result = doctorAuthService.login(doctorDto("doc1", "good"));

        assertEquals(okCode(), result.getCode());
        verify(loginAttemptGuard).clearOnSuccess("doc1");
    }
}
