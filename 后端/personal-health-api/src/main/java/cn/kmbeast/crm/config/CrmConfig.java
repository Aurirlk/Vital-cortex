package cn.kmbeast.crm.config;

import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.File;

@Data
@Component
public class CrmConfig {

    private static final Logger log = LoggerFactory.getLogger(CrmConfig.class);

    @Value("${crm.sqlite.db-path:${CRM_SQLITE_PATH:./crm_data/chat_history.db}}")
    private String sqliteDbPath;

    @Value("${crm.vectordb.store-path:${CRM_VECTOR_PATH:./crm_data/vector_store}}")
    private String vectorStorePath;

    @Value("${crm.embedding.model:${EMBEDDING_MODEL:text-embedding-3-small}}")
    private String embeddingModel;

    /**
     * RAG-03：原默认值 {@code https://api.deepseek.com/v1/embeddings} 是错误端点——
     * DeepSeek 官方至今未提供 embeddings API，调用必然 404。
     * 必须显式配置可用的嵌入服务（如 OpenAI 兼容接口或本地 bge/CLIP 服务）。
     */
    @Value("${crm.embedding.api-url:${EMBEDDING_API_URL:}}")
    private String embeddingApiUrl;

    /**
     * CRM 机器对机器接口访问密钥。
     *
     * <p>SEC-02 整改：移除 {@code crm-default-key} 兜底值。该值随代码公开，
     * 保留兜底等同于「默认无认证」。未配置或强度不足时启动直接失败。
     */
    @Value("${crm.api-key:}")
    private String crmApiKey;

    private static final int MIN_API_KEY_LENGTH = 24;

    private static final java.util.List<String> LEAKED_API_KEYS = java.util.Arrays.asList(
            "crm-default-key", "changeme", "test", "123456");

    @Value("${crm.react.max-rounds:5}")
    private int maxReactRounds;

    @Value("${crm.react.temperature:0.3}")
    private double reactTemperature;

    @Value("${crm.react.prompt:}")
    private String reactPrompt;

    @Value("${crm.react.stream-temperature:0.7}")
    private double reactStreamTemperature;

    @Value("${crm.react.tool-timeout-seconds:30}")
    private int toolTimeoutSeconds;

    @PostConstruct
    public void init() {
        validateApiKey();
        validateEmbeddingConfig();

        File sqliteParent = new File(sqliteDbPath).getParentFile();
        if (sqliteParent != null && !sqliteParent.exists()) {
            sqliteParent.mkdirs();
            log.info("[CRM] SQLite目录已创建: {}", sqliteParent.getAbsolutePath());
        }

        File vectorDir = new File(vectorStorePath);
        if (!vectorDir.exists()) {
            vectorDir.mkdirs();
            log.info("[CRM] 向量存储目录已创建: {}", vectorDir.getAbsolutePath());
        }

        if (reactPrompt == null || reactPrompt.isEmpty()) {
            log.info("[CRM] 使用默认 System Prompt（可通过 crm.react.prompt 配置）");
        } else {
            log.info("[CRM] 已加载自定义 System Prompt ({} 字符)", reactPrompt.length());
        }

        log.info("[CRM] CRM模块初始化完成: sqlite={}, vector={}, model={}", 
                sqliteDbPath, vectorStorePath, embeddingModel);
    }

    /**
     * 启动期强校验 CRM API Key，杜绝「用默认密钥上线」。
     */
    private void validateApiKey() {
        if (crmApiKey == null || crmApiKey.trim().isEmpty()) {
            throw new IllegalStateException(
                    "CRM API 密钥未配置，应用拒绝启动。/crm/** 下包含问诊历史查询、任意 SQL 执行、" +
                            "向量库删除等高危接口，必须配置 crm.api-key（或环境变量 CRM_API_KEY）。" +
                            "生成方式：openssl rand -hex 32");
        }
        String key = crmApiKey.trim();
        if (LEAKED_API_KEYS.contains(key)) {
            throw new IllegalStateException(
                    "检测到使用了已公开的默认 CRM API 密钥，应用拒绝启动。请立即更换 CRM_API_KEY。");
        }
        if (key.length() < MIN_API_KEY_LENGTH) {
            throw new IllegalStateException(
                    "CRM API 密钥强度不足：当前 " + key.length() + " 字符，要求至少 "
                            + MIN_API_KEY_LENGTH + " 字符。");
        }
    }

    /**
     * RAG-03：嵌入服务端点校验。DeepSeek 没有 embeddings API，
     * 必须显式配置可用的嵌入服务，否则向量检索永远失败。
     */
    private void validateEmbeddingConfig() {
        if (embeddingApiUrl == null || embeddingApiUrl.trim().isEmpty()) {
            throw new IllegalStateException(
                    "嵌入服务地址未配置，应用拒绝启动。请设置 crm.embedding.api-url" +
                            "（或环境变量 EMBEDDING_API_URL）为可用的 embeddings 端点，例如 " +
                            "https://api.openai.com/v1/embeddings 或本地兼容服务。");
        }
        if (embeddingApiUrl.contains("api.deepseek.com/v1/embeddings")) {
            throw new IllegalStateException(
                    "检测到 DeepSeek embeddings 端点。DeepSeek 官方不提供 embeddings API，该端点必然失败。" +
                            "请改用可用的嵌入服务并同步配置 EMBEDDING_MODEL（当前: " + embeddingModel + "）。");
        }
    }
}
