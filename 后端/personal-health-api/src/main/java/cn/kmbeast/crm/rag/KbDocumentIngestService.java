package cn.kmbeast.crm.rag;

import cn.kmbeast.crm.config.RagProperties;
import cn.kmbeast.crm.rag.parse.DocumentParseException;
import cn.kmbeast.crm.rag.parse.DocumentParserRegistry;
import cn.kmbeast.crm.rag.parse.ExtractedText;
import cn.kmbeast.mapper.KbChunkMapper;
import cn.kmbeast.mapper.KbDocumentMapper;
import cn.kmbeast.mapper.KnowledgeBaseMapper;
import cn.kmbeast.pojo.entity.KbChunk;
import cn.kmbeast.pojo.entity.KbDocument;
import cn.kmbeast.pojo.entity.KnowledgeBase;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

/**
 * 文档灌库管线：字节/文本 → 解析 → 分块 → 落 {@code kb_chunk} → 向量化。
 *
 * <h3>「入库成功」的定义</h3>
 * <p>本管线的成功终点是 <b>块已落 MySQL</b>，<u>不</u>包含向量化。
 * 向量化是<b>可选增强</b>：嵌入服务没配、超时、限流时，块依然在库里，
 * 稀疏路依然能检索到它们。若把「向量化成功」也作为入库的充分条件，
 * 嵌入服务一抖就会整批文档入库失败——把一条本来能用的链路拖死。</p>
 * <p>代价是会出现「已入库但稠密路召回不到」的文档。这个代价用
 * {@code kb_document.chunk_count} 与向量集合的条数对比就能发现，
 * 管理端可对单篇执行「重建索引」补齐。</p>
 *
 * <h3>状态机</h3>
 * <pre>PARSING → INDEXED（成功）
 * PARSING → FAILED （解析失败/分块为空/超限，error_msg 写明原因）</pre>
 * <p>失败时文档行<b>保留</b>而不是回滚删除。理由：失败原因（扫描件无文字层、
 * 文件损坏、体积超限）是管理员最需要看到的信息，回滚掉就等于让人对着
 * 「上传失败」四个字猜。列表里带着 FAILED 状态与 error_msg 才是可用的反馈。</p>
 */
@Slf4j
@Service
public class KbDocumentIngestService {

    /**
     * 单条 INSERT 语句携带的行数上限。
     *
     * <p>不设这个数就得赌 {@code max_allowed_packet}。默认值在不同 MySQL 版本
     * 与部署间差别很大（4MB / 16MB / 64MB），一次 5000 块的批量插入
     * 很容易在别人的环境上炸而本地正常。200 行/语句对 200 字的中文块
     * 约 50KB，离任何默认上限都远。</p>
     */
    private static final int SQL_INSERT_BATCH = 200;

    @Resource
    private DocumentParserRegistry parserRegistry;

    @Resource
    private KbDocumentMapper kbDocumentMapper;

    @Resource
    private KbChunkMapper kbChunkMapper;

    @Resource
    private KnowledgeBaseMapper knowledgeBaseMapper;

    @Resource
    private KbVectorIndexer vectorIndexer;

    @Resource
    private RagProperties ragProperties;

    // ------------------------------------------------------------------ 入口

    /**
     * 灌入上传的文件。
     *
     * @return 灌库后的文档（status = INDEXED）
     * @throws DocumentParseException 解析失败 / 内容重复 / 分块超限
     */
    public KbDocument ingestFile(KnowledgeBase kb, byte[] content, String fileName,
                                 Integer uploaderId, String audience) {
        requireKb(kb);
        if (content == null || content.length == 0) {
            throw new DocumentParseException("文件内容为空");
        }
        if (fileName == null || fileName.trim().isEmpty()) {
            throw new DocumentParseException("缺少文件名，无法判断文件类型");
        }

        String hash = sha256(content);
        KbDocument duplicate = safeFindByHash(kb.getId(), hash);
        if (duplicate != null) {
            if ("INDEXED".equals(duplicate.getStatus())) {
                // 显式拒绝而不是静默复用：静默复用会让管理员以为「上传成功了」，
                // 而实际上他刚编辑过的文件内容一个字都没进去。
                throw new DocumentParseException(
                        "该知识库下已存在内容完全相同的文档《" + duplicate.getTitle()
                                + "》(id=" + duplicate.getId() + ")。如需重新解析，请先删除原文档。");
            }
            // 上次解析失败的残留：直接清掉，免得列表里堆一串同内容的失败记录
            purgeDocument(duplicate);
        }

        KbDocument doc = new KbDocument();
        doc.setKbId(kb.getId());
        doc.setTitle(titleOf(fileName));
        doc.setFileName(fileName);
        doc.setFileType(DocumentParserRegistry.extensionOf(fileName));
        doc.setFileSize((long) content.length);
        doc.setSourceType("file");
        doc.setContentHash(hash);
        doc.setStatus("PARSING");
        doc.setUploaderId(uploaderId);
        doc.setAudience(resolveAudience(audience, kb));
        doc.setDeleted(false);
        doc.setChunkCount(0);
        doc.setCharCount(0);
        kbDocumentMapper.save(doc);

        ExtractedText extracted;
        try {
            extracted = parserRegistry.parse(content, fileName);
        } catch (DocumentParseException e) {
            markFailed(doc, e.getMessage());
            throw e;
        }
        return finish(doc, kb, extracted);
    }

    /**
     * 灌入直接粘贴的文本（走同一个出口，因此同样享有「空正文即失败」校验）。
     */
    public KbDocument ingestText(KnowledgeBase kb, String title, String text,
                                 Integer uploaderId, String audience) {
        requireKb(kb);
        ExtractedText extracted = parserRegistry.parseText(text, title);

        String hash = sha256(extracted.getText().getBytes(StandardCharsets.UTF_8));
        KbDocument duplicate = safeFindByHash(kb.getId(), hash);
        if (duplicate != null) {
            if ("INDEXED".equals(duplicate.getStatus())) {
                throw new DocumentParseException(
                        "该知识库下已存在内容完全相同的文档《" + duplicate.getTitle()
                                + "》(id=" + duplicate.getId() + ")。");
            }
            purgeDocument(duplicate);
        }

        KbDocument doc = new KbDocument();
        doc.setKbId(kb.getId());
        doc.setTitle(title == null || title.trim().isEmpty() ? "粘贴文本" : title.trim());
        doc.setFileName(null);
        doc.setFileType("text");
        doc.setFileSize((long) extracted.getText().length());
        doc.setSourceType("text");
        doc.setContentHash(hash);
        doc.setStatus("PARSING");
        doc.setUploaderId(uploaderId);
        doc.setAudience(resolveAudience(audience, kb));
        doc.setDeleted(false);
        doc.setChunkCount(0);
        doc.setCharCount(0);
        kbDocumentMapper.save(doc);

        return finish(doc, kb, extracted);
    }

    // ------------------------------------------------------------------ 管线

    private KbDocument finish(KbDocument doc, KnowledgeBase kb, ExtractedText extracted) {
        List<KbChunk> chunks = chunk(doc, kb, extracted);
        if (chunks.isEmpty()) {
            String msg = "未能从文档中切出任何文本块，请检查内容。";
            markFailed(doc, msg);
            throw new DocumentParseException(msg);
        }
        insertChunks(chunks);
        int vectorized = vectorize(chunks, kb);
        if (vectorized < chunks.size()) {
            log.warn("[RAG-ingest] 文档 {} 向量化 {}/{} 块（稀疏路仍可检索；可在管理端重建索引补齐）",
                    doc.getId(), vectorized, chunks.size());
        }

        KbDocument upd = new KbDocument();
        upd.setId(doc.getId());
        upd.setStatus("INDEXED");
        upd.setChunkCount(chunks.size());
        upd.setCharCount(extracted.getText().length());
        kbDocumentMapper.update(upd);

        refreshCounts(kb.getId());
        log.info("[RAG-ingest] 文档入库完成: kbId={}, docId={}, 块={}, 字数={}, 向量={}",
                kb.getId(), doc.getId(), chunks.size(), extracted.getText().length(), vectorized);

        doc.setStatus("INDEXED");
        doc.setChunkCount(chunks.size());
        doc.setCharCount(extracted.getText().length());
        return doc;
    }

    private List<KbChunk> chunk(KbDocument doc, KnowledgeBase kb, ExtractedText extracted) {
        // 块参数取知识库自己的配置，缺失时回落 ChunkUtil 默认值。
        // 这正是「知识库级可配」的落点：药品说明书用小块、科普长文用大块。
        int chunkSize = kb.getChunkSize() == null ? ChunkUtil.CHUNK_SIZE : kb.getChunkSize();
        int overlap = kb.getChunkOverlap() == null ? ChunkUtil.CHUNK_OVERLAP : kb.getChunkOverlap();
        List<String> pieces = ChunkUtil.split(extracted.getText(), chunkSize, overlap);

        int maxChunks = ragProperties.getMaxChunksPerDocument();
        if (pieces.size() > maxChunks) {
            // 超限直接失败而不是截断：截断入库会得到一份「看起来完整、
            // 实际后半本不存在」的知识，而检索时无从判断。
            String msg = "文档切出 " + pieces.size() + " 块，超过单文档上限 " + maxChunks
                    + " 块（crm.rag.ingest.max-chunks）。请拆分文件后分批上传。";
            markFailed(doc, msg);
            throw new DocumentParseException(msg);
        }

        List<KbChunk> chunks = new ArrayList<>(pieces.size());
        for (int i = 0; i < pieces.size(); i++) {
            KbChunk chunk = new KbChunk();
            chunk.setKbId(kb.getId());
            chunk.setDocId(doc.getId());
            chunk.setChunkIndex(i);
            chunk.setTitle(doc.getTitle());
            chunk.setContent(pieces.get(i));
            chunk.setContentLen(pieces.get(i).length());
            chunk.setEnabled(true);
            chunks.add(chunk);
        }
        return chunks;
    }

    /** 分批插入；{@code batchInsert} 已开 {@code useGeneratedKeys}，插入后每个块都带上了 id。 */
    private void insertChunks(List<KbChunk> chunks) {
        for (int i = 0; i < chunks.size(); i += SQL_INSERT_BATCH) {
            int end = Math.min(i + SQL_INSERT_BATCH, chunks.size());
            kbChunkMapper.batchInsert(new ArrayList<>(chunks.subList(i, end)));
        }
    }

    /**
     * 向量化。
     *
     * <p>按 {@code crm.rag.ingest.batch-size} 分批：{@code batchUpsert} 会把整批文本
     * 塞进<b>一个</b> embedding 请求，一次几千块必然超时或被限流。</p>
     *
     * @return 实际灌入向量库的块数
     */
    public int vectorize(List<KbChunk> chunks, KnowledgeBase kb) {
        if (!ragProperties.isDenseEnabled() || chunks == null || chunks.isEmpty()) {
            return 0;
        }
        int batch = ragProperties.effectiveIngestBatchSize();
        int ok = 0;
        for (int i = 0; i < chunks.size(); i += batch) {
            int end = Math.min(i + batch, chunks.size());
            ok += vectorIndexer.indexChunks(kb.getId(), kb.getCode(), chunks.subList(i, end));
        }
        return ok;
    }

    // ------------------------------------------------------------------ 维护

    /**
     * 重建单个文档的向量索引。
     *
     * <p>用于「块在 MySQL 里、向量库里没有」的补齐（嵌入服务当时不可用）。
     * 先按 {@code doc_id} 删干净再灌，避免同一块出现两条向量。</p>
     *
     * @return 灌入的向量条数
     */
    public int reindex(KbDocument doc, KnowledgeBase kb) {
        if (doc == null || doc.getId() == null || kb == null) {
            return 0;
        }
        vectorIndexer.deleteByDoc(kb.getId(), doc.getId());
        List<KbChunk> chunks = kbChunkMapper.listByDocId(doc.getId());
        if (chunks == null || chunks.isEmpty()) {
            return 0;
        }
        return vectorize(chunks, kb);
    }

    /**
     * 删除文档的块与向量（不删文档行本身）。
     *
     * <p>顺序有意为之：<b>先删向量、再删块</b>。反过来的话，中途失败会留下
     * 「块已没了、向量还在」的状态——检索时向量命中、回读正本失败，
     * 结果里凭空少一条且无迹可寻。而先删向量再失败，只是留下一批查不到的块，
     * 是安全的一侧。</p>
     */
    public void removeChunksAndVectors(KbDocument doc) {
        if (doc == null || doc.getId() == null) {
            return;
        }
        vectorIndexer.deleteByDoc(doc.getKbId(), doc.getId());
        try {
            kbChunkMapper.deleteByDocId(doc.getId());
        } catch (Exception e) {
            log.error("[RAG-ingest] 删除文档块失败: docId={}", doc.getId(), e);
        }
        refreshCounts(doc.getKbId());
    }

    /** 彻底删除文档行 + 块 + 向量（用于清理失败残留）。 */
    public void purgeDocument(KbDocument doc) {
        if (doc == null || doc.getId() == null) {
            return;
        }
        removeChunksAndVectors(doc);
        try {
            kbDocumentMapper.hardDelete(doc.getId());
        } catch (Exception e) {
            log.warn("[RAG-ingest] 物理删除文档失败: docId={}, {}", doc.getId(), e.getMessage());
        }
    }

    /** 重算知识库的文档数/块数。 */
    public void refreshCounts(Integer kbId) {
        if (kbId == null) {
            return;
        }
        try {
            knowledgeBaseMapper.refreshCounts(kbId);
        } catch (Exception e) {
            log.warn("[RAG-ingest] 刷新知识库计数失败: kbId={}, {}", kbId, e.getMessage());
        }
    }

    // ------------------------------------------------------------------ 工具

    private void markFailed(KbDocument doc, String message) {
        if (doc == null || doc.getId() == null) {
            return;
        }
        try {
            KbDocument upd = new KbDocument();
            upd.setId(doc.getId());
            upd.setStatus("FAILED");
            upd.setChunkCount(0);
            upd.setCharCount(0);
            upd.setErrorMsg(message);
            kbDocumentMapper.update(upd);
        } catch (Exception e) {
            log.warn("[RAG-ingest] 记录失败状态时异常: docId={}, {}", doc.getId(), e.getMessage());
        }
    }

    private KbDocument safeFindByHash(Integer kbId, String hash) {
        if (hash == null) {
            return null;
        }
        try {
            return kbDocumentMapper.findByHash(kbId, hash);
        } catch (Exception e) {
            // 去重是增强项，查不到就当没有重复，不能让它挡住正常上传
            log.warn("[RAG-ingest] 内容去重查询失败（忽略）: {}", e.getMessage());
            return null;
        }
    }

    private static void requireKb(KnowledgeBase kb) {
        if (kb == null || kb.getId() == null) {
            throw new IllegalArgumentException("知识库不存在");
        }
    }

    private static String resolveAudience(String audience, KnowledgeBase kb) {
        if (audience != null && !audience.trim().isEmpty()) {
            return audience.trim();
        }
        return kb.getAudience() == null || kb.getAudience().trim().isEmpty() ? "all" : kb.getAudience();
    }

    /** 文件名去掉扩展名作为标题。 */
    private static String titleOf(String fileName) {
        String name = fileName;
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        int dot = name.lastIndexOf('.');
        String title = dot > 0 ? name.substring(0, dot) : name;
        return title.isEmpty() ? name : title;
    }

    /** 内容指纹（用于重复检测）。 */
    public static String sha256(byte[] content) {
        if (content == null) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(content);
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception e) {
            log.warn("[RAG-ingest] 计算内容指纹失败: {}", e.getMessage());
            return null;
        }
    }

    /** 供管理端展示：当前支持的扩展名。 */
    public List<String> supportedExtensions() {
        return new ArrayList<>(parserRegistry.supportedExtensions());
    }
}
