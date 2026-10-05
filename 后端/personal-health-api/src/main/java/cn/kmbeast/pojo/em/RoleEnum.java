package cn.kmbeast.pojo.em;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 用户角色枚举
 *
 * <p>2026-10-03 新增 {@link #DOCTOR}：医生与 user 表解耦后，医生拥有独立账号体系
 * （{@code hospital_doctor.username} + BCrypt 密码），登录后 JWT 中携带 {@code role=3}。
 *
 * <p><b>注意</b>：本枚举是所有 {@code @Protector(role = "...")} 鉴权的唯一依据。
 * 新增角色时务必同步更新 {@code ProtectorAspect} 的放行逻辑与前端路由守卫，
 * 否则该角色的所有受保护端点都会因 {@code RoleEnum.ROLE()} 返回 null 而被拒。
 */
@Getter
@AllArgsConstructor
public enum RoleEnum {

    /** 管理员（user 表 user_role=1） */
    ADMIN(1, "管理员"),

    /** 普通用户（user 表 user_role=2） */
    USER(2, "用户"),

    /**
     * 医生（独立身份，不占用 user 表）。
     * <p>医生登录入口为 {@code POST /doctor/login}，登录后仅可访问医生端接口。
     */
    DOCTOR(3, "医生");

    /**
     * 角色编码
     */
    private final Integer role;

    /**
     * 角色名（与 {@code @Protector(role = "...")} 中声明的中文名严格一致）
     */
    private final String name;

    /**
     * 由角色编码获取角色名
     *
     * @param role 角色编码
     * @return String 角色名；编码不存在时返回 null（调用方需按「拒绝」处理）
     */
    public static String ROLE(Integer role) {
        if (role == null) {
            return null;
        }
        for (RoleEnum value : RoleEnum.values()) {
            if (value.getRole().equals(role)) {
                return value.name;
            }
        }
        return null;
    }

    /**
     * 判断角色编码是否存在
     *
     * @param role 角色编码
     */
    public static boolean exists(Integer role) {
        return ROLE(role) != null;
    }
}
