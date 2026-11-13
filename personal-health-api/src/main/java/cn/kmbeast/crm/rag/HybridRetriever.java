package cn.kmbeast.crm.rag;

import cn.kmbeast.crm.config.RagProperties;
import cn.kmbeast.crm.rag.fusion.RRFusion;
import cn.kmbeast.crm.vectordb.LocalVectorStore;
import cn.kmbeast.mapper.NewsMapper;
import cn.kmbeast.pojo.vo.NewsVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
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

    @Resource
    private LocalVectorStore vectorStore;

    @Resource
    private NewsMapper newsMapper;

    /**
     * 带初始化器的注入：纯单测（不起 Spring 容器）只 setField 了 vectorStore / newsMapper 时，
     * 这里的默认值就是改造前的行为（两路各 1.0、k=60），不会因为缺注入而 NPE。
     */
    @Resource
    private RagProperties ragProperties = new RagProperties();

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
     * 混合检索：RRF 融合向量（稠密）与 LIKE（稀疏）两路结果。
     *
     * <p>阶段 2 改造：融合逻辑收敛到 {@link RRFusion}，权重与 k 走
     * {@link RagProperties} 配置。默认值（两路各 1.0、k=60）与改造前完全一致，
     * 因此行为不变；改配置才需要重启，不需要改代码。</p>
     *
     * <p>顺带修掉一个原有的计分错误：同一篇文章若在<b>同一条路</b>里出现两次，
     * 旧实现会把两次的 RRF 分<b>累加</b>（{@code rrfScore.merge}）却只保留一个结果，
     * 等于给重复项额外加分，排名被人为抬高。{@link RRFusion} 对同一路内的重复
     * 只取首次（最好）名次。</p>
     *
     * @param query    用户问题（向量/稠密路）
     * @param keywords 关键词（LIKE/稀疏路）
     * @param topK     返回条数
     */
    public List<RetrievedDoc> search(String query, List<String> keywords, int topK) {
        String cacheKey = query + "::" + (keywords != null ? keywords : "") + "::" + topK;
        List<RetrievedDoc> cached = cache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        int candidate = topK * ragProperties.effectiveCandidateFactor();
        Map<String, RetrievedDoc> payload = new LinkedHashMap<>();

        // ---- 稠密路：向量语义检索 ----
        List<String> denseKeys = new ArrayList<>();
        try {
            if (vectorStore.collectionExists(KnowledgeIngestionService.COLLECTION)) {
                List<LocalVectorStore.SearchResult> vectorHits =
                        vectorStore.search(KnowledgeIngestionService.COLLECTION, query, candidate);
                for (LocalVectorStore.SearchResult hit : vectorHits) {
                    int articleId = parseArticleId(hit.metadata);
                    if (articleId <= 0) {
                        continue;
                    }
                    String key = String.valueOf(articleId);
                    if (payload.containsKey(key)) {
                        continue;
                    }
                    String title = hit.metadata != null && hit.metadata.get("article_title") != null
                            ? String.valueOf(hit.metadata.get("article_title")) : "";
                    String tag = hit.metadata != null && hit.metadata.get("tag") != null
                            ? String.valueOf(hit.metadata.get("tag")) : "";
                    payload.put(key, new RetrievedDoc(articleId, title, tag, hit.content, 0.0, "vector"));
                    denseKeys.add(key);
                }
            }
        } catch (Exception e) {
            log.warn("[Hybrid] 向量检索不可用（检查 EMBEDDING_API_URL），退化为纯关键词: {}", e.getMessage());
        }

        // ---- 稀疏路：关键词 LIKE ----
        List<String> sparseKeys = new ArrayList<>();
        try {
            if (keywords != null && !keywords.isEmpty()) {
                List<NewsVO> likeHits = newsMapper.ragSearch(keywords, candidate);
                for (NewsVO article : likeHits) {
                    if (article.getId() == null) {
                        continue;
                    }
                    String key = String.valueOf(article.getId());
                    if (payload.containsKey(key)) {
                        continue;
                    }
                    String content = article.getContent() != null ? article.getContent() : "";
                    payload.put(key, new RetrievedDoc(article.getId(),
                            article.getName() != null ? article.getName() : "",
                            article.getTagName() != null ? article.getTagName() : "",
                            content, 0.0, "keyword"));
                    sparseKeys.add(key);
                }
            }
        } catch (Exception e) {
            log.warn("[Hybrid] LIKE 检索异常: {}", e.getMessage());
        }

        // ---- 融合 ----
        List<RRFusion.FusedItem> fused = RRFusion.fuse(sparseKeys, denseKeys,
                ragProperties.effectiveSparseWeight(),
                ragProperties.effectiveDenseWeight(),
                ragProperties.getRrfK(), topK);

        List<RetrievedDoc> results = new ArrayList<>(fused.size());
        for (RRFusion.FusedItem item : fused) {
            RetrievedDoc doc = payload.get(item.getKey());
            if (doc == null) {
                continue;
            }
            doc.score = item.getScore();
            // 沿用改造前的来源取值（vector / keyword / both），
            // AiServiceImpl 会把它直接写进给模型看的 prompt
            doc.source = item.fromBoth() ? "both" : (item.fromDense() ? "vector" : "keyword");
            results.add(doc);
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
