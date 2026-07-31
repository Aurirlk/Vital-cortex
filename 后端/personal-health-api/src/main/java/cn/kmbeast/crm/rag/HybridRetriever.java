package cn.kmbeast.crm.rag;

import cn.kmbeast.crm.vectordb.LocalVectorStore;
import cn.kmbeast.mapper.NewsMapper;
import cn.kmbeast.pojo.vo.NewsVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 混合检索器（RAG-10）。
 *
 * <p>稀疏检索（MySQL LIKE，关键词命中）与稠密检索（向量语义）各有所长：
 * LIKE 对专业术语/疾病名精准，向量对"症状描述"这类语义表述更强。
 * 用 RRF（Reciprocal Rank Fusion）把两路结果按排名融合，比直接拼接更稳。
 *
 * <p>向量路依赖 EMBEDDING_API_URL 配置；未配置时自动退化为纯 LIKE（不抛错）。
 */
@Slf4j
@Service
public class HybridRetriever {

    /** RRF 常数 k（标准取 60） */
    private static final int RRF_K = 60;

    @Resource
    private LocalVectorStore vectorStore;

    @Resource
    private NewsMapper newsMapper;

    /** 检索结果缓存（同一轮对话内避免重复嵌入调用） */
    private final Map<String, List<RetrievedDoc>> cache = new ConcurrentHashMap<>();

    public static class RetrievedDoc {
        public int articleId;
        public String title;
        public String tag;
        public String content;
        public double score;
        public String source; // vector / keyword / both

        public RetrievedDoc(int articleId, String title, String tag, String content,
                            double score, String source) {
            this.articleId = articleId;
            this.title = title;
            this.tag = tag;
            this.content = content;
            this.score = score;
            this.source = source;
        }
    }

    /**
     * 混合检索：RRF 融合向量与 LIKE 两路结果。
     *
     * @param query    用户问题（向量路）
     * @param keywords 关键词（LIKE 路）
     * @param topK     返回条数
     */
    public List<RetrievedDoc> search(String query, List<String> keywords, int topK) {
        String cacheKey = query + "::" + (keywords != null ? keywords : "") + "::" + topK;
        List<RetrievedDoc> cached = cache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        Map<Integer, RetrievedDoc> fused = new LinkedHashMap<>();
        Map<Integer, Double> rrfScore = new HashMap<>();

        // 路 1：向量语义检索
        List<LocalVectorStore.SearchResult> vectorHits = new ArrayList<>();
        try {
            if (vectorStore.collectionExists(KnowledgeIngestionService.COLLECTION)) {
                vectorHits = vectorStore.search(KnowledgeIngestionService.COLLECTION, query, topK * 2);
            }
        } catch (Exception e) {
            log.warn("[Hybrid] 向量检索不可用（检查 EMBEDDING_API_URL），退化为纯关键词: {}", e.getMessage());
        }

        int rank = 1;
        for (LocalVectorStore.SearchResult hit : vectorHits) {
            int articleId = parseArticleId(hit.metadata);
            if (articleId <= 0) continue;
            String title = hit.metadata != null && hit.metadata.get("article_title") != null
                    ? String.valueOf(hit.metadata.get("article_title")) : "";
            String tag = hit.metadata != null && hit.metadata.get("tag") != null
                    ? String.valueOf(hit.metadata.get("tag")) : "";
            double score = 1.0 / (RRF_K + rank);
            rrfScore.merge(articleId, score, Double::sum);
            fused.put(articleId, new RetrievedDoc(articleId, title, tag, hit.content, score, "vector"));
            rank++;
        }

        // 路 2：关键词 LIKE 检索
        List<NewsVO> likeHits = new ArrayList<>();
        try {
            if (keywords != null && !keywords.isEmpty()) {
                likeHits = newsMapper.ragSearch(keywords, topK * 2);
            }
        } catch (Exception e) {
            log.warn("[Hybrid] LIKE 检索异常: {}", e.getMessage());
        }

        rank = 1;
        for (NewsVO article : likeHits) {
            if (article.getId() == null) continue;
            int articleId = article.getId();
            double score = 1.0 / (RRF_K + rank);
            rrfScore.merge(articleId, score, Double::sum);
            if (fused.containsKey(articleId)) {
                RetrievedDoc doc = fused.get(articleId);
                doc.source = "both";
                doc.score = rrfScore.get(articleId);
            } else {
                String content = article.getContent() != null ? article.getContent() : "";
                fused.put(articleId, new RetrievedDoc(articleId, article.getName() != null ? article.getName() : "",
                        article.getTagName() != null ? article.getTagName() : "",
                        content, score, "keyword"));
            }
            rank++;
        }

        List<RetrievedDoc> results = new ArrayList<>(fused.values());
        results.sort((a, b) -> Double.compare(b.score, a.score));
        if (results.size() > topK) {
            results = results.subList(0, topK);
        }

        // 缓存：限制容量避免无限增长（简单 FIFO 淘汰）
        if (cache.size() > 200) {
            cache.clear();
        }
        cache.put(cacheKey, results);
        return results;
    }

    private int parseArticleId(Map<String, Object> metadata) {
        if (metadata == null || metadata.get("article_id") == null) {
            return -1;
        }
        try {
            return Integer.parseInt(String.valueOf(metadata.get("article_id")));
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
