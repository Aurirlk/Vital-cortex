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

    @Value("${crm.embedding.api-url:${EMBEDDING_API_URL:https://api.deepseek.com/v1/embeddings}}")
    private String embeddingApiUrl;

    @Value("${crm.api-key:${CRM_API_KEY:crm-default-key}}")
    private String crmApiKey;

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
}
