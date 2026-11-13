package cn.kmbeast.core.agent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 端词表本身的一致性 —— 尤其是「账号角色 → 端」这张翻译表。
 *
 * <p>它值得单独锁住的原因很朴素：映射写反（2 与 3 互换）不会报错、不会告警、
 * 也不会让任何一条用例变红，只会让每一次运行的审计里端都是错的。
 * 出事时审计是最后能说话的东西，不能让它张冠李戴。</p>
 */
@DisplayName("端词表与角色翻译")
class AudienceTest {

    @Test
    @DisplayName("角色编码 → 端：1 管理端 / 2 用户端 / 3 医生端")
    void roleIdMapsToAudience() {
        assertEquals(Audience.ADMIN, Audience.fromRoleId(1));
        assertEquals(Audience.USER, Audience.fromRoleId(2));
        assertEquals(Audience.DOCTOR, Audience.fromRoleId(3));
    }

    @Test
    @DisplayName("未登录 / 未知角色 → null（端未知），不猜一个端")
    void unknownRoleYieldsNull() {
        assertNull(Audience.fromRoleId(null));
        assertNull(Audience.fromRoleId(0));
        assertNull(Audience.fromRoleId(99));
    }

    @Test
    @DisplayName("取值与 SQL 列注释、注册表里的端名一致（改字面量前先看这里）")
    void valuesMatchTheContract() {
        // agent_config.audience 的列注释：user 用户端 / doctor 医生端 / admin 管理端 / all 全部
        assertEquals("user", Audience.USER);
        assertEquals("doctor", Audience.DOCTOR);
        assertEquals("admin", Audience.ADMIN);
        assertEquals("all", Audience.ALL);
    }
}
