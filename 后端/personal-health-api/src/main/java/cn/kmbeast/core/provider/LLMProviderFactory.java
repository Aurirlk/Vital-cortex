package cn.kmbeast.core.provider;

import cn.kmbeast.config.AiConfig;
import cn.kmbeast.core.http.HttpClientFactory;
import lombok.extern.slf4j.Slf4j;
import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * SEC-08: LLM provider factory.
 *
 * <p>Selects the active provider from {@code ai.provider} (deepseek | zhikangyun-local | local).
 * Unknown values fall back to deepseek and log a warning. Providers are created lazily and cached.
 */
@Slf4j
@Component
public class LLMProviderFactory {

    @Resource
    private AiConfig aiConfig;

    /** 2026-10-04：共享 HTTP 客户端（统一超时/连接池/线程池上限，避免各处自建导致线程与连接被放大 N 倍） */
    @jakarta.annotation.Resource
    private HttpClientFactory httpClientFactory;

    private OkHttpClient httpClient;

    private final Map<String, LLMProvider> cache = new ConcurrentHashMap<>();
    private volatile String cachedProviderId;

    @PostConstruct
    public void init() {
        String configured = aiConfig.getProvider();
        if (configured == null || configured.trim().isEmpty()) {
            configured = DeepSeekProvider.ID;
        }
        cachedProviderId = normalize(configured);
        // 2026-10-04：统一走共享工厂（原为自建，与其他 LLM 调用方重复占用连接池）
        this.httpClient = httpClientFactory.llmClient();
        log.info("[LLMProviderFactory] active provider = {} (configured: {})", cachedProviderId, configured);
    }

    private String normalize(String raw) {
        String v = raw.trim().toLowerCase();
        if (v.equals("local") || v.equals("vllm") || v.equals("zhikangyun-local") || v.equals("zhikangyunlocal")) {
            return LocalVllmProvider.ID;
        }
        if (v.equals("deepseek") || v.equals("deepseek-chat")) {
            return DeepSeekProvider.ID;
        }
        log.warn("[LLMProviderFactory] unknown provider '{}', falling back to deepseek", raw);
        return DeepSeekProvider.ID;
    }

    /** Active provider id (normalized). */
    public String getActiveProviderId() {
        return cachedProviderId;
    }

    public LLMProvider getProvider() {
        return getProvider(cachedProviderId);
    }

    public LLMProvider getProvider(String providerId) {
        String id = providerId == null || providerId.isEmpty() ? DeepSeekProvider.ID : normalize(providerId);
        return cache.computeIfAbsent(id, this::create);
    }

    private LLMProvider create(String id) {
        if (LocalVllmProvider.ID.equals(id)) {
            return new LocalVllmProvider(httpClient);
        }
        return new DeepSeekProvider(httpClient);
    }
}
