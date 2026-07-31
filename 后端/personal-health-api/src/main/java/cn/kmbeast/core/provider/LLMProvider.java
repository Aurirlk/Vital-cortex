package cn.kmbeast.core.provider;

import okhttp3.Response;

import java.io.IOException;

/**
 * SEC-08: abstraction over upstream LLM API providers.
 *
 * <p>Replaces the hard-coded vendor branches inside the service layer. Each implementation
 * owns the HTTP transport, auth header, and (optionally) a {@link CircuitBreaker}.
 * The caller still selects the endpoint (chat / reasoner / local vLLM) and passes url+key,
 * keeping the decision surface small while the factory activates runtime provider switching.
 */
public interface LLMProvider {

    /**
     * Provider id, e.g. "deepseek", "zhikangyun-local".
     */
    String getId();

    /**
     * Non-streaming chat completion. Returns the raw response body string.
     *
     * @param requestBody full JSON payload (model/messages/...)
     * @param apiUrl      endpoint base url
     * @param apiKey      bearer token
     * @throws IOException on transport / HTTP errors
     * @throws IllegalStateException when the circuit breaker is open
     */
    String chat(String requestBody, String apiUrl, String apiKey) throws IOException;

    /**
     * Streaming chat completion. Returns the OkHttp response; the caller reads the SSE stream.
     * The response must be closed by the caller (try-with-resources).
     */
    Response stream(String requestBody, String apiUrl, String apiKey) throws IOException;
}
