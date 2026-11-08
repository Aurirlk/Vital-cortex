package cn.kmbeast.support;

import cn.kmbeast.context.LocalThreadHolder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 身份清场扩展的「守护者的守护」（2026-11-08 新增）。
 *
 * <p>它不测业务，只测一件事：{@link IdentityCleanupExtension} 是否真的被 JUnit 平台
 * 自动注册、并保证<b>每个测试都从干净状态开始</b>。做法是故意制造身份泄漏：
 * <ol>
 *   <li>{@code @BeforeAll} 把身份设成 3001（医生），模拟「别的类没清干净」；</li>
 *   <li>第 1 个测试断言自己读到 null，并在结尾<b>故意再留一个身份</b> 7；</li>
 *   <li>第 2 个测试断言自己仍读到 null —— 证明上一条留下的身份被清掉了。</li>
 * </ol>
 *
 * <p>判据：任意一步读到残留身份即<b>红</b>，说明整套测试又回到了「靠执行顺序碰运气」，
 * 而那种状态下越权类断言可能因为「当前用户恰好是管理员」而假通过。
 */
@DisplayName("身份清场扩展（防顺序依赖）")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class IdentityCleanupGuardTest {

    @BeforeAll
    static void leakIdentityOnPurpose() {
        LocalThreadHolder.setUserId(3001, 3);
    }

    @Test
    @Order(1)
    @DisplayName("测试开始前必须已清空 @BeforeAll 留下的身份")
    void beforeEachMustClearIdentityLeakedBeforeTests() {
        assertNull(LocalThreadHolder.getUserId(),
                "测试开始前仍读到 3001 → IdentityCleanupExtension 未生效，测试已重新依赖执行顺序");
        assertNull(LocalThreadHolder.getRoleId(),
                "角色残留会让 isAdmin() 误判，进而使越权断言假通过");

        // 故意留一个身份给下一个测试，验证它不会泄漏过去
        LocalThreadHolder.setUserId(7, 2);
        assertEquals(7, LocalThreadHolder.getUserId(), "前置设置本身应当生效");
    }

    @Test
    @Order(2)
    @DisplayName("上一个测试留下的身份不得泄漏到本测试")
    void previousTestIdentityMustNotLeak() {
        assertNull(LocalThreadHolder.getUserId(),
                "上一个测试结尾设置的 7 仍然可见 → 身份跨测试泄漏，顺序依赖回归");
        assertNull(LocalThreadHolder.getRoleId(), "角色同样不得泄漏");
    }
}
