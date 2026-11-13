package cn.kmbeast.Interceptor;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Actuator 来源限制测试 —— 2026-10-10 新增。
 *
 * <p>这条防护的形态很特殊：它默认放行的恰恰是「看起来最像自己人」的地址，
 * 所以任何写反的分支都不会在常规测试里暴露 —— 只有公网地址必须被拒这一条
 * 才是它的全部价值。因此下面把「必须放行」与「必须拒绝」两侧都钉死，
 * 并专门验证它<b>不看 X-Forwarded-For</b>（否则伪造一个头就绕过了）。
 */
@DisplayName("Actuator 访问来源限制")
class ActuatorAccessInterceptorTest {

    private final ActuatorAccessInterceptor interceptor = new ActuatorAccessInterceptor();

    private MockHttpServletRequest requestFrom(String ip) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/prometheus");
        request.setRemoteAddr(ip);
        return request;
    }

    private boolean allowed(String ip) {
        return interceptor.preHandle(requestFrom(ip), new MockHttpServletResponse(), new Object());
    }

    // ==================== 必须放行：回环与私有网段 ====================

    @Test
    @DisplayName("容器内的健康检查（127.0.0.1）必须放行，否则容器会被判为 unhealthy")
    void shouldAllowLoopback() {
        assertTrue(allowed("127.0.0.1"));
        assertTrue(allowed("127.0.0.5"), "127/8 整段都是回环");
    }

    @Test
    @DisplayName("IPv6 回环 ::1 放行")
    void shouldAllowIpv6Loopback() {
        assertTrue(allowed("::1"));
    }

    @Test
    @DisplayName("同容器网络的 Prometheus / nginx（172.16/12）必须放行")
    void shouldAllowDockerNetwork() {
        assertTrue(allowed("172.18.0.5"), "docker 默认网桥就是 172.17/172.18 这一段");
    }

    @Test
    @DisplayName("其余私有网段（10/8、192.168/16）放行")
    void shouldAllowOtherPrivateRanges() {
        assertTrue(allowed("10.0.0.9"));
        assertTrue(allowed("192.168.1.20"));
    }

    @Test
    @DisplayName("链路本地地址（169.254/16、fe80::/10）放行；带 zone id 的也要认")
    void shouldAllowLinkLocal() {
        assertTrue(allowed("169.254.10.1"));
        assertTrue(allowed("fe80::1"));
        assertTrue(allowed("fe80::1%eth0"), "同一地址带 zone id 必须仍被识别");
    }

    @Test
    @DisplayName("IPv4-mapped IPv6（::ffff:127.0.0.1）按内嵌的 IPv4 判断")
    void shouldHandleIpv4MappedIpv6() {
        assertTrue(allowed("::ffff:127.0.0.1"));
        assertFalse(allowed("::ffff:203.0.113.7"), "映射形式不能成为公网地址的绕过通道");
    }

    // ==================== 必须拒绝：公网来源 ====================

    @Test
    @DisplayName("公网直连 21090 读指标：拒绝，且返回 404 而非 403 以不确认路径存在")
    void shouldRejectPublicAddress() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean pass = interceptor.preHandle(requestFrom("203.0.113.7"), response, new Object());

        assertFalse(pass, "公网可达的 /actuator/prometheus 等于把 JVM 与全量 URI 指标挂到公网上");
        assertEquals(404, response.getStatus(), "403 会告诉扫描器「这里确实有东西」");
    }

    @Test
    @DisplayName("伪造 X-Forwarded-For 无效：只看 TCP 对端地址")
    void shouldIgnoreForgedXForwardedFor() {
        MockHttpServletRequest request = requestFrom("203.0.113.7");
        request.addHeader("X-Forwarded-For", "127.0.0.1");
        request.addHeader("X-Real-IP", "127.0.0.1");

        assertFalse(interceptor.preHandle(request, new MockHttpServletResponse(), new Object()),
                "拿可伪造的头做访问控制等于没做防护");
    }

    @Test
    @DisplayName("非 IP 字面量（主机名）直接拒绝，且不得触发 DNS 解析")
    void shouldRejectNonLiteralWithoutDns() {
        // 若实现里直接把字符串丢给 InetAddress.getByName，这里会去解析
        // attacker.example.com —— 既是一个阻塞点，也把「我们访问过这个域名」泄出去。
        assertFalse(allowed("attacker.example.com"));
        assertFalse(allowed("localhost"), "不是字面量就拒绝，不做名字解析");
    }

    @Test
    @DisplayName("非法/空地址一律拒绝（fail-closed）")
    void shouldRejectInvalidOrEmpty() {
        assertFalse(allowed(null));
        assertFalse(allowed(""));
        assertFalse(allowed("   "));
        assertFalse(allowed("999.1.1.1"), "越界八位组不是合法 IPv4 字面量");
        assertFalse(allowed("10.0.0.1:8080"), "带端口的是 host:port，不是地址字面量");
    }

    // ==================== 开关 ====================

    @Test
    @DisplayName("allow-private-only=false 时不再限制（供外部监控端抓取，需自行用鉴权/防火墙兜底）")
    void shouldAllowAnyoneWhenGuardDisabled() {
        ReflectionTestUtils.setField(interceptor, "allowPrivateOnly", false);

        assertTrue(allowed("203.0.113.7"));
    }

    // ==================== 地址判定本身 ====================

    @Test
    @DisplayName("isLocalOrPrivate 的边界：私有/公网/畸形输入")
    void shouldClassifyAddresses() {
        assertTrue(ActuatorAccessInterceptor.isLocalOrPrivate("172.16.0.1"));
        assertTrue(ActuatorAccessInterceptor.isLocalOrPrivate("172.31.255.254"));
        assertFalse(ActuatorAccessInterceptor.isLocalOrPrivate("172.32.0.1"),
                "172.16/12 到 172.31 为止，172.32 已经是公网地址");
        assertFalse(ActuatorAccessInterceptor.isLocalOrPrivate("11.0.0.1"));
        assertFalse(ActuatorAccessInterceptor.isLocalOrPrivate("192.169.1.1"));
    }
}
