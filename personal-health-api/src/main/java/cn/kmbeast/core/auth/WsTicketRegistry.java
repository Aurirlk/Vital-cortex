package cn.kmbeast.core.auth;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket 握手<b>一次性短期票据</b>（阶段 11 · S6）。
 *
 * <p>整改原因：原实现把长期有效的 JWT 直接拼在 URL 路径里
 * （{@code /ws/notification/{token}}）。URL 会进入浏览器历史、反向代理 access log、
 * Referer 与各类监控采样，等于把可复用 7 天的身份凭证泄漏到日志里；且任何拿到该 URL 的人
 * 都能在 JWT 有效期内冒充该用户建立长连接。
 *
 * <p>新链路：登录态调用 {@code GET /ws/ticket} 换取一枚 256 位随机票据（30 秒有效、
 * 一次性消费）→ 浏览器把票据放在 Sec-WebSocket-Protocol 里发起握手（WebSocket API 不允许
 * 自定义 Header，这是浏览器侧唯一可行的通道）→ 服务端在握手阶段原子消费票据并把身份放进
 * {@code EndpointConfig#getUserProperties()} → {@code @OnOpen} 只认这个身份，URL 里不再有凭证。
 *
 * <p><b>部署限制（如实登记，不虚标）</b>：票据保存在单实例 JVM 内存中，与本项目
 * {@code docker-compose.yml} 单 backend 实例的部署形态一致；若将来横向扩容到多实例，
 * 必须把本类换成 Redis（{@code GETDEL}）等共享存储，否则票据会落到另一台实例上而校验失败。
 *
 * <p>实例由 Spring 托管，静态入口 {@link #getInstance()} 供非 Spring 托管的
 * {@code ServerEndpointConfig.Configurator} 使用，范式与 {@link cn.kmbeast.utils.JwtUtil} 一致。
 */
@Slf4j
@Component
public class WsTicketRegistry {

    /** 票据有效期：拿到后立即建连，30s 足以覆盖网络抖动与重连排队。 */
    public static final Duration DEFAULT_TTL = Duration.ofSeconds(30);

    /** 256 位随机票据，Base64URL 无填充编码（43 字符，可安全放进 sub-protocol token）。 */
    private static final int TICKET_BYTES = 32;

    /** 票据表上限，防止异常流量把 JVM 堆撑爆；达到上限时清过期项后仍满则拒绝签发（fail-closed）。 */
    private static final int MAX_ENTRIES = 10_000;

    private static final SecureRandom RANDOM = new SecureRandom();

    private static volatile WsTicketRegistry instance;

    private final ConcurrentHashMap<String, Ticket> tickets = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        instance = this;
    }

    public static WsTicketRegistry getInstance() {
        WsTicketRegistry ref = instance;
        if (ref == null) {
            throw new IllegalStateException("WsTicketRegistry 尚未初始化，请确认 Spring 容器已启动完成");
        }
        return ref;
    }

    /** 签发一枚票据，绑定签发者身份。 */
    public String issue(Integer userId, Integer roleId) {
        return issue(userId, roleId, DEFAULT_TTL);
    }

    /**
     * 签发票据；{@code ttl} 可注入以便测试过期分支（生产调用只用 {@link #issue(Integer, Integer)}）。
     */
    public String issue(Integer userId, Integer roleId, Duration ttl) {
        if (userId == null) {
            throw new IllegalArgumentException("签发 WebSocket 票据必须提供 userId");
        }
        purgeExpired();
        if (tickets.size() >= MAX_ENTRIES) {
            // 不退化成「无限放行」：宁可让这次建连失败并重试，也不让票据表无界增长
            log.warn("[WS] 票据表已达上限 {}，拒绝本次签发", MAX_ENTRIES);
            throw new IllegalStateException("WebSocket 票据服务繁忙，请稍后重试");
        }
        byte[] buf = new byte[TICKET_BYTES];
        RANDOM.nextBytes(buf);
        String ticket = Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
        long expiresAt = System.currentTimeMillis() + ttl.toMillis();
        tickets.put(ticket, new Ticket(userId, roleId, expiresAt));
        return ticket;
    }

    /**
     * 原子消费票据：{@code remove} 保证同一枚票据最多成功一次（重放/并发双握手都只能有一个赢）。
     * 过期、不存在、格式为空一律返回 empty（fail-closed），调用方必须据此关闭连接。
     */
    public Optional<Identity> consume(String ticket) {
        if (ticket == null || ticket.isBlank()) {
            return Optional.empty();
        }
        Ticket t = tickets.remove(ticket);
        if (t == null) {
            return Optional.empty();
        }
        if (System.currentTimeMillis() > t.expiresAt()) {
            log.warn("[WS] 票据已过期，拒绝握手");
            return Optional.empty();
        }
        return Optional.of(new Identity(t.userId(), t.roleId()));
    }

    /** 惰性清理过期票据；仅在表规模过半时执行，避免每次签发都全表扫描。 */
    private void purgeExpired() {
        if (tickets.size() < MAX_ENTRIES / 2) {
            return;
        }
        long now = System.currentTimeMillis();
        tickets.entrySet().removeIf(e -> e.getValue().expiresAt() <= now);
    }

    /** 仅用于测试清场；生产不调用。 */
    public void clear() {
        tickets.clear();
    }

    public int size() {
        return tickets.size();
    }

    /** 票据换出的身份，绝不接受客户端自报的 userId。 */
    public record Identity(Integer userId, Integer roleId) {
    }

    private record Ticket(Integer userId, Integer roleId, long expiresAt) {
    }

    /** 供诊断使用，不暴露票据原文。 */
    public Map<String, Object> stats() {
        return Map.of("activeTickets", size(), "ttlSeconds", DEFAULT_TTL.toSeconds());
    }
}
