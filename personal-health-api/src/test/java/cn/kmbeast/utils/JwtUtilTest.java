package cn.kmbeast.utils;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JWT 工具单元测试（2026-10-04 构建）
 *
 * <p>覆盖 SEC-01 整改引入的启动期强校验：
 * <ul>
 *   <li>密钥为空 → 拒绝启动</li>
 *   <li>密钥命中已泄露黑名单 → 拒绝启动</li>
 *   <li>密钥不足 32 字节 → 拒绝启动（HS256 硬性要求）</li>
 *   <li>合规密钥 → 正常签发，且 claim 中带 id/role/ver</li>
 *   <li>篡改/伪造 token → 解析返回 null 而非抛异常</li>
 *   <li>会话版本 ver 参与签名，改 ver 即失效（无需黑名单即可踢人）</li>
 * </ul>
 *
 * <p>{@code init()} 由 {@code @PostConstruct} 触发，这里直接手动调用，
 * 因为不启动 Spring 容器就能验证「拒绝启动」这一关键行为。
 */
@DisplayName("JWT 签发与校验")
class JwtUtilTest {

    private static final String VALID_SECRET = "unit-test-only-secret-key-0123456789ABCDEF";

    /** 绕过 @Value 注入，直接塞私钥字段 */
    private JwtUtil newJwtUtil(String secret, Long expirationMs) throws Exception {
        JwtUtil util = new JwtUtil();
        set(util, "privateKey", secret);
        set(util, "expiration", expirationMs == null ? 604800000L : expirationMs);
        return util;
    }

    private void set(Object target, String field, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(field);
        f.setAccessible(true);
        f.set(target, value);
    }

    // ---------------------------------------------------------------- 启动期强校验

    @Test
    @DisplayName("密钥为空：拒绝启动，且提示如何生成")
    void emptySecretShouldFailFast() throws Exception {
        JwtUtil util = newJwtUtil("   ", null);
        IllegalStateException e = assertThrows(IllegalStateException.class, util::init);
        assertTrue(e.getMessage().contains("JWT_SECRET"),
                "错误信息应指明配置项，实际: " + e.getMessage());
    }

    @Test
    @DisplayName("密钥为 null：拒绝启动")
    void nullSecretShouldFailFast() throws Exception {
        JwtUtil util = newJwtUtil(null, null);
        assertThrows(IllegalStateException.class, util::init);
    }

    @Test
    @DisplayName("已泄露的默认密钥：拒绝启动")
    void leakedSecretShouldFailFast() throws Exception {
        // 黑名单是「整串精确匹配」（LEAKED_SECRETS.contains(trimmed)），
        // 且该检查排在长度检查之前 —— 所以原始短值也能命中，不会被长度规则抢先。
        for (String leaked : new String[]{"changeme", "secret", "your-secret-key"}) {
            JwtUtil util = newJwtUtil(leaked, null);
            IllegalStateException e = assertThrows(IllegalStateException.class, util::init,
                    "泄露密钥 " + leaked + " 必须被拒绝");
            assertTrue(e.getMessage().contains("泄露"),
                    "应提示密钥泄露，实际: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("泄露密钥的大小写/空白变体也要拦住（trim 后精确比对）")
    void leakedSecretWithSurroundingSpacesShouldFailFast() throws Exception {
        JwtUtil util = newJwtUtil("  changeme  ", null);
        assertThrows(IllegalStateException.class, util::init);
    }

    @Test
    @DisplayName("与泄露密钥无关的长密钥应正常通过（黑名单不做前缀/包含匹配）")
    void unrelatedLongSecretShouldPass() throws Exception {
        // 说明白名单边界：只要不是那三个整串，长度够就应放行，
        // 避免把「包含 changeme」这类正常密钥误杀。
        JwtUtil util = newJwtUtil("my-changeme-prefix-but-otherwise-unique-key-9f2a", null);
        assertDoesNotThrow(util::init);
    }

    @Test
    @DisplayName("密钥不足 32 字节：拒绝启动并说明 HS256 要求")
    void shortSecretShouldFailFast() throws Exception {
        JwtUtil util = newJwtUtil("only-16-bytes-long!!", null);
        IllegalStateException e = assertThrows(IllegalStateException.class, util::init);
        assertTrue(e.getMessage().contains("32"),
                "应说明 32 字节要求，实际: " + e.getMessage());
    }

    @Test
    @DisplayName("合规密钥：init 通过")
    void validSecretShouldInitialize() throws Exception {
        JwtUtil util = newJwtUtil(VALID_SECRET, null);
        assertDoesNotThrow(util::init);
        // init 后应可拿到单例（WebSocket 端点依赖它）
        assertSame(util, JwtUtil.getInstance());
    }

    // ---------------------------------------------------------------- 签发与解析

    @Test
    @DisplayName("签发的 token 携带 id / role / ver 三个声明")
    void tokenShouldCarryIdRoleVer() throws Exception {
        JwtUtil util = newJwtUtil(VALID_SECRET, null);
        util.init();

        String token = util.toToken(3001, 3, 7);
        Claims c = util.fromToken(token);

        assertNotNull(c, "自己签发的 token 必须能解析");
        assertEquals(3001, c.get("id", Integer.class));
        assertEquals(3, c.get("role", Integer.class), "医生 role=3");
        assertEquals(7, c.get("ver", Integer.class));
    }

    @Test
    @DisplayName("两参数重载：ver 默认为 0")
    void twoArgOverloadShouldDefaultVerToZero() throws Exception {
        JwtUtil util = newJwtUtil(VALID_SECRET, null);
        util.init();

        Claims c = util.fromToken(util.toToken(1, 1));
        assertEquals(0, c.get("ver", Integer.class));
    }

    @Test
    @DisplayName("ver 传 null 时按 0 处理，不写 null 进 claim")
    void nullVerShouldBecomeZero() throws Exception {
        JwtUtil util = newJwtUtil(VALID_SECRET, null);
        util.init();

        Claims c = util.fromToken(util.toToken(1, 1, null));
        assertEquals(0, c.get("ver", Integer.class));
    }

    @Test
    @DisplayName("getUserId 便捷方法")
    void getUserIdShouldReadIdClaim() throws Exception {
        JwtUtil util = newJwtUtil(VALID_SECRET, null);
        util.init();

        String token = util.toToken(42, 2, 1);
        assertEquals(42, util.getUserId(token));
    }

    // ---------------------------------------------------------------- 非法 token

    @Test
    @DisplayName("非法 token 返回 null 而非抛异常（拦截器依赖此行为）")
    void malformedTokenShouldReturnNull() throws Exception {
        JwtUtil util = newJwtUtil(VALID_SECRET, null);
        util.init();

        assertNull(util.fromToken("not-a-jwt"));
        assertNull(util.fromToken("a.b.c"));
        assertNull(util.fromToken("   "));
        assertNull(util.fromToken(null));
    }

    @Test
    @DisplayName("用别的密钥签发的 token 无法被本实例解析（防伪造）")
    void tokenSignedByOtherKeyShouldFail() throws Exception {
        JwtUtil issuer = newJwtUtil(VALID_SECRET, null);
        issuer.init();
        String foreign = issuer.toToken(1, 1, 1);   // role=1 管理员

        JwtUtil other = newJwtUtil("a-completely-different-secret-key-9876543210", null);
        other.init();

        assertNull(other.fromToken(foreign),
                "攻击者自签的管理员 token 必须解析失败，否则可完全绕过鉴权");
    }

    @Test
    @DisplayName("篡改 payload 后签名校验失败")
    void tamperedPayloadShouldFail() throws Exception {
        JwtUtil util = newJwtUtil(VALID_SECRET, null);
        util.init();
        String token = util.toToken(1, 2, 1);
        // 改动签名段
        String[] parts = token.split("\\.");
        String tampered = parts[0] + "." + parts[1] + ".aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

        assertNull(util.fromToken(tampered));
    }

    // ---------------------------------------------------------------- 会话版本

    @Test
    @DisplayName("会话版本 ver 不同：token 本身仍可解析，版本比对交由 AuthSessionManager")
    void differentVerStillParses() throws Exception {
        JwtUtil util = newJwtUtil(VALID_SECRET, null);
        util.init();

        Claims c1 = util.fromToken(util.toToken(1, 1, 1));
        Claims c2 = util.fromToken(util.toToken(1, 1, 2));

        assertNotNull(c1);
        assertNotNull(c2);
        assertNotEquals(c1.get("ver", Integer.class), c2.get("ver", Integer.class),
                "ver 必须能被 JwtInterceptor 读出并与 Redis 中的当前版本比对");
    }

    @Test
    @DisplayName("过期 token 解析为 null")
    void expiredTokenShouldReturnNull() throws Exception {
        JwtUtil util = newJwtUtil(VALID_SECRET, -1000L);   // 已过期
        util.init();

        assertNull(util.fromToken(util.toToken(1, 1, 1)));
    }
}
