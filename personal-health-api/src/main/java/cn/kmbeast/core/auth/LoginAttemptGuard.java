package cn.kmbeast.core.auth;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;

/**
 * 登录失败计数与临时封禁（防撞库）—— 2026-10-10 新增。
 *
 * <p><b>为什么需要它</b>：此前唯一的登录保护是 {@code SentinelConfig} 里
 * {@code user:login} 的一条<b>全局 QPS=10</b> 流控规则。那不是防撞库，语义完全不同：
 * <ul>
 *   <li>全局 QPS 阀是「整个接口每秒 10 次」，<b>一个攻击者就能把配额吃满</b>，
 *       所有真实用户被挡在门外（自伤式 DoS）——攻击者不需要猜密码，刷就完了；</li>
 *   <li>它也不区分账号：{@code sentinel.qps.login=10} 意味着对同一个账号
 *       每秒可尝试 10 次密码，一天 86 万次，弱口令必然被撞开；</li>
 *   <li>{@code /doctor/login} 连 {@code @SentinelResource} 都没有，完全裸奔。</li>
 * </ul>
 * 本组件按<b>账号维度</b>（跨 IP 累计）与 <b>IP 维度</b>（跨账号累计）分别记失败次数，
 * 超阈值即写入带 TTL 的封禁键。与 Sentinel 的分工：Sentinel 防「把接口打满」，
 * 本组件防「把某个账号撞开」。
 *
 * <p><b>为什么不直接把 {@code user.is_login} 置 1 锁账号</b>：那是需要管理员解封的
 * 持久锁定，任何知道他人账号的人都能用它把对方永久锁死（DoS 换 DoS）。
 * 这里改成 Redis 里带 TTL 的临时封禁，到点自解，无人需要善后。
 *
 * <p><b>降级策略：Redis 异常时 fail-open</b>（放行并记 warn）。与
 * {@link AuthSessionManager} 的降级口径一致 —— 缓存/限流组件挂掉时把全体用户
 * 锁在门外，比暂时失去限流更糟；此时 {@code user:login} 的 Sentinel QPS 阀仍在。
 *
 * <p><b>客户端 IP 的取法与「IP 维度何时启用」</b>：IP 维度需要<b>可信的客户端地址</b>，
 * 而这取决于部署拓扑，所以由 {@code security.login.trusted-proxy} 控制：
 * <ul>
 *   <li>{@code false}（默认）：<b>不做 IP 维度封禁</b>，只按账号计。
 *       原因见 {@link #guardableClientIp()} —— 后端在 nginx 之后时所有请求的
 *       remoteAddr 都是 nginx 容器 IP，按它封禁等于对全站用户连坐；</li>
 *   <li>{@code true}：取 {@code X-Forwarded-For} 的<b>最右</b>一项
 *       （那是我们自己的 nginx 追加的直接对端地址，见 nginx.conf 的
 *       {@code $proxy_add_x_forwarded_for}；客户端自带的前缀在左边，不可信），
 *       此时才启用 IP 维度。
 *       <b>必须同时把 21090 限制为仅 nginx/内网可达</b>：该端口目前对外发布，
 *       攻击者绕开 nginx 直连时可以随意伪造 XFF，那样按 IP 封禁就形同虚设。</li>
 * </ul>
 * 账号维度在任何拓扑下都生效，它才是防撞库的主力。两种配置的配套关系
 * 另见 docker-compose.yml 中 backend 端口映射处的说明。
 */
@Slf4j
@Component
public class LoginAttemptGuard {

    private static final String KEY_FAIL_ACCOUNT = "auth:login:fail:acct:";
    private static final String KEY_BLOCK_ACCOUNT = "auth:login:block:acct:";
    private static final String KEY_FAIL_IP = "auth:login:fail:ip:";
    private static final String KEY_BLOCK_IP = "auth:login:block:ip:";

    /** 同一账号允许的连续失败次数（跨 IP 累计） */
    @Value("${security.login.max-failures-per-account:5}")
    private int maxFailuresPerAccount = 5;

    /**
     * 同一 IP 允许的失败次数（跨账号累计，容忍值给得高一些）。
     * <p>医院/校园这类 NAT 出口下大量用户共用一个公网 IP，阈值太低会误伤；
     * 真正的撞库行为会在短时间内命中几十上百个不同账号。
     */
    @Value("${security.login.max-failures-per-ip:30}")
    private int maxFailuresPerIp = 30;

    /** 失败计数的统计窗口（自第一次失败起算的固定窗口） */
    @Value("${security.login.failure-window-minutes:15}")
    private int failureWindowMinutes = 15;

    /** 触发后的封禁时长（到点自动解除，无需人工介入） */
    @Value("${security.login.lock-minutes:15}")
    private int lockMinutes = 15;

    /** 是否信任 nginx 传来的 X-Forwarded-For（仅在 21090 不对外网暴露时可开） */
    @Value("${security.login.trusted-proxy:false}")
    private boolean trustedProxy = false;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 登录前置检查：该账号或该来源 IP 是否处于封禁期。
     *
     * <p>必须在查库、比密码<b>之前</b>调用：被封锁时既省掉一次数据库查询，
     * 也避免继续给攻击者提供「密码对不对」的反馈信号。
     *
     * @param account 登录账号（原始输入即可，内部会归一化）
     * @return 放行返回 {@code null}；被封禁返回给用户看的提示语
     */
    public String checkBlocked(String account) {
        String acctKey = KEY_BLOCK_ACCOUNT + normalize(account);
        String ip = guardableClientIp();
        String ipKey = ip == null ? null : KEY_BLOCK_IP + ip;
        try {
            if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(acctKey))) {
                log.warn("[LoginGuard] 账号处于封禁期，拒绝登录: account={}, ip={}",
                        mask(account), ip);
                return "登录尝试过于频繁，请 " + lockMinutes + " 分钟后重试";
            }
            if (ipKey != null && Boolean.TRUE.equals(stringRedisTemplate.hasKey(ipKey))) {
                log.warn("[LoginGuard] 来源 IP 处于封禁期，拒绝登录: account={}, ip={}",
                        mask(account), ip);
                return "登录尝试过于频繁，请 " + lockMinutes + " 分钟后重试";
            }
        } catch (Exception e) {
            // 连异常类型一起打：只有 message 时，「Redis 连不上」和「代码里的 NPE」
            // 在日志里长得一模一样，排查时会一直怀疑是缓存的问题。
            log.warn("[LoginGuard] 封禁检查失败，本次放行(fail-open): {} - {}",
                    e.getClass().getSimpleName(), e.getMessage());
        }
        return null;
    }

    /**
     * 记一次登录失败（密码错误、账号不存在、账号停用等一律计入，避免通过
     * 「是否计数」反推账号是否存在）。达到阈值即写封禁键。
     */
    public void recordFailure(String account) {
        String acct = normalize(account);
        String ip = guardableClientIp();
        try {
            Long acctCount = incrementWithWindow(KEY_FAIL_ACCOUNT + acct);
            if (acctCount != null && acctCount >= maxFailuresPerAccount) {
                // setIfAbsent：只有「首次写下封禁」才返回 true，用它去重日志，
                // 免得封禁期内每次失败都刷一条 WARN（攻击者正好能借此刷爆日志）
                Boolean first = stringRedisTemplate.opsForValue().setIfAbsent(
                        KEY_BLOCK_ACCOUNT + acct, "1", Duration.ofMinutes(lockMinutes));
                if (Boolean.TRUE.equals(first)) {
                    log.warn("[LoginGuard] 账号连续失败 {} 次，封禁 {} 分钟: account={}, ip={}",
                            acctCount, lockMinutes, mask(account), ip);
                }
            }
            if (ip != null) {
                Long ipCount = incrementWithWindow(KEY_FAIL_IP + ip);
                if (ipCount != null && ipCount >= maxFailuresPerIp) {
                    Boolean first = stringRedisTemplate.opsForValue().setIfAbsent(
                            KEY_BLOCK_IP + ip, "1", Duration.ofMinutes(lockMinutes));
                    if (Boolean.TRUE.equals(first)) {
                        log.warn("[LoginGuard] 来源 IP 累计失败 {} 次（疑似撞库），封禁 {} 分钟: ip={}",
                                ipCount, lockMinutes, ip);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[LoginGuard] 记录登录失败次数失败(fail-open): {} - {}",
                    e.getClass().getSimpleName(), e.getMessage());
        }
    }

    /**
     * 登录成功后清空该账号与来源 IP 的失败计数（封禁键不动：
     * 能成功登录说明本来就没被封）。
     */
    public void clearOnSuccess(String account) {
        String ip = guardableClientIp();
        try {
            stringRedisTemplate.delete(KEY_FAIL_ACCOUNT + normalize(account));
            if (ip != null) {
                stringRedisTemplate.delete(KEY_FAIL_IP + ip);
            }
        } catch (Exception e) {
            log.warn("[LoginGuard] 清理登录失败计数失败: {} - {}",
                    e.getClass().getSimpleName(), e.getMessage());
        }
    }

    /**
     * 自增计数并在首次写入时挂上窗口 TTL。
     * <p>TTL 挂在「第一次失败」上（固定窗口），而不是每次失败都续期 ——
     * 否则持续尝试会让窗口无限后移，攻击者可以一直保持计数不归零。
     */
    private Long incrementWithWindow(String key) {
        Long count = stringRedisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            stringRedisTemplate.expire(key, Duration.ofMinutes(failureWindowMinutes));
        }
        return count;
    }

    /** 账号归一化：去空白 + 转小写，避免 Admin / admin 各占一份计数被绕过 */
    private String normalize(String account) {
        return account == null ? "" : account.trim().toLowerCase();
    }

    /**
     * 当前请求的客户端 IP；无请求上下文（如单元测试、定时任务）返回 {@code null}。
     */
    String currentClientIp() {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return null;
        }
        if (trustedProxy) {
            String xff = request.getHeader("X-Forwarded-For");
            if (xff != null && !xff.isBlank()) {
                // 取最右一项：那是 nginx 用 $proxy_add_x_forwarded_for 追加的直接对端地址，
                // 客户端自己塞的前缀在左边，不可信。
                String[] parts = xff.split(",");
                String last = parts[parts.length - 1].trim();
                if (!last.isEmpty()) {
                    return last;
                }
            }
        }
        return request.getRemoteAddr();
    }

    /**
     * 可用于「按 IP 封禁」的客户端地址；不可信时返回 {@code null}（= 不做 IP 维度限流）。
     *
     * <p>2026-10-10 修正一处<b>自伤式 DoS 隐患</b>：本组件最初无条件按
     * {@code request.getRemoteAddr()} 记 IP。但当后端位于 nginx 之后时，
     * 所有请求的 remoteAddr 都是 <b>nginx 容器 IP</b> —— 于是任何人只要失败
     * {@code max-failures-per-ip} 次，就会把这个「IP」封掉，
     * 效果是全站所有用户都被锁在登录页外 15 分钟。这和本轮修掉的
     * Sentinel 全局 QPS 阀是同一类错误：把「一个人作恶」惩罚成「所有人受罚」。
     *
     * <p>所以默认（{@code trusted-proxy=false}）<b>不做 IP 维度封禁</b>，
     * 只保留始终有效的账号维度 —— 那才是防撞库的主力。
     * 把 21090 限制为仅 nginx/内网可达、并把 {@code trusted-proxy} 置 true 之后，
     * 客户端 IP 才变得可信，IP 维度（拦「换账号继续试」）随之启用。
     */
    private String guardableClientIp() {
        if (!trustedProxy) {
            return null;
        }
        return currentClientIp();
    }

    private HttpServletRequest currentRequest() {
        try {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            return attrs == null ? null : attrs.getRequest();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 把账号打码后再进日志。
     * <p>登录账号可能是手机号，日志是长期留存且会被采集到 Loki 的，
     * 明文手机号等于把一份用户清单写进了日志系统。
     */
    static String mask(String account) {
        if (account == null || account.isEmpty()) {
            return "(empty)";
        }
        String trimmed = account.trim();
        if (trimmed.length() <= 2) {
            return "***";
        }
        if (trimmed.length() <= 6) {
            return trimmed.charAt(0) + "***" + trimmed.charAt(trimmed.length() - 1);
        }
        // 形如手机号/长账号：保留首尾各 2 位
        return trimmed.substring(0, 2) + "****" + trimmed.substring(trimmed.length() - 2);
    }
}
