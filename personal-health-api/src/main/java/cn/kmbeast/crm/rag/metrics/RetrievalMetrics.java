package cn.kmbeast.crm.rag.metrics;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 检索质量指标（纯函数，无 Spring、无 IO）。
 *
 * <h3>为什么这些指标必须由金标算，而不能「看返回了几条」</h3>
 * <p>「返回了 5 条」与「返回对了 5 条」在数字上一模一样。之前的
 * {@code RAGEvaluationServiceImpl} 用启发式打分（数答案里的词有没有出现在
 * 上下文里）冒充准确率，那个数字与召回无关，拿它做「优化前后对比」
 * 会得出完全错误的结论。召回率的分母只能是<b>事先标注的期望答案集</b>。</p>
 *
 * <h3>本类最要紧的一条设计：区分「0 分」与「无法计分」</h3>
 * <p>当一个用例的金标为空时，召回率的分母是 0 —— 这在数学上<b>无定义</b>，
 * 不是 0。把它当 0 会让平均召回率被一道无效题目拉低，看起来像检索出了问题；
 * 把它当 1 又会虚高。两种做法都会让指标失去意义。</p>
 * <p>因此每个用例的得分都带 {@code undefined} 标记，聚合时
 * <b>只对有定义的用例求平均</b>，并在结果里如实报告被排除了几道题。
 * 「5 道题里有 2 道没计分」这句话，比一个来路不明的 0.73 有用得多。</p>
 *
 * <h3>重复命中去重</h3>
 * <p>检索结果按 id 去重（保留首次出现的排名）后再计算。不去重时，
 * 同一个文档出现两次会让 precision 的分母虚增、且让「首个命中的排名」
 * 取决于哪一次出现先被看到，指标随实现细节抖动。</p>
 */
public final class RetrievalMetrics {

    private RetrievalMetrics() {
    }

    /**
     * 单题得分。
     *
     * <p>各指标用 {@code defined} 标记是否有定义，调用方必须显式判断 ——
     * 这正是本类的存在意义，故不提供「反正给个 0」的便捷方法。</p>
     */
    public static final class CaseScore {

        /** 去重后的返回条数 */
        public final int retrievedCount;

        /** 金标条数 */
        public final int goldenCount;

        /** 命中的金标条数 */
        public final int hitCount;

        /** 首个命中项的排名（1 起）；未命中为 0 */
        public final int firstHitRank;

        /** 评测截断深度 k */
        public final int k;

        /** 召回率；金标为空时无定义 */
        public final double recall;

        public final boolean recallDefined;

        /** 准确率 @k；k<=0 时无定义 */
        public final double precisionAtK;

        public final boolean precisionDefined;

        /** 倒数排名；命中时为 1/firstHitRank，未命中为 0 */
        public final double reciprocalRank;

        /** 归一化折损累计增益（二值相关） */
        public final double ndcg;

        private CaseScore(int retrievedCount, int goldenCount, int hitCount, int firstHitRank, int k,
                          double recall, boolean recallDefined,
                          double precisionAtK, boolean precisionDefined,
                          double reciprocalRank, double ndcg) {
            this.retrievedCount = retrievedCount;
            this.goldenCount = goldenCount;
            this.hitCount = hitCount;
            this.firstHitRank = firstHitRank;
            this.k = k;
            this.recall = recall;
            this.recallDefined = recallDefined;
            this.precisionAtK = precisionAtK;
            this.precisionDefined = precisionDefined;
            this.reciprocalRank = reciprocalRank;
            this.ndcg = ndcg;
        }

        /** 是否至少命中一条金标 */
        public boolean isHit() {
            return hitCount > 0;
        }
    }

    /**
     * 计算单题得分。
     *
     * @param retrievedIds 检索返回的 id 列表（按相关度降序）；内部会去重并截断到 k
     * @param goldenIds    金标 id 集合
     * @param k            评测深度（本轮问检索要了多少条）
     */
    public static CaseScore score(List<Long> retrievedIds, Collection<Long> goldenIds, int k) {
        List<Long> ranked = dedupePreservingOrder(retrievedIds);
        if (k > 0 && ranked.size() > k) {
            ranked = ranked.subList(0, k);
        }
        // 必须拷贝成可变集合再 remove —— 直接持有 Set.of() 时下面那次
        // golden.remove(null) 会抛 UnsupportedOperationException（不可变集合）。
        // 这个坑只在「金标为空」这条路径上触发，也就是最不容易被测到的那条。
        Set<Long> golden = new LinkedHashSet<>(goldenIds == null ? List.of() : goldenIds);
        // 剔除金标里的 null：从数据库读逗号分隔串时，一个多余的分隔符就会产生 null，
        // 而 null 会永远命中不了，表现为「召回率莫名上不去」
        golden.remove(null);

        int hits = 0;
        int firstHitRank = 0;
        for (int i = 0; i < ranked.size(); i++) {
            Long id = ranked.get(i);
            if (id != null && golden.contains(id)) {
                hits++;
                if (firstHitRank == 0) {
                    firstHitRank = i + 1;
                }
            }
        }

        boolean recallDefined = !golden.isEmpty();
        double recall = recallDefined ? (double) hits / golden.size() : 0d;

        boolean precisionDefined = k > 0;
        double precision = precisionDefined ? (double) hits / k : 0d;

        double rr = firstHitRank > 0 ? 1.0 / firstHitRank : 0d;

        return new CaseScore(ranked.size(), golden.size(), hits, firstHitRank, k,
                recall, recallDefined, precision, precisionDefined, rr, ndcg(ranked, golden, k));
    }

    /**
     * nDCG（二值相关）：把「命中出现在第几位」折算进分数。
     *
     * <p>为什么在已经有 MRR 的情况下还要它：MRR 只看第一个命中，
     * 对「第 1 位命中 vs 第 1、2、3 位全命中」给出一模一样的分数。
     * nDCG 会把后面那些命中也算进来，用来衡量<b>排序整体的质量</b>。</p>
     *
     * <p>IDCG 取「理想情况下最好的前 min(|金标|, k) 位」的 DCG。
     * 金标为空时返回 0（此时 nDCG 同样无定义，调用方按召回率的
     * {@code defined} 标记一并排除即可）。</p>
     */
    static double ndcg(List<Long> ranked, Set<Long> golden, int k) {
        int limit = k > 0 ? Math.min(ranked.size(), k) : ranked.size();
        if (limit == 0 || golden.isEmpty()) {
            return 0d;
        }
        double dcg = 0d;
        for (int i = 0; i < limit; i++) {
            Long id = ranked.get(i);
            if (id != null && golden.contains(id)) {
                dcg += 1.0 / log2(i + 2);   // 排名 i+1，折损分母 log2(rank+1)
            }
        }
        int ideal = Math.min(golden.size(), limit);
        double idcg = 0d;
        for (int i = 0; i < ideal; i++) {
            idcg += 1.0 / log2(i + 2);
        }
        return idcg == 0d ? 0d : dcg / idcg;
    }

    private static double log2(double v) {
        return Math.log(v) / Math.log(2);
    }

    private static List<Long> dedupePreservingOrder(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return new ArrayList<>();
        }
        Set<Long> seen = new LinkedHashSet<>(ids);
        return new ArrayList<>(seen);
    }

    // ==================== 聚合 ====================

    /** 一次评测的宏平均结果。 */
    public static final class Aggregate {

        /** 参与评测的题目总数（含未计分的） */
        public final int caseTotal;

        /** 召回率有定义、因而参与了均值计算的题目数 */
        public final int recallScored;

        /** 准确率有定义、因而参与了均值计算的题目数 */
        public final int precisionScored;

        /** 至少命中一条金标的题目数 */
        public final int caseHit;

        /** 召回率（宏平均，仅计有定义的题目） */
        public final double recall;

        /** 准确率 @k（宏平均，仅计有定义的题目） */
        public final double precisionAtK;

        /** MRR（宏平均，未命中记 0 —— 它本身对所有题都有定义） */
        public final double mrr;

        /** nDCG（宏平均，仅在召回率有定义的题目上） */
        public final double ndcg;

        /** 命中率 = caseHit / caseTotal */
        public final double hitRate;

        Aggregate(int caseTotal, int recallScored, int precisionScored, int caseHit,
                  double recall, double precisionAtK, double mrr, double ndcg, double hitRate) {
            this.caseTotal = caseTotal;
            this.recallScored = recallScored;
            this.precisionScored = precisionScored;
            this.caseHit = caseHit;
            this.recall = recall;
            this.precisionAtK = precisionAtK;
            this.mrr = mrr;
            this.ndcg = ndcg;
            this.hitRate = hitRate;
        }

        /** 因金标为空而未能计分的题目数。 */
        public int recallUnscored() {
            return caseTotal - recallScored;
        }

        /** 指标是否可信：没有任何一道题有金标时，报出来的 0 毫无意义。 */
        public boolean hasUsableGolden() {
            return recallScored > 0;
        }
    }

    /**
     * 宏平均（每道题等权）。
     *
     * <p>用宏平均而非微平均（把全部命中先汇总再除）：微平均会让
     * 「金标条数多的题」主导结果，而金标条数多只是标注者的习惯，
     * 与被评测系统的质量无关。</p>
     */
    public static Aggregate aggregate(List<CaseScore> scores) {
        if (scores == null || scores.isEmpty()) {
            return new Aggregate(0, 0, 0, 0, 0d, 0d, 0d, 0d, 0d);
        }
        int caseTotal = scores.size();
        int recallScored = 0;
        int precisionScored = 0;
        int caseHit = 0;
        double recallSum = 0d;
        double precisionSum = 0d;
        double rrSum = 0d;
        double ndcgSum = 0d;

        for (CaseScore s : scores) {
            if (s.recallDefined) {
                recallSum += s.recall;
                ndcgSum += s.ndcg;
                recallScored++;
            }
            if (s.precisionDefined) {
                precisionSum += s.precisionAtK;
                precisionScored++;
            }
            if (s.isHit()) {
                caseHit++;
            }
            // MRR 对所有题都有定义：未命中就是 0，这正是它要表达的信息
            rrSum += s.reciprocalRank;
        }

        return new Aggregate(
                caseTotal,
                recallScored,
                precisionScored,
                caseHit,
                recallScored == 0 ? 0d : recallSum / recallScored,
                precisionScored == 0 ? 0d : precisionSum / precisionScored,
                rrSum / caseTotal,
                recallScored == 0 ? 0d : ndcgSum / recallScored,
                (double) caseHit / caseTotal);
    }
}
