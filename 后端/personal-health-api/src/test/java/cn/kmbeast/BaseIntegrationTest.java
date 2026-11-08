package cn.kmbeast;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.socket.server.standard.ServerEndpointExporter;

/**
 * 集成测试基类（2026-10-25 新增，2026-11-08 修正配置来源）。
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
 * <h3>🔴 为什么激活 {@code prod} 而不是 {@code local}（2026-11-08 关键修正）</h3>
 * <p>{@code .gitignore} 把 <b>{@code application.yml} 与 {@code application-local.yml} 都排除了</b>，
 * 入库的只有 {@code application-example.yml}（模板）与 {@code application-prod.yml}（生产）。
 * 也就是说：
 * <ul>
 *   <li>本地能起来，靠的是**不在 git 里**的 {@code application-local.yml}；</li>
 *   <li>CI 上这两个文件<b>根本不存在</b>，上下文没有任何数据源/mybatis/端口配置；</li>
 *   <li>生产容器靠 {@code SPRING_PROFILES_ACTIVE=prod} + {@code application-prod.yml} 起来
 *       （见 {@code Dockerfile.backend} 的 ENTRYPOINT 与那段 2026-10-10 修复注释）。</li>
 * </ul>
 * <p>结果是：以前这个「启动守护」在 CI 上<b>永远是红的</b>（先报 `CRM API 密钥未配置`，
 * 补上后又报 `Failed to determine a suitable driver class`）——守护本身没在守护任何东西。
 * 改为激活 {@code prod} 后，IT 校验的正是**生产实际用的那份配置**能不能装配成功，
 * 且该文件在 git 里，本地与 CI 完全一致。
 *
 * <p><b>下面的 properties 全部是测试专用假值</b>：{@code application-prod.yml} 里
 * {@code DB_PASS} / {@code JWT_SECRET} / {@code CRM_API_KEY} / {@code EMBEDDING_API_URL}
 * 都是无默认值的占位符（生产由环境变量注入），测试必须补值才能解析。
 * 同时**没有**设置 {@code crm.strict-startup=false} —— 让 CrmConfig / JwtUtil 的强校验
 * 照常执行，这样「校验逻辑本身被改坏」也能被这条 IT 抓到。
 */
@SpringBootTest(properties = {
        // 数据源：指向无人监听的端口。启动期不取连接（Hikari 懒初始化），
        // 因此 CI 上不需要 MySQL 也能起上下文；connectTimeout 防止万一取连接时长时间挂住。
        "spring.datasource.url=jdbc:mysql://127.0.0.1:3999/nodb"
                + "?characterEncoding=utf8&useSSL=false&serverTimezone=GMT%2B8"
                + "&allowPublicKeyRetrieval=true&connectTimeout=1000&socketTimeout=1000",
        "spring.datasource.password=it-only-not-a-real-password",
        // CRM API Key：validateApiKey() 对缺失/弱口令/已泄露值一律拒绝启动。
        // 这里给 ≥24 字符、且不在 LEAKED_API_KEYS 里的假值。
        "crm.api-key=it-only-crm-api-key-0123456789abcdef",
        // 嵌入端点：validateEmbeddingConfig() 缺失即拒绝启动；必须非空且不是 deepseek 端点。
        // 指向不可路由地址，确保测试期间绝不会真的发出外部请求。
        "crm.embedding.api-url=http://127.0.0.1:9/v1/embeddings",
        // JWT 密钥：JwtUtil.init() 要求 ≥32 字节且非已泄露值。
        "jwt.secret=it-only-jwt-secret-0123456789abcdef0123456789abcdef",
        // prod 里 logging.file.path=/app/logs，CI runner 无权创建 → 改到构建目录
        "logging.file.path=target/it-logs"
})
@ActiveProfiles("prod")
abstract class BaseIntegrationTest {

    @MockBean
    @SuppressWarnings("unused")
    private ServerEndpointExporter serverEndpointExporter;
}
