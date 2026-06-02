package cn.kmbeast.config;

import com.alibaba.fastjson2.JSONObject;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.io.File;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 配置（支持多厂商）
 */
@Data
@Component
public class AiConfig {

    private static final Logger log = LoggerFactory.getLogger(AiConfig.class);

    @Resource
    private AiConfigPersistenceService persistenceService;

    // ==================== 厂商选择 ====================
    
    /**
     * 当前选择的AI厂商
     */
    @Value("${ai.provider:deepseek}")
    private String provider;

    // ==================== 普通对话配置 ====================
    
    @Value("${ai.chat.api-key:}")
    private String apiKey;

    @Value("${ai.chat.api-url:https://api.deepseek.com/v1/chat/completions}")
    private String apiUrl;

    @Value("${ai.chat.model:deepseek-v4-flash}")
    private String model;

    // ==================== 深度思考配置 ====================
    
    @Value("${ai.reasoner.api-key:}")
    private String reasonerApiKey;

    @Value("${ai.reasoner.api-url:https://api.deepseek.com/v1/chat/completions}")
    private String reasonerApiUrl;

    @Value("${ai.reasoner.model:deepseek-v4-pro}")
    private String reasonerModel;

    // ==================== 联网搜索配置 ====================
    
    @Value("${ai.websearch.enabled:true}")
    private boolean webSearchEnabled;

    @Value("${ai.websearch.provider:auto}")
    private String webSearchProvider;

    // 博查AI
    @Value("${ai.websearch.bocha.api-key:}")
    private String bochaApiKey;

    @Value("${ai.websearch.bocha.api-url:https://api.bochaai.com/v1/web-search}")
    private String bochaApiUrl;

    // Tavily
    @Value("${ai.websearch.tavily.api-key:}")
    private String tavilyApiKey;

    @Value("${ai.websearch.tavily.api-url:https://api.tavily.com/search}")
    private String tavilyApiUrl;

    // DuckDuckGo
    @Value("${ai.websearch.duckduckgo.api-url:https://api.duckduckgo.com/}")
    private String duckduckgoApiUrl;

    // Serper
    @Value("${ai.websearch.serper.api-key:}")
    private String serperApiKey;

    @Value("${ai.websearch.serper.api-url:https://google.serper.dev/search}")
    private String serperApiUrl;

    // SerpAPI
    @Value("${ai.websearch.serpapi.api-key:}")
    private String serpapiApiKey;

    @Value("${ai.websearch.serpapi.api-url:https://serpapi.com/search}")
    private String serpapiApiUrl;

    // ==================== Embedding配置 ====================
    
    @Value("${ai.embedding.api-key:}")
    private String embeddingApiKey;

    @Value("${ai.embedding.api-url:https://api.deepseek.com/v1/embeddings}")
    private String embeddingApiUrl;

    @Value("${ai.embedding.model:text-embedding-3-small}")
    private String embeddingModel;

    // ==================== 通用配置 ====================
    
    @Value("${ai.connect-timeout:30000}")
    private Integer connectTimeout;

    @Value("${ai.read-timeout:60000}")
    private Integer readTimeout;

    @Value("${ai.max-tokens:4096}")
    private Integer maxTokens;

    @Value("${ai.max-history-rounds:10}")
    private Integer maxHistoryRounds;

    @Value("${ai.vector-cache-dir:./vector_cache}")
    private String vectorCacheDir;

    // ==================== 厂商配置表 ====================
    
    public static final Map<String, ProviderConfig> PROVIDERS = new LinkedHashMap<>();
    
    static {
        PROVIDERS.put("deepseek", new ProviderConfig(
                "DeepSeek",
                "https://api.deepseek.com/v1/chat/completions",
                "https://api.deepseek.com/anthropic",
                Arrays.asList("deepseek-v4-flash", "deepseek-v4-pro", "deepseek-chat", "deepseek-reasoner")
        ));
        PROVIDERS.put("moonshot", new ProviderConfig(
                "Moonshot AI (Kimi)",
                "https://api.moonshot.cn/v1/chat/completions",
                "https://api.moonshot.cn/anthropic",
                Arrays.asList("kimi-k2.6", "moonshot-v1-8k", "moonshot-v1-32k")
        ));
        PROVIDERS.put("zhipu", new ProviderConfig(
                "智谱AI (GLM)",
                "https://open.bigmodel.cn/api/paas/v4/chat/completions",
                "https://open.bigmodel.cn/api/anthropic",
                Arrays.asList("glm-5.1", "glm-4.7", "glm-4-plus")
        ));
        PROVIDERS.put("qwen", new ProviderConfig(
                "阿里云 (通义千问)",
                "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions",
                "https://dashscope.aliyuncs.com/apps/anthropic",
                Arrays.asList(
                        "qwen3.7-max", "qwen3.6-max-preview", "qwen3-max", "qwen-max",
                        "qwen3.6-plus", "qwen3.5-plus", "qwen-plus",
                        "qwen3.6-flash", "qwen3.5-flash", "qwen-flash",
                        "qwen-turbo",
                        "qwen3-coder-plus", "qwen3-coder-flash", "qwen-coder-plus", "qwen-coder-turbo",
                        "qwq-plus"
                )
        ));
        PROVIDERS.put("minimax", new ProviderConfig(
                "MiniMax",
                "https://api.minimaxi.com/v1/chat/completions",
                "https://api.minimaxi.com/anthropic",
                Arrays.asList("MiniMax-M2.7", "M2.7-HighSpeed")
        ));
        PROVIDERS.put("baidu", new ProviderConfig(
                "百度 (文心一言)",
                "https://qianfan.baidubce.com/v2/chat/completions",
                null,
                Arrays.asList("ernie-5.0", "ernie-4.0", "ernie-3.5")
        ));
        PROVIDERS.put("bytedance", new ProviderConfig(
                "字节跳动 (豆包)",
                "https://ark.cn-beijing.volces.com/api/v3/chat/completions",
                null,
                Arrays.asList("doubao-seed-2.0-pro", "doubao-1.5-pro")
        ));
        PROVIDERS.put("tencent", new ProviderConfig(
                "腾讯 (混元)",
                "https://api.hunyuan.cloud.tencent.com/v1/chat/completions",
                null,
                Arrays.asList("hunyuan-turbo", "hunyuan-large")
        ));
        PROVIDERS.put("01ai", new ProviderConfig(
                "零一万物 (Yi)",
                "https://api.lingyiwanwu.com/v1/chat/completions",
                null,
                Arrays.asList("yi-large", "yi-lightning")
        ));
        PROVIDERS.put("baichuan", new ProviderConfig(
                "百川智能",
                "https://api.baichuan-ai.com/v1/chat/completions",
                null,
                Arrays.asList("baichuan-4", "baichuan-turbo")
        ));
        PROVIDERS.put("stepfun", new ProviderConfig(
                "阶跃星辰",
                "https://api.stepfun.com/v1/chat/completions",
                null,
                Arrays.asList("step-2-16k", "step-2-32k")
        ));
        PROVIDERS.put("xiaomi", new ProviderConfig(
                "小米 (MiMo)",
                "https://api.xiaomimimo.com/v1/chat/completions",
                "https://api.xiaomimimo.com/anthropic",
                Arrays.asList("mimo-v2.5", "mimo-v2.5-pro")
        ));
    }

    /**
     * 厂商配置
     */
    @Data
    public static class ProviderConfig {
        private String name;
        private String openaiBaseUrl;
        private String anthropicBaseUrl;
        private List<String> models;

        public ProviderConfig(String name, String openaiBaseUrl, String anthropicBaseUrl, List<String> models) {
            this.name = name;
            this.openaiBaseUrl = openaiBaseUrl;
            this.anthropicBaseUrl = anthropicBaseUrl;
            this.models = models;
        }
    }

    /**
     * 初始化配置
     */
    @PostConstruct
    public void init() {
        // 从持久化文件加载配置
        loadFromPersistence();
        
        // 如果深度思考的API Key为空，则使用普通对话的API Key
        if (reasonerApiKey == null || reasonerApiKey.isEmpty()) {
            reasonerApiKey = apiKey;
        }
        if (embeddingApiKey == null || embeddingApiKey.isEmpty()) {
            embeddingApiKey = apiKey;
        }

        log.info("[AI] 配置初始化完成:");
        log.info("  厂商: {}", provider);
        log.info("  普通对话: model={}, apiUrl={}", model, apiUrl);
        log.info("  深度思考: model={}, apiUrl={}", reasonerModel, reasonerApiUrl);
        log.info("  联网搜索: provider={}, enabled={}", webSearchProvider, webSearchEnabled);
        log.info("  Embedding: model={}", embeddingModel);

        // 创建缓存目录
        File cacheDir = new File(vectorCacheDir);
        if (!cacheDir.exists()) {
            boolean created = cacheDir.mkdirs();
            if (created) {
                log.info("[AI] 缓存目录已创建: {}", cacheDir.getAbsolutePath());
            }
        }
    }
    
    /**
     * 从持久化文件加载配置
     */
    private void loadFromPersistence() {
        try {
            JSONObject saved = persistenceService.loadConfig();
            if (saved == null) {
                return;
            }
            
            // 加载基础配置
            if (saved.containsKey("provider")) this.provider = saved.getString("provider");
            if (saved.containsKey("apiUrl")) this.apiUrl = saved.getString("apiUrl");
            if (saved.containsKey("model")) this.model = saved.getString("model");
            if (saved.containsKey("apiKey") && !saved.getString("apiKey").isEmpty()) {
                this.apiKey = saved.getString("apiKey");
            }
            
            // 加载深度思考配置
            if (saved.containsKey("reasonerApiUrl")) this.reasonerApiUrl = saved.getString("reasonerApiUrl");
            if (saved.containsKey("reasonerModel")) this.reasonerModel = saved.getString("reasonerModel");
            if (saved.containsKey("reasonerApiKey") && !saved.getString("reasonerApiKey").isEmpty()) {
                this.reasonerApiKey = saved.getString("reasonerApiKey");
            }
            
            // 加载联网搜索配置
            if (saved.containsKey("webSearchEnabled")) this.webSearchEnabled = saved.getBooleanValue("webSearchEnabled");
            if (saved.containsKey("webSearchProvider")) this.webSearchProvider = saved.getString("webSearchProvider");
            if (saved.containsKey("bochaApiKey")) this.bochaApiKey = saved.getString("bochaApiKey");
            if (saved.containsKey("bochaApiUrl")) this.bochaApiUrl = saved.getString("bochaApiUrl");
            if (saved.containsKey("tavilyApiKey")) this.tavilyApiKey = saved.getString("tavilyApiKey");
            if (saved.containsKey("tavilyApiUrl")) this.tavilyApiUrl = saved.getString("tavilyApiUrl");
            if (saved.containsKey("duckduckgoApiUrl")) this.duckduckgoApiUrl = saved.getString("duckduckgoApiUrl");
            if (saved.containsKey("serperApiKey")) this.serperApiKey = saved.getString("serperApiKey");
            if (saved.containsKey("serperApiUrl")) this.serperApiUrl = saved.getString("serperApiUrl");
            if (saved.containsKey("serpapiApiKey")) this.serpapiApiKey = saved.getString("serpapiApiKey");
            if (saved.containsKey("serpapiApiUrl")) this.serpapiApiUrl = saved.getString("serpapiApiUrl");
            
            // 加载Embedding配置
            if (saved.containsKey("embeddingApiUrl")) this.embeddingApiUrl = saved.getString("embeddingApiUrl");
            if (saved.containsKey("embeddingModel")) this.embeddingModel = saved.getString("embeddingModel");
            if (saved.containsKey("embeddingApiKey") && !saved.getString("embeddingApiKey").isEmpty()) {
                this.embeddingApiKey = saved.getString("embeddingApiKey");
            }
            
            // 加载通用配置
            if (saved.containsKey("connectTimeout")) this.connectTimeout = saved.getInteger("connectTimeout");
            if (saved.containsKey("readTimeout")) this.readTimeout = saved.getInteger("readTimeout");
            if (saved.containsKey("maxTokens")) this.maxTokens = saved.getInteger("maxTokens");
            if (saved.containsKey("maxHistoryRounds")) this.maxHistoryRounds = saved.getInteger("maxHistoryRounds");
            
            log.info("[AI] 已从持久化文件加载配置");
        } catch (Exception e) {
            log.warn("[AI] 加载持久化配置失败，使用默认配置", e);
        }
    }

    /**
     * 获取有效配置的摘要信息（隐藏敏感信息）
     */
    public String getConfigSummary() {
        ProviderConfig pc = PROVIDERS.get(provider);
        String providerName = pc != null ? pc.getName() : provider;
        return String.format(
                "厂商: %s | 普通: %s | 深度思考: %s | 联网搜索: %s (%s) | Embedding: %s",
                providerName,
                model,
                reasonerModel,
                webSearchProvider,
                webSearchEnabled ? "已启用" : "已禁用",
                embeddingModel
        );
    }

    /**
     * 验证API Key是否有效（非空）
     */
    public boolean isApiKeyValid() {
        return apiKey != null && !apiKey.isEmpty() && !apiKey.equals("sk-xxx");
    }

    /**
     * 根据厂商更新配置
     */
    public void applyProvider(String providerKey) {
        ProviderConfig pc = PROVIDERS.get(providerKey);
        if (pc == null) return;
        
        this.provider = providerKey;
        this.apiUrl = pc.getOpenaiBaseUrl();
        if (pc.getModels() != null && !pc.getModels().isEmpty()) {
            this.model = pc.getModels().get(0);
        }
        if (pc.getAnthropicBaseUrl() != null) {
            this.reasonerApiUrl = pc.getOpenaiBaseUrl();
        }
    }
    
    /**
     * 保存当前配置到持久化文件
     */
    public void persistConfig() {
        persistenceService.saveConfig(this);
    }
}
