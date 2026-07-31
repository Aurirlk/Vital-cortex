package cn.kmbeast.service.impl;

import cn.kmbeast.crm.rag.HybridRetriever;
import cn.kmbeast.service.RAGEvaluationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * RAG 检索质量评测（RAG-02 真实化）。
 *
 * <p>原实现用 Random 伪造指标——已停发。现改为<b>真实检索评测</b>：
 * 对固定 golden set 的每个问题执行混合检索，按检索结果与标准上下文
 * 的覆盖度计算真实指标：
 * <ul>
 *   <li>contextPrecision（上下文精确度）：Top3 检索结果中，与标准上下文
 *       存在关键实体/数字重叠的比例；</li>
 *   <li>faithfulness（忠实度）：检索到的上下文对标准答案要点的覆盖比例
 *       （检索不到 → 模型无法忠实作答，该指标反映检索完备性）；</li>
 *   <li>answerRelevance（答案相关性）：按检索结果里与问题关键词命中的比例估算。</li>
 * </ul>
 * 全部数字来自真实检索链路，非模拟。生产环境建议进一步接入 LLM-as-judge
 * 与更多 golden set 样本（当前 5 题基础集）。
 */
@Slf4j
@Service
public class RAGEvaluationServiceImpl implements RAGEvaluationService {

    @Resource
    private HybridRetriever hybridRetriever;

    // 评测历史（内存存储，生产环境应持久化）
    private final List<Map<String, Object>> evaluationHistory = new ArrayList<>();

    // 告警阈值
    private Map<String, Integer> thresholds = new HashMap<>();

    {
        thresholds.put("contextPrecision", 70);
        thresholds.put("faithfulness", 80);
        thresholds.put("answerRelevance", 70);
    }

    // 测试数据集（golden set，可扩展）
    private static final List<Map<String, String>> TEST_DATASET = Arrays.asList(
        Map.of("question", "高血压患者日常生活中应该注意什么？", "context", "高血压患者应该低盐低脂清淡饮食", "answer", "高血压患者应注意低盐饮食，每天不超过6克，适当运动"),
        Map.of("question", "糖尿病可以吃什么水果？", "context", "糖尿病患者应选择低糖水果", "answer", "糖尿病患者可选择苹果、猕猴桃等低糖水果"),
        Map.of("question", "如何预防感冒？", "context", "春季气温变化大容易感冒", "answer", "适当增减衣物、多喝水、保持室内通风"),
        Map.of("question", "正常血压范围是多少？", "context", "正常血压为收缩压90-140mmHg", "answer", "正常血压为90-140/60-90mmHg"),
        Map.of("question", "空腹血糖正常值是多少？", "context", "空腹血糖正常值为3.9-6.1mmol/L", "answer", "空腹血糖正常值为3.9-6.1mmol/L")
    );

    @Override
    public Map<String, Object> runEvaluation() {
        log.info("[RAG评测] 开始真实检索评测（{} 个 golden 样本）", TEST_DATASET.size());

        double precisionSum = 0;
        double faithfulnessSum = 0;
        double relevanceSum = 0;
        int evaluated = 0;
        List<Map<String, Object>> perQuestion = new ArrayList<>();

        for (Map<String, String> sample : TEST_DATASET) {
            String question = sample.get("question");
            String goldContext = sample.get("context");
            String goldAnswer = sample.get("answer");

            // 提取问题关键词（去除疑问词/介词后按字符重叠粗匹配）
            List<String> keywords = extractKeywords(question);

            List<HybridRetriever.RetrievedDoc> docs;
            try {
                docs = hybridRetriever.search(question, keywords, 3);
            } catch (Exception e) {
                log.warn("[RAG评测] 检索异常: {}", e.getMessage());
                docs = new ArrayList<>();
            }

            String retrievedText = new StringBuilder()
                    .append(docs.stream().map(d -> d.title).reduce("", (a, b) -> a + " " + b))
                    .append(" ")
                    .append(docs.stream().map(d -> d.content).reduce("", (a, b) -> a + " " + b))
                    .toString();

            // contextPrecision：Top3 中与 gold context 有实体/数字重叠的比例
            double precision = docs.isEmpty() ? 0 :
                    docs.stream().filter(d -> overlaps(d.content, goldContext)).count()
                            / (double) Math.min(docs.size(), 3);

            // faithfulness：检索上下文覆盖 gold answer 要点的比例
            double faithfulness = coverage(retrievedText, goldAnswer);

            // answerRelevance：检索结果与问题关键词命中比例
            double relevance = questionRelevance(retrievedText, question);

            precisionSum += precision;
            faithfulnessSum += faithfulness;
            relevanceSum += relevance;
            evaluated++;

            Map<String, Object> q = new LinkedHashMap<>();
            q.put("question", question);
            q.put("contextPrecision", round(precision));
            q.put("faithfulness", round(faithfulness));
            q.put("answerRelevance", round(relevance));
            q.put("retrieved_docs", docs.size());
            perQuestion.add(q);
        }

        double avgPrecision = evaluated > 0 ? precisionSum / evaluated : 0;
        double avgFaithfulness = evaluated > 0 ? faithfulnessSum / evaluated : 0;
        double avgRelevance = evaluated > 0 ? relevanceSum / evaluated : 0;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "pass");
        result.put("contextPrecision", round(avgPrecision * 100));
        result.put("faithfulness", round(avgFaithfulness * 100));
        result.put("answerRelevance", round(avgRelevance * 100));
        result.put("totalEvaluations", evaluationHistory.size() + 1);
        result.put("createTime", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        result.put("method", "真实检索评测（golden set=5，混合检索 Top3）");
        result.put("details", perQuestion);

        boolean pass = avgPrecision * 100 >= thresholds.get("contextPrecision")
                && avgFaithfulness * 100 >= thresholds.get("faithfulness")
                && avgRelevance * 100 >= thresholds.get("answerRelevance");
        result.put("status", pass ? "pass" : "fail");

        evaluationHistory.add(0, result);
        log.info("[RAG评测] 完成: 精确度={}%, 忠实度={}%, 相关性={}%",
                round(avgPrecision * 100), round(avgFaithfulness * 100), round(avgRelevance * 100));
        return result;
    }

    @Override
    public List<Map<String, Object>> getEvaluationHistory(Integer limit) {
        if (limit == null || limit <= 0) limit = 20;
        return evaluationHistory.subList(0, Math.min(limit, evaluationHistory.size()));
    }

    @Override
    public Map<String, Object> getMetricsSummary() {
        Map<String, Object> summary = new HashMap<>();
        if (evaluationHistory.isEmpty()) {
            summary.put("contextPrecision", 0);
            summary.put("faithfulness", 0);
            summary.put("answerRelevance", 0);
            summary.put("totalEvaluations", 0);
            summary.put("status", "not_run");
        } else {
            Map<String, Object> latest = evaluationHistory.get(0);
            summary.put("contextPrecision", latest.get("contextPrecision"));
            summary.put("faithfulness", latest.get("faithfulness"));
            summary.put("answerRelevance", latest.get("answerRelevance"));
            summary.put("totalEvaluations", evaluationHistory.size());
            summary.put("status", latest.get("status"));
        }
        return summary;
    }

    @Override
    public void saveThresholds(Map<String, Integer> thresholds) {
        this.thresholds = thresholds;
        log.info("[RAG评测] 告警阈值已更新: {}", thresholds);
    }

    @Override
    public Map<String, Integer> getThresholds() {
        return thresholds;
    }

    // ==================== 评估辅助 ====================

    /** 两个文本是否有实质重叠（按 2+ 字符的公共 n-gram 或数字/单位） */
    private boolean overlaps(String text, String gold) {
        if (text == null || gold == null) return false;
        String t = text.replaceAll("\\s", "");
        String g = gold.replaceAll("\\s", "");
        // 数字/单位精确匹配优先（如 90-140、3.9-6.1）
        for (String token : g.split("[^0-9.\\-]+")) {
            if (token.length() >= 3 && t.contains(token)) {
                return true;
            }
        }
        // 2 字公共子串
        for (int i = 0; i + 2 <= g.length(); i++) {
            String sub = g.substring(i, i + 2);
            if (t.contains(sub) && !isStopBigram(sub)) {
                return true;
            }
        }
        return false;
    }

    /** gold answer 要点被检索文本覆盖的比例（按 4 字片段计算） */
    private double coverage(String retrieved, String goldAnswer) {
        if (retrieved == null || retrieved.isEmpty() || goldAnswer == null || goldAnswer.isEmpty()) {
            return 0;
        }
        String r = retrieved.replaceAll("\\s", "");
        String g = goldAnswer.replaceAll("\\s", "");
        int total = Math.max(1, g.length() - 3);
        int hit = 0;
        for (int i = 0; i + 4 <= g.length(); i++) {
            String seg = g.substring(i, i + 4);
            if (r.contains(seg)) hit++;
        }
        return (double) hit / total;
    }

    /** 检索结果与问题关键词的命中比例 */
    private double questionRelevance(String retrieved, String question) {
        if (retrieved == null || retrieved.isEmpty()) return 0;
        String r = retrieved.replaceAll("\\s", "");
        String q = question.replaceAll("\\s", "");
        int total = Math.max(1, q.length() - 1);
        int hit = 0;
        for (int i = 0; i + 2 <= q.length(); i++) {
            String seg = q.substring(i, i + 2);
            if (r.contains(seg) && !isStopBigram(seg)) hit++;
        }
        return (double) hit / total;
    }

    /** 常见疑问词/虚词双字组合，避免干扰评分 */
    private boolean isStopBigram(String bigram) {
        String[] stops = {"什么", "应该", "怎么", "可以", "如何", "多少", "哪些", "为什", "时候",
                "注意", "日常", "生活", "正常", "饮食", "预防", "选择", "患者", "应该"};
        for (String s : stops) {
            if (s.equals(bigram)) return true;
        }
        return false;
    }

    private List<String> extractKeywords(String question) {
        List<String> words = new ArrayList<>();
        // 简单切分：取 2 字词作为关键词（疾病名、症状名多为 2-4 字）
        String q = question.replaceAll("[？?。，,.!！\\s]", "");
        for (int i = 0; i + 2 <= q.length(); i++) {
            String bigram = q.substring(i, i + 2);
            if (!isStopBigram(bigram)) {
                words.add(bigram);
            }
        }
        if (words.isEmpty()) {
            words.add(q);
        }
        return words.stream().distinct().limit(8).collect(java.util.stream.Collectors.toList());
    }

    private double round(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
