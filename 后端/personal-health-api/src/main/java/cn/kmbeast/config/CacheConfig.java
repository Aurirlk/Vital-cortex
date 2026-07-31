package cn.kmbeast.config;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.File;

/**
 * 统一缓存目录配置
 * 所有运行时产生的缓存、数据文件统一存放到 ./cache/ 目录下
 * 
 * 目录结构：
 *   cache/
 *   ├── vector/              向量嵌入缓存
 *   ├── chat_history/        聊天历史 JSON 备份
 *   ├── crm/
 *   │   ├── chat_history.db  CRM SQLite 数据库
 *   │   └── vector_store/    CRM 向量存储
 *   ├── ai_data/             AI 工具数据（药品、健康数据导出）
 *   ├── knowledge_graph/     知识图谱数据
 *   └── temp/                临时文件
 */
@Data
@Slf4j
@Component
public class CacheConfig {

    /**
     * 缓存根目录
     */
    @Value("${cache.root:./cache}")
    private String cacheRoot;

    /**
     * 向量缓存目录
     */
    @Value("${cache.vector:./cache/vector}")
    private String vectorCacheDir;

    /**
     * 聊天历史备份目录
     */
    @Value("${cache.chat-history:./cache/chat_history}")
    private String chatHistoryDir;

    /**
     * CRM SQLite 数据库路?     */
    @Value("${cache.crm.db:./cache/crm/chat_history.db}")
    private String crmDbPath;

    /**
     * CRM 向量存储目录
     */
    @Value("${cache.crm.vector-store:./cache/crm/vector_store}")
    private String crmVectorStorePath;

    /**
     * AI 数据导出目录（药品、健康数据）
     */
    @Value("${cache.ai-data:./cache/ai_data}")
    private String aiDataDir;

    /**
     * 知识图谱数据目录
     */
    @Value("${cache.knowledge-graph:./cache/knowledge_graph}")
    private String knowledgeGraphDir;

    /**
     * 临时文件目录
     */
    @Value("${cache.temp:./cache/temp}")
    private String tempDir;

    @PostConstruct
    public void init() {
        // 创建所有缓存目?        createDir(cacheRoot);
        createDir(vectorCacheDir);
        createDir(chatHistoryDir);
        createDir(getParentDir(crmDbPath));
        createDir(crmVectorStorePath);
        createDir(aiDataDir);
        createDir(knowledgeGraphDir);
        createDir(tempDir);

        log.info("[Cache] 统一缓存目录初始化完? {}", new File(cacheRoot).getAbsolutePath());
    }

    /**
     * 创建目录（如果不存在?     */
    private void createDir(String path) {
        if (path == null || path.isEmpty()) return;
        File dir = new File(path);
        if (!dir.exists()) {
            boolean created = dir.mkdirs();
            if (created) {
                log.debug("[Cache] 创建目录: {}", dir.getAbsolutePath());
            }
        }
    }

    /**
     * 获取文件的父目录路径
     */
    private String getParentDir(String filePath) {
        if (filePath == null) return null;
        File file = new File(filePath);
        File parent = file.getParentFile();
        return parent != null ? parent.getPath() : null;
    }
}
