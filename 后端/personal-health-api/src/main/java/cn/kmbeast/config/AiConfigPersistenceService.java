package cn.kmbeast.config;

import cn.kmbeast.mapper.AiConfigMapper;
import cn.kmbeast.pojo.entity.AiConfigEntity;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * AI配置持久化服务
 * 使用MySQL存储配置，支持AES加密保存API Key
 */
@Slf4j
@Service
public class AiConfigPersistenceService {

    @Resource
    private AiConfigMapper aiConfigMapper;

    private static final String ENCRYPTION_KEY = "HealthAI2024Key"; // 16字节密钥

    /**
     * 保存配置到MySQL
     */
    public void saveConfig(AiConfig config) {
        try {
            List<AiConfigEntity> configs = new ArrayList<>();
            
            // 基础配置
            configs.add(buildConfig("provider", config.getProvider(), "AI厂商"));
            configs.add(buildConfig("api_url", config.getApiUrl(), "普通对话API地址"));
            configs.add(buildConfig("model", config.getModel(), "普通对话模型"));
            configs.add(buildConfig("api_key", encryptApiKey(config.getApiKey()), "普通对话API Key（加密）"));
            
            // 深度思考配置
            configs.add(buildConfig("reasoner_api_url", config.getReasonerApiUrl(), "深度思考API地址"));
            configs.add(buildConfig("reasoner_model", config.getReasonerModel(), "深度思考模型"));
            configs.add(buildConfig("reasoner_api_key", encryptApiKey(config.getReasonerApiKey()), "深度思考API Key（加密）"));
            
            // 联网搜索配置
            configs.add(buildConfig("web_search_enabled", String.valueOf(config.isWebSearchEnabled()), "联网搜索是否启用"));
            configs.add(buildConfig("web_search_provider", config.getWebSearchProvider(), "联网搜索引擎"));
            configs.add(buildConfig("bocha_api_key", encryptApiKey(config.getBochaApiKey()), "博查API Key（加密）"));
            configs.add(buildConfig("bocha_api_url", config.getBochaApiUrl(), "博查API地址"));
            configs.add(buildConfig("tavily_api_key", encryptApiKey(config.getTavilyApiKey()), "Tavily API Key（加密）"));
            configs.add(buildConfig("tavily_api_url", config.getTavilyApiUrl(), "Tavily API地址"));
            configs.add(buildConfig("duckduckgo_api_url", config.getDuckduckgoApiUrl(), "DuckDuckGo API地址"));
            configs.add(buildConfig("serper_api_key", encryptApiKey(config.getSerperApiKey()), "Serper API Key（加密）"));
            configs.add(buildConfig("serper_api_url", config.getSerperApiUrl(), "Serper API地址"));
            configs.add(buildConfig("serpapi_api_key", encryptApiKey(config.getSerpapiApiKey()), "SerpAPI Key（加密）"));
            configs.add(buildConfig("serpapi_api_url", config.getSerpapiApiUrl(), "SerpAPI地址"));
            
            // Embedding配置
            configs.add(buildConfig("embedding_api_url", config.getEmbeddingApiUrl(), "Embedding API地址"));
            configs.add(buildConfig("embedding_model", config.getEmbeddingModel(), "Embedding模型"));
            configs.add(buildConfig("embedding_api_key", encryptApiKey(config.getEmbeddingApiKey()), "Embedding API Key（加密）"));
            
            // 通用配置
            configs.add(buildConfig("connect_timeout", String.valueOf(config.getConnectTimeout()), "连接超时(ms)"));
            configs.add(buildConfig("read_timeout", String.valueOf(config.getReadTimeout()), "读取超时(ms)"));
            configs.add(buildConfig("max_tokens", String.valueOf(config.getMaxTokens()), "最大Token数"));
            configs.add(buildConfig("max_history_rounds", String.valueOf(config.getMaxHistoryRounds()), "最大历史轮数"));
            
            // 批量保存到MySQL
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
                
                // 解密API Key
                if (key.endsWith("ApiKey") && value != null && !value.isEmpty()) {
                    value = decryptApiKey(value);
                }
                
                json.put(key, value);
            }
            
            log.info("[AiConfig] 从MySQL加载配置成功，共{}项", configs.size());
            return json;
        } catch (Exception e) {
            log.error("[AiConfig] 加载配置失败", e);
            return null;
        }
    }
    
    /**
     * 下划线转驼峰
     */
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
    
    /**
     * AES加密API Key
     */
    private String encryptApiKey(String apiKey) {
        if (apiKey == null || apiKey.isEmpty()) {
            return "";
        }
        try {
            SecretKey key = new SecretKeySpec(ENCRYPTION_KEY.getBytes(StandardCharsets.UTF_8), "AES");
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, key);
            byte[] encrypted = cipher.doFinal(apiKey.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            log.error("[AiConfig] 加密失败", e);
            return apiKey;
        }
    }
    
    /**
     * AES解密API Key
     */
    private String decryptApiKey(String encryptedKey) {
        if (encryptedKey == null || encryptedKey.isEmpty()) {
            return "";
        }
        try {
            SecretKey key = new SecretKeySpec(ENCRYPTION_KEY.getBytes(StandardCharsets.UTF_8), "AES");
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, key);
            byte[] decrypted = cipher.doFinal(Base64.getDecoder().decode(encryptedKey));
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            // 解密失败可能是未加密的旧配置，直接返回
            return encryptedKey;
        }
    }
}
