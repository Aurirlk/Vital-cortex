package cn.kmbeast.config;

import cn.kmbeast.mapper.AiConfigMapper;
import cn.kmbeast.pojo.entity.AiConfigEntity;
import cn.kmbeast.utils.AESUtils;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * AI配置持久化服务
 * 使用MySQL存储配置；**敏感字段（各类 api_key）经 AES-GCM 加密后入库**，读取时自动解密。
 *
 * <p>2026-10-07 修复两个存储机制缺陷：<br>
 * ① <b>重启必丢配置</b>——原实现对保存异常只 log 不抛，前端恒收到「配置更新成功」而库表实际未更新；
 * 且掩码值（{@code sk-xxx****abcd}）会被当真实 key 落库，重启后模型读到掩码即不可用。<br>
 * ② <b>加密后读不出</b>——{@code AESUtils} 早已实现但从未接线；现已接入本类，
 * 写入走 {@link AESUtils#encryptIfNeeded}，读取走 {@link AESUtils#decryptIfNeeded}
 * （<b>幂等</b>：库中仍是明文的历史数据也能正常读取，读取成功后就地回写为密文）。</p>
 */
@Slf4j
@Service
public class AiConfigPersistenceService {

    @Resource
    private AiConfigMapper aiConfigMapper;

    /**
     * 敏感字段清单：这些 config_key 的值以 AES-GCM 密文入库。
     * ⚠️ 与 {@code AiConfigController} 的掩码判断、{@code SystemConfigManage.vue} 的剔除逻辑必须一致。
     */
    private static final Set<String> SENSITIVE_KEYS = Set.of(
            "api_key", "reasoner_api_key", "embedding_api_key", "vision_api_key",
            "bocha_api_key", "tavily_api_key", "serper_api_key", "serpapi_api_key",
            "dify_api_key");

    /**
     * 保存配置到MySQL（敏感字段加密入库）
     *
     * @throws IllegalStateException 入库失败时抛出，<b>不再静默吞异常</b>
     */
    public void saveConfig(AiConfig config) {
        try {
            List<AiConfigEntity> configs = new ArrayList<>();
            
            configs.add(buildConfig("provider", config.getProvider(), "AI厂商"));
            configs.add(buildConfig("api_url", config.getApiUrl(), "API地址"));
            configs.add(buildConfig("model", config.getModel(), "模型"));
            configs.add(sensitive("api_key", config.getApiKey(), "API Key"));
            configs.add(buildConfig("reasoner_api_url", config.getReasonerApiUrl(), "深度思考API地址"));
            configs.add(buildConfig("reasoner_model", config.getReasonerModel(), "深度思考模型"));
            configs.add(sensitive("reasoner_api_key", config.getReasonerApiKey(), "深度思考API Key"));
            configs.add(buildConfig("web_search_enabled", String.valueOf(config.isWebSearchEnabled()), "联网搜索"));
            configs.add(buildConfig("web_search_provider", config.getWebSearchProvider(), "搜索引擎"));
            configs.add(sensitive("bocha_api_key", config.getBochaApiKey(), "博查API Key"));
            configs.add(buildConfig("bocha_api_url", config.getBochaApiUrl(), "博查API地址"));
            configs.add(sensitive("tavily_api_key", config.getTavilyApiKey(), "Tavily API Key"));
            configs.add(buildConfig("tavily_api_url", config.getTavilyApiUrl(), "Tavily API地址"));
            configs.add(buildConfig("duckduckgo_api_url", config.getDuckduckgoApiUrl(), "DuckDuckGo API地址"));
            configs.add(sensitive("serper_api_key", config.getSerperApiKey(), "Serper API Key"));
            configs.add(buildConfig("serper_api_url", config.getSerperApiUrl(), "Serper API地址"));
            configs.add(sensitive("serpapi_api_key", config.getSerpapiApiKey(), "SerpAPI Key"));
            configs.add(buildConfig("serpapi_api_url", config.getSerpapiApiUrl(), "SerpAPI地址"));
            configs.add(buildConfig("embedding_api_url", config.getEmbeddingApiUrl(), "Embedding API地址"));
            configs.add(buildConfig("embedding_model", config.getEmbeddingModel(), "Embedding模型"));
            configs.add(sensitive("embedding_api_key", config.getEmbeddingApiKey(), "Embedding API Key"));
            configs.add(buildConfig("connect_timeout", String.valueOf(config.getConnectTimeout()), "连接超时"));
            configs.add(buildConfig("read_timeout", String.valueOf(config.getReadTimeout()), "读取超时"));
            configs.add(buildConfig("max_tokens", String.valueOf(config.getMaxTokens()), "最大Token"));
            configs.add(buildConfig("max_history_rounds", String.valueOf(config.getMaxHistoryRounds()), "历史轮数"));
            configs.add(sensitive("dify_api_key", config.getDifyApiKey(), "Dify API Key"));
            configs.add(buildConfig("dify_base_url", config.getDifyBaseUrl(), "Dify基础URL"));
            configs.add(buildConfig("dify_keyword_workflow", config.getDifyKeywordWorkflow(), "Dify关键词端点"));
            
            aiConfigMapper.batchSaveOrUpdate(configs);
            log.info("[AiConfig] 配置已保存到MySQL，共{}项（敏感字段已加密）", configs.size());
        } catch (Exception e) {
            log.error("[AiConfig] 保存配置失败", e);
            // 🔴 2026-10-07：不再静默。原实现只 log，导致前端恒显示「配置更新成功」而库表未更新，
            //    用户重启后才发现配置全丢。抛出异常让 Controller 返回失败，前端能真实感知。
            throw new IllegalStateException("AI 配置保存失败: " + e.getMessage(), e);
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
            boolean anyPlaintextSensitive = false;
            for (AiConfigEntity config : configs) {
                String rawKey = config.getConfigKey();
                String key = camelCase(rawKey);
                String value = config.getConfigValue();

                // 🔑 敏感字段解密：decryptIfNeeded 幂等 —— 密文解出明文，明文原样返回
                // （兼容本次修复前遗留的明文历史数据，避免"加密后读不出"）
                if (rawKey != null && SENSITIVE_KEYS.contains(rawKey)) {
                    String decrypted = AESUtils.decryptIfNeeded(value);
                    if (!decrypted.equals(value)) {
                        // 本次修复前的历史明文，读取成功后就地回写为密文（渐进迁移）
                        try {
                            aiConfigMapper.saveOrUpdate(sensitive(rawKey, decrypted, config.getDescription()));
                            log.info("[AiConfig] 敏感字段 {} 已从明文迁移为密文", rawKey);
                        } catch (Exception ex) {
                            log.warn("[AiConfig] 敏感字段 {} 回写密文失败（不影响本次启动读取）: {}", rawKey, ex.getMessage());
                        }
                    }
                    value = decrypted;
                }
                json.put(key, value);
            }

            log.info("[AiConfig] 从MySQL加载配置成功，共{}项{}", configs.size(),
                    anyPlaintextSensitive ? "" : "（敏感字段已解密）");
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

    /**
     * 构建敏感配置实体：值经 AES-GCM 加密后入库。
     * 空值原样返回空串（不加密），避免把 null 变成密文。
     */
    private AiConfigEntity sensitive(String key, String value, String description) {
        String plain = value != null ? value : "";
        String stored = plain.isEmpty() ? plain : AESUtils.encryptIfNeeded(plain);
        return buildConfig(key, stored, description);
    }
}
