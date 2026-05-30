package cn.kmbeast.crm.vectordb;

import cn.kmbeast.config.AiConfig;
import cn.kmbeast.crm.CrmException;
import cn.kmbeast.crm.config.CrmConfig;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class EmbeddingService {

    @Resource
    private CrmConfig crmConfig;

    @Resource
    private AiConfig aiConfig;

    private OkHttpClient httpClient;

    private static final MediaType JSON_MEDIA_TYPE = MediaType.get("application/json; charset=utf-8");

    private static final int MAX_RETRIES = 3;

    private static final long BASE_BACKOFF_MS = 500;

    private static final long MAX_BACKOFF_MS = 5000;

    private static final int CACHE_MAX = 500;

    private final Map<String, float[]> embeddingCache = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(aiConfig.getConnectTimeout(), TimeUnit.MILLISECONDS)
                .readTimeout(aiConfig.getReadTimeout(), TimeUnit.MILLISECONDS)
                .connectionPool(new ConnectionPool(10, 5, TimeUnit.MINUTES))
                .build();
    }

    public float[] embed(String text) {
        float[] cached = embeddingCache.get(text);
        if (cached != null) return cached;

        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            try {
                JSONObject body = new JSONObject();
                body.put("model", crmConfig.getEmbeddingModel());
                body.put("input", text);

                Request request = new Request.Builder()
                        .url(crmConfig.getEmbeddingApiUrl())
                        .addHeader("Authorization", "Bearer " + aiConfig.getApiKey())
                        .addHeader("Content-Type", "application/json")
                        .post(RequestBody.create(body.toJSONString(), JSON_MEDIA_TYPE))
                        .build();

                try (Response response = httpClient.newCall(request).execute()) {
                    if (response.code() == 429) {
                        long waitMs = Math.min(BASE_BACKOFF_MS * (1L << attempt), MAX_BACKOFF_MS);
                        log.warn("[Embedding] 触发限流, 等待{}ms后重试({}/{})", waitMs, attempt + 1, MAX_RETRIES);
                        sleep(waitMs);
                        continue;
                    }

                    if (!response.isSuccessful()) {
                        if (response.code() >= 500 && attempt < MAX_RETRIES - 1) {
                            long waitMs = Math.min(BASE_BACKOFF_MS * (1L << attempt), MAX_BACKOFF_MS);
                            log.warn("[Embedding] 服务端错误 {}, 等待{}ms后重试({}/{})",
                                    response.code(), waitMs, attempt + 1, MAX_RETRIES);
                            sleep(waitMs);
                            continue;
                        }
                        log.error("[Embedding] API返回非200: code={}", response.code());
                        throw CrmException.embeddingFailed("HTTP " + response.code());
                    }

                    String respBody = response.body().string();
                    JSONObject json = JSON.parseObject(respBody);
                    JSONArray data = json.getJSONArray("data");
                    if (data == null || data.isEmpty()) {
                        throw CrmException.embeddingFailed("返回数据为空");
                    }

                    JSONArray embeddingArr = data.getJSONObject(0).getJSONArray("embedding");
                    float[] vector = new float[embeddingArr.size()];
                    for (int i = 0; i < embeddingArr.size(); i++) {
                        vector[i] = embeddingArr.getFloatValue(i);
                    }

                    if (embeddingCache.size() < CACHE_MAX) {
                        embeddingCache.put(text, vector);
                    }
                    return vector;
                }
            } catch (CrmException e) {
                throw e;
            } catch (IOException e) {
                if (attempt < MAX_RETRIES - 1) {
                    long waitMs = Math.min(BASE_BACKOFF_MS * (1L << attempt), MAX_BACKOFF_MS);
                    log.warn("[Embedding] 网络异常, 等待{}ms后重试({}/{}): {}", waitMs, attempt + 1, MAX_RETRIES, e.getMessage());
                    sleep(waitMs);
                } else {
                    throw CrmException.embeddingFailed("网络异常: " + e.getMessage());
                }
            } catch (Exception e) {
                log.error("[Embedding] 嵌入向量生成失败: {}", e.getMessage());
                throw CrmException.embeddingFailed(e.getMessage());
            }
        }
        throw CrmException.embeddingFailed("重试" + MAX_RETRIES + "次后仍失败");
    }

    public List<float[]> batchEmbed(List<String> texts) {
        List<float[]> vectors = new ArrayList<>();
        List<String> uncached = new ArrayList<>();
        List<Integer> uncachedIndices = new ArrayList<>();

        for (int i = 0; i < texts.size(); i++) {
            float[] cached = embeddingCache.get(texts.get(i));
            if (cached != null) {
                vectors.add(cached);
            } else {
                uncached.add(texts.get(i));
                uncachedIndices.add(i);
            }
        }

        if (uncached.isEmpty()) return vectors;

        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            try {
                JSONArray inputArray = new JSONArray();
                for (String text : uncached) inputArray.add(text);

                JSONObject body = new JSONObject();
                body.put("model", crmConfig.getEmbeddingModel());
                body.put("input", inputArray);

                Request request = new Request.Builder()
                        .url(crmConfig.getEmbeddingApiUrl())
                        .addHeader("Authorization", "Bearer " + aiConfig.getApiKey())
                        .addHeader("Content-Type", "application/json")
                        .post(RequestBody.create(body.toJSONString(), JSON_MEDIA_TYPE))
                        .build();

                try (Response response = httpClient.newCall(request).execute()) {
                    if (response.code() == 429) {
                        long waitMs = Math.min(BASE_BACKOFF_MS * (1L << attempt), MAX_BACKOFF_MS);
                        log.warn("[Embedding] 批量限流, 等待{}ms后重试", waitMs);
                        sleep(waitMs);
                        continue;
                    }

                    if (!response.isSuccessful() && response.code() >= 500 && attempt < MAX_RETRIES - 1) {
                        long waitMs = Math.min(BASE_BACKOFF_MS * (1L << attempt), MAX_BACKOFF_MS);
                        sleep(waitMs);
                        continue;
                    }
                    if (!response.isSuccessful()) {
                        throw CrmException.embeddingFailed("批量HTTP " + response.code());
                    }

                    String respBody = response.body().string();
                    JSONObject json = JSON.parseObject(respBody);
                    JSONArray data = json.getJSONArray("data");
                    if (data == null || data.isEmpty()) {
                        throw CrmException.embeddingFailed("批量返回数据为空");
                    }

                    List<float[]> batchVectors = new ArrayList<>();
                    for (int i = 0; i < data.size(); i++) {
                        JSONArray embeddingArr = data.getJSONObject(i).getJSONArray("embedding");
                        float[] vector = new float[embeddingArr.size()];
                        for (int j = 0; j < embeddingArr.size(); j++) {
                            vector[j] = embeddingArr.getFloatValue(j);
                        }
                        batchVectors.add(vector);
                        if (embeddingCache.size() < CACHE_MAX && i < uncached.size()) {
                            embeddingCache.put(uncached.get(i), vector);
                        }
                    }

                    Map<Integer, float[]> resultMap = new TreeMap<>();
                    for (int j = 0; j < uncachedIndices.size(); j++) {
                        resultMap.put(uncachedIndices.get(j), batchVectors.get(j));
                    }
                    for (Map.Entry<Integer, float[]> entry : resultMap.entrySet()) {
                        if (entry.getKey() < vectors.size()) {
                            vectors.add(entry.getKey(), entry.getValue());
                        } else {
                            vectors.add(entry.getValue());
                        }
                    }
                    return vectors;
                }
            } catch (CrmException e) {
                throw e;
            } catch (IOException e) {
                if (attempt < MAX_RETRIES - 1) {
                    long waitMs = Math.min(BASE_BACKOFF_MS * (1L << attempt), MAX_BACKOFF_MS);
                    sleep(waitMs);
                } else {
                    throw CrmException.embeddingFailed("批量网络异常: " + e.getMessage());
                }
            } catch (Exception e) {
                log.error("[Embedding] 批量嵌入失败", e);
                throw CrmException.embeddingFailed("批量嵌入失败: " + e.getMessage());
            }
        }
        throw CrmException.embeddingFailed("批量重试" + MAX_RETRIES + "次后仍失败");
    }

    private void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
