package cn.kmbeast.service;

import cn.kmbeast.pojo.api.Result;
import cn.kmbeast.pojo.dto.request.DoctorLoginDTO;

/**
 * 医生认证服务（2026-10-03 医生与 user 解耦）
 *
 * <p><b>为什么独立于 {@link UserService}</b>：医生是<b>执业身份</b>而非登录账号。
 * 迁移前 {@code hospital_doctor.user_id} 关联 {@code user} 表，导致改一次医生资料要同步两张表、
 * {@code user.role} 语义混乱。现在医生拥有自己的账号体系（{@code username} + BCrypt 密码），
 * 由管理员统一管理；<b>医生登录后仅可进入医生端，不可访问用户端与管理端</b>。
 *
 * <p><b>角色约定</b>：医生在 JWT 中使用 {@code role=3}（user 表的 1=管理员、2=普通用户），
 * 便于 {@code @Protector(role="医生")} 做端点级鉴权。
 */
public interface DoctorAuthService {

    /**
     * 医生登录
     *
     * <p>流程：按 username 查医生 → 校验启用状态 → 校验密码 → 若为首次登录则返回
     * {@code needInitPassword} 标记（由前端引导设置初始密码）→ 递增会话版本并签发 JWT。
     *
     * @param dto 登录请求（username + password）
     * @return 登录结果；成功时 data 含 token / doctorId / name / needInitPassword
     */
    Result<Object> login(DoctorLoginDTO dto);

    /**
     * 设置/重置医生密码（需管理员权限）
     *
     * <p>使用 BCrypt 加密，保存后置 {@code needInitPassword=0}，强制医生下次用新密码登录。
     *
     * @param doctorId 医生ID
     * @param password 新密码（明文，服务端加密后存储）
     * @return 操作结果
     */
    Result<Void> resetPassword(Integer doctorId, String password);
}
