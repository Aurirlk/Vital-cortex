package cn.kmbeast.core.provider;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * LLM factory (legacy, deprecated).
 *
 * @deprecated RAG-12 / SEC-08: dead code superseded by {@link LLMProviderFactory}.
 * Kept only for binary compatibility; never referenced by the business layer.
 * Register/get are adapted to the new {@link LLMProvider} contract ({@code getId()}).
 */
@Slf4j
@Component
@Deprecated
public class LLMFactory {

    private final Map<String, LLMProvider> providers = new ConcurrentHashMap<>();

    public void registerProvider(LLMProvider provider) {
        providers.put(provider.getId(), provider);
        log.info("Registered LLM provider: {}", provider.getId());
    }

    public LLMProvider getProvider(String name) {
        return providers.get(name);
    }

    public LLMProvider getDefaultProvider() {
        return providers.values().stream().findFirst().orElse(null);
    }
}
