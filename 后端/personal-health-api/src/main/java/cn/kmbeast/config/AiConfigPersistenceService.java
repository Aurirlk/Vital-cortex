package cn.kmbeast.config;

import cn.kmbeast.mapper.AiConfigMapper;
import cn.kmbeast.pojo.entity.AiConfigEntity;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

/**
 * AI配置持久化服务
 * 使用MySQL存储配置（明文存储）
 */
@Slf4j
@Service
public class AiConfigPersistenceService {

    @Resource
    private AiConfigMapper aiConfigMapper;

    /**
     * 保存配置到MySQL（明文）
     */
    public void saveConfig(AiConfig config) {
        try {
            List<AiConfigEntity> configs = new ArrayList<>();
            
            configs.add(buildConfig("provider", config.getProvider(), "AI厂商"));
            configs.add(buildConfig("api_url", config.getApiUrl(), "API地址"));
            configs.add(buildConfig("model", config.getModel(), "模型"));
            configs.add(buildConfig("api_key", config.getApiKey(), "API Key"));
            configs.add(buildConfig("reasoner_api_url", config.getReasonerApiUrl(), "深度思考API地址"));
            configs.add(buildConfig("reasoner_model", config.getReasonerModel(), "深度思考模型"));
            configs.add(buildConfig("reasoner_api_key", config.getReasonerApiKey(), "深度思考API Key"));
            configs.add(buildConfig("web_search_enabled", String.valueOf(config.isWebSearchEnabled()), "联网搜索"));
            configs.add(buildConfig("web_search_provider", config.getWebSearchProvider(), "搜索引擎"));
            configs.add(buildConfig("bocha_api_key", config.getBochaApiKey(), "博查API Key"));
            configs.add(buildConfig("bocha_api_url", config.getBochaApiUrl(), "博查API地址"));
            configs.add(buildConfig("tavily_api_key", config.getTavilyApiKey(), "Tavily API Key"));
            configs.add(buildConfig("tavily_api_url", config.getTavilyApiUrl(), "Tavily API地址"));
            configs.add(buildConfig("duckduckgo_api_url", config.getDuckduckgoApiUrl(), "DuckDuckGo API地址"));
            configs.add(buildConfig("serper_api_key", config.getSerperApiKey(), "Serper API Key"));
            configs.add(buildConfig("serper_api_url", config.getSerperApiUrl(), "Serper API地址"));
            configs.add(buildConfig("serpapi_api_key", config.getSerpapiApiKey(), "SerpAPI Key"));
            configs.add(buildConfig("serpapi_api_url", config.getSerpapiApiUrl(), "SerpAPI地址"));
            configs.add(buildConfig("embedding_api_url", config.getEmbeddingApiUrl(), "Embedding API地址"));
            configs.add(buildConfig("embedding_model", config.getEmbeddingModel(), "Embedding模型"));
            configs.add(buildConfig("embedding_api_key", config.getEmbeddingApiKey(), "Embedding API Key"));
            configs.add(buildConfig("connect_timeout", String.valueOf(config.getConnectTimeout()), "连接超时"));
            configs.add(buildConfig("read_timeout", String.valueOf(config.getReadTimeout()), "读取超时"));
            configs.add(buildConfig("max_tokens", String.valueOf(config.getMaxTokens()), "最大Token"));
            configs.add(buildConfig("max_history_rounds", String.valueOf(config.getMaxHistoryRounds()), "历史轮数"));
            configs.add(buildConfig("dify_api_key", config.getDifyApiKey(), "Dify API Key"));
            configs.add(buildConfig("dify_base_url", config.getDifyBaseUrl(), "Dify基础URL"));
            configs.add(buildConfig("dify_keyword_workflow", config.getDifyKeywordWorkflow(), "Dify关键词端点"));
            
            aiConfigMapper.batchSaveOrUpdate(configs);
            log.info("[AiConfig] 配置已保存到MySQL，共{}项", configs.size());
        } catch (Exception e) {
            log.error("[AiConfig] 保存配置失败", e);
        }
    }
    
    /**
     * 从MySQL加载配置
     */
    public JSONObject loadConfig() {
        try {
            List<AiConfigEntity> configs = aiConfigMapper.findAll();
            if (configs == null || configs.isEmpty()) {
                log.info("[AiConfig] 数据库中无配置，使用默认配置");
                return null;
            }
            
            JSONObject json = new JSONObject();
            for (AiConfigEntity config : configs) {
                String key = camelCase(config.getConfigKey());
                String value = config.getConfigValue();
                json.put(key, value);
            }
            
            log.info("[AiConfig] 从MySQL加载配置成功，共{}项", configs.size());
            return json;
        } catch (Exception e) {
            log.error("[AiConfig] 加载配置失败", e);
            return null;
        }
    }
    
    private String camelCase(String underscore) {
        if (underscore == null) return null;
        StringBuilder sb = new StringBuilder();
        boolean upper = false;
        for (char c : underscore.toCharArray()) {
            if (c == '_') {
                upper = true;
            } else {
                sb.append(upper ? Character.toUpperCase(c) : c);
                upper = false;
            }
        }
        return sb.toString();
    }
    
    private AiConfigEntity buildConfig(String key, String value, String description) {
        AiConfigEntity entity = new AiConfigEntity();
        entity.setConfigKey(key);
        entity.setConfigValue(value != null ? value : "");
        entity.setDescription(description);
        return entity;
    }
}
