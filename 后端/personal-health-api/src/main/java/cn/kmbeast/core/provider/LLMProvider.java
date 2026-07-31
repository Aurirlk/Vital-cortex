package cn.kmbeast.core.provider;

/**
 * LLM 供应商接口
 */
public interface LLMProvider {
    String chat(String message, String systemPrompt);
    String getProviderName();
    boolean isAvailable();
}
