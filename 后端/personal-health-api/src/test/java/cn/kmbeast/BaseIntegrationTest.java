package cn.kmbeast;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.socket.server.standard.ServerEndpointExporter;

/**
 * 集成测试基类（2026-10-25 新增）。
 *
 * <p>所有 {@code *IT} 共用同一份 Spring 上下文（测试框架按配置缓存，只起一次），避免每个 IT 重复冷启动。
 *
 * <p><b>为什么 mock 掉 {@link ServerEndpointExporter}</b>：
 * 该 Bean 在 {@code afterPropertiesSet()} 里向 Servlet 容器注册 WebSocket 端点，
 * 需要真实的 {@code jakarta.websocket.server.ServerContainer}。而 {@link SpringBootTest} 默认
 * 的 MOCK Web 环境并不提供这个容器，会导致上下文启动即抛
 * {@code IllegalStateException: jakarta.websocket.server.ServerContainer not available}。
 * 生产环境（内嵌 Tomcat 启动）下该 Bean 正常工作，此处用 mock 替换仅是为了让测试上下文能起来，
 * 不影响任何业务 Bean 的装配与验证。
 */
@SpringBootTest
@ActiveProfiles("local")
abstract class BaseIntegrationTest {

    @MockBean
    @SuppressWarnings("unused")
    private ServerEndpointExporter serverEndpointExporter;
}
