package cn.kmbeast.websocket;

import cn.kmbeast.core.auth.WsTicketRegistry;
import jakarta.websocket.HandshakeResponse;
import jakarta.websocket.server.HandshakeRequest;
import jakarta.websocket.server.ServerEndpointConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * WebSocket 握手阶段的一次性票据校验（阶段 11 · S6）。
 *
 * <p>JSR-356 的 {@code @OnOpen} 只能拿到 query 参数，读不到握手 Header；浏览器 WebSocket API
 * 又不允许设置自定义 Header。因此凭证只能走两条通道，且都必须在 Configurator 里读取：
 * <ul>
 *   <li><b>Sec-WebSocket-Protocol</b>：浏览器侧唯一可行通道。前端传
 *       {@code ws-ticket.<票据>}，本类协商回同一个 protocol（浏览器要求服务端回的值必须在
 *       请求列表里，否则直接断开）。</li>
 *   <li><b>X-WS-Ticket</b>：给非浏览器客户端（服务端到服务端、压测脚本）用，优先级更高。</li>
 * </ul>
 *
 * <p>校验通过后把身份写进 {@code ServerEndpointConfig#getUserProperties()}，
 * 由 {@code WebSocketServer#onOpen(Session, EndpointConfig)} 读取。
 * <b>任何失败都不写身份</b>（fail-closed），onOpen 见不到身份即关闭连接。
 * 票据由 {@link WsTicketRegistry#consume} 原子消费，同一枚票据无法用于第二次握手。
 */
public class WebSocketTicketConfigurator extends ServerEndpointConfig.Configurator {

    private static final Logger log = LoggerFactory.getLogger(WebSocketTicketConfigurator.class);

    public static final String SUB_PROTOCOL_PREFIX = "ws-ticket.";
    public static final String HEADER_TICKET = "x-ws-ticket";
    private static final String HEADER_SUB_PROTOCOL = "sec-websocket-protocol";
    static final String PROP_USER_ID = "ws.userId";
    static final String PROP_ROLE_ID = "ws.roleId";

    @Override
    public void modifyHandshake(ServerEndpointConfig sec, HandshakeRequest request, HandshakeResponse response) {
        // 先清空上次握手可能残留的身份（userProperties 由容器提供，不信任其初始内容）
        sec.getUserProperties().remove(PROP_USER_ID);
        sec.getUserProperties().remove(PROP_ROLE_ID);

        String ticket = extractTicket(request);
        if (ticket == null) {
            log.warn("[WS] 握手未提供一次性票据，拒绝连接");
            return;
        }
        try {
            Optional<WsTicketRegistry.Identity> identity = WsTicketRegistry.getInstance().consume(ticket);
            if (identity.isEmpty()) {
                // 票据不存在 / 已用过 / 已过期：一律不写身份
                log.warn("[WS] 一次性票据无效（已消费或已过期），拒绝连接");
                return;
            }
            sec.getUserProperties().put(PROP_USER_ID, identity.get().userId());
            sec.getUserProperties().put(PROP_ROLE_ID, identity.get().roleId());
        } catch (Exception e) {
            // 注册中心未初始化 / Redis 等异常：fail-closed，绝不放行
            log.warn("[WS] 票据校验异常，拒绝连接: {}", e.getMessage());
        }
    }

    /**
     * 浏览器要求服务端回一个「请求列表里出现过」的 sub-protocol，否则建连失败。
     * 这里把票据协议原样回给浏览器；票据此时已被消费，且不在 URL / 日志路径里。
     */
    @Override
    public String getNegotiatedSubprotocol(List<String> requested, List<String> supported) {
        if (requested != null) {
            for (String p : requested) {
                if (p != null && p.startsWith(SUB_PROTOCOL_PREFIX)) {
                    return p;
                }
            }
        }
        return "";
    }

    /**
     * 从握手请求提取票据：先自定义 Header，再 Sec-WebSocket-Protocol。
     * HTTP Header 名大小写不敏感，故用 equalsIgnoreCase 遍历而不是硬取小写键。
     */
    static String extractTicket(HandshakeRequest request) {
        if (request == null) {
            return null;
        }
        Map<String, List<String>> headers = request.getHeaders();
        if (headers == null || headers.isEmpty()) {
            return null;
        }
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            if (entry.getKey() != null && HEADER_TICKET.equalsIgnoreCase(entry.getKey())) {
                for (String value : nullSafe(entry.getValue())) {
                    if (value != null && !value.isBlank()) {
                        return value.trim();
                    }
                }
            }
        }
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            if (entry.getKey() != null && HEADER_SUB_PROTOCOL.equalsIgnoreCase(entry.getKey())) {
                for (String line : nullSafe(entry.getValue())) {
                    if (line == null) {
                        continue;
                    }
                    for (String part : line.split(",")) {
                        String protocol = part.trim();
                        if (protocol.startsWith(SUB_PROTOCOL_PREFIX)
                                && protocol.length() > SUB_PROTOCOL_PREFIX.length()) {
                            return protocol.substring(SUB_PROTOCOL_PREFIX.length());
                        }
                    }
                }
            }
        }
        return null;
    }

    private static List<String> nullSafe(List<String> values) {
        return values == null ? Collections.emptyList() : values;
    }
}
