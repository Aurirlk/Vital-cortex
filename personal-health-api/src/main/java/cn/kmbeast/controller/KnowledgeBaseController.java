package cn.kmbeast.controller;

import cn.kmbeast.aop.Pager;
import cn.kmbeast.aop.Protector;
import cn.kmbeast.context.LocalThreadHolder;
import cn.kmbeast.pojo.api.ApiResult;
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
import cn.kmbeast.service.KnowledgeBaseService;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.Resource;
import java.util.List;
import java.util.Map;

/**
 * 知识库管理（管理员）：知识库、文档上传、数据源、检索质量统计。
 *
 * <h3>为什么单独开 {@code /rag/admin}</h3>
 * <p>与 {@code AgentAdminController} 同一理由：{@code /rag} 下已有的评测端点
 * 是「只读 + 触发评测」，而这里是「改数据、传文件、配远端库连接」。
 * 两者权限面差着一个量级，混在同一前缀下，任何一次 {@code @RequestMapping}
 * 的宽松匹配改动都可能把管理端点暴露出去。物理隔离比逐条 {@code @Protector} 稳。</p>
 *
 * <h3>上传体积</h3>
 * <p>有效上限是 {@code min(spring.servlet.multipart.max-file-size, crm.rag.parse.max-bytes)}，
 * 两者默认都是 10MB。超限由 Spring 在解析 multipart 时抛出，统一在
 * {@code GlobalExceptionHandler} 里转成中文提示 —— 那才是用户唯一会看到的文案。</p>
 */
@Slf4j
@RestController
@RequestMapping("/rag/admin")
public class KnowledgeBaseController {

    @Resource
    private KnowledgeBaseService knowledgeBaseService;

    // ===================================================================
    // 知识库
    // ===================================================================

    /** 分页查询知识库。 */
    @Pager
    @Protector(role = "管理员")
    @PostMapping("/kb/query")
    public Result<List<KnowledgeBase>> queryKb(@RequestBody KnowledgeBaseQueryDto dto) {
        return knowledgeBaseService.queryKb(dto);
    }

    /** 知识库详情（含向量集合统计与块总数）。 */
    @Protector(role = "管理员")
    @GetMapping("/kb/{id}")
    public Result<Map<String, Object>> detailKb(@PathVariable("id") Integer id) {
        return knowledgeBaseService.detailKb(id);
    }

    /** 新建知识库。 */
    @Protector(role = "管理员")
    @PostMapping("/kb/save")
    public Result<KnowledgeBase> saveKb(@RequestBody KnowledgeBase kb) {
        return knowledgeBaseService.saveKb(kb);
    }

    /** 修改知识库。 */
    @Protector(role = "管理员")
    @PutMapping("/kb/update")
    public Result<Void> updateKb(@RequestBody KnowledgeBase kb) {
        return knowledgeBaseService.updateKb(kb);
    }

    /** 删除知识库（级联清理，库内还有文本块时拒绝）。 */
    @Protector(role = "管理员")
    @DeleteMapping("/kb/{id}")
    public Result<Void> deleteKb(@PathVariable("id") Integer id) {
        return knowledgeBaseService.deleteKb(id);
    }

    /** 支持的文档格式（前端 accept 属性与上传提示共用）。 */
    @Protector(role = "管理员")
    @GetMapping("/formats")
    public Result<List<String>> supportedFormats() {
        return knowledgeBaseService.supportedFormats();
    }

    // ===================================================================
    // 文档
    // ===================================================================

    /** 分页查询文档。 */
    @Pager
    @Protector(role = "管理员")
    @PostMapping("/doc/query")
    public Result<List<KbDocument>> queryDoc(@RequestBody KbDocumentQueryDto dto) {
        return knowledgeBaseService.queryDoc(dto);
    }

    /**
     * 上传文件灌库（multipart）。
     *
     * <p>字节全部读进内存后再交给解析器：上限 10MB，堆内一次持有是安全的，
     * 且解析器（PDFBox / POI）本来就需要随机访问整个文档，落临时文件
     * 只是把内存换成了磁盘 + 清理责任，没有收益。</p>
     */
    @Protector(role = "管理员")
    @PostMapping("/doc/upload")
    public Result<KbDocument> uploadDoc(@RequestParam("kbId") Integer kbId,
                                        @RequestParam("file") MultipartFile file,
                                        @RequestParam(value = "audience", required = false) String audience) {
        if (file == null || file.isEmpty()) {
            return ApiResult.error("请选择要上传的文件");
        }
        try {
            return knowledgeBaseService.uploadFile(kbId, file.getBytes(),
                    file.getOriginalFilename(), LocalThreadHolder.getUserId(), audience);
        } catch (Exception e) {
            log.error("[KB] 上传文件读取失败: kbId={}", kbId, e);
            return ApiResult.error("读取上传文件失败：" + e.getMessage());
        }
    }

    /** 粘贴文本灌库。 */
    @Protector(role = "管理员")
    @PostMapping("/doc/text")
    public Result<KbDocument> uploadText(@RequestBody JSONObject body) {
        Integer kbId = body.getInteger("kbId");
        String title = body.getString("title");
        String text = body.getString("content");
        String audience = body.getString("audience");
        return knowledgeBaseService.uploadText(kbId, title, text,
                LocalThreadHolder.getUserId(), audience);
    }

    /** 文档的文本块列表（核验分块效果）。 */
    @Protector(role = "管理员")
    @GetMapping("/doc/{id}/chunks")
    public Result<List<KbChunk>> listChunks(@PathVariable("id") Long id,
                                            @RequestParam(value = "limit", required = false) Integer limit) {
        return knowledgeBaseService.listChunks(id, limit);
    }

    /** 删除文档。 */
    @Protector(role = "管理员")
    @DeleteMapping("/doc/{id}")
    public Result<Void> deleteDoc(@PathVariable("id") Long id) {
        return knowledgeBaseService.deleteDoc(id);
    }

    /** 重建单篇文档的向量索引。 */
    @Protector(role = "管理员")
    @PostMapping("/doc/{id}/reindex")
    public Result<Map<String, Object>> reindexDoc(@PathVariable("id") Long id) {
        return knowledgeBaseService.reindexDoc(id);
    }

    // ===================================================================
    // 数据源
    // ===================================================================

    /** 分页查询数据源（不含密码）。 */
    @Pager
    @Protector(role = "管理员")
    @PostMapping("/ds/query")
    public Result<List<KbDataSource>> queryDataSource(@RequestBody KbDataSourceQueryDto dto) {
        return knowledgeBaseService.queryDataSource(dto);
    }

    /**
     * 新建数据源。
     *
     * <p>用 {@link JSONObject} 而不是直接收 {@link KbDataSource}：密码是<b>明文入参</b>、
     * 密文落库，实体上刻意没有 {@code password} 字段（避免它在任何序列化方向上
     * 泄露）。所以密码只能从原始 JSON 里单独取出来，再交给服务加密。</p>
     */
    @Protector(role = "管理员")
    @PostMapping("/ds/save")
    public Result<KbDataSource> saveDataSource(@RequestBody JSONObject body) {
        KbDataSource ds = body.toJavaObject(KbDataSource.class);
        return knowledgeBaseService.saveDataSource(ds, body.getString("password"));
    }

    /** 修改数据源；{@code password} 留空表示不改密码。 */
    @Protector(role = "管理员")
    @PutMapping("/ds/update")
    public Result<Void> updateDataSource(@RequestBody JSONObject body) {
        KbDataSource ds = body.toJavaObject(KbDataSource.class);
        return knowledgeBaseService.updateDataSource(ds, body.getString("password"));
    }

    /** 删除数据源（被知识库引用时拒绝）。 */
    @Protector(role = "管理员")
    @DeleteMapping("/ds/{id}")
    public Result<Void> deleteDataSource(@PathVariable("id") Integer id) {
        return knowledgeBaseService.deleteDataSource(id);
    }

    /** 测试数据源连通性。 */
    @Protector(role = "管理员")
    @PostMapping("/ds/{id}/check")
    public Result<Map<String, Object>> checkDataSource(@PathVariable("id") Integer id) {
        return knowledgeBaseService.checkDataSource(id);
    }

    /** 只读取数试跑（SQL 过 MySqlAiSqlGuard，非 SELECT 一律拒绝）。 */
    @Protector(role = "管理员")
    @PostMapping("/ds/{id}/preview")
    public Result<List<Map<String, Object>>> previewDataSource(@PathVariable("id") Integer id,
                                                               @RequestBody JSONObject body) {
        return knowledgeBaseService.previewDataSource(id, body.getString("sql"),
                body.getInteger("limit"));
    }

    // ===================================================================
    // 检索统计
    // ===================================================================

    /** 检索链路健康度总览（含退化率与按路径分组）。 */
    @Protector(role = "管理员")
    @GetMapping("/retrieval/summary")
    public Result<Map<String, Object>> retrievalSummary(
            @RequestParam(value = "days", required = false) Integer days) {
        return knowledgeBaseService.retrievalSummary(days);
    }

    /** 检索日志分页。 */
    @Pager
    @Protector(role = "管理员")
    @PostMapping("/retrieval/query")
    public Result<List<RetrievalLog>> queryRetrievalLog(@RequestBody RetrievalLogQueryDto dto) {
        return knowledgeBaseService.queryRetrievalLog(dto);
    }

    /** 清理过期检索日志。 */
    @Protector(role = "管理员")
    @PostMapping("/retrieval/purge")
    public Result<Map<String, Object>> purgeRetrievalLog() {
        return knowledgeBaseService.purgeRetrievalLog();
    }
}
