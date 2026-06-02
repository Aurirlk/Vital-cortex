package cn.kmbeast.crm.service;

import cn.kmbeast.config.AiConfig;
import cn.kmbeast.crm.dto.WebSearchResult;
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
import java.util.concurrent.TimeUnit;

/**
 * 联网搜索服务（多搜索引擎支持）
 */
@Slf4j
@Service
public class WebSearchService {

    @Resource
    private AiConfig aiConfig;

    private OkHttpClient httpClient;

    private static final MediaType JSON_MEDIA_TYPE = MediaType.get("application/json; charset=utf-8");

    @PostConstruct
    public void init() {
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build();
    }

    /**
     * 搜索（根据配置的搜索引擎）
     */
    public List<WebSearchResult> search(String query) {
        if (query == null || query.trim().isEmpty()) {
            return Collections.emptyList();
        }

        String provider = aiConfig.getWebSearchProvider();
        log.info("[WebSearch] 使用搜索引擎: {}, 查询: {}", provider, query);

        switch (provider) {
            case "bocha":
                return searchWithBocha(query);
            case "tavily":
                return searchWithTavily(query);
            case "duckduckgo":
                return searchWithDuckDuckGo(query);
            case "serper":
                return searchWithSerper(query);
            case "serpapi":
                return searchWithSerpApi(query);
            case "auto":
            default:
                return searchWithAuto(query);
        }
    }

    /**
     * 自动搜索（优先博查，失败后Tavily，最后DuckDuckGo）
     */
    private List<WebSearchResult> searchWithAuto(String query) {
        try {
            List<WebSearchResult> results = searchWithBocha(query);
            if (results != null && !results.isEmpty()) return results;
        } catch (Exception e) {
            log.warn("[WebSearch] 博查搜索失败: {}", e.getMessage());
        }

        try {
            List<WebSearchResult> results = searchWithTavily(query);
            if (results != null && !results.isEmpty()) return results;
        } catch (Exception e) {
            log.warn("[WebSearch] Tavily搜索失败: {}", e.getMessage());
        }

        try {
            List<WebSearchResult> results = searchWithDuckDuckGo(query);
            if (results != null && !results.isEmpty()) return results;
        } catch (Exception e) {
            log.warn("[WebSearch] DuckDuckGo搜索失败: {}", e.getMessage());
        }

        log.warn("[WebSearch] 所有搜索引擎都失败: query={}", query);
        return Collections.emptyList();
    }

    /**
     * 博查AI搜索
     */
    private List<WebSearchResult> searchWithBocha(String query) {
        String apiUrl = aiConfig.getBochaApiUrl();
        String apiKey = aiConfig.getBochaApiKey();

        if (apiKey == null || apiKey.isEmpty()) {
            log.debug("[WebSearch] 博查API Key未配置");
            return Collections.emptyList();
        }

        JSONObject body = new JSONObject();
        body.put("query", query);
        body.put("freshness", "noLimit");
        body.put("summary", true);
        body.put("count", 5);

        Request request = new Request.Builder()
                .url(apiUrl)
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(body.toJSONString(), JSON_MEDIA_TYPE))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                log.warn("[WebSearch] 博查API返回错误: {}", response.code());
                return Collections.emptyList();
            }

            String respBody = response.body().string();
            JSONObject json = JSON.parseObject(respBody);
            List<WebSearchResult> results = new ArrayList<>();

            if (json.containsKey("data")) {
                JSONObject data = json.getJSONObject("data");
                if (data.containsKey("webPages")) {
                    JSONArray webPages = data.getJSONArray("webPages");
                    for (int i = 0; i < webPages.size(); i++) {
                        JSONObject page = webPages.getJSONObject(i);
                        results.add(WebSearchResult.builder()
                                .title(page.getString("name"))
                                .url(page.getString("url"))
                                .snippet(page.getString("snippet"))
                                .source(page.getString("siteName"))
                                .publishDate(page.getString("dateLastCrawled"))
                                .build());
                    }
                }
            }

            log.info("[WebSearch] 博查搜索成功: query={}, results={}", query, results.size());
            return results;
        } catch (Exception e) {
            log.error("[WebSearch] 博查搜索异常: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Tavily搜索
     */
    private List<WebSearchResult> searchWithTavily(String query) {
        String apiUrl = aiConfig.getTavilyApiUrl();
        String apiKey = aiConfig.getTavilyApiKey();

        if (apiKey == null || apiKey.isEmpty()) {
            log.debug("[WebSearch] Tavily API Key未配置");
            return Collections.emptyList();
        }

        JSONObject body = new JSONObject();
        body.put("query", query);
        body.put("search_depth", "basic");
        body.put("include_answer", true);
        body.put("include_raw_content", false);
        body.put("max_results", 5);

        Request request = new Request.Builder()
                .url(apiUrl)
                .addHeader("api-key", apiKey)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(body.toJSONString(), JSON_MEDIA_TYPE))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                log.warn("[WebSearch] Tavily API返回错误: {}", response.code());
                return Collections.emptyList();
            }

            String respBody = response.body().string();
            JSONObject json = JSON.parseObject(respBody);
            List<WebSearchResult> results = new ArrayList<>();

            if (json.containsKey("results")) {
                JSONArray resultsArray = json.getJSONArray("results");
                for (int i = 0; i < resultsArray.size(); i++) {
                    JSONObject item = resultsArray.getJSONObject(i);
                    results.add(WebSearchResult.builder()
                            .title(item.getString("title"))
                            .url(item.getString("url"))
                            .snippet(item.getString("content"))
                            .source(extractDomain(item.getString("url")))
                            .build());
                }
            }

            log.info("[WebSearch] Tavily搜索成功: query={}, results={}", query, results.size());
            return results;
        } catch (Exception e) {
            log.error("[WebSearch] Tavily搜索异常: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * DuckDuckGo搜索（免费，无需API Key）
     */
    private List<WebSearchResult> searchWithDuckDuckGo(String query) {
        String apiUrl = aiConfig.getDuckduckgoApiUrl();
        if (apiUrl == null || apiUrl.isEmpty()) {
            apiUrl = "https://api.duckduckgo.com/";
        }

        HttpUrl url = HttpUrl.parse(apiUrl).newBuilder()
                .addQueryParameter("q", query)
                .addQueryParameter("format", "json")
                .addQueryParameter("no_html", "1")
                .addQueryParameter("skip_disambig", "1")
                .build();

        Request request = new Request.Builder()
                .url(url)
                .addHeader("User-Agent", "Mozilla/5.0")
                .get()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                log.warn("[WebSearch] DuckDuckGo返回错误: {}", response.code());
                return Collections.emptyList();
            }

            String respBody = response.body().string();
            JSONObject json = JSON.parseObject(respBody);
            List<WebSearchResult> results = new ArrayList<>();

            if (json.containsKey("AbstractText") && !json.getString("AbstractText").isEmpty()) {
                results.add(WebSearchResult.builder()
                        .title(json.getString("Heading"))
                        .url(json.getString("AbstractURL"))
                        .snippet(json.getString("AbstractText"))
                        .source(json.getString("AbstractSource"))
                        .build());
            }

            if (json.containsKey("RelatedTopics")) {
                JSONArray topics = json.getJSONArray("RelatedTopics");
                for (int i = 0; i < Math.min(topics.size(), 5); i++) {
                    JSONObject topic = topics.getJSONObject(i);
                    if (topic.containsKey("Text") && topic.containsKey("FirstURL")) {
                        String text = topic.getString("Text");
                        results.add(WebSearchResult.builder()
                                .title(text.substring(0, Math.min(50, text.length())))
                                .url(topic.getString("FirstURL"))
                                .snippet(text)
                                .source("DuckDuckGo")
                                .build());
                    }
                }
            }

            log.info("[WebSearch] DuckDuckGo搜索成功: query={}, results={}", query, results.size());
            return results;
        } catch (Exception e) {
            log.error("[WebSearch] DuckDuckGo搜索异常: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Serper.dev搜索（Google搜索API）
     */
    private List<WebSearchResult> searchWithSerper(String query) {
        String apiUrl = aiConfig.getSerperApiUrl();
        String apiKey = aiConfig.getSerperApiKey();

        if (apiKey == null || apiKey.isEmpty()) {
            log.debug("[WebSearch] Serper API Key未配置");
            return Collections.emptyList();
        }

        if (apiUrl == null || apiUrl.isEmpty()) {
            apiUrl = "https://google.serper.dev/search";
        }

        JSONObject body = new JSONObject();
        body.put("q", query);
        body.put("num", 5);

        Request request = new Request.Builder()
                .url(apiUrl)
                .addHeader("X-API-KEY", apiKey)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(body.toJSONString(), JSON_MEDIA_TYPE))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                log.warn("[WebSearch] Serper返回错误: {}", response.code());
                return Collections.emptyList();
            }

            String respBody = response.body().string();
            JSONObject json = JSON.parseObject(respBody);
            List<WebSearchResult> results = new ArrayList<>();

            if (json.containsKey("organic")) {
                JSONArray organic = json.getJSONArray("organic");
                for (int i = 0; i < organic.size(); i++) {
                    JSONObject item = organic.getJSONObject(i);
                    results.add(WebSearchResult.builder()
                            .title(item.getString("title"))
                            .url(item.getString("link"))
                            .snippet(item.getString("snippet"))
                            .source(item.getString("link"))
                            .build());
                }
            }

            log.info("[WebSearch] Serper搜索成功: query={}, results={}", query, results.size());
            return results;
        } catch (Exception e) {
            log.error("[WebSearch] Serper搜索异常: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * SerpAPI搜索（Google/Bing搜索）
     */
    private List<WebSearchResult> searchWithSerpApi(String query) {
        String apiUrl = aiConfig.getSerpapiApiUrl();
        String apiKey = aiConfig.getSerpapiApiKey();

        if (apiKey == null || apiKey.isEmpty()) {
            log.debug("[WebSearch] SerpAPI Key未配置");
            return Collections.emptyList();
        }

        if (apiUrl == null || apiUrl.isEmpty()) {
            apiUrl = "https://serpapi.com/search";
        }

        HttpUrl url = HttpUrl.parse(apiUrl).newBuilder()
                .addQueryParameter("q", query)
                .addQueryParameter("api_key", apiKey)
                .addQueryParameter("num", "5")
                .addQueryParameter("engine", "google")
                .build();

        Request request = new Request.Builder()
                .url(url)
                .get()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                log.warn("[WebSearch] SerpAPI返回错误: {}", response.code());
                return Collections.emptyList();
            }

            String respBody = response.body().string();
            JSONObject json = JSON.parseObject(respBody);
            List<WebSearchResult> results = new ArrayList<>();

            if (json.containsKey("organic_results")) {
                JSONArray organic = json.getJSONArray("organic_results");
                for (int i = 0; i < organic.size(); i++) {
                    JSONObject item = organic.getJSONObject(i);
                    results.add(WebSearchResult.builder()
                            .title(item.getString("title"))
                            .url(item.getString("link"))
                            .snippet(item.getString("snippet"))
                            .source(extractDomain(item.getString("link")))
                            .build());
                }
            }

            log.info("[WebSearch] SerpAPI搜索成功: query={}, results={}", query, results.size());
            return results;
        } catch (Exception e) {
            log.error("[WebSearch] SerpAPI搜索异常: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 从URL提取域名
     */
    private String extractDomain(String url) {
        if (url == null) return "unknown";
        try {
            return url.replaceAll("https?://", "").replaceAll("/.*", "");
        } catch (Exception e) {
            return "unknown";
        }
    }

    /**
     * 格式化搜索结果为文本（用于注入Prompt）
     */
    public String formatResultsAsText(List<WebSearchResult> results) {
        if (results == null || results.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("【联网搜索结果】\n\n");

        for (int i = 0; i < results.size(); i++) {
            WebSearchResult r = results.get(i);
            sb.append(String.format("%d. %s\n", i + 1, r.getTitle()));
            sb.append("   ").append(r.getSnippet()).append("\n");
            sb.append("   来源：").append(r.getUrl()).append("\n\n");
        }

        return sb.toString();
    }
}
