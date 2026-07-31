package cn.kmbeast.service.impl;

import cn.kmbeast.service.RAGEvaluationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Service
public class RAGEvaluationServiceImpl implements RAGEvaluationService {

    // 评测历史（内存存储，生产环境应持久化）
    private final List<Map<String, Object>> evaluationHistory = new ArrayList<>();

    // 告警阈值
    private Map<String, Integer> thresholds = new HashMap<>();

    {
        thresholds.put("contextPrecision", 70);
        thresholds.put("faithfulness", 80);
        thresholds.put("answerRelevance", 70);
    }

    // 测试数据集（生产环境应从数据库加载）
    private static final List<Map<String, String>> TEST_DATASET = Arrays.asList(
        Map.of("question", "高血压患者应该注意什么？", "context", "高血压患者应该低盐饮食", "answer", "高血压患者应注意低盐饮食，每天不超过6克"),
        Map.of("question", "糖尿病可以吃什么水果？", "context", "糖尿病患者应选择低糖水果", "answer", "糖尿病患者可选择苹果、猕猴桃等低糖水果"),
        Map.of("question", "如何预防感冒？", "context", "春季气温变化大容易感冒", "answer", "适当增减衣物、多喝水、保持室内通风"),
        Map.of("question", "正常血压范围是多少？", "context", "正常血压为收缩压90-140mmHg", "answer", "正常血压为90-140/60-90mmHg"),
        Map.of("question", "空腹血糖正常值是多少？", "context", "空腹血糖正常值为3.9-6.1mmol/L", "answer", "空腹血糖正常值为3.9-6.1mmol/L")
    );

    @Override
    public Map<String, Object> runEvaluation() {
        // RAG-02 整改：原实现用 Random 伪造三项指标（注释自称"模拟评测过程"），
        // 而管理后台把这些数字当真实质量数据展示给决策者，属于数据造假。
        // 在真正的 RAGAS 评测管线（检索 + LLM 打分）落地前，拒绝输出伪指标：
        // 返回"评测不可用"状态，前端可据此灰化指标卡片，而不是显示好看的假数字。
        log.warn("[RAG评测] 真实评测管线未接入，拒绝输出模拟指标");
        Map<String, Object> result = new HashMap<>();
        result.put("status", "unavailable");
        result.put("contextPrecision", null);
        result.put("faithfulness", null);
        result.put("answerRelevance", null);
        result.put("totalEvaluations", 0);
        result.put("createTime", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        result.put("message", "真实评测管线尚未接入，本接口不再返回模拟数据。请实现基于真实检索与 LLM 打分的 RAGAS 评测后再启用。");
        return result;
    }

    @Override
    public List<Map<String, Object>> getEvaluationHistory(Integer limit) {
        if (limit == null || limit <= 0) limit = 20;
        return evaluationHistory.subList(0, Math.min(limit, evaluationHistory.size()));
    }

    @Override
    public Map<String, Object> getMetricsSummary() {
        // RAG-02：同样不允许回放伪指标，全部置为不可用
        Map<String, Object> summary = new HashMap<>();
        summary.put("contextPrecision", null);
        summary.put("faithfulness", null);
        summary.put("answerRelevance", null);
        summary.put("totalEvaluations", 0);
        summary.put("status", "unavailable");
        return summary;
    }

    @Override
    public void saveThresholds(Map<String, Integer> thresholds) {
        this.thresholds = thresholds;
        log.info("告警阈值已更新: {}", thresholds);
    }

    @Override
    public Map<String, Integer> getThresholds() {
        return thresholds;
    }
}
