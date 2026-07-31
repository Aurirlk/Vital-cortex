package cn.kmbeast.controller;

import cn.kmbeast.aop.Protector;
import cn.kmbeast.pojo.api.ApiResult;
import cn.kmbeast.pojo.api.Result;
import cn.kmbeast.service.RAGEvaluationService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/rag")
public class RAGEvaluationController {

    @Resource
    private RAGEvaluationService ragEvaluationService;

    @Protector(role = "管理员")
    @PostMapping("/evaluate")
    public Result<Map<String, Object>> runEvaluation() {
        return ApiResult.success(ragEvaluationService.runEvaluation());
    }

    @Protector(role = "管理员")
    @GetMapping("/evaluations")
    public Result<List<Map<String, Object>> > getEvaluationHistory(
            @RequestParam(required = false) Integer limit) {
        return ApiResult.success(ragEvaluationService.getEvaluationHistory(limit));
    }

    @Protector(role = "管理员")
    @GetMapping("/metrics")
    public Result<Map<String, Object>> getMetricsSummary() {
        return ApiResult.success(ragEvaluationService.getMetricsSummary());
    }

    @Protector(role = "管理员")
    @PostMapping("/thresholds")
    public Result<Void> saveThresholds(@RequestBody Map<String, Integer> thresholds) {
        ragEvaluationService.saveThresholds(thresholds);
        return ApiResult.success();
    }

    @Protector(role = "管理员")
    @GetMapping("/thresholds")
    public Result<Map<String, Integer>> getThresholds() {
        return ApiResult.success(ragEvaluationService.getThresholds());
    }
}
