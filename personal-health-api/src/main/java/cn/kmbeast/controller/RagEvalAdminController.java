package cn.kmbeast.controller;

import cn.kmbeast.aop.Pager;
import cn.kmbeast.aop.Protector;
import cn.kmbeast.crm.rag.RagRetrievalEvalService;
import cn.kmbeast.pojo.api.ApiResult;
import cn.kmbeast.pojo.api.PageResult;
import cn.kmbeast.pojo.api.Result;
import cn.kmbeast.pojo.dto.query.extend.RagEvalCaseQueryDto;
import cn.kmbeast.pojo.entity.RagEvalCase;
import cn.kmbeast.pojo.entity.RagEvalRun;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 检索质量评测（管理员）：金标评测集维护 + 跑评测 + 看历史。
 *
 * <h3>与既有 {@code /rag/evaluate} 的分工</h3>
 * <p>{@code RAGEvaluationServiceImpl} 那套是「问句 → 检索 → 用 4-gram 覆盖率/关键词重合
 * 打分」的启发式评测，度量的是<b>回答有没有覆盖到预期要点</b>；
 * 本控制器这套是<b>标准的信息检索指标</b>（召回率 / 精确率@K / MRR / nDCG），
 * 度量的是<b>检索有没有把该给的文档找出来</b>。</p>
 * <p>两者回答的是不同问题，因此并存而不是替换。只有后者能给出召回率 ——
 * 因为它依赖人工标注的金标集，而不依赖任何相似度猜测。</p>
 */
@Slf4j
@RestController
@RequestMapping("/rag/admin/eval")
public class RagEvalAdminController {

    @Resource
    private RagRetrievalEvalService ragRetrievalEvalService;

    // ===================================================================
    // 评测执行
    // ===================================================================

    /**
     * 跑一次评测。
     *
     * <p>请求体：{@code {"audience":"all","kbIds":[1,2],"topK":5,"triggerBy":"manual"}}。
     * {@code kbIds} 为空表示按每个用例自己的配置解析。</p>
     */
    @Protector(role = "管理员")
    @PostMapping("/run")
    public Result<Map<String, Object>> run(@RequestBody(required = false) JSONObject body) {
        JSONObject params = body == null ? new JSONObject() : body;
        try {
            RagRetrievalEvalService.Report report = ragRetrievalEvalService.run(
                    params.getString("audience"),
                    params.getList("kbIds", Integer.class),
                    params.getInteger("topK"),
                    params.getString("triggerBy"));
            return ApiResult.success(RagRetrievalEvalService.toMap(report));
        } catch (RagRetrievalEvalService.EvalNotReadyException e) {
            // 「评测集没准备好」是可操作的配置问题，原样回显原因与怎么办
            return ApiResult.error(e.getMessage());
        } catch (Exception e) {
            log.error("[RAG-eval] 评测执行失败", e);
            return ApiResult.error("评测执行失败：" + e.getMessage());
        }
    }

    /** 最近一次评测（含逐题明细）。 */
    @Protector(role = "管理员")
    @GetMapping("/latest")
    public Result<Map<String, Object>> latest(@RequestParam(value = "audience", required = false) String audience) {
        RagEvalRun row = ragRetrievalEvalService.latest(audience);
        if (row == null) {
            return ApiResult.success(null);
        }
        return ApiResult.success(withDetail(row));
    }

    /** 某一次评测的完整报告。 */
    @Protector(role = "管理员")
    @GetMapping("/runs/{runId}")
    public Result<Map<String, Object>> runDetail(@PathVariable("runId") String runId) {
        RagEvalRun row = ragRetrievalEvalService.getByRunId(runId);
        if (row == null) {
            return ApiResult.error("评测记录不存在");
        }
        return ApiResult.success(withDetail(row));
    }

    /** 评测历史（不含逐题明细）。 */
    @Pager
    @Protector(role = "管理员")
    @PostMapping("/runs")
    public Result<List<RagEvalRun>> history(@RequestBody JSONObject body) {
        JSONObject params = body == null ? new JSONObject() : body;
        String audience = params.getString("audience");
        List<RagEvalRun> rows = ragRetrievalEvalService.history(audience,
                params.getInteger("current"), params.getInteger("size"));
        return PageResult.success(rows, ragRetrievalEvalService.historyCount(audience));
    }

    /**
     * 把 {@code detail_json} 解析回对象。
     *
     * <p>数据库里存的是字符串，直接丢给前端会得到一坨需要二次 parse 的文本；
     * 解析失败时保留原始字符串而不是丢掉 —— 明细坏了也该看得见，而不是变成空白。</p>
     */
    private static Map<String, Object> withDetail(RagEvalRun row) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("run", row);
        String json = row.getDetailJson();
        if (json != null && !json.isEmpty()) {
            try {
                out.put("detail", JSON.parse(json));
            } catch (Exception e) {
                out.put("detailRaw", json);
            }
        }
        return out;
    }

    // ===================================================================
    // 评测集
    // ===================================================================

    /** 分页查询评测用例。 */
    @Pager
    @Protector(role = "管理员")
    @PostMapping("/cases/query")
    public Result<List<RagEvalCase>> queryCases(@RequestBody RagEvalCaseQueryDto dto) {
        List<RagEvalCase> rows = ragRetrievalEvalService.queryCases(dto.getQuestion(),
                dto.getMatchLevel(), dto.getAudience(), dto.getEnabled(),
                dto.getCurrent(), dto.getSize());
        return PageResult.success(rows, ragRetrievalEvalService.countCases(dto.getQuestion(),
                dto.getMatchLevel(), dto.getAudience(), dto.getEnabled()));
    }

    /** 用例详情。 */
    @Protector(role = "管理员")
    @GetMapping("/cases/{id}")
    public Result<RagEvalCase> caseDetail(@PathVariable("id") Integer id) {
        RagEvalCase one = ragRetrievalEvalService.getCase(id);
        return one == null ? ApiResult.error("用例不存在") : ApiResult.success(one);
    }

    /** 新建用例。 */
    @Protector(role = "管理员")
    @PostMapping("/cases/save")
    public Result<RagEvalCase> saveCase(@RequestBody RagEvalCase one) {
        String invalid = ragRetrievalEvalService.saveCase(one);
        return invalid == null ? ApiResult.success(one) : ApiResult.error(invalid);
    }

    /** 修改用例。 */
    @Protector(role = "管理员")
    @PutMapping("/cases/update")
    public Result<Void> updateCase(@RequestBody RagEvalCase one) {
        String invalid = ragRetrievalEvalService.updateCase(one);
        return invalid == null ? ApiResult.success() : ApiResult.error(invalid);
    }

    /** 删除用例。 */
    @Protector(role = "管理员")
    @DeleteMapping("/cases/{id}")
    public Result<Void> deleteCase(@PathVariable("id") Integer id) {
        return ragRetrievalEvalService.deleteCase(id) > 0
                ? ApiResult.success() : ApiResult.error("用例不存在");
    }

    /** 启用中的用例数（前端提示「还没编评测集」用）。 */
    @Protector(role = "管理员")
    @GetMapping("/cases/count")
    public Result<Integer> caseCount() {
        return ApiResult.success(ragRetrievalEvalService.countEnabledCases());
    }
}
