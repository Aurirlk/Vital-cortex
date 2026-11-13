package cn.kmbeast.crm.vectordb;

import cn.kmbeast.crm.CrmException;
import cn.kmbeast.crm.config.CrmConfig;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.TypeReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class LocalVectorStoreImpl implements LocalVectorStore {

    @Resource
    private CrmConfig crmConfig;

    @Resource
    private EmbeddingService embeddingService;

    private String storeRoot;
    private static final int DIMENSION = 1536;

    private final Map<String, List<float[]>> vectorsMap = new ConcurrentHashMap<>();
    private final Map<String, List<Double>> normsMap = new ConcurrentHashMap<>();
    private final Map<String, List<VectorEntity>> documentsMap = new ConcurrentHashMap<>();
    private final Map<String, CollectionMetadata> metadataMap = new ConcurrentHashMap<>();
    private final Map<String, VectorBinaryStore> binaryStores = new ConcurrentHashMap<>();

    /**
     * RAG-04：内存索引的读写锁。写入路径（upsert/batchUpsert/delete/compact）
     * 与读取路径（search）并发时，ConcurrentHashMap 只能保证单条 get/put 原子，
     * 无法保证"遍历所有向量 + 读文档列表"这一复合操作的线程安全。
     */
    private final java.util.concurrent.locks.ReadWriteLock indexLock =
            new java.util.concurrent.locks.ReentrantReadWriteLock();

    @PostConstruct
    public void init() {
        storeRoot = crmConfig.getVectorStorePath();
        File dir = new File(storeRoot);
        if (!dir.exists()) dir.mkdirs();
        loadAllCollections();
    }

    private void loadAllCollections() {
        Path collectionsFile = Path.of(storeRoot, "collections.json");
        if (!Files.exists(collectionsFile)) return;

        try {
            String json = new String(Files.readAllBytes(collectionsFile), StandardCharsets.UTF_8);
            JSONObject root = JSON.parseObject(json);
            List<Map<String, Object>> collections = root.getObject("collections", List.class);
            if (collections == null) return;

            for (Map<String, Object> col : collections) {
                String name = (String) col.get("name");
                loadCollection(name);
            }
            log.info("[VectorStore] 已加载 {} 个集合", metadataMap.size());
        } catch (Exception e) {
            log.error("[VectorStore] 加载集合列表失败", e);
        }
    }

    /**
     * 计算三者对齐后的有效条数。
     *
     * <p>⚠️ <b>2026-10-06 整改（阶段 0-A / A2）</b>：向量索引依赖三份数据<b>下标严格对齐</b>，
     * 但它们可能不一致——本次实测 {@code health_knowledge} 集合：元数据声明 312 条，
     * {@code vectors.bin} 只够 208 条，而 {@code documents.json} 有 312 条。
     *
     * <p>原实现只靠 {@link VectorBinaryStore#readAll} 内部的 {@code Math.min} 静默截断，
     * 文档列表仍按 312 构造 → {@link #cosineSearchTopK} 的循环上限是向量数(208)，
     * 于是 <b>下标 208~311 的 104 篇文档永久不可召回，且不报任何错</b>。
     *
     * <p>修复原则：<b>以三者最小值为准</b>，绝不静默。缺失部分通过 {@code log.error} 暴露，
     * 由运维执行 rebuild 补齐（本修只能保证"不错位"，补不回丢失的 embedding）。
     *
     * @param declaredCount    元数据 metadata.json 声明的 docCount
     * @param availableVectors vectors.bin 实际可读的向量条数
     * @param availableDocs    documents.json 实际存在的文档条数
     * @return 可安全参与检索的有效条数
     */
    static int effectiveCount(int declaredCount, int availableVectors, int availableDocs) {
        // fail-safe：任一路数据缺失或为负 → 无法保证下标对齐，宁可不召回也不能错配
        if (declaredCount <= 0 || availableVectors < 0 || availableDocs < 0) {
            return 0;
        }
        return Math.min(declaredCount, Math.min(availableVectors, availableDocs));
    }

    private void loadCollection(String collectionName) {
        try {
            Path colDir = Path.of(storeRoot, collectionName);
            if (!Files.exists(colDir)) return;

            Path metadataFile = colDir.resolve("metadata.json");
            if (!Files.exists(metadataFile)) {
                log.warn("[VectorStore] 集合 '{}' 缺少 metadata.json，已跳过", collectionName);
                return;
            }

            String metaJson = new String(Files.readAllBytes(metadataFile), StandardCharsets.UTF_8);
            CollectionMetadata meta = JSON.parseObject(metaJson, CollectionMetadata.class);
            if (meta == null) {
                log.warn("[VectorStore] 集合 '{}' metadata.json 解析失败，已跳过", collectionName);
                return;
            }

            VectorBinaryStore store = new VectorBinaryStore(colDir.resolve("vectors.bin"), meta.getDimension());
            binaryStores.put(collectionName, store);

            // getDocCount() 是基本类型 int，不涉及 null；负数视为 0
            int declaredCount = Math.max(0, meta.getDocCount());
            int availableVectors = store.availableCount();

            // ---- 载入文档 ----
            List<VectorEntity> docs = new ArrayList<>();
            int availableDocs = 0;
            Path docsFile = colDir.resolve("documents.json");
            if (Files.exists(docsFile)) {
                String docsJson = new String(Files.readAllBytes(docsFile), StandardCharsets.UTF_8);
                JSONObject root = JSON.parseObject(docsJson);
                Map<String, VectorEntity> docMap = JSON.parseObject(
                        root.getJSONObject("documents").toJSONString(),
                        new TypeReference<Map<String, VectorEntity>>() {});
                availableDocs = docMap.size();
                int n = effectiveCount(declaredCount, availableVectors, availableDocs);
                for (int i = 0; i < n; i++) {
                    VectorEntity doc = docMap.get(String.valueOf(i + 1));
                    if (doc == null) {
                        docs.add(VectorEntity.builder().id(i + 1).content("").build());
                    } else {
                        doc.setId(i + 1);
                        docs.add(doc);
                    }
                }
            }

            int effective = effectiveCount(declaredCount, availableVectors, availableDocs);

            // ---- 🔴 不一致必须显式告警：这是「静默不可召回」的唯一可见化手段 ----
            if (declaredCount > effective) {
                log.error("[VectorStore] 集合 '{}' 数据不完整！元数据声明 {} 条，"
                                + "实际可用(向量{} / 文档{}) → 仅前 {} 条可参与检索，"
                                + "另有 {} 条不可召回。请执行知识库重建补齐 embedding。",
                        collectionName, declaredCount, availableVectors, availableDocs,
                        effective, declaredCount - effective);
            }

            // ---- 载入向量（同样按 effective 截断，保证与 docs 下标严格对齐）----
            float[][] vectors = store.readAll(declaredCount);
            List<float[]> vecList = new ArrayList<>(effective);
            List<Double> normList = new ArrayList<>(effective);
            for (int i = 0; i < effective && i < vectors.length; i++) {
                vecList.add(vectors[i]);
                normList.add(vectors[i] != null ? norm(vectors[i]) : 0.0);
            }

            metadataMap.put(collectionName, meta);
            vectorsMap.put(collectionName, vecList);
            normsMap.put(collectionName, normList);
            documentsMap.put(collectionName, docs);

            log.info("[VectorStore] 加载集合 '{}': 声明 {} 条 / 向量 {} 条 / 文档 {} 条 → 有效 {} 条",
                    collectionName, declaredCount, availableVectors, availableDocs, effective);
        } catch (Exception e) {
            log.error("[VectorStore] 加载集合失败: {}", collectionName, e);
        }
    }

    @Override
    public void createCollection(String collectionName) {
        if (metadataMap.containsKey(collectionName)) return;

        try {
            Path colDir = Path.of(storeRoot, collectionName);
            Files.createDirectories(colDir);

            CollectionMetadata meta = new CollectionMetadata();
            meta.setCollectionName(collectionName);
            meta.setDimension(DIMENSION);
            meta.setDocCount(0);
            meta.setNextId(1);
            meta.setIdIndex(new ArrayList<>());
            meta.setDeletedIds(new HashSet<>());
            meta.setCreatedAt(new Date());
            meta.setUpdatedAt(new Date());

            metadataMap.put(collectionName, meta);

            VectorBinaryStore store = new VectorBinaryStore(colDir.resolve("vectors.bin"), DIMENSION);
            binaryStores.put(collectionName, store);

            vectorsMap.put(collectionName, new ArrayList<>());
            normsMap.put(collectionName, new ArrayList<>());
            documentsMap.put(collectionName, new ArrayList<>());

            persistMetadata(collectionName);
            persistDocuments(collectionName);
            persistCollections();
            log.info("[VectorStore] 创建集合: {}", collectionName);
        } catch (Exception e) {
            log.error("[VectorStore] 创建集合失败: {}", collectionName, e);
        }
    }

    @Override
    public boolean collectionExists(String collectionName) {
        return metadataMap.containsKey(collectionName);
    }

    @Override
    public void upsert(String collectionName, String docId, String content, Map<String, Object> metadata) {
        try {
            float[] embedding = embeddingService.embed(content);
            if (embedding == null) {
                log.error("[VectorStore] 无法生成嵌入向量: {}", collectionName);
                return;
            }
            doUpsert(collectionName, content, metadata, embedding);
        } catch (CrmException e) {
            log.error("[VectorStore] 嵌入生成失败: collection={}, error={}", collectionName, e.getMessage());
        }
    }

    @Override
    public void batchUpsert(String collectionName, List<VectorEntity> documents) {
        if (documents == null || documents.isEmpty()) return;

        List<String> texts = new ArrayList<>();
        for (VectorEntity doc : documents) texts.add(doc.getContent());

        try {
            List<float[]> embeddings = embeddingService.batchEmbed(texts);
            if (embeddings == null || embeddings.isEmpty()) {
                log.error("[VectorStore] 批量嵌入返回空: collection={}", collectionName);
                return;
            }
            if (embeddings.size() != documents.size()) {
                log.error("[VectorStore] 批量嵌入数量不匹配: {} vs {}", embeddings.size(), documents.size());
                return;
            }

            for (int i = 0; i < documents.size(); i++) {
                VectorEntity doc = documents.get(i);
                doUpsertCore(collectionName, doc.getContent(), doc.getMetadata(), embeddings.get(i));
            }

            persistMetadata(collectionName);
            persistDocuments(collectionName);
            persistCollections();
        } catch (CrmException e) {
            log.error("[VectorStore] 批量嵌入失败: collection={}, error={}", collectionName, e.getMessage());
        }
    }

    private void doUpsert(String collectionName, String content, Map<String, Object> metadata, float[] embedding) {
        doUpsertCore(collectionName, content, metadata, embedding);
        persistMetadata(collectionName);
        persistDocuments(collectionName);
        persistCollections();
    }

    private void doUpsertCore(String collectionName, String content, Map<String, Object> metadata, float[] embedding) {
        if (!metadataMap.containsKey(collectionName)) {
            createCollection(collectionName);
        }

        indexLock.writeLock().lock();
        try {
            CollectionMetadata meta = metadataMap.get(collectionName);
            int id = meta.getNextId();

            try {
                binaryStores.get(collectionName).append(id, embedding);

                VectorEntity doc = VectorEntity.builder()
                        .id(id)
                        .content(content)
                        .metadata(metadata)
                        .embedding(embedding)
                        .build();
                documentsMap.get(collectionName).add(doc);

                vectorsMap.get(collectionName).add(embedding);
                normsMap.get(collectionName).add(norm(embedding));

                meta.setDocCount(meta.getDocCount() + 1);
                meta.setNextId(id + 1);
                meta.getIdIndex().add(id);
                meta.setUpdatedAt(new Date());
            } catch (Exception e) {
                log.error("[VectorStore] upsert失败: collection={}, id={}", collectionName, id, e);
            }
        } finally {
            indexLock.writeLock().unlock();
        }
    }

    @Override
    public List<SearchResult> search(String collectionName, String query, int topK) {
        float[] queryVec;
        try {
            queryVec = embeddingService.embed(query);
        } catch (CrmException e) {
            log.error("[VectorStore] 搜索嵌入生成失败: {}", e.getMessage());
            return new ArrayList<>();
        }
        if (queryVec == null) return new ArrayList<>();

        indexLock.readLock().lock();
        try {
            if (!metadataMap.containsKey(collectionName)) return new ArrayList<>();

            List<float[]> allVectors = vectorsMap.get(collectionName);
            List<Double> allNorms = normsMap.get(collectionName);
            List<VectorEntity> docs = documentsMap.get(collectionName);
            if (allVectors == null || docs == null) return new ArrayList<>();

            return cosineSearchTopK(queryVec, allVectors, allNorms, docs, topK);
        } finally {
            indexLock.readLock().unlock();
        }
    }

    private List<SearchResult> cosineSearchTopK(float[] queryVec, List<float[]> allVectors,
                                                  List<Double> allNorms, List<VectorEntity> docs, int topK) {
        PriorityQueue<SearchResult> pq = new PriorityQueue<>(
                Comparator.comparingDouble(a -> a.score));
        float queryNorm = (float) norm(queryVec);
        if (queryNorm == 0) return new ArrayList<>();

        for (int i = 0; i < allVectors.size(); i++) {
            float[] v = allVectors.get(i);
            if (v == null) continue;
            double docNorm = allNorms != null && i < allNorms.size() ? allNorms.get(i) : 0.0;
            if (docNorm == 0) continue;

            float dot = dotProduct(queryVec, v);
            float score = (float) (dot / (queryNorm * docNorm));

            if (pq.size() < topK) {
                VectorEntity doc = i < docs.size() ? docs.get(i) : null;
                pq.offer(new SearchResult(i + 1,
                        doc != null ? doc.getContent() : "",
                        doc != null ? doc.getMetadata() : null,
                        score));
            } else if (score > pq.peek().score) {
                pq.poll();
                VectorEntity doc = i < docs.size() ? docs.get(i) : null;
                pq.offer(new SearchResult(i + 1,
                        doc != null ? doc.getContent() : "",
                        doc != null ? doc.getMetadata() : null,
                        score));
            }
        }

        List<SearchResult> results = new ArrayList<>(pq);
        results.sort((a, b) -> Float.compare(b.score, a.score));
        return results;
    }

    private float dotProduct(float[] a, float[] b) {
        float sum = 0;
        for (int i = 0; i < a.length; i++) sum += a[i] * b[i];
        return sum;
    }

    private double norm(float[] v) {
        float sum = 0;
        for (float x : v) sum += x * x;
        return Math.sqrt(sum);
    }

    @Override
    public void delete(String collectionName, int id) {
        indexLock.writeLock().lock();
        try {
            CollectionMetadata meta = metadataMap.get(collectionName);
            if (meta == null) return;

            meta.getDeletedIds().add(id);
            int index = id - 1;
            List<float[]> vecs = vectorsMap.get(collectionName);
            List<Double> norms = normsMap.get(collectionName);
            if (vecs != null && index < vecs.size()) {
                vecs.set(index, null);
                if (norms != null && index < norms.size()) {
                    norms.set(index, 0.0);
                }
            }
            meta.setUpdatedAt(new Date());
            persistMetadata(collectionName);
            persistCollections();
        } finally {
            indexLock.writeLock().unlock();
        }
    }

    /**
     * RAG-19：按元数据匹配删除（文章删除 → 向量库联动清理）。
     * 从 documentsMap 反向找到匹配的向量 id，逐个 delete。
     */
    @Override
    public int deleteByMetadata(String collectionName, String metadataKey, Object metadataValue) {
        List<VectorEntity> docs = documentsMap.get(collectionName);
        if (docs == null || metadataKey == null) {
            return 0;
        }
        String expect = String.valueOf(metadataValue);
        List<Integer> ids = new ArrayList<>();
        for (VectorEntity doc : docs) {
            if (doc.getMetadata() != null) {
                Object v = doc.getMetadata().get(metadataKey);
                if (v != null && expect.equals(String.valueOf(v))) {
                    ids.add(doc.getId());
                }
            }
        }
        for (Integer id : ids) {
            delete(collectionName, id);
        }
        if (!ids.isEmpty()) {
            log.info("[VectorStore] 按元数据删除: collection={}, key={}, value={}, count={}",
                    collectionName, metadataKey, metadataValue, ids.size());
        }
        return ids.size();
    }

    @Override
    public void deleteCollection(String collectionName) {
        indexLock.writeLock().lock();
        try {
            metadataMap.remove(collectionName);
            vectorsMap.remove(collectionName);
            normsMap.remove(collectionName);
            documentsMap.remove(collectionName);
            binaryStores.remove(collectionName);

            try {
                Path colDir = Path.of(storeRoot, collectionName);
                if (Files.exists(colDir)) {
                    Files.walk(colDir)
                            .sorted(Comparator.reverseOrder())
                            .forEach(p -> { try { Files.delete(p); } catch (Exception ignored) {} });
                }
            } catch (Exception e) {
                log.error("[VectorStore] 删除集合失败: {}", collectionName, e);
            }
            persistCollections();
        } finally {
            indexLock.writeLock().unlock();
        }
    }

    @Override
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        int totalDocs = 0;
        for (Map.Entry<String, CollectionMetadata> entry : metadataMap.entrySet()) {
            Map<String, Object> cs = new LinkedHashMap<>();
            cs.put("doc_count", entry.getValue().getDocCount());
            cs.put("dimension", entry.getValue().getDimension());
            cs.put("created_at", entry.getValue().getCreatedAt());
            totalDocs += entry.getValue().getDocCount();
            stats.put(entry.getKey(), cs);
        }
        stats.put("total_collections", metadataMap.size());
        stats.put("total_documents", totalDocs);
        stats.put("store_path", storeRoot);
        return stats;
    }

    @Override
    public Map<String, Object> getCollectionStats(String collectionName) {
        CollectionMetadata meta = metadataMap.get(collectionName);
        if (meta == null) return new LinkedHashMap<>();
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("name", meta.getCollectionName());
        stats.put("doc_count", meta.getDocCount());
        stats.put("dimension", meta.getDimension());
        stats.put("next_id", meta.getNextId());
        stats.put("deleted_ids", meta.getDeletedIds());
        stats.put("created_at", meta.getCreatedAt());
        stats.put("updated_at", meta.getUpdatedAt());
        return stats;
    }

    private void persistMetadata(String collectionName) {
        try {
            Path file = Path.of(storeRoot, collectionName, "metadata.json");
            Path tmp = Path.of(storeRoot, collectionName, "metadata.json.tmp");
            String json = JSON.toJSONString(metadataMap.get(collectionName));
            Files.write(tmp, json.getBytes(StandardCharsets.UTF_8));
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception e) {
            log.error("[VectorStore] 持久化metadata失败: {}", collectionName, e);
        }
    }

    private void persistDocuments(String collectionName) {
        try {
            Path file = Path.of(storeRoot, collectionName, "documents.json");
            Path tmp = Path.of(storeRoot, collectionName, "documents.json.tmp");
            JSONObject root = new JSONObject();
            root.put("version", 1);
            Map<String, Object> docMap = new LinkedHashMap<>();
            List<VectorEntity> docs = documentsMap.get(collectionName);
            if (docs != null) {
                for (VectorEntity doc : docs) {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("content", doc.getContent());
                    entry.put("metadata", doc.getMetadata());
                    docMap.put(String.valueOf(doc.getId()), entry);
                }
            }
            root.put("documents", docMap);
            Files.write(tmp, JSON.toJSONString(root).getBytes(StandardCharsets.UTF_8));
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception e) {
            log.error("[VectorStore] 持久化documents失败: {}", collectionName, e);
        }
    }

    private void persistCollections() {
        try {
            Path file = Path.of(storeRoot, "collections.json");
            Path tmp = Path.of(storeRoot, "collections.json.tmp");
            JSONObject root = new JSONObject();
            root.put("version", 1);
            root.put("updated_at", new Date().toString());
            List<Map<String, Object>> list = new ArrayList<>();
            for (Map.Entry<String, CollectionMetadata> entry : metadataMap.entrySet()) {
                Map<String, Object> col = new LinkedHashMap<>();
                col.put("name", entry.getKey());
                col.put("dimension", entry.getValue().getDimension());
                col.put("doc_count", entry.getValue().getDocCount());
                col.put("created_at", entry.getValue().getCreatedAt());
                list.add(col);
            }
            root.put("collections", list);
            Files.write(tmp, JSON.toJSONString(root).getBytes(StandardCharsets.UTF_8));
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception e) {
            log.error("[VectorStore] 持久化collections失败", e);
        }
    }

    public void compact() {
        indexLock.writeLock().lock();
        try {
            for (String collectionName : metadataMap.keySet()) {
                compactCollection(collectionName);
            }
        } finally {
            indexLock.writeLock().unlock();
        }
    }

    private void compactCollection(String collectionName) {
        CollectionMetadata meta = metadataMap.get(collectionName);
        if (meta == null || meta.getDeletedIds().isEmpty()) return;

        log.info("[VectorStore] 压缩集合 '{}': 移除 {} 个已删除向量",
                collectionName, meta.getDeletedIds().size());

        List<float[]> oldVectors = vectorsMap.get(collectionName);
        List<Double> oldNorms = normsMap.get(collectionName);
        List<VectorEntity> oldDocs = documentsMap.get(collectionName);
        if (oldVectors == null) return;

        List<float[]> newVectors = new ArrayList<>();
        List<Double> newNorms = new ArrayList<>();
        List<VectorEntity> newDocs = new ArrayList<>();
        Map<Integer, Integer> idMap = new HashMap<>();

        int newIndex = 0;
        for (int i = 0; i < oldVectors.size(); i++) {
            int oldId = i + 1;
            if (oldVectors.get(i) == null) continue; // deleted
            newVectors.add(oldVectors.get(i));
            newNorms.add(oldNorms != null && oldNorms.get(i) != null ? oldNorms.get(i) : 0.0);
            if (oldDocs != null && i < oldDocs.size()) {
                VectorEntity doc = oldDocs.get(i);
                doc.setId(newIndex + 1);
                newDocs.add(doc);
            }
            idMap.put(oldId, newIndex + 1);
            newIndex++;
        }

        vectorsMap.put(collectionName, newVectors);
        normsMap.put(collectionName, newNorms);
        documentsMap.put(collectionName, newDocs);

        meta.setDocCount(newVectors.size());
        meta.setNextId(newVectors.size() + 1);
        meta.setDeletedIds(new HashSet<>());
        meta.setIdIndex(new ArrayList<>(idMap.keySet()));
        meta.setUpdatedAt(new Date());

        try {
            // RAG-01 修复：重建向量文件前必须删除旧文件。
            // 原实现直接 new VectorBinaryStore(同一路径) 后 append，
            // 而 append 以 APPEND 模式打开——旧向量全部保留，新向量追加在尾部，
            // readAll 会读到"旧数据+新数据"两份，docCount 却只认新数量，
            // 导致索引错位、检索结果完全错乱，且文件体积永久翻倍。
            Path binPath = Path.of(storeRoot, collectionName, "vectors.bin");
            Files.deleteIfExists(binPath);
            VectorBinaryStore store = new VectorBinaryStore(binPath, DIMENSION);
            for (int i = 0; i < newVectors.size(); i++) {
                store.append(i + 1, newVectors.get(i));
            }
            binaryStores.put(collectionName, store);
        } catch (Exception e) {
            log.error("[VectorStore] 压缩时重写向量文件失败: {}", collectionName, e);
        }

        persistMetadata(collectionName);
        persistDocuments(collectionName);
        persistCollections();

        log.info("[VectorStore] 集合 '{}' 压缩完成: {} -> {} 条文档",
                collectionName, oldVectors.size(), newVectors.size());
    }

    public int getCompactCount(String collectionName) {
        CollectionMetadata meta = metadataMap.get(collectionName);
        if (meta == null) return 0;
        return meta.getDeletedIds().size();
    }

    public static class CollectionMetadata {
        private String collectionName;
        private int dimension;
        private int docCount;
        private int nextId;
        private List<Integer> idIndex;
        private Set<Integer> deletedIds;
        private Date createdAt;
        private Date updatedAt;

        public String getCollectionName() { return collectionName; }
        public void setCollectionName(String v) { this.collectionName = v; }
        public int getDimension() { return dimension; }
        public void setDimension(int v) { this.dimension = v; }
        public int getDocCount() { return docCount; }
        public void setDocCount(int v) { this.docCount = v; }
        public int getNextId() { return nextId; }
        public void setNextId(int v) { this.nextId = v; }
        public List<Integer> getIdIndex() { return idIndex; }
        public void setIdIndex(List<Integer> v) { this.idIndex = v; }
        public Set<Integer> getDeletedIds() { return deletedIds; }
        public void setDeletedIds(Set<Integer> v) { this.deletedIds = v; }
        public Date getCreatedAt() { return createdAt; }
        public void setCreatedAt(Date v) { this.createdAt = v; }
        public Date getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(Date v) { this.updatedAt = v; }
    }

    static class VectorBinaryStore {
        private final int dimension;
        private final Path filePath;

        VectorBinaryStore(Path filePath, int dimension) {
            this.filePath = filePath;
            this.dimension = dimension;
        }

        void append(int id, float[] vector) throws IOException {
            ByteBuffer buffer = ByteBuffer.allocate(dimension * 4);
            buffer.order(ByteOrder.LITTLE_ENDIAN);
            for (float v : vector) buffer.putFloat(v);
            buffer.flip();
            try (FileChannel channel = FileChannel.open(filePath,
                    StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                channel.write(buffer);
            }
        }

        /**
         * 返回 vectors.bin 实际可读的向量条数（不依赖元数据声明）。
         *
         * <p>用于发现「元数据声明 N 条、文件只够 M 条」的数据不一致。
         * 维度为 0 时视为 0 条，避免除零。
         */
        int availableCount() throws IOException {
            if (dimension <= 0 || !Files.exists(filePath)) return 0;
            long bytes = Files.size(filePath);
            return (int) (bytes / (dimension * 4L));
        }

        float[][] readAll(int docCount) throws IOException {
            if (!Files.exists(filePath) || docCount == 0) return new float[0][];
            byte[] fileBytes = Files.readAllBytes(filePath);
            int expectedBytes = dimension * 4;
            int count = Math.min(docCount, fileBytes.length / expectedBytes);
            float[][] vectors = new float[count][dimension];
            ByteBuffer buffer = ByteBuffer.wrap(fileBytes);
            buffer.order(ByteOrder.LITTLE_ENDIAN);
            for (int i = 0; i < count; i++) {
                for (int d = 0; d < dimension; d++) {
                    if (buffer.remaining() >= 4) {
                        vectors[i][d] = buffer.getFloat();
                    }
                }
            }
            return vectors;
        }
    }
}
