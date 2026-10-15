package cn.kmbeast.crm.rag;

import cn.kmbeast.crm.config.RagProperties;
import cn.kmbeast.crm.rag.fusion.RRFusion;
import cn.kmbeast.crm.vectordb.LocalVectorStore;
import cn.kmbeast.mapper.KbChunkMapper;
import cn.kmbeast.mapper.KnowledgeBaseMapper;
import cn.kmbeast.pojo.entity.KbChunk;
import cn.kmbeast.pojo.entity.KnowledgeBase;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 知识库统一检索入口：稀疏 + 稠密 → 加权 RRF 融合 → 回读正本 → 落检索日志。
 *
 * <h3>检索链路全貌</h3>
 * <pre>
 *   问题 ─┬─ 稀疏路 (ngram 全文索引，失败时 LIKE 兜底) ─→ 块 id 序列 ─┐
 *        │                                                          ├─ 加权 RRF ─→ 融合名次
 *        └─ 稠密路 (按知识库查向量集合，余弦 × 库权重，合并名次) ────┘
 *                                                                        │
 *                        kb_chunk 回读正本（丢弃已删/已停用的块） ←──────┘
 *                                                                        │
 *                                              返回 KbHit + 落 retrieval_log
 * </pre>
 *
 * <h3>两个刻意的设计取舍</h3>
 * <ol>
 *   <li><b>只用 id 融合，正文回表读</b>。融合需要的是「两路是否都认为它相关」，
 *       只需要稳定标识；而正文必须来自正本 {@code kb_chunk}。这顺带白拿了一个能力：
 *       向量库里的陈尸条目（块已删、向量还在）在回表时自然被丢弃，
 *       不需要额外的对账任务。</li>
 *   <li><b>任何一路失败都不让整体失败</b>。稀疏挂了走稠密，稠密挂了走稀疏，
 *       两路都挂才返回空——而且路径标记会如实写出是哪一种情况。
 *       检索是对用户可见的主链路，宁可给一半结果，也不要 500。</li>
 * </ol>
 *
 * <h3>路径标记（写入 {@code retrieval_log.path}）</h3>
 * <ul>
 *   <li>{@code hybrid} —— 两路都召回，理想状态</li>
 *   <li>{@code sparse_only} —— 只有词法路召回：可能确实是术语查询，也可能是向量路没在工作</li>
 *   <li>{@code dense_only} —— 只有向量路召回：典型的口语化提问</li>
 *   <li>{@code sparse_like} —— <b>退化信号</b>：向量路无召回 <i>且</i> 词法路走的是 LIKE 兜底。
 *       这个指标长期偏高就是「向量服务一直是坏的」的铁证</li>
 *   <li>{@code none} —— 空结果</li>
 * </ul>
 */
@Slf4j
@Service
public class KbSearchService {

    public static final String PATH_HYBRID = "hybrid";
    public static final String PATH_SPARSE_ONLY = "sparse_only";
    public static final String PATH_DENSE_ONLY = "dense_only";
    public static final String PATH_SPARSE_LIKE = "sparse_like";
    public static final String PATH_NONE = "none";

    /** 单次检索允许的最大条数，防止管理端传个 100000 把结果集拉爆。 */
    private static final int MAX_TOP_K = 50;

    @Resource
    private KbSparseRetriever sparseRetriever;

    @Resource
    private KbVectorIndexer vectorIndexer;

    @Resource
    private KbChunkMapper kbChunkMapper;

    @Resource
    private KnowledgeBaseMapper knowledgeBaseMapper;

    @Resource
    private RagProperties ragProperties;

    @Resource
    private RetrievalLogRecorder logRecorder;

    /** 一条知识库命中。 */
    public static final class KbHit {

        private final Long chunkId;
        private final Long docId;
        private final Integer kbId;
        private final String kbName;
        private final String title;
        private final String content;
        private final double score;
        private final String source;

        KbHit(Long chunkId, Long docId, Integer kbId, String kbName, String title,
              String content, double score, String source) {
            this.chunkId = chunkId;
            this.docId = docId;
            this.kbId = kbId;
            this.kbName = kbName;
            this.title = title;
            this.content = content;
            this.score = score;
            this.source = source;
        }

        public Long getChunkId() {
            return chunkId;
        }

        /**
         * 所属文档 id。
         *
         * <p>评测需要它：金标可以标在「块」粒度，也可以标在「文档」粒度
         * （{@code rag_eval_case.match_level = doc}）。文档级匹配时必须把
         * 召回到的块折算回文档，否则永远算不出正确的召回率。</p>
         */
        public Long getDocId() {
            return docId;
        }

        public Integer getKbId() {
            return kbId;
        }

        public String getKbName() {
            return kbName;
        }

        public String getTitle() {
            return title;
        }

        public String getContent() {
            return content;
        }

        public double getScore() {
            return score;
        }

        /** sparse / dense / both */
        public String getSource() {
            return source;
        }
    }

    /** 一次检索的完整结果与过程指标。 */
    public static final class SearchOutcome {

        private final List<KbHit> hits;
        private final String path;
        private final int sparseHits;
        private final int denseHits;
        private final int bothHits;
        private final boolean likedFallback;
        private final long latencyMs;
        private final List<Integer> kbIds;

        SearchOutcome(List<KbHit> hits, String path, int sparseHits, int denseHits, int bothHits,
                      boolean likedFallback, long latencyMs, List<Integer> kbIds) {
            this.hits = hits;
            this.path = path;
            this.sparseHits = sparseHits;
            this.denseHits = denseHits;
            this.bothHits = bothHits;
            this.likedFallback = likedFallback;
            this.latencyMs = latencyMs;
            this.kbIds = kbIds;
        }

        public List<KbHit> hits() {
            return hits;
        }

        public String path() {
            return path;
        }

        public int sparseHits() {
            return sparseHits;
        }

        public int denseHits() {
            return denseHits;
        }

        public int bothHits() {
            return bothHits;
        }

        public boolean likedFallback() {
            return likedFallback;
        }

        public long latencyMs() {
            return latencyMs;
        }

        public List<Integer> kbIds() {
            return kbIds;
        }
    }

    // ------------------------------------------------------------------ 检索

    /**
     * 检索。
     *
     * @param query    用户问题
     * @param keywords 关键词（稀疏兜底路用）；为 null 时现场从 query 抽取
     * @param kbIds    限定知识库；为空表示按 audience 取全部启用知识库
     * @param audience 受众（doctor / user / ...），仅在 kbIds 为空时参与解析
     * @param userId   发起用户（写日志用）
     * @param topK     返回条数
     * @return 检索结果（永不返回 null）
     */
    public SearchOutcome search(String query, List<String> keywords, List<Integer> kbIds,
                                String audience, Integer userId, int topK) {
        long start = System.currentTimeMillis();
        int k = topK <= 0 ? 5 : Math.min(topK, MAX_TOP_K);
        int candidate = k * ragProperties.effectiveCandidateFactor();

        List<KnowledgeBase> kbs = resolveKnowledgeBases(kbIds, audience);
        List<Integer> ids = idsOf(kbs);
        if (ids.isEmpty()) {
            // 一个启用的知识库都没有：这不是错误，是「还没配」。
            // 仍要落日志——否则「空结果率高」会被误读成检索质量问题。
            long cost = System.currentTimeMillis() - start;
            logRecorder.record(query, audience, userId, ids, k, 0, 0, 0, 0,
                    PATH_NONE, cost, null);
            return new SearchOutcome(Collections.emptyList(), PATH_NONE, 0, 0, 0, false, cost, ids);
        }

        // ---- 稀疏路 ----
        KbSparseRetriever.SparseResult sparse =
                sparseRetriever.search(query, keywords, ids, candidate);
        List<KbChunk> sparseChunks = sparse.chunks();

        // ---- 稠密路 ----
        List<String> denseKeys = Collections.emptyList();
        if (ragProperties.isDenseEnabled()) {
            denseKeys = denseKeys(kbs, query, candidate);
        }

        // ---- 融合 ----
        List<String> sparseKeys = new ArrayList<>(sparseChunks.size());
        for (KbChunk chunk : sparseChunks) {
            if (chunk != null && chunk.getId() != null) {
                sparseKeys.add(String.valueOf(chunk.getId()));
            }
        }
        List<RRFusion.FusedItem> fused = RRFusion.fuse(
                sparseKeys, denseKeys,
                ragProperties.effectiveSparseWeight(),
                ragProperties.effectiveDenseWeight(),
                ragProperties.getRrfK(), k);

        // ---- 回读正本 ----
        Map<Long, KbChunk> byId = new LinkedHashMap<>();
        for (KbChunk chunk : sparseChunks) {
            if (chunk != null && chunk.getId() != null) {
                byId.put(chunk.getId(), chunk);
            }
        }
        List<Long> missing = new ArrayList<>();
        for (RRFusion.FusedItem item : fused) {
            Long id = parseId(item.getKey());
            if (id != null && !byId.containsKey(id)) {
                missing.add(id);
            }
        }
        if (!missing.isEmpty()) {
            try {
                List<KbChunk> loaded = kbChunkMapper.listByIds(missing);
                if (loaded != null) {
                    for (KbChunk chunk : loaded) {
                        if (chunk != null && chunk.getId() != null) {
                            byId.put(chunk.getId(), chunk);
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("[RAG-kb] 回读块正本失败（稠密命中将被丢弃）: {}", e.getMessage());
            }
        }

        Map<Integer, String> kbNames = new LinkedHashMap<>();
        for (KnowledgeBase kb : kbs) {
            kbNames.put(kb.getId(), kb.getName());
        }

        List<KbHit> hits = new ArrayList<>(fused.size());
        for (RRFusion.FusedItem item : fused) {
            Long id = parseId(item.getKey());
            KbChunk chunk = id == null ? null : byId.get(id);
            if (chunk == null) {
                // 向量库里有、正本里没有：块已删或已停用。丢弃即是对账。
                continue;
            }
            if (Boolean.FALSE.equals(chunk.getEnabled())) {
                continue;
            }
            hits.add(new KbHit(chunk.getId(), chunk.getDocId(), chunk.getKbId(),
                    kbNames.get(chunk.getKbId()),
                    chunk.getTitle(), chunk.getContent(),
                    item.getScore(), item.source()));
        }

        String path = classify(sparseChunks.size(), denseKeys.size(), hits.size(),
                sparse.likedFallback());
        long cost = System.currentTimeMillis() - start;
        logRecorder.record(query, audience, userId, ids, k,
                sparseChunks.size(), denseKeys.size(), RRFusion.countBoth(fused), hits.size(),
                path, cost, topsOf(hits));
        return new SearchOutcome(hits, path, sparseChunks.size(), denseKeys.size(),
                RRFusion.countBoth(fused), sparse.likedFallback(), cost, ids);
    }

    /** 便利方法：只要命中列表。 */
    public List<KbHit> searchHits(String query, List<String> keywords, List<Integer> kbIds,
                                  String audience, Integer userId, int topK) {
        return search(query, keywords, kbIds, audience, userId, topK).hits();
    }

    /**
     * 路径归类。
     *
     * <p>包级可见，便于直接单测这条判定——它是「退化可见」的唯一出口，
     * 判错就等于把观测能力做没了，值得单独锁住。</p>
     *
     * @param sparseHits   稀疏路召回条数
     * @param denseHits    稠密路召回条数
     * @param fusedHits    融合后实际可用条数（回读正本之后）
     * @param likedFallback 稀疏路是否走了 LIKE 兜底
     */
    static String classify(int sparseHits, int denseHits, int fusedHits, boolean likedFallback) {
        if (fusedHits <= 0) {
            return PATH_NONE;
        }
        if (denseHits <= 0) {
            // 稠密路零召回 + 词法路靠 LIKE 撑着 = 这台系统此刻只是一台关键词检索。
            // 单列一类，就是为了让这个状态在监控上一眼可辨。
            return likedFallback ? PATH_SPARSE_LIKE : PATH_SPARSE_ONLY;
        }
        if (sparseHits <= 0) {
            return PATH_DENSE_ONLY;
        }
        return PATH_HYBRID;
    }

    // ------------------------------------------------------------------ 内部

    private List<KnowledgeBase> resolveKnowledgeBases(List<Integer> kbIds, String audience) {
        try {
            List<KnowledgeBase> kbs;
            if (kbIds != null && !kbIds.isEmpty()) {
                kbs = knowledgeBaseMapper.listByIds(kbIds);
            } else {
                kbs = knowledgeBaseMapper.listEnabled(audience);
            }
            if (kbs == null) {
                return Collections.emptyList();
            }
            // 停用的库即便被显式点名也不参与——「停用」就该是停用，
            // 否则管理端那个开关只是装饰。
            List<KnowledgeBase> enabled = new ArrayList<>(kbs.size());
            for (KnowledgeBase kb : kbs) {
                if (kb != null && kb.getId() != null && !Boolean.FALSE.equals(kb.getEnabled())) {
                    enabled.add(kb);
                }
            }
            return enabled;
        } catch (Exception e) {
            log.warn("[RAG-kb] 解析知识库失败（11_knowledge_base.sql 是否已执行？）: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 稠密路：逐知识库查向量集合，按 {@code 分数 × 库权重} 合并成一条统一名次。
     *
     * <p>合并不是可选项。向量库是按知识库分集合存的，一次问句要打 N 个集合，
     * 每个集合都返回「自己内部的第 1 名」。如果直接把 N 条并列第 1 扔给 RRF，
     * 排名语义就废了——所以必须先按分数归并成一条列表。</p>
     */
    private List<String> denseKeys(List<KnowledgeBase> kbs, String query, int candidate) {
        Map<String, Double> scored = new LinkedHashMap<>();
        for (KnowledgeBase kb : kbs) {
            if (!vectorIndexer.isAvailable(kb.getId())) {
                continue;
            }
            List<LocalVectorStore.SearchResult> hits = vectorIndexer.search(kb.getId(), query, candidate);
            double kbWeight = kb.getWeight() == null ? 1.0 : kb.getWeight().doubleValue();
            for (LocalVectorStore.SearchResult hit : hits) {
                if (hit == null || hit.metadata == null) {
                    continue;
                }
                Long chunkId = parseId(String.valueOf(hit.metadata.get("chunk_id")));
                if (chunkId == null) {
                    continue;
                }
                // 负余弦 = 语义相反，按 0 相关处理；不钳的话乘权重后反而会「变高」。
                double score = Math.max(0.0, hit.score) * Math.max(0.0, kbWeight);
                scored.merge(String.valueOf(chunkId), score, Math::max);
            }
        }
        return RRFusion.mergeByScoreDesc(scored);
    }

    private static List<Integer> idsOf(List<KnowledgeBase> kbs) {
        List<Integer> ids = new ArrayList<>(kbs.size());
        for (KnowledgeBase kb : kbs) {
            if (kb != null && kb.getId() != null) {
                ids.add(kb.getId());
            }
        }
        return ids;
    }

    private static List<Map<String, Object>> topsOf(List<KbHit> hits) {
        List<Map<String, Object>> tops = new ArrayList<>();
        for (KbHit hit : hits) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("chunk_id", hit.getChunkId());
            row.put("kb_id", hit.getKbId());
            row.put("score", Math.round(hit.getScore() * 10000) / 10000.0);
            row.put("source", hit.getSource());
            tops.add(row);
        }
        return tops;
    }

    /** 元数据里的 id 可能是 Integer / Long / String（JSON 往返后不确定），统一按字符串解析。 */
    private static Long parseId(String raw) {
        if (raw == null || raw.isEmpty() || "null".equals(raw)) {
            return null;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ------------------------------------------------------------------ 统计

    /** 检索质量总览（供管理端「召回/精确度」面板）。 */
    public Map<String, Object> summarize(int days) {
        Map<String, Object> out = new LinkedHashMap<>(logRecorder.summarize(days));
        out.put("byPath", logRecorder.byPath(days));
        return out;
    }
}
