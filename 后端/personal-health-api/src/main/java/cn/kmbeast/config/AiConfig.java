package cn.kmbeast.config;

import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.File;

/**
 * DeepSeek AI 配置
 * 所有敏感配置必须通过环境变量注入，禁止硬编码默认值
 */
@Data
@Component
@ConfigurationProperties(prefix = "deepseek")
public class AiConfig {

    private static final Logger log = LoggerFactory.getLogger(AiConfig.class);

    /**
     * API密钥 - 必须从环境变量读取
     */
    @Value("${deepseek.api-key:${DEEPSEEK_API_KEY:}}")
    private String apiKey;

    /**
     * API地址
     */
    @Value("${deepseek.api-url:${DEEPSEEK_API_URL:https://api.deepseek.com/v1/chat/completions}}")
    private String apiUrl;

    /**
     * 模型名称
     */
    @Value("${deepseek.model:${DEEPSEEK_MODEL:deepseek-chat}}")
    private String model;

    /**
     * 连接超时（毫秒）
     */
    @Value("${deepseek.connect-timeout:${AI_CONNECT_TIMEOUT:30000}}")
    private Integer connectTimeout;

    /**
     * 读取超时（毫秒）
     */
    @Value("${deepseek.read-timeout:${AI_READ_TIMEOUT:60000}}")
    private Integer readTimeout;

    /**
     * 最大历史对话轮数
     */
    @Value("${ai.max-history-rounds:${AI_MAX_HISTORY_ROUNDS:10}}")
    private Integer maxHistoryRounds;

    /**
     * 最大Token数
     */
    @Value("${ai.max-tokens:${AI_MAX_TOKENS:4096}}")
    private Integer maxTokens;

    /**
     * 向量缓存目录
     */
    @Value("${ai.vector-cache-dir:${VECTOR_CACHE_DIR:./vector_cache}}")
    private String vectorCacheDir;

    /**
     * 初始化配置
     */
    @PostConstruct
    public void init() {
        // 检查API密钥是否配置
        if (apiKey == null || apiKey.isEmpty()) {
            log.warn("[AI] 未配置 DEEPSEEK_API_KEY 环境变量，AI功能将不可用");
        } else {
            log.info("[AI] DeepSeek API密钥已配置");
        }

        // 创建缓存目录
        File cacheDir = new File(vectorCacheDir);
        if (!cacheDir.exists()) {
            boolean created = cacheDir.mkdirs();
            if (created) {
                log.info("[AI] 缓存目录已创建: {}", cacheDir.getAbsolutePath());
            }
        }
    }
}
