package cn.kmbeast.Interceptor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.net.InetAddress;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Actuator 访问来源限制拦截器 —— 2026-10-10 新增。
 *
 * <p><b>问题</b>：后端为 Prometheus 暴露了 {@code /actuator/health} 与
 * {@code /actuator/prometheus}，而 {@code /actuator/**} 在
 * {@link cn.kmbeast.config.InterceptorConfig} 里被排除在 JWT 之外（抓取端没有 token，
 * 这是必须的）。可 21090 端口是直接发布到宿主机所有网卡的，于是任何人访问
 * {@code http://<宿主机IP>:21090/api/personal-health/v1.0/actuator/prometheus}
 * 就能拿到 JVM、连接池、以及<b>带全部 URI 标签的 HTTP 指标</b>（等价于一份完整接口清单）。
 * nginx 那条 {@code location .../actuator/ { return 404; }} 只封住了 80 端口这一条路，
 * 绕开 nginx 直连 21090 依然可达 —— 也就是说那份防护是「看起来有、实际可绕过」的那种。
 *
 * <p><b>做法</b>：不依赖部署时记得改端口映射（那件事很容易被漏掉，且 compose 一改就被冲掉），
 * 而是在应用内按<b>来源地址</b>显式拦一道，默认只允许回环与私有网段：
 * <ul>
 *   <li>Prometheus、nginx 都在 compose 的 {@code backend-net} 里，源地址是 172.x 私有地址 → 放行；</li>
 *   <li>Dockerfile 的健康检查在容器内打 {@code localhost} → 回环 → 放行；</li>
 *   <li>从公网直连 21090 的请求 → 源地址是公网地址 → 拒绝。</li>
 * </ul>
 *
 * <p><b>为什么只看 remoteAddr、不看 X-Forwarded-For</b>：XFF 是客户端可伪造的头，
 * 拿它做访问控制等于没做（这与 {@code LoginAttemptGuard} 必须引入
 * {@code trusted-proxy} 开关是同一件事）。而 TCP 对端地址无法伪造，所以本拦截器
 * <b>不需要也不应该</b>关心任何代理信任配置：经 nginx 来的请求源地址是 nginx 容器 IP
 * （私有 → 放行），而 nginx 自己已经把那批路径 404 掉了，正好形成两层。
 *
 * <p><b>响应 404 而不是 403</b>：403 等于确认「这个路径存在但你没权限」，
 * 等于给扫描器指路；对未授权的来源直接装作没有这个路径（与 nginx 的处理一致）。
 *
 * <p>如确有外部监控端需要抓取（例如 Prometheus 不在同一容器网络），
 * 把 {@code security.actuator.allow-private-only} 置为 {@code false} 即可关闭本限制，
 * 但那时应当改用鉴权或防火墙来保护该端口。
 */
@Slf4j
@Component
public class ActuatorAccessInterceptor implements HandlerInterceptor {

    /** IPv4 字面量（是否合法在 {@link #isLocalOrPrivate} 里逐段核对） */
    private static final Pattern IPV4 = Pattern.compile("^(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})$");
    /** IPv6 字面量的粗筛：只含十六进制字符与冒号 */
    private static final Pattern IPV6 = Pattern.compile("^[0-9a-fA-F:]+$");

    /**
     * 是否只允许回环 / 私有网段访问 Actuator。
     * <p>默认 {@code true}。改 false 前请先确认该端口不会从公网可达。
     */
    @Value("${security.actuator.allow-private-only:true}")
    private boolean allowPrivateOnly = true;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!allowPrivateOnly) {
            return true;
        }
        // 只看 TCP 对端地址：任何代理头都可以被伪造，不能用作访问控制的依据
        String remote = request.getRemoteAddr();
        if (isLocalOrPrivate(remote)) {
            return true;
        }
        log.warn("[ActuatorGuard] 拒绝来自非内网地址的 Actuator 访问: ip={}, uri={}",
                remote, request.getRequestURI());
        // 404 而非 403：不向扫描器确认该路径存在
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        return false;
    }

    /**
     * 判断来源地址是否属于回环 / 站点本地 / 链路本地网段。
     * <p>无法解析为 IP 字面量时返回 {@code false}（拒绝）—— 这里刻意不做 DNS 解析，
     * 否则一个构造出来的主机名就能让我们去查一次 DNS（既是阻塞点也是信息泄露面）。
     */
    static boolean isLocalOrPrivate(String ip) {
        if (ip == null || ip.isBlank()) {
            return false;
        }
        String candidate = ip.trim();
        // 去掉 IPv6 的 zone id（fe80::1%eth0）
        int zone = candidate.indexOf('%');
        if (zone >= 0) {
            candidate = candidate.substring(0, zone);
        }
        // IPv4-mapped IPv6（::ffff:127.0.0.1）：按内嵌的 IPv4 判断
        if (candidate.regionMatches(true, 0, "::ffff:", 0, 7)) {
            candidate = candidate.substring(7);
        }
        if (!isIpLiteral(candidate)) {
            return false;
        }
        try {
            InetAddress addr = InetAddress.getByName(candidate);
            return addr.isLoopbackAddress()
                    || addr.isAnyLocalAddress()
                    || addr.isSiteLocalAddress()
                    || addr.isLinkLocalAddress();
        } catch (Exception e) {
            // 能过 isIpLiteral 却解析失败，属于不该发生的情况：记下来并拒绝（fail-closed）
            log.warn("[ActuatorGuard] 来源地址解析失败，按拒绝处理: ip={}, err={}", candidate, e.getMessage());
            return false;
        }
    }

    /** 只接受 IP 字面量，避免把主机名交给 {@code InetAddress} 触发 DNS 解析 */
    private static boolean isIpLiteral(String s) {
        Matcher v4 = IPV4.matcher(s);
        if (v4.matches()) {
            for (int i = 1; i <= 4; i++) {
                if (Integer.parseInt(v4.group(i)) > 255) {
                    return false;
                }
            }
            return true;
        }
        return s.indexOf(':') >= 0 && IPV6.matcher(s).matches();
    }
}
