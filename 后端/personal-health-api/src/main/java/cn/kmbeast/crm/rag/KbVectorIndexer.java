package cn.kmbeast.crm.rag;

import cn.kmbeast.crm.vectordb.LocalVectorStore;
import cn.kmbeast.crm.vectordb.VectorEntity;
import cn.kmbeast.pojo.entity.KbChunk;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 知识库向量索引（稠密路的写入与读取端）。
 *
 * <h3>集合划分：一个知识库一个集合</h3>
 * <p>集合名 {@code kb_<id>}，用 <b>id 而不是 code</b>：集合名会落到文件系统路径下
 * （{@code crm_data/vector_store/<集合名>/}），而 {@code code} 是用户可编辑字段，
 * 允许大小写与特殊字符，直接拿来拼路径必然有一天会出事（Windows 保留名、
 * 路径分隔符、长度上限）。id 单调且天然安全。</p>
 * <p>分库存储的代价是「一次查询要打多个集合再合并名次」（见 {@code KbSearchService}），
 * 换来的是三件必须有的能力：按知识库维度独立重建、独立删除、
 * 以及——检索时能把某个库整体停用而不影响其它库。</p>
 *
 * <h3>向量条目的权威内容不在向量库里</h3>
 * <p>元数据里只存 {@code chunk_id}（外加 kb_id/doc_id/title 用于过滤和展示），
 * 检索命中后<b>回 {@code kb_chunk} 读正文</b>。原因：向量库是「索引」，
 * {@code kb_chunk} 是「正本」。一旦存了正文副本，删块/改块时两边必须同时成功，
 * 而这是做不到的——任一时刻的中断都会留下「索引里有一条已经不存在的正文」，
 * 且没有任何手段能发现它。只存 id 时，这种不一致表现为「查不到」，
 * 一个安全得多的失败模式。</p>
 *
 * <h3>⚠️ 单模型约束</h3>
 * <p>{@code knowledge_base.embedding_model} 这一列目前<b>只是记录，不生效</b>：
 * {@code LocalVectorStoreImpl} 的向量维度是编译期常量 1536，全体集合共用同一个
 * {@code EmbeddingService}。真正做「每库一模型」需要向量库支持按集合存维度，
 * 属于另一项改造。此处不假装支持——检索时也不会按该列切模型，
 * 免得配了个模型名却发现毫无作用、更查不出原因。</p>
 */
@Slf4j
@Service
public class KbVectorIndexer {

    /** 集合名前缀。 */
    public static final String COLLECTION_PREFIX = "kb_";

    @Resource
    private LocalVectorStore vectorStore;

    /** 知识库 id → 向量集合名。 */
    public static String collectionName(Integer kbId) {
        return COLLECTION_PREFIX + kbId;
    }

    /**
     * 确保集合存在。
     *
     * @return 集合当前是否可用
     */
    public boolean ensureCollection(Integer kbId) {
        if (kbId == null) {
            return false;
        }
        try {
            String name = collectionName(kbId);
            if (!vectorStore.collectionExists(name)) {
                vectorStore.createCollection(name);
            }
            return true;
        } catch (Exception e) {
            log.error("[RAG-dense] 创建向量集合失败: kbId={}", kbId, e);
            return false;
        }
    }

    public boolean isAvailable(Integer kbId) {
        if (kbId == null) {
            return false;
        }
        try {
            return vectorStore.collectionExists(collectionName(kbId));
        } catch (Exception e) {
            log.warn("[RAG-dense] 检查集合存在性失败: kbId={}, {}", kbId, e.getMessage());
            return false;
        }
    }

    /**
     * 把一批块灌入向量库。
     *
     * <p>调用前 {@code chunk.id} <b>必须已被回填</b>（{@code KbChunkMapper.batchInsert}
     * 开了 {@code useGeneratedKeys}，插入后 id 就在对象上）。没有 id 就没有稳定 key，
     * 灌进去的向量永远无法被删除或回读。</p>
     *
     * @param kbId   知识库 id
     * @param kbCode 知识库 code（写进元数据便于排查）
     * @param chunks 待灌块
     * @return 成功灌入的块数
     */
    public int indexChunks(Integer kbId, String kbCode, List<KbChunk> chunks) {
        if (kbId == null || chunks == null || chunks.isEmpty()) {
            return 0;
        }
        List<VectorEntity> entities = new ArrayList<>(chunks.size());
        for (KbChunk chunk : chunks) {
            if (chunk == null || chunk.getId() == null || chunk.getContent() == null
                    || chunk.getContent().trim().isEmpty()) {
                continue;
            }
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("kb_id", kbId);
            meta.put("kb_code", kbCode == null ? "" : kbCode);
            meta.put("chunk_id", chunk.getId());
            meta.put("doc_id", chunk.getDocId());
            meta.put("chunk_index", chunk.getChunkIndex());
            meta.put("title", chunk.getTitle() == null ? "" : chunk.getTitle());
            entities.add(VectorEntity.builder()
                    .content(chunk.getContent())
                    .metadata(meta)
                    .build());
        }
        if (entities.isEmpty()) {
            return 0;
        }
        try {
            if (!ensureCollection(kbId)) {
                return 0;
            }
            vectorStore.batchUpsert(collectionName(kbId), entities);
            log.info("[RAG-dense] 知识库 {} 灌入 {} 个向量块", kbId, entities.size());
            return entities.size();
        } catch (Exception e) {
            log.error("[RAG-dense] 灌入向量失败: kbId={}, blocks={}", kbId, entities.size(), e);
            return 0;
        }
    }

    /** 删除某个文档的全部向量。 */
    public int deleteByDoc(Integer kbId, Long docId) {
        if (kbId == null || docId == null) {
            return 0;
        }
        try {
            String name = collectionName(kbId);
            if (!vectorStore.collectionExists(name)) {
                return 0;
            }
            return vectorStore.deleteByMetadata(name, "doc_id", docId);
        } catch (Exception e) {
            log.warn("[RAG-dense] 删除文档向量失败: kbId={}, docId={}, {}", kbId, docId, e.getMessage());
            return 0;
        }
    }

    /** 删除整个知识库的向量集合（知识库删除时调用）。 */
    public void deleteKb(Integer kbId) {
        if (kbId == null) {
            return;
        }
        try {
            String name = collectionName(kbId);
            if (vectorStore.collectionExists(name)) {
                vectorStore.deleteCollection(name);
                log.info("[RAG-dense] 已删除知识库向量集合: {}", name);
            }
        } catch (Exception e) {
            // 删不掉集合不应阻断知识库删除：向量库是索引，留着只是浪费磁盘，
            // 而 kb_chunk 已清空，残留向量即使被检索到也会在回读正本时被丢弃。
            log.error("[RAG-dense] 删除向量集合失败: kbId={}", kbId, e);
        }
    }

    /**
     * 稠密检索。
     *
     * @return 命中结果；集合不存在或嵌入服务不可用时为空列表（<b>不抛异常</b>，
     *         让稀疏路仍能独立完成本次检索）
     */
    public List<LocalVectorStore.SearchResult> search(Integer kbId, String query, int topK) {
        if (kbId == null || query == null || query.trim().isEmpty() || topK <= 0) {
            return Collections.emptyList();
        }
        try {
            String name = collectionName(kbId);
            if (!vectorStore.collectionExists(name)) {
                return Collections.emptyList();
            }
            List<LocalVectorStore.SearchResult> hits = vectorStore.search(name, query, topK);
            return hits == null ? Collections.emptyList() : hits;
        } catch (Exception e) {
            log.warn("[RAG-dense] 向量检索失败（本次退化为稀疏路）: kbId={}, {}", kbId, e.getMessage());
            return Collections.emptyList();
        }
    }

    /** 集合状态（管理端展示用）。 */
    public Map<String, Object> stats(Integer kbId) {
        if (kbId == null) {
            return Collections.emptyMap();
        }
        try {
            return vectorStore.getCollectionStats(collectionName(kbId));
        } catch (Exception e) {
            log.warn("[RAG-dense] 读取集合状态失败: kbId={}, {}", kbId, e.getMessage());
            return Collections.emptyMap();
        }
    }
}
