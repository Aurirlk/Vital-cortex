package cn.kmbeast.pojo.dto.request;

import lombok.Data;

/**
 * 医生登录请求
 *
 * <p>2026-10-03 医生与 user 解耦后新增。字段命名与用户登录保持一致的语义，
 * 但走独立的 {@code /doctor/login} 端点与独立的账号体系。
 */
@Data
public class DoctorLoginDTO {

    /** 医生登录账号，如 doctor3001 */
    private String username;

    /** 登录密码（明文，服务端用 BCrypt 比对） */
    private String password;

    /**
     * 首次登录时提交的新密码。
     * <p>当账号处于 {@code needInitPassword=1} 状态时必填。
     */
    private String newPassword;
}
