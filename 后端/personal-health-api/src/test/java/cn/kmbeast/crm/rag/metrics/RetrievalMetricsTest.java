package cn.kmbeast.crm.rag.metrics;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link RetrievalMetrics} 的单测。
 *
 * <p>这组用例要锁住的核心不是「公式算得对」（那部分是教科书），
 * 而是<b>「无定义」与「0 分」必须被区分开</b>。把无定义当 0 是评测工具
 * 最害人的一类缺陷：它让指标看起来有值，而那个值取决于有多少道题没标金标，
 * 与被评测的检索质量毫无关系。</p>
 */
@DisplayName("检索指标：召回率 / 准确率 / MRR / nDCG")
class RetrievalMetricsTest {

    private static List<Long> ids(Long... v) {
        return new ArrayList<>(Arrays.asList(v));
    }

    private static Set<Long> golden(Long... v) {
        return new java.util.LinkedHashSet<>(Arrays.asList(v));
    }

    // ==================== 基本正确性 ====================

    @Test
    @DisplayName("T1 全部命中且排在前面 → 各项指标都是满分")
    void perfectRetrieval() {
        RetrievalMetrics.CaseScore s = RetrievalMetrics.score(ids(7L, 8L, 9L), golden(7L, 8L), 3);

        assertEquals(3, s.retrievedCount);
        assertEquals(2, s.goldenCount);
        assertEquals(2, s.hitCount);
        assertEquals(1, s.firstHitRank);
        assertEquals(1.0, s.recall, 1e-9);
        assertEquals(2.0 / 3, s.precisionAtK, 1e-9, "命中 2 条 / 请求 3 条");
        assertEquals(1.0, s.reciprocalRank, 1e-9);
        assertEquals(1.0, s.ndcg, 1e-9);
    }

    @Test
    @DisplayName("T2 一条没命中 → 全是 0，但「有定义」，不该被当成未计分")
    void completeMissIsZeroNotUndefined() {
        RetrievalMetrics.CaseScore s = RetrievalMetrics.score(ids(1L, 2L, 3L), golden(7L, 8L), 3);

        assertEquals(0, s.hitCount);
        assertEquals(0, s.firstHitRank);
        assertTrue(s.recallDefined, "金标非空，召回率有定义 —— 它就是 0 分");
        assertEquals(0.0, s.recall, 1e-9);
        assertTrue(s.precisionDefined);
        assertEquals(0.0, s.precisionAtK, 1e-9);
        assertEquals(0.0, s.reciprocalRank, 1e-9);
        assertEquals(0.0, s.ndcg, 1e-9);
    }

    @Test
    @DisplayName("T3 金标为空 → 召回率「无定义」而不是 0 分")
    void emptyGoldenMeansRecallIsUndefined() {
        RetrievalMetrics.CaseScore s = RetrievalMetrics.score(ids(1L, 2L), golden(), 5);

        assertFalse(s.recallDefined,
                "0/0 无定义。当成 0 分会凭空拉低平均召回率，看起来像检索变差了");
        assertEquals(0, s.goldenCount);
        assertTrue(s.precisionDefined, "准确率仍然有定义：命中 0 / 请求 5");
        assertEquals(0.0, s.precisionAtK, 1e-9);
    }

    @Test
    @DisplayName("T4 k<=0 → 准确率无定义（不能除以 0，也不该报 0）")
    void nonPositiveKMeansPrecisionIsUndefined() {
        RetrievalMetrics.CaseScore s = RetrievalMetrics.score(ids(7L), golden(7L), 0);

        assertFalse(s.precisionDefined);
        assertEquals(0.0, s.precisionAtK, 1e-9);
        assertTrue(s.recallDefined);
        assertEquals(1.0, s.recall, 1e-9, "召回率与 k 无关");
    }

    // ==================== 边界与脏数据 ====================

    @Test
    @DisplayName("T5 结果为空 → 召回 0 分（有定义），准确率 0 分（有定义），不炸")
    void emptyRetrievalIsAllZero() {
        RetrievalMetrics.CaseScore s = RetrievalMetrics.score(ids(), golden(7L), 5);

        assertEquals(0, s.retrievedCount);
        assertEquals(0, s.hitCount);
        assertTrue(s.recallDefined);
        assertEquals(0.0, s.recall, 1e-9);
        assertEquals(0.0, s.precisionAtK, 1e-9);
    }

    @Test
    @DisplayName("T6 入参为 null 一律按空处理，不抛 NPE")
    void nullInputsAreTolerated() {
        assertDoesNotThrow(() -> {
            RetrievalMetrics.CaseScore a = RetrievalMetrics.score(null, null, 5);
            assertFalse(a.recallDefined);
            assertTrue(a.precisionDefined);
            RetrievalMetrics.score(ids(1L), null, 5);
            RetrievalMetrics.score(null, golden(1L), 5);
        });
        RetrievalMetrics.CaseScore s = RetrievalMetrics.score(ids(1L), null, 5);
        assertEquals(0, s.goldenCount);
        assertEquals(0, s.hitCount);
    }

    @Test
    @DisplayName("T7 金标里的 null 被剔除 —— 一个多余逗号就能让召回率永远上不去")
    void nullsInGoldenAreDropped() {
        List<Long> withNull = new ArrayList<>(Arrays.asList(7L, null));
        RetrievalMetrics.CaseScore s = RetrievalMetrics.score(ids(7L), withNull, 5);

        assertEquals(1, s.goldenCount, "null 不是一条真金标；留着它会让召回率封顶在 1/2");
        assertEquals(1.0, s.recall, 1e-9);
    }

    @Test
    @DisplayName("T8 重复命中去重：同一文档出现两次不该让准确率虚增，也不该把排名搅乱")
    void duplicatesAreDedupedPreservingFirstRank() {
        RetrievalMetrics.CaseScore s = RetrievalMetrics.score(ids(7L, 7L, 9L, 9L), golden(9L), 4);

        assertEquals(2, s.retrievedCount, "去重后只剩 7 和 9 两条");
        assertEquals(2, s.firstHitRank, "9 首次出现在第 2 位");
        assertEquals(0.5, s.reciprocalRank, 1e-9);
        assertEquals(0.25, s.precisionAtK, 1e-9, "命中 1 / 请求 4");
        assertEquals(1.0, s.recall, 1e-9, "唯一的那条金标命中了");
    }

    @Test
    @DisplayName("T9 超出 k 的命中不计入：截断发生在打分之前")
    void hitsBeyondKAreNotCounted() {
        RetrievalMetrics.CaseScore s = RetrievalMetrics.score(ids(1L, 2L, 7L), golden(7L), 2);

        assertEquals(2, s.retrievedCount, "只取前 2 条参与打分");
        assertEquals(0, s.hitCount);
        assertEquals(0.0, s.recall, 1e-9, "第 3 位那条不算命中 —— 它不在用户实际拿到的上下文里");
    }

    // ==================== 排序质量 ====================

    @Test
    @DisplayName("T10 nDCG 对「命中排在第几」敏感；MRR 只看第一条")
    void ndcgIsSensitiveToOrdering() {
        // 同样的命中集合 {7,8}，一个排在第 1、2 位，一个排在第 3、4 位
        RetrievalMetrics.CaseScore front = RetrievalMetrics.score(ids(7L, 8L, 1L, 2L), golden(7L, 8L), 4);
        RetrievalMetrics.CaseScore back = RetrievalMetrics.score(ids(1L, 2L, 7L, 8L), golden(7L, 8L), 4);

        assertEquals(1.0, front.ndcg, 1e-9);
        assertTrue(back.ndcg < front.ndcg, "靠后的命中折损更多：back=" + back.ndcg);
        assertTrue(back.ndcg > 0, "但仍然是正数 —— 命中确实在结果里");
        assertTrue(back.reciprocalRank < front.reciprocalRank);
    }

    @Test
    @DisplayName("T11 MRR 只认第一个命中：第 1 位命中恒为 1，第 4 位命中为 1/4")
    void mrrUsesOnlyTheFirstHit() {
        // 金标 {8,9} 都在，但 8 排在第 2 位 —— MRR 取 1/2，不看后面那条也命中了
        assertEquals(0.5, RetrievalMetrics.score(ids(7L, 8L, 9L), golden(8L, 9L), 3).reciprocalRank, 1e-9);
        // 首条就命中 → 满分
        assertEquals(1.0, RetrievalMetrics.score(ids(8L, 7L, 9L), golden(8L, 9L), 3).reciprocalRank, 1e-9);
        assertEquals(0.25, RetrievalMetrics.score(ids(1L, 2L, 3L, 9L), golden(8L, 9L), 4).reciprocalRank, 1e-9);
    }

    // ==================== 聚合 ====================

    @Test
    @DisplayName("T12 聚合跳过无定义的题：『全对 + 一道无金标』的平均召回率必须是 1.0，不是 0.5")
    void aggregateSkipsUndefinedCases() {
        List<RetrievalMetrics.CaseScore> scores = Arrays.asList(
                RetrievalMetrics.score(ids(7L), golden(7L), 3),      // 有金标，召回 1.0
                RetrievalMetrics.score(ids(1L), golden(), 3));        // 无金标，未计分

        RetrievalMetrics.Aggregate agg = RetrievalMetrics.aggregate(scores);

        assertEquals(2, agg.caseTotal, "题目总数照实计");
        assertEquals(1, agg.recallScored, "只有一道题参与了召回率均值");
        assertEquals(1, agg.recallUnscored());
        assertEquals(1.0, agg.recall, 1e-9,
                "把无金标的题按 0 分算会得到 0.5 —— 那个数字反映的是标注完整度，不是检索质量");
        assertTrue(agg.hasUsableGolden());
    }

    @Test
    @DisplayName("T13 一道题都没有金标时，指标不可信，必须能被调用方识别出来")
    void aggregateReportsUnusableGolden() {
        List<RetrievalMetrics.CaseScore> scores = Arrays.asList(
                RetrievalMetrics.score(ids(1L), golden(), 3),
                RetrievalMetrics.score(ids(2L), golden(), 3));

        RetrievalMetrics.Aggregate agg = RetrievalMetrics.aggregate(scores);

        assertFalse(agg.hasUsableGolden(),
                "此时报出的 recall=0 毫无意义，页面必须显示「未标注金标」而不是一个 0% 的召回率");
        assertEquals(0, agg.recallScored);
        assertEquals(0.0, agg.hitRate, 1e-9);
    }

    @Test
    @DisplayName("T14 MRR 参与全部题目（未命中就是 0），命中率按题目数算")
    void mrrAndHitRateCoverAllCases() {
        List<RetrievalMetrics.CaseScore> scores = Arrays.asList(
                RetrievalMetrics.score(ids(7L, 1L), golden(7L), 2),   // rr=1
                RetrievalMetrics.score(ids(1L, 7L), golden(7L), 2),   // rr=0.5
                RetrievalMetrics.score(ids(1L, 2L), golden(7L), 2));  // rr=0（未命中）

        RetrievalMetrics.Aggregate agg = RetrievalMetrics.aggregate(scores);

        assertEquals(3, agg.caseTotal);
        assertEquals(2, agg.caseHit);
        assertEquals((1.0 + 0.5 + 0.0) / 3, agg.mrr, 1e-9);
        assertEquals(2.0 / 3, agg.hitRate, 1e-9);
        assertEquals(2.0 / 3, agg.recall, 1e-9, "三道题各命中 1 条 / 金标 1 条");
        assertEquals((0.5 + 0.5 + 0.0) / 3, agg.precisionAtK, 1e-9,
                "准确率的分母是 k=2 而不是命中数：每道题各命中 1 条，故每题 1/2");
    }

    @Test
    @DisplayName("T15 空集合、null 集合的聚合不抛异常")
    void aggregateHandlesEmpty() {
        RetrievalMetrics.Aggregate empty = RetrievalMetrics.aggregate(Collections.emptyList());
        assertEquals(0, empty.caseTotal);
        assertEquals(0.0, empty.recall, 1e-9);
        assertFalse(empty.hasUsableGolden());

        RetrievalMetrics.Aggregate nul = RetrievalMetrics.aggregate(null);
        assertEquals(0, nul.caseTotal);
    }
}
