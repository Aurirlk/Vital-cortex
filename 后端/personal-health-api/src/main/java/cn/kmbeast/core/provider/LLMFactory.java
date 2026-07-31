package cn.kmbeast.core.provider;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * LLM 工厂
 * 管理和切换不同的 LLM 供应商
 */
@Slf4j
@Component
public class LLMFactory {

    private final Map<String, LLMProvider> providers = new ConcurrentHashMap<>();

    public void registerProvider(LLMProvider provider) {
        providers.put(provider.getProviderName(), provider);
        log.info("Registered LLM provider: {}", provider.getProviderName());
    }

    public LLMProvider getProvider(String name) {
        return providers.get(name);
    }

    public LLMProvider getDefaultProvider() {
        return providers.values().stream()
                .filter(LLMProvider::isAvailable)
                .findFirst()
                .orElse(null);
    }
}
