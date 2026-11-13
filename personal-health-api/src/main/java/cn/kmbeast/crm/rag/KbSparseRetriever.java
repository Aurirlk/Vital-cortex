package cn.kmbeast.crm.rag;

import cn.kmbeast.crm.config.RagProperties;
import cn.kmbeast.mapper.KbChunkMapper;
import cn.kmbeast.pojo.entity.KbChunk;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 稀疏（词法）检索路。
 *
 * <h3>主路径与兜底路径</h3>
 * <ol>
 *   <li><b>主路径</b>：{@code MATCH(content) AGAINST(? IN NATURAL LANGUAGE MODE)}，
 *       依赖 {@code kb_chunk} 上的 {@code FULLTEXT ... WITH PARSER ngram}。
 *       ngram 解析器把正文切成 N 字滑窗（默认 n=2），于是中文不需要分词也能建倒排，
 *       且 MySQL 会算出真正的 TF-IDF 相关度用于排序。</li>
 *   <li><b>兜底路径</b>：索引不存在 / ngram 未启用 / 建表脚本没跑时，主路径语句<b>直接抛异常</b>
 *       （不是返回空集）。此时回落 {@code LIKE}。这条回落允许存在，
 *       但<b>必须留下痕迹</b>——{@link SparseResult#likedFallback()} 会被写进
 *       {@code retrieval_log.path}，否则系统会以「表面正常」的姿态长期退化成关键词检索。</li>
 * </ol>
 *
 * <h3>为什么主路径的异常要区分处理</h3>
 * <p>这里捕获 {@link Exception} 而不是某个具体类型：不同 MySQL 版本/驱动下，
 * 「索引不存在」可能报 {@code SQLSyntaxErrorException}、{@code BadSqlGrammarException}
 * （被 MyBatis 包装后）或 {@code MySQLSyntaxErrorException}。逐个枚举必然漏，
 * 而漏掉一个的后果是「检索直接失败」而不是「降级」——后者才是可接受的。
 * 兜底本身有 {@code likeFallbackEnabled} 开关和日志留痕，不会把问题藏起来。</p>
 */
@Slf4j
@Service
public class KbSparseRetriever {

    @Resource
    private KbChunkMapper kbChunkMapper;

    @Resource
    private RagProperties ragProperties;

    /** 稀疏路结果。 */
    public static final class SparseResult {

        private final List<KbChunk> chunks;
        private final boolean likedFallback;

        SparseResult(List<KbChunk> chunks, boolean likedFallback) {
            this.chunks = chunks == null ? Collections.emptyList() : chunks;
            this.likedFallback = likedFallback;
        }

        public List<KbChunk> chunks() {
            return chunks;
        }

        /** true 表示本次走的是 {@code LIKE} 兜底而非 ngram 全文索引。 */
        public boolean likedFallback() {
            return likedFallback;
        }

        public boolean isEmpty() {
            return chunks.isEmpty();
        }
    }

    /**
     * 稀疏检索。
     *
     * @param query    原始问题（主路径用）
     * @param keywords 关键词（兜底路径用）；为空时由 {@link KeywordExtractor} 从 query 现场抽取
     * @param kbIds    限定知识库 id；为 null 或空表示「不做知识库限定」，
     *                 但<b>调用方通常应显式传入</b>——不限定时相当于全库扫描
     * @param limit    返回条数上限
     * @return 结果（永不返回 null）
     */
    public SparseResult search(String query, List<String> keywords, List<Integer> kbIds, int limit) {
        if (limit <= 0) {
            return new SparseResult(Collections.emptyList(), false);
        }
        boolean hasQuery = query != null && !query.trim().isEmpty();
        if (!hasQuery && (keywords == null || keywords.isEmpty())) {
            return new SparseResult(Collections.emptyList(), false);
        }

        // ---- 主路径：ngram 全文索引 ----
        if (hasQuery) {
            try {
                List<KbChunk> hits = kbChunkMapper.fullTextSearch(query.trim(), kbIds, limit);
                if (hits != null && !hits.isEmpty()) {
                    return new SparseResult(hits, false);
                }
                // 主路径可用但没命中：这是正常的「查不到」，不是索引故障。
                // 不能再落 LIKE —— 那会把「确实没有这个知识」伪装成「召回了点别的」，
                // 让评测里的精确度指标彻底失去意义。
                return new SparseResult(Collections.emptyList(), false);
            } catch (Exception e) {
                if (!ragProperties.isLikeFallbackEnabled()) {
                    log.warn("[RAG-sparse] ngram 全文检索失败且未开启 LIKE 兜底，本次稀疏路返回空: {}",
                            e.getMessage());
                    return new SparseResult(Collections.emptyList(), false);
                }
                log.warn("[RAG-sparse] ngram 全文检索不可用，回落 LIKE 兜底"
                        + "（请确认 Data/02_迁移脚本/11_knowledge_base.sql 已执行且 MySQL 支持 ngram）：{}",
                        e.getMessage());
            }
        }

        // ---- 兜底路径：LIKE ----
        List<String> kws = keywords;
        if (kws == null || kws.isEmpty()) {
            kws = KeywordExtractor.extract(query);
        }
        // 调用方直接传进来的关键词也要清洗一遍：% _ \ 是 LIKE 的元字符，
        // 词表由上层拼装时（例如管理端的自定义词表）不会被 extract 覆盖到，
        // 一个 '%' 就会让 CONCAT('%','%','%') 命中整表，兜底路等于没做。
        kws = KeywordExtractor.sanitize(kws);
        if (kws.isEmpty()) {
            return new SparseResult(Collections.emptyList(), true);
        }
        try {
            List<KbChunk> hits = kbChunkMapper.likeSearch(kws, kbIds, limit);
            return new SparseResult(hits == null ? Collections.emptyList() : hits, true);
        } catch (Exception e) {
            // 兜底也失败：这条路彻底不可用。返回空而不是抛出，
            // 让稠密路还有机会独立完成本次检索。
            log.error("[RAG-sparse] LIKE 兜底检索同样失败: {}", e.getMessage());
            return new SparseResult(Collections.emptyList(), true);
        }
    }

    /** 便捷方法：只要块本身。 */
    public List<KbChunk> searchChunks(String query, List<String> keywords,
                                      List<Integer> kbIds, int limit) {
        return new ArrayList<>(search(query, keywords, kbIds, limit).chunks());
    }
}
