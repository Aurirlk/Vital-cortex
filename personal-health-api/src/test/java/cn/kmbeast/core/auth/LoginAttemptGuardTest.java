package cn.kmbeast.core.auth;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 登录防撞库组件测试 —— 2026-10-10 新增。
 *
 * <p>这个组件的契约比普通业务代码更要紧，原因有二：
 * <ol>
 *   <li>它<b>判错方向</b>的两个后果都是事故：放太松 = 撞库防护形同虚设；
 *       判太严 = 把正常用户挡在登录页外（而且这是登录入口，用户连报错的渠道都没有）。</li>
 *   <li>它的 IP 维度如果无条件相信 {@code X-Forwarded-For}，攻击者直连 21090
 *       伪造一个头就绕过了 —— 这类「看起来有防护、实际可绕过」的假防护最难被发现。</li>
 * </ol>
 * 所以下面的用例同时钉住四件事：阈值行为、TTL 语义、Redis 不可用时的 fail-open，
 * 以及 <b>IP 维度默认关闭</b>（后端在 nginx 之后时，按容器 IP 封禁等于把
 * 「一个人作恶」惩罚成「所有人受罚」，所以它必须由 trusted-proxy 显式开启）。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("登录防撞库（失败计数与临时封禁）")
class LoginAttemptGuardTest {

    private static final String BLOCK_ACCT = "auth:login:block:acct:admin";
    private static final String FAIL_ACCT = "auth:login:fail:acct:admin";
    private static final String FAIL_IP = "auth:login:fail:ip:10.0.0.9";
    private static final String BLOCK_IP = "auth:login:block:ip:10.0.0.9";
    private static final Duration LOCK = Duration.ofMinutes(15);
    private static final Duration WINDOW = Duration.ofMinutes(15);

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOps;

    @InjectMocks
    private LoginAttemptGuard guard;

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    /** 让 currentClientIp() 拿得到一个固定来源地址 */
    private void withClientIp(String ip) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(ip);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    private void withXForwardedFor(String ip, String xff) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(ip);
        request.addHeader("X-Forwarded-For", xff);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    // ==================== 封禁检查 ====================

    @Test
    @DisplayName("未被封禁时放行（返回 null）")
    void shouldPassWhenNotBlocked() {
        when(stringRedisTemplate.hasKey(BLOCK_ACCT)).thenReturn(false);

        assertNull(guard.checkBlocked("admin"));
    }

    @Test
    @DisplayName("账号处于封禁期：拒绝，并告知可以稍后重试")
    void shouldRejectBlockedAccount() {
        when(stringRedisTemplate.hasKey(BLOCK_ACCT)).thenReturn(true);

        String msg = guard.checkBlocked("admin");

        assertNotNull(msg, "账号被封禁时必须拒绝，否则计数毫无意义");
        assertTrue(msg.contains("15"), "提示里带上等待时长，否则用户会不断重试");
    }

    @Test
    @DisplayName("来源 IP 处于封禁期：换一个新账号也照样拒绝（跨账号撞库要能挡住）")
    void shouldRejectBlockedIp() {
        // 2026-10-10：IP 维度改为「仅在 trusted-proxy=true 时启用」后才可用，
        // 先把这个开关打开再验行为（为什么默认关闭见 shouldNotUseIpDimensionWhenProxyNotTrusted）。
        ReflectionTestUtils.setField(guard, "trustedProxy", true);
        withClientIp("10.0.0.9");
        // 注意：随机换账号（这里是 someone-else）时，账号维度的键必须按<b>实际账号</b>桩，
        // 否则 Mockito 严格模式会抛 PotentialStubbingProblem，而它会被守卫的
        // catch(Exception) 当作 Redis 故障吃掉 —— 测试就变成了 fail-open 的假绿。
        when(stringRedisTemplate.hasKey("auth:login:block:acct:someone-else")).thenReturn(false);
        when(stringRedisTemplate.hasKey(BLOCK_IP)).thenReturn(true);

        assertNotNull(guard.checkBlocked("someone-else"),
                "IP 维度存在的意义就是拦住「换账号继续试」的行为");
    }

    @Test
    @DisplayName("账号归一化：Admin / admin / 带空格 共用一份计数，否则换个大小写就绕过")
    void shouldNormalizeAccountKey() {
        when(stringRedisTemplate.hasKey(BLOCK_ACCT)).thenReturn(false);

        assertNull(guard.checkBlocked("  ADMIN "));
        verify(stringRedisTemplate).hasKey(BLOCK_ACCT);
    }

    // ==================== 客户端 IP 的取法 ====================

    @Test
    @DisplayName("trusted-proxy=false（默认）：忽略 X-Forwarded-For，免得直连伪造就绕过 IP 维度")
    void shouldIgnoreXffWhenProxyNotTrusted() {
        withXForwardedFor("10.0.0.9", "1.2.3.4");

        assertEquals("10.0.0.9", guard.currentClientIp(),
                "21090 对外发布时，谁都能伪造 XFF；默认必须只信 TCP 对端地址");
    }

    @Test
    @DisplayName("trusted-proxy=true：取最右一项（客户端塞的前缀在左边，不可信）")
    void shouldTakeRightmostXffWhenProxyTrusted() {
        ReflectionTestUtils.setField(guard, "trustedProxy", true);
        // nginx 用 $proxy_add_x_forwarded_for：客户端可自带"1.2.3.4"在前，nginx 追加真实对端在后
        withXForwardedFor("172.18.0.5", "1.2.3.4, 203.0.113.7");

        assertEquals("203.0.113.7", guard.currentClientIp());
    }

    @Test
    @DisplayName("无请求上下文（定时任务/单测）返回 null，不得抛异常")
    void shouldReturnNullWithoutRequestContext() {
        assertNull(new LoginAttemptGuard().currentClientIp());
    }

    // ==================== 失败计数与阈值 ====================

    @Test
    @DisplayName("第一次失败挂上统计窗口 TTL，避免计数永久累积")
    void firstFailureShouldSetWindowTtl() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment(FAIL_ACCT)).thenReturn(1L);

        guard.recordFailure("admin");

        verify(stringRedisTemplate).expire(FAIL_ACCT, WINDOW);
        verify(valueOps, never()).setIfAbsent(anyString(), anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("后续失败不续期：TTL 挂在首次失败上，否则攻击者可让计数永不归零")
    void laterFailuresShouldNotRefreshWindow() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment(FAIL_ACCT)).thenReturn(3L);

        guard.recordFailure("admin");

        verify(stringRedisTemplate, never()).expire(anyString(), any(Duration.class));
        verify(valueOps, never()).setIfAbsent(anyString(), anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("账号失败达到阈值：写入带封禁时长的封禁键")
    void shouldBlockAccountAtThreshold() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment(FAIL_ACCT)).thenReturn(5L);   // 默认阈值 5
        when(valueOps.setIfAbsent(BLOCK_ACCT, "1", LOCK)).thenReturn(true);

        guard.recordFailure("admin");

        verify(valueOps).setIfAbsent(BLOCK_ACCT, "1", LOCK);
    }

    @Test
    @DisplayName("下一轮失败不再重复写封禁键（setIfAbsent 语义），避免封禁期刷爆日志")
    void shouldNotRewriteBlockKeyWhileLocked() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment(FAIL_ACCT)).thenReturn(6L);
        when(valueOps.setIfAbsent(BLOCK_ACCT, "1", LOCK)).thenReturn(false); // 已存在

        guard.recordFailure("admin");

        verify(valueOps).setIfAbsent(BLOCK_ACCT, "1", LOCK);
        // 返回值 false 表示封禁键已存在，实现里不再打 WARN（日志去重）
        verifyNoMoreInteractions(valueOps);
    }

    @Test
    @DisplayName("IP 维度失败达到阈值：写入 IP 封禁键（跨账号的撞库）")
    void shouldBlockIpAtThreshold() {
        ReflectionTestUtils.setField(guard, "trustedProxy", true);
        withClientIp("10.0.0.9");
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment(FAIL_ACCT)).thenReturn(1L);   // 单账号还没到阈值
        when(valueOps.increment(FAIL_IP)).thenReturn(30L);    // 默认 IP 阈值 30
        when(valueOps.setIfAbsent(BLOCK_IP, "1", LOCK)).thenReturn(true);

        guard.recordFailure("admin");

        verify(valueOps).setIfAbsent(BLOCK_IP, "1", LOCK);
        verify(valueOps, never()).setIfAbsent(eq(BLOCK_ACCT), anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("登录成功清空账号与 IP 的失败计数，但不动封禁键")
    void shouldClearCountersOnSuccess() {
        ReflectionTestUtils.setField(guard, "trustedProxy", true);
        withClientIp("10.0.0.9");

        guard.clearOnSuccess("admin");

        verify(stringRedisTemplate).delete(FAIL_ACCT);
        verify(stringRedisTemplate).delete(FAIL_IP);
        verify(stringRedisTemplate, never()).delete(BLOCK_ACCT);
    }

    @Test
    @DisplayName("IP 维度已启用但无请求上下文：不触碰 IP 键（定时任务场景不得 NPE）")
    void shouldSkipIpDimensionWithoutRequestContext() {
        // 即使开关打开，没有请求上下文也取不到 IP —— 关键是不得因此 NPE。
        ReflectionTestUtils.setField(guard, "trustedProxy", true);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment(FAIL_ACCT)).thenReturn(1L);

        assertDoesNotThrow(() -> guard.recordFailure("admin"));

        verify(stringRedisTemplate, never()).expire(argThat(k -> k.contains(":ip:")), any(Duration.class));
    }

    @Test
    @DisplayName("默认（trusted-proxy=false）完全不用 IP 维度：否则 nginx 后按容器 IP 封禁 = 全站连坐")
    void shouldNotUseIpDimensionWhenProxyNotTrusted() {
        // 这是本组件最重要的一条安全默认值。后端在 nginx 之后时，所有请求的
        // remoteAddr 都是 nginx 容器 IP；若照它计数，「一个人失败 30 次」
        // 会把这个 IP 封掉，效果是<b>全站所有用户</b> 15 分钟登不上 ——
        // 防护措施本身变成了攻击者的一键 DoS。
        withClientIp("10.0.0.9");
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment(FAIL_ACCT)).thenReturn(1L);

        guard.recordFailure("admin");

        // 只允许按账号记一次；IP 维度一次都不许碰
        verify(valueOps).increment(FAIL_ACCT);
        verify(valueOps, never()).increment(argThat(k -> k.contains(":ip:")));
        verify(valueOps, never()).setIfAbsent(argThat(k -> k.contains(":ip:")), anyString(), any(Duration.class));
    }

    // ==================== 降级：Redis 不可用 ====================

    @Test
    @DisplayName("Redis 异常时 fail-open：放行登录，不把所有人锁在门外")
    void shouldFailOpenWhenRedisDown() {
        when(stringRedisTemplate.hasKey(BLOCK_ACCT)).thenThrow(new RuntimeException("connection refused"));

        assertNull(guard.checkBlocked("admin"),
                "限流组件挂掉时把全体用户锁在登录页外，比暂时失去限流更糟");
    }

    @Test
    @DisplayName("Redis 异常时记录失败不抛异常（不得让登录接口 500）")
    void shouldSwallowRedisErrorOnRecord() {
        when(stringRedisTemplate.opsForValue()).thenThrow(new RuntimeException("boom"));

        assertDoesNotThrow(() -> guard.recordFailure("admin"));
    }

    // ==================== 日志打码 ====================

    @Test
    @DisplayName("账号进日志前打码：登录账号可能是手机号，明文等于往 Loki 写用户清单")
    void shouldMaskAccountInLogs() {
        assertEquals("(empty)", LoginAttemptGuard.mask(null));
        assertEquals("(empty)", LoginAttemptGuard.mask(""));
        assertEquals("***", LoginAttemptGuard.mask("ab"));
        assertEquals("a***c", LoginAttemptGuard.mask("abc"));
        assertEquals("a***n", LoginAttemptGuard.mask("admin"));
        assertEquals("ab****89", LoginAttemptGuard.mask("abc12389"));
        assertEquals("13****00", LoginAttemptGuard.mask("13800138000"));
    }
}
