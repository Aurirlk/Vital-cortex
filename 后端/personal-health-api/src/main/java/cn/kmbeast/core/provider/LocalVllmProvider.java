package cn.kmbeast.core.provider;

import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.IOException;

/**
 * SEC-08: local vLLM provider ("zhikangyun-local").
 *
 * <p>Points at the locally deployed fine-tuned model served by vLLM
 * (see dir/server.py, OpenAI-compatible endpoint on port 8000, /v1).
 * No external key required; auth header is sent empty if key is blank.
 */
@Slf4j
public class LocalVllmProvider implements LLMProvider {

    public static final String ID = "zhikangyun-local";
    public static final String DEFAULT_BASE = "http://localhost:8000/v1";

    private static final MediaType JSON_MEDIA_TYPE = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient httpClient;
    private final CircuitBreaker circuitBreaker;

    public LocalVllmProvider(OkHttpClient httpClient) {
        this.httpClient = httpClient;
        this.circuitBreaker = CircuitBreaker.defaultFor("llm-local");
    }

    @Override
    public String getId() {
        return ID;
    }

    public CircuitBreaker getCircuitBreaker() {
        return circuitBreaker;
    }

    /**
     * Resolve a possibly-relative apiUrl against the vLLM base.
     * e.g. "/v1/chat/completions" -> "http://localhost:8000/v1/chat/completions"
     */
    public static String resolveUrl(String apiUrl, String base) {
        String b = (base == null || base.isEmpty()) ? DEFAULT_BASE : base;
        if (apiUrl == null || apiUrl.isEmpty()) {
            return b + "/chat/completions";
        }
        if (apiUrl.startsWith("http://") || apiUrl.startsWith("https://")) {
            return apiUrl;
        }
        return b.replaceAll("/+$", "") + "/" + apiUrl.replaceAll("^/+", "");
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
        String url = resolveUrl(apiUrl, DEFAULT_BASE);
        Request.Builder builder = new Request.Builder()
                .url(url)
                .post(RequestBody.create(requestBody, JSON_MEDIA_TYPE));
        if (apiKey != null && !apiKey.isEmpty()) {
            builder.addHeader("Authorization", "Bearer " + apiKey);
        }
        if (streaming) {
            builder.addHeader("Accept", "text/event-stream");
        }
        Response response = httpClient.newCall(builder.build()).execute();
        if (!response.isSuccessful()) {
            String body = response.body() != null ? response.body().string() : "";
            response.close();
            throw new IOException("Local LLM HTTP " + response.code() + " " + body);
        }
        return response;
    }
}
