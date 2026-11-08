package cn.kmbeast;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
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
 *
 * <h3>⚠️ 不写 {@code @ActiveProfiles}（2026-11-08 调整）</h3>
 * <p>此前基类带 {@code @ActiveProfiles("local")}，而 {@code application-local.yml} 被
 * {@code .gitignore} 排除、<b>CI 上并不存在</b> —— 于是本地与 CI 跑的是<b>两套配置</b>，
 * 本地绿不代表 CI 绿。去掉注解后，profile 完全由 {@code application.yml} 决定
 * （该文件里本就写了 {@code spring.profiles.active: local}），有无 local 文件都能启动。
 *
 * <p>已实测：把数据源指向一个<b>没人监听的端口</b>（模拟 CI 无 MySQL），
 * 上下文仍在 18 秒内启动成功、4 个 IT 全绿 —— 原因是 {@code DataSource}、
 * {@code RedisTemplate}、{@code Neo4jClient} 都是懒连接，启动期不真正取连接。
 * 这正是启动守护能在<b>任何环境</b>（含 CI 裸机）无条件跑起来的前提。
 */
@SpringBootTest(properties = {
        // ── 以下三项是「测试专用假值」，只为让启动期强校验照常通过，绝不用于生产 ──
        // 1) CRM API Key：CrmConfig.validateApiKey() 在缺失/弱口令/已泄露值时**故意拒绝启动**。
        //    这里给一个 ≥24 字符、且不在 LEAKED_API_KEYS 里的假值。
        "crm.api-key=it-only-crm-api-key-0123456789abcdef",
        // 2) 嵌入端点：validateEmbeddingConfig() 缺失即拒绝启动；必须非空且不含 deepseek 端点。
        //    指向不可路由地址，确保测试期间绝不会真的发出外部请求。
        "crm.embedding.api-url=http://127.0.0.1:9/v1/embeddings",
        // 3) JWT 密钥：JwtUtil.init() 要求 ≥32 字节且非已泄露值。
        "jwt.secret=it-only-jwt-secret-0123456789abcdef0123456789abcdef"
})
abstract class BaseIntegrationTest {

    @MockBean
    @SuppressWarnings("unused")
    private ServerEndpointExporter serverEndpointExporter;
}
