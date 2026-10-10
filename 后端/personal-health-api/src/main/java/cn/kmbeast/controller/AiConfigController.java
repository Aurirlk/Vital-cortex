package cn.kmbeast.controller;

import cn.kmbeast.aop.Protector;
import cn.kmbeast.config.AiConfig;
import cn.kmbeast.pojo.api.ApiResult;
import cn.kmbeast.pojo.api.Result;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import cn.kmbeast.config.AiPromptConfig;
import cn.kmbeast.context.LocalThreadHolder;
import cn.kmbeast.core.auth.AuthSessionManager;
import cn.kmbeast.mapper.UserMapper;
import cn.kmbeast.pojo.entity.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import jakarta.annotation.Resource;
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

    @Resource
    private UserMapper userMapper;

    @Resource
    private PasswordEncoder passwordEncoder;

    @Resource
    private AuthSessionManager authSessionManager;


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
     * 判断前端回传的 apiKey 是否只是掩码占位（而非真实密钥）。
     *
     * <p>2026-10-07 新增。{@code /get} 为安全只返回掩码（{@code sk-xxx****abcd}），
     * 前端加载后原样回传；若后端照单全收，就会<b>把掩码当真实 key 落库</b>，
     * 导致重启后模型读到 {@code ****} 而完全不可用 —— 这正是"每次重启都要重填"的根因。
     * 掩码值一律视为「未修改」，保留库中真实密钥。</p>
     *
     * @return true 表示这是掩码占位，不应写入
     */
private boolean isMaskedApiKey(String apiKey) {
    if (apiKey == null || apiKey.isEmpty()) {
        return true;
    }
    // 统一掩码 / 含星号 / 前端占位符
    return apiKey.contains("****")
            || apiKey.contains("******")
            || "sk-xxx".equals(apiKey)
            || apiKey.matches("^[xX*]+$");
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
                if (!isMaskedApiKey(chat.getApiKey())) {
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
                if (!isMaskedApiKey(reasoner.getApiKey())) {
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
                    if (!isMaskedApiKey(webSearch.getBocha().getApiKey())) {
                        aiConfig.setBochaApiKey(webSearch.getBocha().getApiKey());
                    }
                    if (webSearch.getBocha().getApiUrl() != null) {
                        aiConfig.setBochaApiUrl(webSearch.getBocha().getApiUrl());
                    }
                }
                if (webSearch.getTavily() != null) {
                    if (!isMaskedApiKey(webSearch.getTavily().getApiKey())) {
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
                    if (!isMaskedApiKey(webSearch.getSerper().getApiKey())) {
                        aiConfig.setSerperApiKey(webSearch.getSerper().getApiKey());
                    }
                    if (webSearch.getSerper().getApiUrl() != null) {
                        aiConfig.setSerperApiUrl(webSearch.getSerper().getApiUrl());
                    }
                }
                if (webSearch.getSerpapi() != null) {
                    if (!isMaskedApiKey(webSearch.getSerpapi().getApiKey())) {
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
                if (!isMaskedApiKey(embedding.getApiKey())) {
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
     * 获取所有可用模型列表（含全部厂商的 models + 本地模型）
     */
    @Protector(role = "管理员")
    @GetMapping("/models")
    public Result<List<Map<String, Object>>> getModels() {
        List<Map<String, Object>> models = new ArrayList<>();
        AiConfig.PROVIDERS.forEach((key, config) -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("providerKey", key);
            item.put("providerName", config.getName());
            item.put("models", config.getModels());
            item.put("current", key.equals(aiConfig.getProvider()));
            models.add(item);
        });
        return ApiResult.success(models);
    }

    /**
     * 切换当前使用模型（同时更新 provider + model 字段）
     */
    @Protector(role = "管理员")
    @PostMapping("/switch-model")
    public Result<Void> switchModel(@RequestBody Map<String, String> request) {
        String providerKey = request.get("providerKey");
        String model = request.get("model");

        if (providerKey == null || !AiConfig.PROVIDERS.containsKey(providerKey)) {
            return ApiResult.error("不支持的厂商: " + providerKey);
        }

        AiConfig.ProviderConfig pc = AiConfig.PROVIDERS.get(providerKey);
        aiConfig.setProvider(providerKey);
        aiConfig.setApiUrl(pc.getOpenaiBaseUrl());

        if (model != null && !model.isEmpty()) {
            aiConfig.setModel(model);
        } else if (pc.getModels() != null && !pc.getModels().isEmpty()) {
            aiConfig.setModel(pc.getModels().get(0));
        }

        aiConfig.persistConfig();
        log.info("[AI配置] 切换模型: provider={}, model={}", providerKey, aiConfig.getModel());
        return ApiResult.success("模型切换成功");
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

    // ========================================================================
    // 以下端点原位于 AiController（类级前缀 /ai），2026-10-07 迁入本类，
    // 以统一 /ai/config URL 空间的鉴权口径（此前两个控制器瓜分同一前缀，
    // 管理员端点与普通用户端点混在一起，加端点时极易漏配 @Protector）。
    // 对外路径【完全不变】，前端零改动。
    // ========================================================================

    /**
     * 获取 AI 能力开关（Phase A/B：图片多模态 + VIP 分级；语音在 roadmap §1.1 接入后扩展）。
     * 前端据此决定图片入口是否可用、上下文/图片档位（VIP 512K/10张 vs 普通 128K/3张）。
     */
    @Protector
    @GetMapping("/capabilities")
    public Result<Map<String, Object>> getCapabilities() {
        Integer userId = LocalThreadHolder.getUserId();
        boolean vip = authSessionManager.isVip(userId);
        Map<String, Object> data = new HashMap<>();
        data.put("visionEnabled", aiConfig.isVisionEnabled());
        data.put("visionModel", aiConfig.getVisionModel());
        data.put("isVip", vip);
        data.put("maxContext", vip ? aiConfig.getContextVip() : aiConfig.getContextNormal());
        data.put("maxImages", vip ? aiConfig.getMaxImagesVip() : aiConfig.getMaxImagesNormal());
        data.put("asrEnabled", false);
        data.put("ttsEnabled", false);
        return ApiResult.success(data);
    }


    // ==================== AI医生配置管理接口 ====================

    /**
     * 获取所有AI医生角色配置
     */
    @Protector(role = "管理员")
    @GetMapping("/list")
    public Result<List<Map<String, Object>>> getAiDoctorConfigs() {
        return ApiResult.success(AiPromptConfig.getAllConfigs());
    }

    /**
     * 获取指定角色配置
     */
    @Protector(role = "管理员")
    @GetMapping("/{role}")
    public Result<Map<String, Object>> getAiDoctorConfig(@PathVariable String role) {
        AiPromptConfig.PresetConfig config = AiPromptConfig.getConfig(role);
        if (config == null) {
            return ApiResult.error("角色不存在");
        }
        Map<String, Object> result = new HashMap<>();
        result.put("key", role);
        result.put("systemPrompt", config.getSystemPrompt());
        result.put("temperature", config.getTemperature());
        result.put("topP", config.getTopP());
        result.put("maxTokens", config.getMaxTokens());
        result.put("presencePenalty", config.getPresencePenalty());
        result.put("frequencyPenalty", config.getFrequencyPenalty());
        result.put("repetitionPenalty", config.getRepetitionPenalty());
        result.put("contextRounds", config.getContextRounds());
        result.put("maxReplyLength", config.getMaxReplyLength());
        return ApiResult.success(result);
    }

    /**
     * 更新指定角色配置
     */
    @Protector(role = "管理员")
    @PutMapping("/{role}")
    public Result<Void> updateAiDoctorConfig(@PathVariable String role,
                                              @RequestBody Map<String, Object> configData) {
        AiPromptConfig.PresetConfig existing = AiPromptConfig.getConfig(role);
        if (existing == null) {
            return ApiResult.error("角色不存在");
        }

        // 修改配置需要管理员密码验证
        String password = (String) configData.get("password");
        if (password == null || password.isEmpty()) {
            return ApiResult.error("请输入管理员密码");
        }
        Integer userId = LocalThreadHolder.getUserId();
        User admin = userMapper.getByActive(User.builder().id(userId).build());
        if (admin == null || !passwordEncoder.matches(password, admin.getUserPwd())) {
            return ApiResult.error("密码验证失败");
        }

        String systemPrompt = (String) configData.getOrDefault("systemPrompt", existing.getSystemPrompt());
        Double temperature = configData.containsKey("temperature")
                ? ((Number) configData.get("temperature")).doubleValue() : existing.getTemperature();
        Double topP = configData.containsKey("topP")
                ? ((Number) configData.get("topP")).doubleValue() : existing.getTopP();
        Integer maxTokens = configData.containsKey("maxTokens")
                ? (configData.get("maxTokens") == null ? null : ((Number) configData.get("maxTokens")).intValue())
                : existing.getMaxTokens();
        Double presencePenalty = configData.containsKey("presencePenalty")
                ? (configData.get("presencePenalty") == null ? null : ((Number) configData.get("presencePenalty")).doubleValue())
                : existing.getPresencePenalty();
        Double frequencyPenalty = configData.containsKey("frequencyPenalty")
                ? (configData.get("frequencyPenalty") == null ? null : ((Number) configData.get("frequencyPenalty")).doubleValue())
                : existing.getFrequencyPenalty();
        Double repetitionPenalty = configData.containsKey("repetitionPenalty")
                ? (configData.get("repetitionPenalty") == null ? null : ((Number) configData.get("repetitionPenalty")).doubleValue())
                : existing.getRepetitionPenalty();
        Integer contextRounds = configData.containsKey("contextRounds")
                ? (configData.get("contextRounds") == null ? null : ((Number) configData.get("contextRounds")).intValue())
                : existing.getContextRounds();
        Integer maxReplyLength = configData.containsKey("maxReplyLength")
                ? (configData.get("maxReplyLength") == null ? null : ((Number) configData.get("maxReplyLength")).intValue())
                : existing.getMaxReplyLength();

        // 参数校验
        if (temperature == null || temperature < 0 || temperature > 2) {
            return ApiResult.error("Temperature 必须在 0-2 之间");
        }
        if (topP == null || topP < 0 || topP > 1) {
            return ApiResult.error("Top-P 必须在 0-1 之间");
        }
        if (maxTokens != null && (maxTokens < 1 || maxTokens > 8192)) {
            return ApiResult.error("Max Tokens 必须在 1-8192 之间");
        }
        if (presencePenalty != null && (presencePenalty < -2 || presencePenalty > 2)) {
            return ApiResult.error("Presence Penalty 必须在 -2~2 之间");
        }
        if (frequencyPenalty != null && (frequencyPenalty < -2 || frequencyPenalty > 2)) {
            return ApiResult.error("Frequency Penalty 必须在 -2~2 之间");
        }
        if (repetitionPenalty != null && (repetitionPenalty < 0 || repetitionPenalty > 2)) {
            return ApiResult.error("重复惩罚必须在 0~2 之间（>1 减少重复，<1 鼓励重复）");
        }
        if (contextRounds != null && (contextRounds < 0 || contextRounds > 50)) {
            return ApiResult.error("上下文轮数必须在 0~50 之间");
        }
        if (maxReplyLength != null && (maxReplyLength < 0 || maxReplyLength > 32768)) {
            return ApiResult.error("最大回复长度必须在 0~32768 之间（0 = 不限制）");
        }

        AiPromptConfig.updateConfig(role, new AiPromptConfig.PresetConfig(
                systemPrompt, temperature, topP, maxTokens, presencePenalty, frequencyPenalty,
                repetitionPenalty, contextRounds, maxReplyLength));
        log.info("[AI配置] 管理员 {} 更新角色 {} 配置: temp={}, topP={}, maxTokens={}, presencePenalty={}, frequencyPenalty={}, repetitionPenalty={}, contextRounds={}, maxReplyLength={}",
                userId, role, temperature, topP, maxTokens, presencePenalty, frequencyPenalty,
                repetitionPenalty, contextRounds, maxReplyLength);
        return ApiResult.success("配置已更新");
    }

    /**
     * 重置指定角色为默认配置（需要密码验证）
     */
    @Protector(role = "管理员")
    @PostMapping("/{role}/reset")
    public Result<Void> resetAiDoctorConfig(@PathVariable String role,
                                             @RequestBody Map<String, String> body) {
        String password = body.get("password");
        if (password == null || password.isEmpty()) {
            return ApiResult.error("请输入管理员密码");
        }

        // 验证当前管理员密码
        Integer userId = LocalThreadHolder.getUserId();
        User admin = userMapper.getByActive(User.builder().id(userId).build());
        if (admin == null || !passwordEncoder.matches(password, admin.getUserPwd())) {
            return ApiResult.error("密码验证失败");
        }

        AiPromptConfig.PresetConfig existing = AiPromptConfig.getConfig(role);
        if (existing == null) {
            return ApiResult.error("角色不存在");
        }

        boolean success = AiPromptConfig.resetToDefault(role);
        if (success) {
            log.info("[AI配置] 管理员 {} 重置角色 {} 为默认配置", userId, role);
            return ApiResult.success("已恢复默认提示词");
        }
        return ApiResult.error("重置失败，角色不存在");
    }

    /**
     * 重置所有角色为默认配置（需要密码验证）
     */
    @Protector(role = "管理员")
    @PostMapping("/reset-all")
    public Result<Void> resetAllAiDoctorConfigs(@RequestBody Map<String, String> body) {
        String password = body.get("password");
        if (password == null || password.isEmpty()) {
            return ApiResult.error("请输入管理员密码");
        }

        Integer userId = LocalThreadHolder.getUserId();
        User admin = userMapper.getByActive(User.builder().id(userId).build());
        if (admin == null || !passwordEncoder.matches(password, admin.getUserPwd())) {
            return ApiResult.error("密码验证失败");
        }

        AiPromptConfig.resetAllToDefault();
        log.info("[AI配置] 管理员 {} 重置所有角色为默认配置", userId);
        return ApiResult.success("已恢复所有角色默认提示词");
    }

}
