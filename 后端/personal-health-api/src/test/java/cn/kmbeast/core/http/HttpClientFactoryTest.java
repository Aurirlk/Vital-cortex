package cn.kmbeast.core.http;

import cn.kmbeast.config.AiConfig;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link HttpClientFactory} 单例语义与超时配置的回归测试。
 *
 * <p><b>为什么这个测试重要</b>：本工厂替代了 6 处各自 {@code new OkHttpClient.Builder()}。
 * 改造后所有调用方在 {@code @PostConstruct} 里取共享实例，一旦工厂的
 * {@code @PostConstruct} 尚未执行就会拿到 {@code null}，且 6 个类<b>同时</b>启动失败——
 * 是那种一旦发生就完全起不来的问题。这里把「工厂已初始化」「实例真的共享」锁死。
 */
@DisplayName("HttpClientFactory 共享实例与超时配置")
class HttpClientFactoryTest {

    private HttpClientFactory factory;

    /** 构造一个注入了超时配置的工厂并完成初始化 */
    private HttpClientFactory newInitializedFactory(Integer connectMs, Integer readMs) throws Exception {
        HttpClientFactory f = new HttpClientFactory();
        AiConfig cfg = new AiConfig();
        // AiConfig 用 @Value 注入超时，直接用反射写入，模拟 Spring 的配置绑定结果
        setField(cfg, "connectTimeout", connectMs);
        setField(cfg, "readTimeout", readMs);

        Field fAiconfig = HttpClientFactory.class.getDeclaredField("aiConfig");
        fAiconfig.setAccessible(true);
        fAiconfig.set(f, cfg);

        f.init();
        return f;
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field fld = target.getClass().getDeclaredField(name);
        fld.setAccessible(true);
        fld.set(target, value);
    }

    @BeforeEach
    void setUp() {
        // 每个用例独立构造，避免共享状态影响断言
    }

    @Test
    @DisplayName("llmClient() 多次调用返回同一个实例——否则共享毫无意义")
    void llmClientIsSingleton() throws Exception {
        factory = newInitializedFactory(30000, 60000);
        OkHttpClient first = factory.llmClient();
        OkHttpClient second = factory.llmClient();
        assertNotNull(first, "llmClient 不应返回 null（@PostConstruct 未执行会导致调用方 NPE）");
        assertSame(first, second, "llmClient 必须是单例，否则又退回到各自 new 的老问题");
    }

    @Test
    @DisplayName("shortClient() 是单例，且与 llmClient() 不是同一实例")
    void shortClientIsSingletonAndDistinct() throws Exception {
        factory = newInitializedFactory(30000, 60000);
        OkHttpClient s1 = factory.shortClient();
        OkHttpClient s2 = factory.shortClient();
        assertSame(s1, s2, "shortClient 必须是单例");

        assertNotSame(factory.llmClient(), factory.shortClient(),
                "两档超时语义不同，必须是两个独立实例，否则改一档会影响另一档");
    }

    @Test
    @DisplayName("llm 档超时取自 AiConfig，配置改了立即生效")
    void llmTimeoutComesFromAiConfig() throws Exception {
        // 用一组明显不同的值，避免与默认值（30000/60000）撞车而测不出差异
        factory = newInitializedFactory(12345, 54321);
        OkHttpClient c = factory.llmClient();
        assertEquals(12345, c.connectTimeoutMillis(), "connectTimeout 未取自 AiConfig");
        assertEquals(54321, c.readTimeoutMillis(), "readTimeout 未取自 AiConfig");
    }

    @Test
    @DisplayName("llm 档写超时独立配置，不会被连接超时带偏")
    void llmWriteTimeoutIsIndependent() throws Exception {
        factory = newInitializedFactory(1000, 2000);
        OkHttpClient c = factory.llmClient();
        assertEquals(60000, c.writeTimeoutMillis(),
                "LLM 全是 POST 请求，写超时应独立给宽松值，不能跟着 connectTimeout 走");
        assertTrue(c.writeTimeoutMillis() > c.connectTimeoutMillis(),
                "写超时应大于连接超时，否则大 prompt 的请求会在写阶段被误杀");
    }

    @Test
    @DisplayName("两档的 Dispatcher 并发上限都是显式有界的")
    void dispatcherLimitsAreBounded() throws Exception {
        factory = newInitializedFactory(30000, 60000);
        for (OkHttpClient c : new OkHttpClient[]{factory.llmClient(), factory.shortClient()}) {
            assertEquals(64, c.dispatcher().getMaxRequests(),
                    "全局并发上限必须是显式配置的 64，不能退回 OkHttp 默认值 64 之外的意图值");
            assertEquals(16, c.dispatcher().getMaxRequestsPerHost(),
                    "单主机并发上限应显式放宽到 16（默认 5 对 LLM 并发偏紧）");
        }
    }

    @Test
    @DisplayName("短请求档超时固定 10s/15s，与原实现语义一致")
    void shortTimeoutIsFixed() throws Exception {
        // 传一组极端的 AiConfig 值，短请求档也不应受影响
        factory = newInitializedFactory(999999, 999999);
        OkHttpClient c = factory.shortClient();
        assertEquals(10000, c.connectTimeoutMillis(), "短请求档连接超时应为 10s");
        assertEquals(15000, c.readTimeoutMillis(), "短请求档读取超时应为 15s");
    }

    /**
     * 防止回退：6 个调用方各自 {@code new OkHttpClient.Builder()} 是本轮要消灭的问题。
     *
     * <p><b>关键点</b>：这些字段在各自 {@code @PostConstruct} 里赋值，不启动 Spring 容器
     * 就全是 {@code null}，断言会假失败。所以这里对每个类<b>手动调用一次初始化方法</b>
     * 复现容器行为（其依赖的 {@code httpClientFactory} 用反射注入工厂实例），
     * 再断言拿到的是工厂的同一实例。
     *
     * <p><b>为什么用 StreamingReActAgent 而不是 BaseReActAgent</b>：后者是抽象类
     * （{@code public abstract class}），无法实例化。初始化方法 {@code initHttpClient}
     * 声明在父类，这里用具体子类代表整个继承链。
     */
    @Test
    @DisplayName("6 个调用方的 httpClient 字段都指向工厂的共享实例（防回退）")
    void allCallersShareTheSameInstance() throws Exception {
        factory = newInitializedFactory(30000, 60000);
        OkHttpClient shared = factory.llmClient();
        OkHttpClient sharedShort = factory.shortClient();

        assertCallerUses(shared, "cn.kmbeast.crm.agent.StreamingReActAgent", "initHttpClient");
        assertCallerUses(shared, "cn.kmbeast.core.provider.LLMProviderFactory", "init");
        assertCallerUses(shared, "cn.kmbeast.crm.vectordb.EmbeddingService", "init");
        assertCallerUses(shared, "cn.kmbeast.service.impl.AiServiceImpl", "init");
        assertCallerUses(sharedShort, "cn.kmbeast.crm.service.WebSearchService", "init");
        assertCallerUses(sharedShort, "cn.kmbeast.service.impl.DifyWorkflowServiceImpl", "init");
    }

    /**
     * 实例化一个调用方、注入工厂、手动调其初始化方法，断言 httpClient 字段等于期望实例。
     *
     * @param expected 期望共享到的实例
     * @param className 全限定类名
     * @param initMethod 初始化方法名（对应它的 {@code @PostConstruct}，可能声明在父类）
     */
    private void assertCallerUses(OkHttpClient expected, String className, String initMethod)
            throws Exception {
        Class<?> cls = Class.forName(className);
        Object bean = cls.getDeclaredConstructor().newInstance();

        // 这些 init() 内部除了取 client，还会读各自的 @Resource 配置
        // （aiConfig.getProvider() / crmConfig.getXxx()）。不注入就会 NPE，
        // 而 NPE 与「client 是否共享」是无关的失败，会掩盖真正要测的断言。
        injectCollaborators(bean, cls);

        // 手动执行等价于 @PostConstruct 的初始化（方法可能声明在父类，故 getMethod 而非 getDeclaredMethod）
        cls.getMethod(initMethod).invoke(bean);

        Object actual = readHttpClient(bean, cls);
        assertSame(expected, actual,
                className + " 没有使用共享 client——各建 client 会让线程池与连接池被放大 N 倍");
    }

    /**
     * 注入初始化方法依赖的协作者。
     *
     * <p>只注入<b>初始化路径上确定会被读</b>的字段；其余留 null。之所以不能简单地
     * 「把同类型字段全塞新实例」——那样断言会变得脆弱：{@code init()} 里哪天多读一个
     * 字段，测试就会因为 NPE 失败，而那与本测试要验证的「共享」毫无关系。
     * 这里逐个按需注入，NPE 出现时说明依赖清单需要更新，是可接受的显式成本。
     */
    private void injectCollaborators(Object bean, Class<?> cls) throws Exception {
        injectFactory(bean, cls);
        AiConfig cfg = new AiConfig();
        setField(cfg, "connectTimeout", 30000);
        setField(cfg, "readTimeout", 60000);
        // 供 LLMProviderFactory.init() 读 provider 判定（该字段裸声明，无 setter）
        setField(cfg, "provider", "deepseek");
        injectIfPresent(bean, cls, "aiConfig", cfg);
        // 供 BaseReActAgent/EmbeddingService 等 init() 读配置用
        injectIfPresent(bean, cls, "crmConfig", new cn.kmbeast.crm.config.CrmConfig());
    }

    /** 字段存在才注入（沿继承链找） */
    private void injectIfPresent(Object bean, Class<?> cls, String name, Object value) {
        Class<?> cur = cls;
        while (cur != null && cur != Object.class) {
            try {
                Field f = cur.getDeclaredField(name);
                f.setAccessible(true);
                f.set(bean, value);
                return;
            } catch (NoSuchFieldException e) {
                cur = cur.getSuperclass();
            } catch (IllegalAccessException ignored) {
                return;
            }
        }
    }

    /** 给 bean 注入 HttpClientFactory（字段可能声明在父类） */
    private void injectFactory(Object bean, Class<?> cls) throws Exception {
        Class<?> cur = cls;
        while (cur != null && cur != Object.class) {
            try {
                Field f = cur.getDeclaredField("httpClientFactory");
                f.setAccessible(true);
                f.set(bean, factory);
                return;
            } catch (NoSuchFieldException e) {
                cur = cur.getSuperclass();
            }
        }
        throw new AssertionError(cls.getName() + " 上找不到 httpClientFactory 字段");
    }

    /**
     * 读取 bean 私有 {@code httpClient} 字段值。
     *
     * <p>字段可能声明在父类（如 BaseReActAgent），所以沿继承链找。
     */
    private Object readHttpClient(Object bean, Class<?> cls) throws Exception {
        Class<?> cur = cls;
        while (cur != null && cur != Object.class) {
            try {
                Field f = cur.getDeclaredField("httpClient");
                f.setAccessible(true);
                return f.get(bean);
            } catch (NoSuchFieldException e) {
                cur = cur.getSuperclass();
            }
        }
        throw new AssertionError(cls.getName() + " 上找不到 httpClient 字段——是否重构时改了字段名？");
    }
}
