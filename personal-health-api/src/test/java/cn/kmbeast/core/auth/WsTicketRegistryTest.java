package cn.kmbeast.core.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * S6：WebSocket 一次性票据的不变式。
 *
 * <p>锁死四条：① 票据换出的身份必须是签发者本人；② 同一枚票据最多消费一次（重放拒绝）；
 * ③ 过期票据拒绝；④ 伪造/空票据拒绝。
 * 若把 {@link WsTicketRegistry#consume} 的 {@code remove} 改成 {@code get}（不删），
 * 「重放拒绝」用例必须真红 —— 这是本测试的反向验证点。
 */
@DisplayName("WebSocket 一次性短期票据")
class WsTicketRegistryTest {

    private WsTicketRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new WsTicketRegistry();
    }

    @Test
    @DisplayName("签发后消费成功，身份是签发者本人而不是客户端自报值")
    void issuedTicketRevealsBoundIdentity() {
        String ticket = registry.issue(7, 2);

        Optional<WsTicketRegistry.Identity> identity = registry.consume(ticket);

        assertTrue(identity.isPresent());
        assertEquals(7, identity.get().userId());
        assertEquals(2, identity.get().roleId());
        assertEquals(0, registry.size(), "票据消费后必须出表，不能留在内存里被再次使用");
    }

    @Test
    @DisplayName("重放：同一枚票据第二次消费必须被拒（remove 而非 get）")
    void replayIsRejected() {
        String ticket = registry.issue(7, 2);
        assertTrue(registry.consume(ticket).isPresent());

        assertFalse(registry.consume(ticket).isPresent(),
                "🔴 票据可重复消费 = 拿到一次握手凭证就能无限期冒充该用户建连");
    }

    @Test
    @DisplayName("过期票据拒绝，且不会残留在表里")
    void expiredTicketIsRejected() {
        String ticket = registry.issue(7, 2, Duration.ofMillis(-1));

        assertFalse(registry.consume(ticket).isPresent());
        assertEquals(0, registry.size());
    }

    @Test
    @DisplayName("空票据 / 伪造票据拒绝")
    void blankAndForgedTicketsAreRejected() {
        registry.issue(7, 2);
        assertFalse(registry.consume(null).isPresent());
        assertFalse(registry.consume("   ").isPresent());
        assertFalse(registry.consume("forged-ticket-value").isPresent());
    }

    @Test
    @DisplayName("不同用户的票据互不串号，且票据本身不可预测")
    void ticketsAreBoundAndUnpredictable() {
        String a = registry.issue(7, 2);
        String b = registry.issue(8, 2);
        String c = registry.issue(7, 2);

        assertNotEquals(a, b);
        assertNotEquals(a, c, "两次签发必须是不同的随机值");
        assertTrue(a.length() >= 43, "32 字节随机数 Base64URL 编码应至少 43 字符（256 位熵）");
        assertEquals(8, registry.consume(b).orElseThrow().userId());
        assertEquals(7, registry.consume(a).orElseThrow().userId());
        assertEquals(7, registry.consume(c).orElseThrow().userId());
    }
}
