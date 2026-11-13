package cn.kmbeast.core.graph;

import cn.kmbeast.aop.Protector;
import cn.kmbeast.pojo.api.ApiResult;
import cn.kmbeast.pojo.api.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 知识图谱导入与统计端点。
 *
 * <p><b>2026-10-07 新增（阶段 0-B / B3）</b>：补齐 {@code neo4j_import.sh} 一直在调用、
 * 但代码里<b>一个都不存在</b>的 5 个端点（此前 {@code grep "graph/import" src → 0 命中}）：
 *
 * <table>
 *   <tr><th>端点</th><th>职责</th><th>数据源</th></tr>
 *   <tr><td>POST /graph/import</td><td>基础三元组 + 疾病词表</td>
 *       <td>seed-triples.json + health_model_config + patient_profile + user_health + visit_record</td></tr>
 *   <tr><td>POST /graph/import/drugs</td><td>药品 + 分类</td>
 *       <td>ai_data/drugs.json + mall_product</td></tr>
 *   <tr><td>POST /graph/import/articles</td><td>文章实体</td><td>news</td></tr>
 *   <tr><td>POST /graph/import/all</td><td>全量编排</td><td>以上全部</td></tr>
 *   <tr><td>GET /graph/stats</td><td>统计与健康状态</td><td>Neo4j + 本地存储</td></tr>
 * </table>
 *
 * <p>🔴 <b>全部限管理员</b>：抽取会遍历 patient_profile（慢病史/家族史）、
 * visit_record（诊断/处方）等敏感表，且写入会改变图谱，绝不可匿名或用户可达。
 *
 * <p>幂等：MERGE 语义，可重复调用。验收标准为连跑两次 {@code /graph/import/all}
 * 后 {@code /graph/stats} 的 nodeCount / relationshipCount 完全不变。
 */
@Slf4j
@RestController
@RequestMapping("/graph")
public class GraphImportController {

    @Resource
    private GraphImportService graphImportService;

    /**
     * 导入基础健康数据（词表 + 指标 + 档案 + 测量 + 就诊）。
     */
    @PostMapping("/import")
    @Protector(role = "管理员")
    public Result<Map<String, Object>> importBase() {
        return ApiResult.success(graphImportService.importBase());
    }

    /**
     * 导入药品数据（drugs.json + mall_product）。
     */
    @PostMapping("/import/drugs")
    @Protector(role = "管理员")
    public Result<Map<String, Object>> importDrugs() {
        return ApiResult.success(graphImportService.importDrugs());
    }

    /**
     * 从知识文章抽取实体（词表命中，零 LLM）。
     */
    @PostMapping("/import/articles")
    @Protector(role = "管理员")
    public Result<Map<String, Object>> importArticles() {
        return ApiResult.success(graphImportService.importArticles());
    }

    /**
     * 一键全量导入（推荐）。
     */
    @PostMapping("/import/all")
    @Protector(role = "管理员")
    public Result<Map<String, Object>> importAll() {
        return ApiResult.success(graphImportService.importAll());
    }

    /**
     * 图谱统计。
     *
     * <p>Neo4j 不可用时<b>仍然返回 200</b>，但在 {@code status} 上标 {@code DEGRADED} ——
     * 与"导入类端点在图谱不可用时应明确失败"不同，统计是只读观测接口，
     * 返回本地兜底的统计比直接报错更有信息量。
     *
     * <p>另提供 {@code /graph/health} 供脚本判断"能不能导入"。
     */
    @GetMapping("/stats")
    @Protector(role = "管理员")
    public Result<Map<String, Object>> stats() {
        return ApiResult.success(graphImportService.stats());
    }

    /**
     * 图谱可用性探测。供 {@code neo4j_import.sh} 在导入前判断，
     * 避免"Neo4j 没起却看到一串成功响应"的假象。
     */
    @GetMapping("/health")
    @Protector(role = "管理员")
    public Result<Map<String, Object>> health() {
        Map<String, Object> out = new LinkedHashMap<>();
        boolean connected = graphImportService.isNeo4jAvailable();
        out.put("neo4jConnected", connected);
        out.put("canImport", true);
        out.put("backend", connected ? "neo4j" : "local");
        out.put("hint", connected
                ? "Neo4j 可用，导入将写入图库并同步落本地"
                : "Neo4j 不可用，导入仅写入本地三元组存储（图谱检索走本地兜底）");
        return ApiResult.success(out);
    }

    /**
     * 清空图谱（重建用）。
     */
    @PostMapping("/clear")
    @Protector(role = "管理员")
    public Result<Map<String, Object>> clear() {
        return ApiResult.success(graphImportService.clear());
    }
}
