package cn.kmbeast.websocket;

import cn.kmbeast.core.auth.WsTicketRegistry;
import jakarta.websocket.HandshakeResponse;
import jakarta.websocket.server.HandshakeRequest;
import jakarta.websocket.server.ServerEndpointConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * S6：握手Configurator 的 fail-closed 行为。
 *
 * <p>锁死：票据只能从 Header / Sec-WebSocket-Protocol 取；校验不通过时
 * {@code EndpointConfig#getUserProperties()} 里<b>不得出现任何身份</b>；
 * 同一枚票据不能被两次握手使用。
 */
@DisplayName("WebSocket 握手一次性票据校验")
class WebSocketTicketConfiguratorTest {

    private WsTicketRegistry registry;
    private WebSocketTicketConfigurator configurator;
    private ServerEndpointConfig sec;
    private Map<String, Object> props;

    @BeforeEach
    void setUp() {
        registry = new WsTicketRegistry();
        // Configurator 由 WebSocket 容器实例化，拿不到 Spring bean，只能走静态入口
        ReflectionTestUtils.setField(WsTicketRegistry.class, "instance", registry);
        configurator = new WebSocketTicketConfigurator();
        sec = mock(ServerEndpointConfig.class);
        props = new HashMap<>();
        when(sec.getUserProperties()).thenReturn(props);
    }

    @AfterEach
    void tearDown() {
        ReflectionTestUtils.setField(WsTicketRegistry.class, "instance", null);
    }

    private HandshakeRequest requestWithHeaders(Map<String, List<String>> headers) {
        HandshakeRequest request = mock(HandshakeRequest.class);
        when(request.getHeaders()).thenReturn(headers);
        return request;
    }

    private static Map<String, List<String>> subProtocol(String value) {
        return Map.of("Sec-WebSocket-Protocol", List.of(value));
    }

    private void handshake(Map<String, List<String>> headers) {
        configurator.modifyHandshake(sec, requestWithHeaders(headers), mock(HandshakeResponse.class));
    }

    @Test
    @DisplayName("sub-protocol 携带有效票据：写入签发者身份")
    void subProtocolTicketGrantsIdentity() {
        String ticket = registry.issue(7, 2);

        handshake(subProtocol("ws-ticket." + ticket));

        assertEquals(7, props.get(WebSocketTicketConfigurator.PROP_USER_ID));
        assertEquals(2, props.get(WebSocketTicketConfigurator.PROP_ROLE_ID));
    }

    @Test
    @DisplayName("重放：同一枚票据第二次握手不再写入身份")
    void replayedTicketGrantsNothing() {
        String ticket = registry.issue(7, 2);
        handshake(subProtocol("ws-ticket." + ticket));
        props.clear(); // 容器每次握手提供新的 userProperties

        handshake(subProtocol("ws-ticket." + ticket));

        assertNull(props.get(WebSocketTicketConfigurator.PROP_USER_ID),
                "🔴 票据重放成功 = 长期凭证泄漏问题原样复现");
    }

    @Test
    @DisplayName("缺少票据 / 票据为空 / 伪造票据：一律不写身份")
    void invalidTicketsGrantNothing() {
        handshake(Collections.emptyMap());
        assertNull(props.get(WebSocketTicketConfigurator.PROP_USER_ID));

        handshake(subProtocol("ws-ticket."));
        assertNull(props.get(WebSocketTicketConfigurator.PROP_USER_ID));

        handshake(subProtocol("ws-ticket.not-a-real-ticket"));
        assertNull(props.get(WebSocketTicketConfigurator.PROP_USER_ID));

        handshake(subProtocol("some-other-protocol"));
        assertNull(props.get(WebSocketTicketConfigurator.PROP_USER_ID));
    }

    @Test
    @DisplayName("非浏览器客户端可用 X-WS-Ticket Header（大小写不敏感）")
    void headerTicketGrantsIdentity() {
        String ticket = registry.issue(9, 3);

        handshake(Map.of("X-WS-Ticket", List.of(ticket)));

        assertEquals(9, props.get(WebSocketTicketConfigurator.PROP_USER_ID));
        assertEquals(3, props.get(WebSocketTicketConfigurator.PROP_ROLE_ID));
    }

    @Test
    @DisplayName("多个 sub-protocol 逗号分隔时能挑出票据协议")
    void commaSeparatedProtocolsAreParsed() {
        String ticket = registry.issue(7, 2);

        handshake(subProtocol("chat, ws-ticket." + ticket));

        assertEquals(7, props.get(WebSocketTicketConfigurator.PROP_USER_ID));
    }

    @Test
    @DisplayName("协商回客户端请求过的票据协议，否则浏览器会断开")
    void negotiatedSubprotocolEchoesTicketProtocol() {
        String protocol = "ws-ticket.abc123";

        String negotiated = configurator.getNegotiatedSubprotocol(
                Arrays.asList("chat", protocol), Collections.emptyList());

        assertEquals(protocol, negotiated);
        assertEquals("", configurator.getNegotiatedSubprotocol(
                List.of("chat"), Collections.emptyList()));
        assertEquals("", configurator.getNegotiatedSubprotocol(null, null));
    }

    @Test
    @DisplayName("注册中心未初始化：fail-closed，不写身份")
    void registryUnavailableFailsClosed() {
        ReflectionTestUtils.setField(WsTicketRegistry.class, "instance", null);

        handshake(subProtocol("ws-ticket.anything"));

        assertNull(props.get(WebSocketTicketConfigurator.PROP_USER_ID));
    }
}
