package cn.kmbeast.controller;

import cn.kmbeast.aop.Protector;
import cn.kmbeast.config.AiConfig;
import cn.kmbeast.pojo.api.ApiResult;
import cn.kmbeast.pojo.api.Result;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.*;

/**
 * AI配置管理接口（仅管理员）
 */
@Slf4j
@RestController
@RequestMapping(value = "/ai/config")
public class AiConfigController {

    @Resource
    private AiConfig aiConfig;

    /**
     * 获取所有支持的厂商列表
     */
    @Protector(role = "管理员")
    @GetMapping("/providers")
    public Result<List<Map<String, Object>>> getProviders() {
        List<Map<String, Object>> providers = new ArrayList<>();
        AiConfig.PROVIDERS.forEach((key, config) -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("key", key);
            item.put("name", config.getName());
            item.put("openaiBaseUrl", config.getOpenaiBaseUrl());
            item.put("anthropicBaseUrl", config.getAnthropicBaseUrl());
            item.put("models", config.getModels());
            providers.add(item);
        });
        return ApiResult.success(providers);
    }

    /**
     * 获取当前AI配置
     */
    @Protector(role = "管理员")
    @GetMapping("/get")
    public Result<Map<String, Object>> getConfig() {
        Map<String, Object> config = new LinkedHashMap<>();
        
        // 厂商信息
        config.put("provider", aiConfig.getProvider());
        
        // 普通对话配置
        Map<String, Object> chat = new LinkedHashMap<>();
        chat.put("apiKey", maskApiKey(aiConfig.getApiKey()));
        chat.put("apiUrl", aiConfig.getApiUrl());
        chat.put("model", aiConfig.getModel());
        config.put("chat", chat);
        
        // 深度思考配置
        Map<String, Object> reasoner = new LinkedHashMap<>();
        reasoner.put("apiKey", maskApiKey(aiConfig.getReasonerApiKey()));
        reasoner.put("apiUrl", aiConfig.getReasonerApiUrl());
        reasoner.put("model", aiConfig.getReasonerModel());
        config.put("reasoner", reasoner);
        
        // 联网搜索配置
        Map<String, Object> webSearch = new LinkedHashMap<>();
        webSearch.put("enabled", aiConfig.isWebSearchEnabled());
        webSearch.put("provider", aiConfig.getWebSearchProvider());
        
        Map<String, Object> bocha = new LinkedHashMap<>();
        bocha.put("apiKey", maskApiKey(aiConfig.getBochaApiKey()));
        bocha.put("apiUrl", aiConfig.getBochaApiUrl());
        webSearch.put("bocha", bocha);
        
        Map<String, Object> tavily = new LinkedHashMap<>();
        tavily.put("apiKey", maskApiKey(aiConfig.getTavilyApiKey()));
        tavily.put("apiUrl", aiConfig.getTavilyApiUrl());
        webSearch.put("tavily", tavily);
        
        Map<String, Object> duckduckgo = new LinkedHashMap<>();
        duckduckgo.put("apiUrl", aiConfig.getDuckduckgoApiUrl());
        webSearch.put("duckduckgo", duckduckgo);
        
        Map<String, Object> serper = new LinkedHashMap<>();
        serper.put("apiKey", maskApiKey(aiConfig.getSerperApiKey()));
        serper.put("apiUrl", aiConfig.getSerperApiUrl());
        webSearch.put("serper", serper);
        
        Map<String, Object> serpapi = new LinkedHashMap<>();
        serpapi.put("apiKey", maskApiKey(aiConfig.getSerpapiApiKey()));
        serpapi.put("apiUrl", aiConfig.getSerpapiApiUrl());
        webSearch.put("serpapi", serpapi);
        
        config.put("webSearch", webSearch);
        
        // Embedding配置
        Map<String, Object> embedding = new LinkedHashMap<>();
        embedding.put("apiKey", maskApiKey(aiConfig.getEmbeddingApiKey()));
        embedding.put("apiUrl", aiConfig.getEmbeddingApiUrl());
        embedding.put("model", aiConfig.getEmbeddingModel());
        config.put("embedding", embedding);
        
        // 通用配置
        Map<String, Object> common = new LinkedHashMap<>();
        common.put("connectTimeout", aiConfig.getConnectTimeout());
        common.put("readTimeout", aiConfig.getReadTimeout());
        common.put("maxTokens", aiConfig.getMaxTokens());
        common.put("maxHistoryRounds", aiConfig.getMaxHistoryRounds());
        config.put("common", common);
        
        // 状态信息
        config.put("apiKeyValid", aiConfig.isApiKeyValid());
        config.put("summary", aiConfig.getConfigSummary());
        
        return ApiResult.success(config);
    }

    /**
     * 切换AI厂商
     */
    @Protector(role = "管理员")
    @PostMapping("/switch-provider")
    public Result<Void> switchProvider(@RequestBody Map<String, String> request) {
        String provider = request.get("provider");
        if (provider == null || !AiConfig.PROVIDERS.containsKey(provider)) {
            return ApiResult.error("不支持的厂商: " + provider);
        }
        
        aiConfig.applyProvider(provider);
        aiConfig.persistConfig(); // 持久化保存
        log.info("[AI配置] 切换厂商: {}", provider);
        return ApiResult.success("厂商切换成功");
    }

    /**
     * 更新AI配置
     */
    @Protector(role = "管理员")
    @PostMapping("/update")
    public Result<Void> updateConfig(@RequestBody AiConfigUpdateRequest request) {
        try {
            // 更新厂商
            if (request.getProvider() != null && !request.getProvider().isEmpty()) {
                aiConfig.setProvider(request.getProvider());
            }
            
            // 更新普通对话配置
            if (request.getChat() != null) {
                ConfigItem chat = request.getChat();
                if (chat.getApiKey() != null && !chat.getApiKey().isEmpty()) {
                    aiConfig.setApiKey(chat.getApiKey());
                }
                if (chat.getApiUrl() != null && !chat.getApiUrl().isEmpty()) {
                    aiConfig.setApiUrl(chat.getApiUrl());
                }
                if (chat.getModel() != null && !chat.getModel().isEmpty()) {
                    aiConfig.setModel(chat.getModel());
                }
            }
            
            // 更新深度思考配置
            if (request.getReasoner() != null) {
                ConfigItem reasoner = request.getReasoner();
                if (reasoner.getApiKey() != null && !reasoner.getApiKey().isEmpty()) {
                    aiConfig.setReasonerApiKey(reasoner.getApiKey());
                }
                if (reasoner.getApiUrl() != null && !reasoner.getApiUrl().isEmpty()) {
                    aiConfig.setReasonerApiUrl(reasoner.getApiUrl());
                }
                if (reasoner.getModel() != null && !reasoner.getModel().isEmpty()) {
                    aiConfig.setReasonerModel(reasoner.getModel());
                }
            }
            
            // 更新联网搜索配置
            if (request.getWebSearch() != null) {
                WebSearchConfig webSearch = request.getWebSearch();
                if (webSearch.getProvider() != null) {
                    aiConfig.setWebSearchProvider(webSearch.getProvider());
                }
                if (webSearch.getEnabled() != null) {
                    aiConfig.setWebSearchEnabled(webSearch.getEnabled());
                }
                if (webSearch.getBocha() != null) {
                    if (webSearch.getBocha().getApiKey() != null) {
                        aiConfig.setBochaApiKey(webSearch.getBocha().getApiKey());
                    }
                    if (webSearch.getBocha().getApiUrl() != null) {
                        aiConfig.setBochaApiUrl(webSearch.getBocha().getApiUrl());
                    }
                }
                if (webSearch.getTavily() != null) {
                    if (webSearch.getTavily().getApiKey() != null) {
                        aiConfig.setTavilyApiKey(webSearch.getTavily().getApiKey());
                    }
                    if (webSearch.getTavily().getApiUrl() != null) {
                        aiConfig.setTavilyApiUrl(webSearch.getTavily().getApiUrl());
                    }
                }
                if (webSearch.getDuckduckgo() != null) {
                    if (webSearch.getDuckduckgo().getApiUrl() != null) {
                        aiConfig.setDuckduckgoApiUrl(webSearch.getDuckduckgo().getApiUrl());
                    }
                }
                if (webSearch.getSerper() != null) {
                    if (webSearch.getSerper().getApiKey() != null) {
                        aiConfig.setSerperApiKey(webSearch.getSerper().getApiKey());
                    }
                    if (webSearch.getSerper().getApiUrl() != null) {
                        aiConfig.setSerperApiUrl(webSearch.getSerper().getApiUrl());
                    }
                }
                if (webSearch.getSerpapi() != null) {
                    if (webSearch.getSerpapi().getApiKey() != null) {
                        aiConfig.setSerpapiApiKey(webSearch.getSerpapi().getApiKey());
                    }
                    if (webSearch.getSerpapi().getApiUrl() != null) {
                        aiConfig.setSerpapiApiUrl(webSearch.getSerpapi().getApiUrl());
                    }
                }
            }
            
            // 更新Embedding配置
            if (request.getEmbedding() != null) {
                ConfigItem embedding = request.getEmbedding();
                if (embedding.getApiKey() != null && !embedding.getApiKey().isEmpty()) {
                    aiConfig.setEmbeddingApiKey(embedding.getApiKey());
                }
                if (embedding.getApiUrl() != null && !embedding.getApiUrl().isEmpty()) {
                    aiConfig.setEmbeddingApiUrl(embedding.getApiUrl());
                }
                if (embedding.getModel() != null && !embedding.getModel().isEmpty()) {
                    aiConfig.setEmbeddingModel(embedding.getModel());
                }
            }
            
            // 更新通用配置
            if (request.getCommon() != null) {
                CommonConfig common = request.getCommon();
                if (common.getConnectTimeout() != null) {
                    aiConfig.setConnectTimeout(common.getConnectTimeout());
                }
                if (common.getReadTimeout() != null) {
                    aiConfig.setReadTimeout(common.getReadTimeout());
                }
                if (common.getMaxTokens() != null) {
                    aiConfig.setMaxTokens(common.getMaxTokens());
                }
                if (common.getMaxHistoryRounds() != null) {
                    aiConfig.setMaxHistoryRounds(common.getMaxHistoryRounds());
                }
            }
            
            log.info("[AI配置] 管理员更新了AI配置: {}", aiConfig.getConfigSummary());
            aiConfig.persistConfig(); // 持久化保存
            return ApiResult.success("配置更新成功");
        } catch (Exception e) {
            log.error("[AI配置] 更新配置失败", e);
            return ApiResult.error("配置更新失败: " + e.getMessage());
        }
    }

    /**
     * 重置为默认配置
     */
    @Protector(role = "管理员")
    @PostMapping("/reset")
    public Result<Void> resetConfig() {
        aiConfig.setProvider("deepseek");
        aiConfig.setApiUrl("https://api.deepseek.com/v1/chat/completions");
        aiConfig.setModel("deepseek-v4-flash");
        aiConfig.setReasonerApiUrl("https://api.deepseek.com/v1/chat/completions");
        aiConfig.setReasonerModel("deepseek-v4-pro");
        aiConfig.setWebSearchEnabled(true);
        aiConfig.setWebSearchProvider("auto");
        aiConfig.setEmbeddingApiUrl("https://api.deepseek.com/v1/embeddings");
        aiConfig.setEmbeddingModel("text-embedding-3-small");
        aiConfig.setConnectTimeout(30000);
        aiConfig.setReadTimeout(60000);
        aiConfig.setMaxTokens(4096);
        aiConfig.setMaxHistoryRounds(10);
        
        log.info("[AI配置] 管理员重置了AI配置为默认值");
        aiConfig.persistConfig(); // 持久化保存
        return ApiResult.success("配置已重置为默认值");
    }

    /**
     * 遮蔽API Key
     */
    private String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.isEmpty()) {
            return "";
        }
        if (apiKey.length() <= 11) {
            return "****";
        }
        return apiKey.substring(0, 7) + "****" + apiKey.substring(apiKey.length() - 4);
    }

    // ==================== DTO类 ====================

    @Data
    public static class AiConfigUpdateRequest {
        private String provider;
        private ConfigItem chat;
        private ConfigItem reasoner;
        private WebSearchConfig webSearch;
        private ConfigItem embedding;
        private CommonConfig common;
    }

    @Data
    public static class ConfigItem {
        private String apiKey;
        private String apiUrl;
        private String model;
    }

    @Data
    public static class WebSearchConfig {
        private String provider;
        private Boolean enabled;
        private SearchProviderConfig bocha;
        private SearchProviderConfig tavily;
        private SearchProviderConfig duckduckgo;
        private SearchProviderConfig serper;
        private SearchProviderConfig serpapi;
    }

    @Data
    public static class SearchProviderConfig {
        private String apiKey;
        private String apiUrl;
    }

    @Data
    public static class CommonConfig {
        private Integer connectTimeout;
        private Integer readTimeout;
        private Integer maxTokens;
        private Integer maxHistoryRounds;
    }
}
