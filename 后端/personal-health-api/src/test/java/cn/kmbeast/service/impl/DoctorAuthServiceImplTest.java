package cn.kmbeast.service.impl;

import cn.kmbeast.core.auth.AuthSessionManager;
import cn.kmbeast.mapper.HospitalDoctorMapper;
import cn.kmbeast.pojo.api.Result;
import cn.kmbeast.pojo.dto.request.DoctorLoginDTO;
import cn.kmbeast.pojo.entity.HospitalDoctor;
import cn.kmbeast.utils.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 医生登录单元测试（2026-10-04 构建）
 *
 * <p>覆盖 2026-10-03「医生与 user 解耦」改造引入的核心鉴权分支：
 * <ol>
 *   <li>首次登录强制设密（存量医生 password=NULL + needInitPassword=1）</li>
 *   <li>密码强度校验（长度 / 字母+数字）</li>
 *   <li>账号枚举防护（账号不存在与密码错误返回同一提示）</li>
 *   <li>停用账号拒绝登录</li>
 *   <li>token 以 role=3(医生) 签发，且医生不占 user 表</li>
 *   <li>管理员重置密码后清除「需初始化」标记</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("医生登录体系")
class DoctorAuthServiceImplTest {

    @Mock
    private HospitalDoctorMapper doctorMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private AuthSessionManager authSessionManager;

    @InjectMocks
    private DoctorAuthServiceImpl service;

    private static final Integer DOCTOR_ID = 3001;
    private static final String USERNAME = "doctor3001";

    private HospitalDoctor doctor;

    @BeforeEach
    void setUp() {
        doctor = new HospitalDoctor();
        doctor.setId(DOCTOR_ID);
        doctor.setUsername(USERNAME);
        doctor.setName("张医生");
        doctor.setTitle("主任医师");
        doctor.setTitleLevel(HospitalDoctor.TITLE_TITLE);
        doctor.setDepartmentId(1);
        doctor.setStatus(1);
    }

    private DoctorLoginDTO dto(String pwd) {
        DoctorLoginDTO d = new DoctorLoginDTO();
        d.setUsername(USERNAME);
        d.setPassword(pwd);
        return d;
    }

    // ---------------------------------------------------------------- 首次登录设密

    @Test
    @DisplayName("首次登录：未提供新密码时返回 needInitPassword 引导设密，不签发 token")
    void firstLoginShouldReturnInitFlag() {
        doctor.setPassword(null);
        doctor.setNeedInitPassword(1);
        when(doctorMapper.getByUsername(USERNAME)).thenReturn(doctor);

        Result<Object> r = service.login(dto(null));

        assertEquals(200, r.getCode());
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) r.getData();
        assertEquals(Boolean.TRUE, data.get("needInitPassword"),
                "首次登录应返回 needInitPassword=true 供前端引导设密");
        // 绝不能在此处签发 token
        verify(jwtUtil, never()).toToken(anyInt(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("首次登录：提供合规新密码时完成设密并签发 token")
    void firstLoginWithNewPasswordShouldSetPasswordAndIssueToken() {
        doctor.setPassword(null);
        doctor.setNeedInitPassword(1);
        when(doctorMapper.getByUsername(USERNAME)).thenReturn(doctor);
        when(passwordEncoder.encode("Doctor2026")).thenReturn("$2a$10$hashed");
        when(authSessionManager.nextVersion(DOCTOR_ID)).thenReturn(1);
        when(jwtUtil.toToken(DOCTOR_ID, DoctorAuthServiceImpl.ROLE_DOCTOR, 1))
                .thenReturn("fake.jwt.token");

        DoctorLoginDTO d = dto(null);
        d.setNewPassword("Doctor2026");
        Result<Object> r = service.login(d);

        assertEquals(200, r.getCode());
        // password 落库、salt 置 null、needInitPassword 清零
        verify(doctorMapper).updatePassword(DOCTOR_ID, "$2a$10$hashed", null, 0);
        // token 必须以「医生」角色签发
        verify(jwtUtil).toToken(DOCTOR_ID, DoctorAuthServiceImpl.ROLE_DOCTOR, 1);

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) r.getData();
        assertEquals(Boolean.FALSE, data.get("needInitPassword"));
        assertEquals(DoctorAuthServiceImpl.ROLE_DOCTOR, data.get("role"));
        assertEquals(DoctorAuthServiceImpl.DOCTOR_HOME, data.get("home"),
                "医生登录后应落到医生端，不能进用户端");
    }

    @Test
    @DisplayName("首次登录：新密码不合规时拒绝，不写库不签发 token")
    void firstLoginWithWeakPasswordShouldReject() {
        doctor.setPassword(null);
        doctor.setNeedInitPassword(1);
        when(doctorMapper.getByUsername(USERNAME)).thenReturn(doctor);

        // 过短
        DoctorLoginDTO d1 = dto(null);
        d1.setNewPassword("Ab1");
        Result<Object> r1 = service.login(d1);
        assertNotEquals(200, r1.getCode());
        assertTrue(r1.getMsg().contains("8 位"), "应提示长度不足，实际: " + r1.getMsg());

        // 纯数字，无字母
        DoctorLoginDTO d2 = dto(null);
        d2.setNewPassword("12345678");
        Result<Object> r2 = service.login(d2);
        assertNotEquals(200, r2.getCode());
        assertTrue(r2.getMsg().contains("字母和数字"), "实际: " + r2.getMsg());

        verify(doctorMapper, never()).updatePassword(anyInt(), anyString(), any(), anyInt());
        verify(jwtUtil, never()).toToken(anyInt(), anyInt(), anyInt());
    }

    // ---------------------------------------------------------------- 常规登录

    @Test
    @DisplayName("常规登录：密码正确时签发 role=3 的 token")
    void loginWithCorrectPasswordShouldIssueToken() {
        doctor.setPassword("$2a$10$hashed");
        doctor.setNeedInitPassword(0);
        when(doctorMapper.getByUsername(USERNAME)).thenReturn(doctor);
        when(passwordEncoder.matches("Doctor2026", "$2a$10$hashed")).thenReturn(true);
        when(authSessionManager.nextVersion(DOCTOR_ID)).thenReturn(7);
        when(jwtUtil.toToken(DOCTOR_ID, DoctorAuthServiceImpl.ROLE_DOCTOR, 7)).thenReturn("tok");

        Result<Object> r = service.login(dto("Doctor2026"));

        assertEquals(200, r.getCode());
        verify(jwtUtil).toToken(DOCTOR_ID, 3, 7);
    }

    @Test
    @DisplayName("常规登录：密码错误时拒绝")
    void loginWithWrongPasswordShouldReject() {
        doctor.setPassword("$2a$10$hashed");
        doctor.setNeedInitPassword(0);
        when(doctorMapper.getByUsername(USERNAME)).thenReturn(doctor);
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

        Result<Object> r = service.login(dto("wrong"));

        assertNotEquals(200, r.getCode());
        verify(jwtUtil, never()).toToken(anyInt(), anyInt(), anyInt());
    }

    // ---------------------------------------------------------------- 安全防护

    @Test
    @DisplayName("账号枚举防护：账号不存在与密码错误返回完全相同的提示")
    void shouldNotRevealWhetherAccountExists() {
        when(doctorMapper.getByUsername("nobody")).thenReturn(null);

        DoctorLoginDTO d1 = new DoctorLoginDTO();
        d1.setUsername("nobody");
        d1.setPassword("x");
        Result<Object> unknownAccount = service.login(d1);

        doctor.setPassword("$2a$10$hashed");
        doctor.setNeedInitPassword(0);
        when(doctorMapper.getByUsername(USERNAME)).thenReturn(doctor);
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);
        Result<Object> wrongPassword = service.login(dto("wrong"));

        assertEquals(unknownAccount.getMsg(), wrongPassword.getMsg(),
                "两种失败必须返回同一提示，否则可被用来枚举已注册的医生账号");
    }

    @Test
    @DisplayName("停用账号拒绝登录")
    void disabledDoctorShouldNotLogin() {
        doctor.setStatus(0);
        when(doctorMapper.getByUsername(USERNAME)).thenReturn(doctor);

        Result<Object> r = service.login(dto("Doctor2026"));

        assertNotEquals(200, r.getCode());
        assertTrue(r.getMsg().contains("停用"), "实际: " + r.getMsg());
        verify(jwtUtil, never()).toToken(anyInt(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("空账号直接拒绝，不查库")
    void blankUsernameShouldRejectWithoutQuery() {
        DoctorLoginDTO d = new DoctorLoginDTO();
        d.setUsername("   ");
        d.setPassword("x");

        Result<Object> r = service.login(d);

        assertNotEquals(200, r.getCode());
        verify(doctorMapper, never()).getByUsername(anyString());
    }

    // ---------------------------------------------------------------- 重置密码

    @Test
    @DisplayName("管理员重置密码：加密落库、清除初始化标记、递增会话版本使旧 token 失效")
    void adminResetPasswordShouldInvalidateOldTokens() {
        // 注意：Mapper.getById 声明返回 DoctorVO（继承自 HospitalDoctor），
        // resetPassword 内部只用到「是否存在」与 username，因此这里用最小 DoctorVO 即可。
        cn.kmbeast.pojo.vo.DoctorVO vo = new cn.kmbeast.pojo.vo.DoctorVO();
        vo.setId(DOCTOR_ID);
        vo.setUsername(USERNAME);
        when(doctorMapper.getById(DOCTOR_ID)).thenReturn(vo);
        when(passwordEncoder.encode("NewPass2026")).thenReturn("$2a$10$newHashed");

        Result<Void> r = service.resetPassword(DOCTOR_ID, "NewPass2026");

        assertEquals(200, r.getCode());
        verify(doctorMapper).updatePassword(DOCTOR_ID, "$2a$10$newHashed", null, 0);
        // 版本递增 → 该医生已签发的 token 全部失效
        verify(authSessionManager).nextVersion(DOCTOR_ID);
    }

    @Test
    @DisplayName("重置弱密码被拒绝，不改动任何数据")
    void resetWithWeakPasswordShouldReject() {
        Result<Void> r = service.resetPassword(DOCTOR_ID, "abc");

        assertNotEquals(200, r.getCode());
        verify(doctorMapper, never()).updatePassword(anyInt(), anyString(), any(), anyInt());
    }

    @Test
    @DisplayName("重置不存在医生的密码返回明确错误")
    void resetForNonExistentDoctorShouldFail() {
        when(doctorMapper.getById(9999)).thenReturn(null);

        Result<Void> r = service.resetPassword(9999, "NewPass2026");

        assertNotEquals(200, r.getCode());
        assertTrue(r.getMsg().contains("不存在"), "实际: " + r.getMsg());
    }
}
