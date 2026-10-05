package cn.kmbeast.service.impl;

import cn.kmbeast.core.auth.AuthSessionManager;
import cn.kmbeast.mapper.HospitalDoctorMapper;
import cn.kmbeast.pojo.api.ApiResult;
import cn.kmbeast.pojo.api.Result;
import cn.kmbeast.pojo.dto.request.DoctorLoginDTO;
import cn.kmbeast.pojo.entity.HospitalDoctor;
import cn.kmbeast.service.DoctorAuthService;
import cn.kmbeast.utils.JwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

/**
 * 医生认证服务实现
 *
 * <p>2026-10-03 医生与 user 解耦后的独立登录体系。
 *
 * <p><b>安全要点</b>：
 * <ul>
 *   <li>密码统一 BCrypt 加密，口径与 user 表一致（复用 {@link PasswordConfig} 的 Bean）</li>
 *   <li>登录失败不区分「账号不存在」与「密码错误」，避免账号枚举</li>
 *   <li>医生在 JWT 中使用 {@code role=3}，与 user 的 1=管理员 / 2=普通用户 区分，
 *       便于 {@code @Protector(role="医生")} 做端点级鉴权</li>
 *   <li>返回体<b>绝不包含 password</b></li>
 * </ul>
 */
@Slf4j
@Service
public class DoctorAuthServiceImpl implements DoctorAuthService {

    /**
     * 医生在 JWT 中的角色标识。
     * <p>user 表约定 1=管理员、2=普通用户；医生不复用 user 表，用 3 作为独立身份标记。
     */
    public static final int ROLE_DOCTOR = 3;

    /** 医生端登录后的默认落地路由（前端据此跳转，不进用户端/管理端） */
    public static final String DOCTOR_HOME = "/doctor/workbench";

    /** 账号未初始化密码时的统一提示 */
    private static final String MSG_NEED_INIT = "首次登录请先设置初始密码";

    @Resource
    private HospitalDoctorMapper doctorMapper;

    @Resource
    private PasswordEncoder passwordEncoder;

    @Resource
    private JwtUtil jwtUtil;

    @Resource
    private AuthSessionManager authSessionManager;

    @Override
    public Result<Object> login(DoctorLoginDTO dto) {
        if (dto == null || dto.getUsername() == null || dto.getUsername().isBlank()) {
            return ApiResult.error("请输入医生账号");
        }

        HospitalDoctor doctor = doctorMapper.getByUsername(dto.getUsername().trim());
        if (doctor == null) {
            // 不提示「账号不存在」，避免账号枚举
            return ApiResult.error("账号或密码错误");
        }

        if (doctor.getStatus() == null || doctor.getStatus() != 1) {
            return ApiResult.error("该医生账号已停用，请联系管理员");
        }

        // 首次登录：password 为 NULL，要求设置初始密码
        boolean needInit = doctor.getNeedInitPassword() != null && doctor.getNeedInitPassword() == 1;
        if (needInit || doctor.getPassword() == null || doctor.getPassword().isEmpty()) {
            String newPassword = dto.getNewPassword();
            if (newPassword == null || newPassword.isBlank()) {
                Map<String, Object> data = new HashMap<>();
                data.put("needInitPassword", true);
                data.put("doctorId", doctor.getId());
                data.put("name", doctor.getName());
                data.put("message", MSG_NEED_INIT);
                return ApiResult.success(data);
            }
            String validate = validatePasswordStrength(newPassword);
            if (validate != null) {
                return ApiResult.error(validate);
            }
            doctorMapper.updatePassword(doctor.getId(),
                    passwordEncoder.encode(newPassword), null, 0);
            log.info("[DoctorAuth] 医生 {} ({}) 首次登录已设置初始密码",
                    doctor.getName(), doctor.getUsername());
        } else {
            // 常规登录：校验密码
            if (dto.getPassword() == null || !passwordEncoder.matches(dto.getPassword(), doctor.getPassword())) {
                return ApiResult.error("账号或密码错误");
            }
        }

        // 签发 token：role=DOCTOR；会话版本号沿用 AuthSessionManager（Redis 不可用时降级为 0）
        int ver = authSessionManager.nextVersion(doctor.getId());
        String token = jwtUtil.toToken(doctor.getId(), ROLE_DOCTOR, ver);

        Map<String, Object> data = new HashMap<>();
        data.put("token", token);
        data.put("doctorId", doctor.getId());
        data.put("name", doctor.getName());
        data.put("avatar", doctor.getAvatar());
        data.put("title", doctor.getTitle());
        data.put("titleLevel", doctor.getTitleLevel());
        data.put("departmentId", doctor.getDepartmentId());
        data.put("role", ROLE_DOCTOR);
        data.put("needInitPassword", false);
        data.put("home", DOCTOR_HOME);
        log.info("[DoctorAuth] 医生登录成功: id={}, username={}", doctor.getId(), doctor.getUsername());
        return ApiResult.success(data);
    }

    @Override
    public Result<Void> resetPassword(Integer doctorId, String password) {
        if (doctorId == null) {
            return ApiResult.error("缺少医生ID");
        }
        String validate = validatePasswordStrength(password);
        if (validate != null) {
            return ApiResult.error(validate);
        }
        HospitalDoctor doctor = doctorMapper.getById(doctorId);
        if (doctor == null) {
            return ApiResult.error("医生不存在");
        }
        // 重置后 needInitPassword=0：下次登录直接用新密码校验，无需再次初始化
        doctorMapper.updatePassword(doctorId, passwordEncoder.encode(password), null, 0);
        // 递增会话版本，使该医生已签发的 token 全部失效
        authSessionManager.nextVersion(doctorId);
        log.info("[DoctorAuth] 管理员重置医生 {} ({}) 的密码", doctorId, doctor.getUsername());
        return ApiResult.success("密码重置成功");
    }

    /**
     * 密码强度校验
     *
     * @return 校验失败原因；通过时返回 null
     */
    private String validatePasswordStrength(String password) {
        if (password == null || password.isEmpty()) {
            return "请输入新密码";
        }
        if (password.length() < 8) {
            return "密码长度至少 8 位";
        }
        if (password.length() > 64) {
            return "密码长度不能超过 64 位";
        }
        boolean hasLetter = false;
        boolean hasDigit = false;
        for (char c : password.toCharArray()) {
            if (Character.isLetter(c)) {
                hasLetter = true;
            } else if (Character.isDigit(c)) {
                hasDigit = true;
            }
        }
        if (!hasLetter || !hasDigit) {
            return "密码需同时包含字母和数字";
        }
        return null;
    }
}
