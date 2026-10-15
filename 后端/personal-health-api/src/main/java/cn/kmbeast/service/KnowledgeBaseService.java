package cn.kmbeast.service;

import cn.kmbeast.crm.rag.ChunkUtil;
import cn.kmbeast.crm.rag.KbDataSourceService;
import cn.kmbeast.crm.rag.KbDocumentIngestService;
import cn.kmbeast.crm.rag.KbSearchService;
import cn.kmbeast.crm.rag.KbVectorIndexer;
import cn.kmbeast.crm.rag.RetrievalLogRecorder;
import cn.kmbeast.crm.rag.parse.DocumentParseException;
import cn.kmbeast.mapper.KbChunkMapper;
import cn.kmbeast.mapper.KbDataSourceMapper;
import cn.kmbeast.mapper.KbDocumentMapper;
import cn.kmbeast.mapper.KnowledgeBaseMapper;
import cn.kmbeast.mapper.RetrievalLogMapper;
import cn.kmbeast.pojo.api.ApiResult;
import cn.kmbeast.pojo.api.PageResult;
import cn.kmbeast.pojo.api.Result;
import cn.kmbeast.pojo.dto.query.extend.KbDataSourceQueryDto;
import cn.kmbeast.pojo.dto.query.extend.KbDocumentQueryDto;
import cn.kmbeast.pojo.dto.query.extend.KnowledgeBaseQueryDto;
import cn.kmbeast.pojo.dto.query.extend.RetrievalLogQueryDto;
import cn.kmbeast.pojo.entity.KbChunk;
import cn.kmbeast.pojo.entity.KbDataSource;
import cn.kmbeast.pojo.entity.KbDocument;
import cn.kmbeast.pojo.entity.KnowledgeBase;
import cn.kmbeast.pojo.entity.RetrievalLog;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Resource;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 知识库管理（管理员）：知识库 CRUD、文档灌库、块浏览、数据源配置、检索统计。
 *
 * <h3>为什么删除必须手写级联</h3>
 * <p>本项目的表<b>没有任何物理外键</b>（一致性靠代码保证）。所以「删知识库」
 * 必须手工清掉 doc / chunk / 向量集合，少清一样就留下一堆谁也不认识谁的
 * 孤儿数据 —— 而且不会有任何报错提示。</p>
 *
 * <h3>删除顺序：索引 → 块 → 文档 → 知识库</h3>
 * <p>有意从「最不重要」删到「最重要」。中途失败时剩下的状态是
 * 「知识库还在，但它的文档/块少了几个」——管理员看得见、可以重新上传补齐。
 * 反过来先删知识库行，中途失败就得到一批指不到父记录、列表里也不显示的孤儿文档，
 * 只能靠人工写 SQL 清理。</p>
 */
@Slf4j
@Service
public class KnowledgeBaseService {

    /** 知识库/数据源标识：小写字母、数字、下划线，2~64 位。 */
    private static final String CODE_PATTERN = "^[a-z0-9_]{2,64}$";

    /** 单篇文档回显的块上限，避免把几千块一次塞进响应体。 */
    private static final int CHUNK_PREVIEW_MAX = 200;

    @Resource
    private KnowledgeBaseMapper knowledgeBaseMapper;

    @Resource
    private KbDocumentMapper kbDocumentMapper;

    @Resource
    private KbChunkMapper kbChunkMapper;

    @Resource
    private KbDataSourceMapper kbDataSourceMapper;

    @Resource
    private RetrievalLogMapper retrievalLogMapper;

    @Resource
    private KbDocumentIngestService ingestService;

    @Resource
    private KbVectorIndexer vectorIndexer;

    @Resource
    private KbSearchService kbSearchService;

    @Resource
    private RetrievalLogRecorder retrievalLogRecorder;

    @Resource
    private KbDataSourceService kbDataSourceService;

    // ===================================================================
    // 知识库
    // ===================================================================

    /** 分页查询知识库。 */
    public Result<List<KnowledgeBase>> queryKb(KnowledgeBaseQueryDto dto) {
        try {
            List<KnowledgeBase> rows = knowledgeBaseMapper.query(
                    dto.getCode(), dto.getName(), dto.getAudience(), dto.getEnabled(),
                    dto.getCurrent(), dto.getSize());
            Integer total = knowledgeBaseMapper.queryCount(
                    dto.getCode(), dto.getName(), dto.getAudience(), dto.getEnabled());
            return PageResult.success(rows, total);
        } catch (Exception e) {
            // 迁移脚本未执行时表不存在：给可操作提示，而不是一个空的 500
            log.warn("[KB] 查询知识库失败: {}", e.getMessage());
            return ApiResult.error("知识库表不可用，请先执行 Data/02_迁移脚本/11_knowledge_base.sql");
        }
    }

    /** 知识库详情（含向量集合统计）。 */
    public Result<Map<String, Object>> detailKb(Integer id) {
        if (id == null) {
            return ApiResult.error("缺少知识库 id");
        }
        KnowledgeBase kb = knowledgeBaseMapper.getById(id);
        if (kb == null) {
            return ApiResult.error("知识库不存在");
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("knowledgeBase", kb);
        out.put("vector", vectorIndexer.stats(id));
        out.put("chunkTotal", kbChunkMapper.countByKbId(id));
        return ApiResult.success(out);
    }

    /** 新建知识库。 */
    public Result<KnowledgeBase> saveKb(KnowledgeBase kb) {
        String invalid = validateKb(kb, true);
        if (invalid != null) {
            return ApiResult.error(invalid);
        }
        try {
            if (knowledgeBaseMapper.getByCode(kb.getCode()) != null) {
                return ApiResult.error("知识库标识已存在：" + kb.getCode());
            }
        } catch (Exception e) {
            return ApiResult.error("知识库表不可用，请先执行 11_knowledge_base.sql");
        }
        applyDefaults(kb);
        knowledgeBaseMapper.save(kb);
        // 建库即建集合：等到第一次上传才建的话，首次检索会因为集合不存在
        // 而静默走成纯词法路，看起来像「向量检索没生效」。
        if (Boolean.TRUE.equals(kb.getEnabled())) {
            vectorIndexer.ensureCollection(kb.getId());
        }
        return ApiResult.success(kb);
    }

    /** 修改知识库。 */
    public Result<Void> updateKb(KnowledgeBase kb) {
        if (kb == null || kb.getId() == null) {
            return ApiResult.error("缺少知识库 id");
        }
        KnowledgeBase existing = knowledgeBaseMapper.getById(kb.getId());
        if (existing == null) {
            return ApiResult.error("知识库不存在");
        }
        String invalid = validateKb(kb, false);
        if (invalid != null) {
            return ApiResult.error(invalid);
        }
        if (kb.getChunkSize() != null || kb.getChunkOverlap() != null) {
            // 块参数钳制后回写，保证库里存的就是实际生效的值
            int size = clampChunkSize(kb.getChunkSize() != null
                    ? kb.getChunkSize() : existing.getChunkSize());
            int overlap = clampOverlap(kb.getChunkOverlap() != null
                    ? kb.getChunkOverlap() : existing.getChunkOverlap(), size);
            kb.setChunkSize(size);
            kb.setChunkOverlap(overlap);
        }
        if (kb.getWeight() != null) {
            kb.setWeight(clampWeight(kb.getWeight()));
        }
        knowledgeBaseMapper.update(kb);
        return ApiResult.success();
    }

    /**
     * 删除知识库（级联清理文档 / 块 / 向量集合）。
     *
     * <p>加了一道前置条件：库里还有文本块时拒绝删除。本系统的知识库只能靠
     * 重新上传重建，误删一次等于几天的工作量；而「先删光文档再删库」
     * 这个两步动作，正好给了管理员一次反悔的机会。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> deleteKb(Integer id) {
        if (id == null) {
            return ApiResult.error("缺少知识库 id");
        }
        KnowledgeBase kb = knowledgeBaseMapper.getById(id);
        if (kb == null) {
            return ApiResult.error("知识库不存在");
        }
        int chunkTotal = kbChunkMapper.countByKbId(id);
        if (chunkTotal > 0) {
            return ApiResult.error("该知识库下仍有 " + chunkTotal + " 个文本块，"
                    + "请先删除全部文档再删除知识库（此操作不可撤销）。");
        }
        // 索引 → 块 → 文档 → 知识库，从最不重要删到最重要（见类注释）
        vectorIndexer.deleteKb(id);
        kbChunkMapper.deleteByKbId(id);
        kbDocumentMapper.deleteByKbId(id);
        knowledgeBaseMapper.deleteById(id);
        log.info("[KB] 已删除知识库: id={}, code={}", id, kb.getCode());
        return ApiResult.success();
    }

    // ===================================================================
    // 文档
    // ===================================================================

    /** 分页查询文档。 */
    public Result<List<KbDocument>> queryDoc(KbDocumentQueryDto dto) {
        try {
            List<KbDocument> rows = kbDocumentMapper.query(
                    dto.getKbId(), dto.getTitle(), dto.getStatus(), dto.getSourceType(),
                    dto.getUploaderId(), dto.getCurrent(), dto.getSize());
            Integer total = kbDocumentMapper.queryCount(
                    dto.getKbId(), dto.getTitle(), dto.getStatus(), dto.getSourceType(),
                    dto.getUploaderId());
            return PageResult.success(rows, total);
        } catch (Exception e) {
            log.warn("[KB] 查询文档失败: {}", e.getMessage());
            return ApiResult.error("文档表不可用，请先执行 11_knowledge_base.sql");
        }
    }

    /** 上传文件灌库。 */
    public Result<KbDocument> uploadFile(Integer kbId, byte[] content, String fileName,
                                        Integer uploaderId, String audience) {
        KnowledgeBase kb = kbId == null ? null : knowledgeBaseMapper.getById(kbId);
        if (kb == null) {
            return ApiResult.error("知识库不存在");
        }
        try {
            return ApiResult.success(ingestService.ingestFile(kb, content, fileName, uploaderId, audience));
        } catch (DocumentParseException e) {
            // 解析类失败是「用户可修复的问题」，原样回显原因：
            // 扫描件没文字层 / 格式不支持 / 体积超限 / 内容重复，处置方式各不相同
            return ApiResult.error(e.getMessage());
        } catch (Exception e) {
            log.error("[KB] 文件灌库异常: kbId={}, file={}", kbId, fileName, e);
            return ApiResult.error("灌库失败：" + e.getMessage());
        }
    }

    /** 粘贴文本灌库。 */
    public Result<KbDocument> uploadText(Integer kbId, String title, String text,
                                        Integer uploaderId, String audience) {
        KnowledgeBase kb = kbId == null ? null : knowledgeBaseMapper.getById(kbId);
        if (kb == null) {
            return ApiResult.error("知识库不存在");
        }
        try {
            return ApiResult.success(ingestService.ingestText(kb, title, text, uploaderId, audience));
        } catch (DocumentParseException e) {
            return ApiResult.error(e.getMessage());
        } catch (Exception e) {
            log.error("[KB] 文本灌库异常: kbId={}", kbId, e);
            return ApiResult.error("灌库失败：" + e.getMessage());
        }
    }

    /** 文档的块列表（用于核验分块效果）。 */
    public Result<List<KbChunk>> listChunks(Long docId, Integer limit) {
        if (docId == null) {
            return ApiResult.error("缺少文档 id");
        }
        List<KbChunk> chunks = kbChunkMapper.listByDocId(docId);
        if (chunks == null) {
            chunks = new ArrayList<>();
        }
        int cap = limit == null || limit <= 0 ? CHUNK_PREVIEW_MAX : Math.min(limit, CHUNK_PREVIEW_MAX);
        if (chunks.size() > cap) {
            chunks = new ArrayList<>(chunks.subList(0, cap));
        }
        return PageResult.success(chunks, kbChunkMapper.countByDocId(docId));
    }

    /** 删除文档（软删 + 清块 + 清向量）。 */
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> deleteDoc(Long id) {
        if (id == null) {
            return ApiResult.error("缺少文档 id");
        }
        KbDocument doc = kbDocumentMapper.getById(id);
        if (doc == null || Boolean.TRUE.equals(doc.getDeleted())) {
            return ApiResult.error("文档不存在");
        }
        ingestService.removeChunksAndVectors(doc);
        kbDocumentMapper.softDelete(id);
        log.info("[KB] 已删除文档: id={}, kbId={}", id, doc.getKbId());
        return ApiResult.success();
    }

    /** 重建单篇文档的向量索引（补齐「已入库但没向量」）。 */
    public Result<Map<String, Object>> reindexDoc(Long id) {
        if (id == null) {
            return ApiResult.error("缺少文档 id");
        }
        KbDocument doc = kbDocumentMapper.getById(id);
        if (doc == null || Boolean.TRUE.equals(doc.getDeleted())) {
            return ApiResult.error("文档不存在");
        }
        KnowledgeBase kb = knowledgeBaseMapper.getById(doc.getKbId());
        if (kb == null) {
            return ApiResult.error("文档所属知识库不存在");
        }
        if (!"INDEXED".equals(doc.getStatus())) {
            return ApiResult.error("文档状态为 " + doc.getStatus() + "，只有 INDEXED 的文档才能重建索引");
        }
        int n = ingestService.reindex(doc, kb);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("docId", id);
        out.put("vectorized", n);
        out.put("chunkCount", doc.getChunkCount());
        return ApiResult.success(out);
    }

    // ===================================================================
    // 数据源
    // ===================================================================

    /** 分页查询数据源（密码字段被 @JsonIgnore 挡住，不会外泄）。 */
    public Result<List<KbDataSource>> queryDataSource(KbDataSourceQueryDto dto) {
        try {
            List<KbDataSource> rows = kbDataSourceMapper.query(
                    dto.getCode(), dto.getName(), dto.getType(), dto.getEnabled(),
                    dto.getCurrent(), dto.getSize());
            Integer total = kbDataSourceMapper.queryCount(
                    dto.getCode(), dto.getName(), dto.getType(), dto.getEnabled());
            return PageResult.success(rows, total);
        } catch (Exception e) {
            log.warn("[KB] 查询数据源失败: {}", e.getMessage());
            return ApiResult.error("数据源表不可用，请先执行 11_knowledge_base.sql");
        }
    }

    /** 新建数据源；密码入参为明文，落库前加密。 */
    public Result<KbDataSource> saveDataSource(KbDataSource ds, String plainPassword) {
        String invalid = validateDataSource(ds, true);
        if (invalid != null) {
            return ApiResult.error(invalid);
        }
        if (kbDataSourceMapper.getByCode(ds.getCode()) != null) {
            return ApiResult.error("数据源标识已存在：" + ds.getCode());
        }
        if (plainPassword != null && !plainPassword.isEmpty()) {
            ds.setPasswordCipher(KbDataSourceService.encryptPassword(plainPassword));
        }
        kbDataSourceMapper.save(ds);
        return ApiResult.success(ds);
    }

    /** 修改数据源。密码留空表示「不改」，非空则替换密文。 */
    public Result<Void> updateDataSource(KbDataSource ds, String plainPassword) {
        if (ds == null || ds.getId() == null) {
            return ApiResult.error("缺少数据源 id");
        }
        KbDataSource existing = kbDataSourceMapper.getById(ds.getId());
        if (existing == null) {
            return ApiResult.error("数据源不存在");
        }
        String invalid = validateDataSource(ds, false);
        if (invalid != null) {
            return ApiResult.error(invalid);
        }
        // 不回传密码时不能让它覆盖已存的密文。update 的动态 <if> 判空会跳过它，
        // 这里显式置 null 是为了「意图写在代码里」，而不是靠 SQL 的巧合。
        ds.setPasswordCipher(plainPassword == null || plainPassword.isEmpty()
                ? null
                : KbDataSourceService.encryptPassword(plainPassword));
        kbDataSourceMapper.update(ds);
        // 配置变了必须踢掉旧连接池，否则改的连接串在新池建出来之前都不生效
        kbDataSourceService.evict(ds.getId());
        return ApiResult.success();
    }

    /** 删除数据源（被知识库引用时拒绝）。 */
    public Result<Void> deleteDataSource(Integer id) {
        if (id == null) {
            return ApiResult.error("缺少数据源 id");
        }
        KbDataSource ds = kbDataSourceMapper.getById(id);
        if (ds == null) {
            return ApiResult.error("数据源不存在");
        }
        Integer refs = kbDataSourceMapper.countKnowledgeBaseRefs(id);
        if (refs != null && refs > 0) {
            return ApiResult.error("该数据源仍被 " + refs + " 个知识库引用，请先解除引用。");
        }
        kbDataSourceService.evict(id);
        kbDataSourceMapper.deleteById(id);
        return ApiResult.success();
    }

    /** 测试数据源连通性（会写回 last_check_*）。 */
    public Result<Map<String, Object>> checkDataSource(Integer id) {
        if (id == null) {
            return ApiResult.error("缺少数据源 id");
        }
        return ApiResult.success(kbDataSourceService.check(id));
    }

    /** 只读取数试跑（SQL 过 MySqlAiSqlGuard）。 */
    public Result<List<Map<String, Object>>> previewDataSource(Integer id, String sql, Integer limit) {
        try {
            return ApiResult.success(kbDataSourceService.query(id, sql, limit == null ? 20 : limit));
        } catch (Exception e) {
            return ApiResult.error(e.getMessage());
        }
    }

    // ===================================================================
    // 检索统计
    // ===================================================================

    /** 检索日志分页。 */
    public Result<List<RetrievalLog>> queryRetrievalLog(RetrievalLogQueryDto dto) {
        try {
            List<RetrievalLog> rows = retrievalLogMapper.query(
                    dto.getQueryText(), dto.getAudience(), dto.getPath(), dto.getUserId(),
                    dto.getCurrent(), dto.getSize());
            Integer total = retrievalLogMapper.queryCount(
                    dto.getQueryText(), dto.getAudience(), dto.getPath(), dto.getUserId());
            return PageResult.success(rows, total);
        } catch (Exception e) {
            log.warn("[KB] 查询检索日志失败: {}", e.getMessage());
            return ApiResult.error("检索日志表不可用，请先执行 11_knowledge_base.sql");
        }
    }

    /** 检索链路健康度总览（召回/退化画像）。 */
    public Result<Map<String, Object>> retrievalSummary(Integer days) {
        return ApiResult.success(kbSearchService.summarize(days == null ? 7 : days));
    }

    /** 清理过期检索日志。 */
    public Result<Map<String, Object>> purgeRetrievalLog() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("deleted", retrievalLogRecorder.purgeExpired());
        return ApiResult.success(out);
    }

    /** 支持的文档格式（前端 accept 属性与提示文案共用）。 */
    public Result<List<String>> supportedFormats() {
        return ApiResult.success(ingestService.supportedExtensions());
    }

    // ===================================================================
    // 校验与工具
    // ===================================================================

    private String validateKb(KnowledgeBase kb, boolean requireCode) {
        if (kb == null) {
            return "参数不能为空";
        }
        if (requireCode || kb.getCode() != null) {
            String error = validateCode(kb.getCode(), "知识库");
            if (error != null) {
                return error;
            }
            kb.setCode(kb.getCode().trim());
        }
        if (kb.getName() == null || kb.getName().trim().isEmpty()) {
            return "知识库名称不能为空";
        }
        if (kb.getTopK() != null && (kb.getTopK() < 1 || kb.getTopK() > 20)) {
            return "默认召回条数需在 1~20 之间";
        }
        return null;
    }

    private String validateDataSource(KbDataSource ds, boolean requireCode) {
        if (ds == null) {
            return "参数不能为空";
        }
        if (requireCode || ds.getCode() != null) {
            String error = validateCode(ds.getCode(), "数据源");
            if (error != null) {
                return error;
            }
            ds.setCode(ds.getCode().trim());
        }
        if (ds.getName() == null || ds.getName().trim().isEmpty()) {
            return "数据源名称不能为空";
        }
        String type = ds.getType() == null ? "local" : ds.getType().trim().toLowerCase();
        if (!"local".equals(type) && !"remote".equals(type)) {
            return "数据源类型只能是 local 或 remote";
        }
        ds.setType(type);
        if ("remote".equals(type) && (ds.getJdbcUrl() == null || ds.getJdbcUrl().trim().isEmpty())) {
            return "远端数据源必须填写 JDBC URL";
        }
        return null;
    }

    private static String validateCode(String code, String label) {
        if (code == null || code.trim().isEmpty()) {
            return label + "标识不能为空";
        }
        if (!code.trim().matches(CODE_PATTERN)) {
            return label + "标识只能包含小写字母、数字与下划线，长度 2~64（当前：" + code + "）";
        }
        return null;
    }

    private void applyDefaults(KnowledgeBase kb) {
        if (kb.getAudience() == null || kb.getAudience().trim().isEmpty()) {
            kb.setAudience("all");
        }
        kb.setChunkSize(clampChunkSize(kb.getChunkSize()));
        kb.setChunkOverlap(clampOverlap(kb.getChunkOverlap(), kb.getChunkSize()));
        if (kb.getTopK() == null) {
            kb.setTopK(5);
        }
        kb.setWeight(clampWeight(kb.getWeight()));
        if (kb.getEnabled() == null) {
            kb.setEnabled(true);
        }
    }

    private static int clampChunkSize(Integer size) {
        if (size == null) {
            return ChunkUtil.CHUNK_SIZE;
        }
        return Math.max(20, Math.min(size, 4000));
    }

    private static int clampOverlap(Integer overlap, Integer size) {
        if (overlap == null) {
            return ChunkUtil.CHUNK_OVERLAP;
        }
        return Math.max(0, Math.min(overlap, clampChunkSize(size) / 2));
    }

    /**
     * 库权重钳到 [0, 9.999]。
     *
     * <p>上界取自列定义 {@code DECIMAL(4,3)} —— 只有 1 位整数。
     * 不钳的话前端填个 10 会让 MySQL 报 {@code Out of range value}，
     * 报错信息里只有列名和数字，看不出「是权重填大了」。</p>
     */
    private static BigDecimal clampWeight(BigDecimal weight) {
        if (weight == null) {
            return BigDecimal.ONE;
        }
        if (weight.signum() < 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal max = new BigDecimal("9.999");
        return weight.compareTo(max) > 0 ? max : weight;
    }
}
