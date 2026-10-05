package cn.kmbeast.pojo.em;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 角色枚举单元测试（2026-10-04 构建）
 *
 * <p>这是「医生与 user 解耦」改造的<b>鉴权根基</b>：
 * {@code ProtectorAspect} 通过 {@code RoleEnum.ROLE(roleId)} 与
 * {@code @Protector(role = "...")} 声明的中文名做严格比对来决定放行与否。
 *
 * <p>2026-10-03 之前该枚举只有 ADMIN/USER，因此医生（role=3）登录后，
 * 所有 {@code @Protector(role = "医生")} 端点都会因比对失败而被拒 ——
 * 且不产生任何报错。本测试锁定这一行为，防止将来有人误删 DOCTOR。
 */
@DisplayName("角色枚举（鉴权根基）")
class RoleEnumTest {

    @Test
    @DisplayName("角色编码到中文名的映射必须与 @Protector 声明一致")
    void roleCodeMappingMustMatchProtectorDeclaration() {
        assertEquals("管理员", RoleEnum.ROLE(1), "user 表 user_role=1 对应管理员");
        assertEquals("用户", RoleEnum.ROLE(2), "user 表 user_role=2 对应普通用户");
        assertEquals("医生", RoleEnum.ROLE(3), "医生是独立身份，JWT role=3");
    }

    @Test
    @DisplayName("医生角色必须存在：否则医生端所有受保护端点都会被拒")
    void doctorRoleMustExist() {
        assertNotNull(RoleEnum.DOCTOR);
        assertEquals(3, RoleEnum.DOCTOR.getRole());
        assertEquals("医生", RoleEnum.DOCTOR.getName());
        assertTrue(RoleEnum.exists(3),
                "RoleEnum.exists(3) 必须为 true，否则 RoleEnum.ROLE(3) 返回 null 会导致医生鉴权失败");
    }

    @Test
    @DisplayName("未知角色编码返回 null（调用方需按拒绝处理，不得放行）")
    void unknownRoleShouldReturnNull() {
        assertNull(RoleEnum.ROLE(0));
        assertNull(RoleEnum.ROLE(99));
        assertNull(RoleEnum.ROLE(-1));
        assertNull(RoleEnum.ROLE(null));
        assertFalse(RoleEnum.exists(0));
        assertFalse(RoleEnum.exists(null));
    }

    @Test
    @DisplayName("三端角色互不重叠：医生不能用管理员或用户身份访问")
    void rolesMustNotOverlap() {
        assertNotEquals(RoleEnum.ROLE(1), RoleEnum.ROLE(2));
        assertNotEquals(RoleEnum.ROLE(1), RoleEnum.ROLE(3));
        assertNotEquals(RoleEnum.ROLE(2), RoleEnum.ROLE(3));
    }

    @Test
    @DisplayName("角色名全局唯一")
    void roleNamesMustBeUnique() {
        long distinct = java.util.Arrays.stream(RoleEnum.values())
                .map(RoleEnum::getName).distinct().count();
        assertEquals(RoleEnum.values().length, distinct, "角色名重复会导致 @Protector 鉴权互相放行");
    }
}
