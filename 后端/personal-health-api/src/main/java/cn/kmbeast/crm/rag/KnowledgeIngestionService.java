package cn.kmbeast.crm.rag;

import cn.kmbeast.crm.vectordb.LocalVectorStore;
import cn.kmbeast.crm.vectordb.VectorEntity;
import cn.kmbeast.mapper.NewsMapper;
import cn.kmbeast.pojo.vo.NewsVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 知识库向量化管线（RAG-04/RAG-19）。
 *
 * <p>此前三个向量集合从未创建/灌数，向量检索"已建成但未通电"。
 * 本服务打通「文章 → 分块 → embedding → 灌库」链路：
 * <ul>
 *   <li>启动时若集合不存在或为空，自动全量灌库一次；</li>
 *   <li>文章保存/更新后增量同步（由 NewsController 调用钩子）；</li>
 *   <li>文章删除时按 article_id 联动清理向量（deleteByMetadata）；</li>
 *   <li>提供全量重建接口（管理端维护用）。</li>
 * </ul>
 */
@Slf4j
@Service
public class KnowledgeIngestionService {

    /** 与 SearchKnowledgeTool 一致的集合名 */
    public static final String COLLECTION = "health_knowledge";

    @Resource
    private LocalVectorStore vectorStore;

    @Resource
    private NewsMapper newsMapper;

    @PostConstruct
    public void init() {
        // 启动自愈：集合不存在或为空时全量灌库
        try {
            if (!vectorStore.collectionExists(COLLECTION)) {
                vectorStore.createCollection(COLLECTION);
            }
            Map<String, Object> stats = vectorStore.getCollectionStats(COLLECTION);
            Object count = stats.get("doc_count");
            if (count == null || ((Number) count).intValue() == 0) {
                log.info("[Ingestion] 向量库为空，启动全量灌库");
                rebuildAll();
            } else {
                log.info("[Ingestion] 向量库已有 {} 条，跳过初始化", count);
            }
        } catch (Exception e) {
            // 嵌入服务未配置时（EMBEDDING_API_URL 为空）启动不失败，
            // 灌库会在配置就绪后由 rebuildAll 触发
            log.warn("[Ingestion] 初始化灌库失败（可稍后通过全量重建触发）: {}", e.getMessage());
        }
    }

    /**
     * 增量同步单篇文章（保存/更新后调用）。
     */
    public void ingestArticle(Integer articleId, String title, String content, String tagName) {
        if (articleId == null || content == null || content.trim().isEmpty()) {
            return;
        }
        // 先清掉该文章的旧向量，避免更新后残留
        deleteArticle(articleId);

        List<String> chunks = ChunkUtil.split(content);
        if (chunks.isEmpty()) {
            return;
        }
        List<VectorEntity> docs = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            Map<String, Object> meta = new HashMap<>();
            meta.put("article_id", articleId);
            meta.put("article_title", title != null ? title : "");
            meta.put("tag", tagName != null ? tagName : "");
            meta.put("chunk_index", i);
            meta.put("chunk_count", chunks.size());
            VectorEntity doc = VectorEntity.builder()
                    .content(chunks.get(i))
                    .metadata(meta)
                    .build();
            docs.add(doc);
        }
        try {
            vectorStore.batchUpsert(COLLECTION, docs);
            log.info("[Ingestion] 文章 {} 已向量化: {} 块", articleId, chunks.size());
        } catch (Exception e) {
            log.error("[Ingestion] 文章向量化失败: articleId={}", articleId, e);
        }
    }

    /**
     * 文章删除联动：清理该文章的全部向量块。
     */
    public void deleteArticle(Integer articleId) {
        if (articleId == null) return;
        try {
            vectorStore.deleteByMetadata(COLLECTION, "article_id", articleId);
        } catch (Exception e) {
            log.warn("[Ingestion] 删除文章向量失败: articleId={}", articleId, e);
        }
    }

    /**
     * 全量重建：清空集合后从数据库灌入全部文章。
     */
    public void rebuildAll() {
        List<NewsVO> articles;
        try {
            articles = newsMapper.selectAllForRag();
        } catch (Exception e) {
            log.error("[Ingestion] 读取文章失败", e);
            return;
        }
        if (articles == null) {
            articles = new ArrayList<>();
        }
        if (vectorStore.collectionExists(COLLECTION)) {
            vectorStore.deleteCollection(COLLECTION);
        }
        vectorStore.createCollection(COLLECTION);

        int totalChunks = 0;
        for (NewsVO article : articles) {
            Integer id = article.getId();
            if (id == null) continue;
            List<String> chunks = ChunkUtil.split(article.getContent());
            if (chunks.isEmpty()) continue;

            List<VectorEntity> docs = new ArrayList<>(chunks.size());
            for (int i = 0; i < chunks.size(); i++) {
                Map<String, Object> meta = new HashMap<>();
                meta.put("article_id", id);
                meta.put("article_title", article.getName() != null ? article.getName() : "");
                meta.put("tag", article.getTagName() != null ? article.getTagName() : "");
                meta.put("chunk_index", i);
                meta.put("chunk_count", chunks.size());
                docs.add(VectorEntity.builder().content(chunks.get(i)).metadata(meta).build());
            }
            try {
                vectorStore.batchUpsert(COLLECTION, docs);
                totalChunks += chunks.size();
            } catch (Exception e) {
                log.warn("[Ingestion] 文章灌库失败: id={}", id, e);
            }
        }
        log.info("[Ingestion] 全量重建完成: {} 篇文章 → {} 个向量块", articles.size(), totalChunks);
    }

    /**
     * 向量库状态（供管理端查看）。
     */
    public Map<String, Object> getVectorStoreStats() {
        return vectorStore.getStats();
    }
}
