package cn.kmbeast.service;

import java.util.List;
import java.util.Map;

/**
 * RAG 检索质量评测服务
 * 基于 Ragas 框架的指标：上下文精确度、回答忠实度、答案相关性
 */
public interface RAGEvaluationService {

    /**
     * 运行评测
     * @return 评测结果（包含各指标分数）
     */
    Map<String, Object> runEvaluation();

    /**
     * 获取评测历史
     * @param limit 记录数
     * @return 评测记录列表
     */
    List<Map<String, Object>> getEvaluationHistory(Integer limit);

    /**
     * 获取评测指标汇总
     * @return 指标汇总
     */
    Map<String, Object> getMetricsSummary();

    /**
     * 保存告警阈值
     * @param thresholds 阈值配置
     */
    void saveThresholds(Map<String, Integer> thresholds);

    /**
     * 获取告警阈值
     * @return 阈值配置
     */
    Map<String, Integer> getThresholds();
}
