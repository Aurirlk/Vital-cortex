package cn.kmbeast.core.provider;

import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * SEC-08: DeepSeek (or any OpenAI-compatible) provider with circuit breaker + retry.
 *
 * <p>Behavior:
 * <ul>
 *   <li>POST to {@code apiUrl} with {@code Authorization: Bearer <key>};</li>
 *   <li>retries 429/5xx up to 2 times with short backoff;</li>
 *   <li>wraps the whole call in {@link CircuitBreaker} (SEC-09).</li>
 * </ul>
 */
@Slf4j
public class DeepSeekProvider implements LLMProvider {

    public static final String ID = "deepseek";

    private static final MediaType JSON_MEDIA_TYPE = MediaType.get("application/json; charset=utf-8");
    private static final int MAX_RETRIES = 2;
    private static final long BACKOFF_MS = 500L;

    private final OkHttpClient httpClient;
    private final CircuitBreaker circuitBreaker;

    public DeepSeekProvider(OkHttpClient httpClient) {
        this.httpClient = httpClient;
        this.circuitBreaker = CircuitBreaker.defaultFor("llm-deepseek");
    }

    @Override
    public String getId() {
        return ID;
    }

    public CircuitBreaker getCircuitBreaker() {
        return circuitBreaker;
    }

    @Override
    public String chat(String requestBody, String apiUrl, String apiKey) throws IOException {
        circuitBreaker.beforeCall();
        try (Response resp = execute(requestBody, apiUrl, apiKey, false)) {
            String body = resp.body() != null ? resp.body().string() : "";
            circuitBreaker.onSuccess();
            return body;
        } catch (IOException e) {
            circuitBreaker.onFailure();
            throw e;
        } catch (RuntimeException e) {
            circuitBreaker.onFailure();
            throw e;
        }
    }

    @Override
    public Response stream(String requestBody, String apiUrl, String apiKey) throws IOException {
        circuitBreaker.beforeCall();
        try {
            Response result = execute(requestBody, apiUrl, apiKey, true);
            circuitBreaker.onSuccess();
            return result;
        } catch (IOException e) {
            circuitBreaker.onFailure();
            throw e;
        } catch (RuntimeException e) {
            circuitBreaker.onFailure();
            throw e;
        }
    }

    private Response execute(String requestBody, String apiUrl, String apiKey, boolean streaming) throws IOException {
        IOException last = null;
        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            Request.Builder builder = new Request.Builder()
                    .url(apiUrl)
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .addHeader("Content-Type", "application/json")
                    .post(RequestBody.create(requestBody, JSON_MEDIA_TYPE));
            if (streaming) {
                builder.addHeader("Accept", "text/event-stream");
            }
            Request request = builder.build();

            Response response = httpClient.newCall(request).execute();
            int code = response.code();
            if (code == 429 || code >= 500) {
                response.close();
                last = new IOException("HTTP " + code);
                if (attempt < MAX_RETRIES) {
                    long wait = BACKOFF_MS * (1L << attempt);
                    log.warn("[Provider:{}] HTTP {} retrying in {}ms (attempt {})", getId(), code, wait, attempt + 1);
                    try {
                        Thread.sleep(wait);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                    continue;
                }
            }
            if (!response.isSuccessful()) {
                String body = response.body() != null ? response.body().string() : "";
                response.close();
                throw new IOException("LLM HTTP " + code + " " + body);
            }
            return response;
        }
        throw last != null ? last : new IOException("LLM call failed after retries");
    }
}
