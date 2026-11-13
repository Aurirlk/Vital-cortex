package cn.kmbeast.core.http;

import cn.kmbeast.config.AiConfig;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import okhttp3.ConnectionPool;
import okhttp3.Dispatcher;
import okhttp3.OkHttpClient;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 共享 HTTP 客户端工厂。
 *
 * <p><b>为什么需要它</b>：项目里有 10 处各自 {@code new OkHttpClient.Builder()}。
 * OkHttp 的设计前提是<b>全局共享少量实例</b>——每个实例都自带
 * {@link Dispatcher} 线程池（默认 maxRequests=64 / maxRequestsPerHost=5）
 * 和独立的 {@link ConnectionPool}。各自 new 意味着：
 * <ul>
 *   <li>线程数与连接数被放大 N 倍，10 个实例理论上限 640 个线程；</li>
 *   <li>到同一个 LLM 厂商的 TLS 连接无法复用，每次调用都要重新握手；</li>
 *   <li>超时策略各写各的（本项目就有 30000/60000、10s/15s、30s/60s 三套），
 *       线上出问题时无法统一调整。</li>
 * </ul>
 *
 * <p><b>分档而非统一</b>：不同调用方的合理超时差异很大——
 * LLM 推理可能几十秒，WebSearch/Dify 只需十几秒。强行统一会让快请求白等慢超时，
 * 或让慢请求被快超时误杀。所以按「档」共享，每档一个单例：
 * <ul>
 *   <li>{@link #llmClient()} —— LLM 推理 / Embedding / Agent 工具调用，
 *       超时取 {@link AiConfig#getConnectTimeout()} 与 {@link AiConfig#getReadTimeout()}；</li>
 *   <li>{@link #shortClient()} —— WebSearch、Dify 工作流等外部 HTTP 调用，
 *       固定 10s 连接 / 15s 读取。</li>
 * </ul>
 *
 * <p><b>为什么不用 Spring 注入 client</b>：OkHttp 官方推荐由应用自己持有单例并
 * 复用，而不是声明为 bean——因为它不是「业务依赖」，没有需要按 profile 替换的实现。
 * 这里同样用 {@code @PostConstruct} 延迟构建，配置项（超时）才能从
 * {@link AiConfig} 读到运行时值而不是构建期常量。
 *
 * <p><b>线程池是有界的</b>：Dispatcher 的 maxRequests 显式设成 64、
 * maxRequestsPerHost 设成 16（默认 5 对 LLM 场景偏紧，并发提问时会排队到 5）。
 * 配合调用方自己的有界线程池（见 {@code BaseReActAgent#toolExecutor}）形成两道闸门。
 *
 * @since 2026-10-04
 */
@Slf4j
@Component
public class HttpClientFactory {

    /** 短请求档：连接超时（秒） */
    private static final int SHORT_CONNECT_TIMEOUT_SEC = 10;
    /** 短请求档：读取超时（秒） */
    private static final int SHORT_READ_TIMEOUT_SEC = 15;
    /** 写超时（秒）——LLM 全是 POST，单独给一个宽松值 */
    private static final int LLM_WRITE_TIMEOUT_SEC = 60;

    private static final int MAX_REQUESTS = 64;
    private static final int MAX_REQUESTS_PER_HOST = 16;
    private static final int CONNECTION_POOL_SIZE = 10;
    private static final int CONNECTION_POOL_KEEP_MINUTES = 5;

    @jakarta.annotation.Resource
    private AiConfig aiConfig;

    private OkHttpClient llm;
    private OkHttpClient shortLived;

    @PostConstruct
    public void init() {
        this.llm = buildLlmClient();
        this.shortLived = buildShortClient();
        log.info("[HttpClientFactory] 初始化完成：llm档 connect={}ms read={}ms，short档 connect={}s read={}s",
                aiConfig.getConnectTimeout(), aiConfig.getReadTimeout(),
                SHORT_CONNECT_TIMEOUT_SEC, SHORT_READ_TIMEOUT_SEC);
    }

    /**
     * LLM / Embedding / Agent 工具调用用的共享客户端。
     *
     * <p>超时取自 {@link AiConfig}，可通过运行时配置中心调整（{@code ai.connect-timeout}、
     * {@code ai.read-timeout}），改完不重启也能生效。
     */
    public OkHttpClient llmClient() {
        return llm;
    }

    /**
     * 短请求档共享客户端（WebSearch、Dify 工作流等）。
     *
     * <p>固定 10s/15s：这类调用有明确上界，拖久了只会拖慢整体响应。
     */
    public OkHttpClient shortClient() {
        return shortLived;
    }

    private OkHttpClient buildLlmClient() {
        Dispatcher dispatcher = new Dispatcher();
        dispatcher.setMaxRequests(MAX_REQUESTS);
        dispatcher.setMaxRequestsPerHost(MAX_REQUESTS_PER_HOST);

        return new OkHttpClient.Builder()
                .connectTimeout(aiConfig.getConnectTimeout(), TimeUnit.MILLISECONDS)
                .readTimeout(aiConfig.getReadTimeout(), TimeUnit.MILLISECONDS)
                .writeTimeout(LLM_WRITE_TIMEOUT_SEC, TimeUnit.SECONDS)
                .connectionPool(new ConnectionPool(CONNECTION_POOL_SIZE, CONNECTION_POOL_KEEP_MINUTES,
                        TimeUnit.MINUTES))
                .dispatcher(dispatcher)
                .build();
    }

    private OkHttpClient buildShortClient() {
        Dispatcher dispatcher = new Dispatcher();
        dispatcher.setMaxRequests(MAX_REQUESTS);
        // 短请求通常打的是不同域名，按 host 放宽
        dispatcher.setMaxRequestsPerHost(MAX_REQUESTS_PER_HOST);

        return new OkHttpClient.Builder()
                .connectTimeout(SHORT_CONNECT_TIMEOUT_SEC, TimeUnit.SECONDS)
                .readTimeout(SHORT_READ_TIMEOUT_SEC, TimeUnit.SECONDS)
                .writeTimeout(SHORT_READ_TIMEOUT_SEC, TimeUnit.SECONDS)
                .connectionPool(new ConnectionPool(CONNECTION_POOL_SIZE, CONNECTION_POOL_KEEP_MINUTES,
                        TimeUnit.MINUTES))
                .dispatcher(dispatcher)
                .build();
    }
}
