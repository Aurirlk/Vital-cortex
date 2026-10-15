package cn.kmbeast.crm.rag.fusion;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 加权 RRF 融合单测。
 *
 * <p>融合是整条检索链路里最容易被「调参调坏」的一环：权重是配置项，
 * 改配置不经过编译检查。因此这里把每一条语义都钉死 ——
 * 尤其是「同路重复不吃名次」和「双权重为 0 退化为均权」这两条，
 * 它们错了不会报错，只会让排序悄悄变得没有意义。</p>
 *
 * <p>断言用精确值（{@code 1/(k+rank)}）而不是「大小关系」：
 * 排名靠相对大小就能过，但分数本身是写进 retrieval_log、喂给评测的东西，
 * 它错了同样要能被测出来。</p>
 */
class RRFusionTest {

    private static final double EPS = 1e-9;

    /** 单路第 1 名的理论分。 */
    private double oneRank(int k, int rank) {
        return 1.0 / (k + rank);
    }

    private static List<RRFusion.FusedItem> byKey(List<RRFusion.FusedItem> items, String key) {
        return items.stream().filter(i -> key.equals(i.getKey())).toList();
    }

    @Test
    void bothPathsRankedTogetherOutrankSinglePath() {
        List<RRFusion.FusedItem> items = RRFusion.fuse(
                Arrays.asList("a", "b"), Arrays.asList("b", "c"), 1.0, 1.0, 60, 10);

        assertEquals(3, items.size());
        // 两路都召回 -> 双份贡献，稳居第一
        assertEquals("b", items.get(0).getKey());
        assertEquals(oneRank(60, 2) + oneRank(60, 1), items.get(0).getScore(), EPS);
        assertTrue(items.get(0).fromBoth());
        // 单路第 1 名（a）压过单路第 2 名（c）
        assertEquals("a", items.get(1).getKey());
        assertEquals("c", items.get(2).getKey());
        assertTrue(items.get(1).getScore() > items.get(2).getScore());
    }

    @Test
    void weightsShiftTheRanking() {
        // 稀疏 [a] 稠密 [b]：默认均权时同分（a 先出现所以排前）；
        // 把稠密权重抬到 10 倍，b 必须反超——这个开关就是「调参」的全部意义。
        List<RRFusion.FusedItem> even = RRFusion.fuse(
                List.of("a"), List.of("b"), 1.0, 1.0, 60, 10);
        assertEquals("a", even.get(0).getKey());
        assertEquals(even.get(0).getScore(), even.get(1).getScore(), EPS);

        List<RRFusion.FusedItem> denseHeavy = RRFusion.fuse(
                List.of("a"), List.of("b"), 1.0, 10.0, 60, 10);
        assertEquals("b", denseHeavy.get(0).getKey());
        assertEquals(10.0 / 61, denseHeavy.get(0).getScore(), EPS);
        assertEquals(1.0 / 61, denseHeavy.get(1).getScore(), EPS);
    }

    @Test
    void duplicateWithinOnePathKeepsFirstRankAndDoesNotConsumeARank() {
        // 稀疏路里 a 出现两次：第二次必须既不重复计分、也不占掉第 2 名。
        // 旧实现会把 RRF 分累加却只保留一条结果，等于给重复项白送排名。
        List<RRFusion.FusedItem> items = RRFusion.fuse(
                Arrays.asList("a", "a", "b"), Collections.emptyList(), 1.0, 1.0, 60, 10);

        assertEquals(2, items.size());
        RRFusion.FusedItem a = byKey(items, "a").get(0);
        RRFusion.FusedItem b = byKey(items, "b").get(0);
        assertEquals(1, a.getSparseRank());
        assertEquals(oneRank(60, 1), a.getScore(), EPS);
        // b 是第 2 名而不是第 3 名——重复项没有消耗名次
        assertEquals(2, b.getSparseRank());
        assertEquals(oneRank(60, 2), b.getScore(), EPS);
    }

    @Test
    void bothWeightsZeroFallsBackToOneToOneInsteadOfAllZero() {
        // 双 0 若照直算，所有分数恒为 0，排序退化成「谁先插入」——
        // 看上去正常，其实完全无意义。设计上退化为经典 1:1。
        List<RRFusion.FusedItem> items = RRFusion.fuse(
                List.of("a"), List.of("b"), 0.0, 0.0, 60, 10);

        assertEquals(oneRank(60, 1), items.get(0).getScore(), EPS);
        assertEquals(oneRank(60, 1), items.get(1).getScore(), EPS);
    }

    @Test
    void negativeWeightClampedToZero() {
        // 负权重不是「反向排序」（RRF 没有这个概念），而是「关掉这一路」。
        List<RRFusion.FusedItem> items = RRFusion.fuse(
                List.of("a"), List.of("b"), -5.0, 1.0, 60, 10);

        RRFusion.FusedItem a = byKey(items, "a").get(0);
        RRFusion.FusedItem b = byKey(items, "b").get(0);
        assertEquals(1.0 / 61, b.getScore(), EPS);
        // a 仍留在结果里，但贡献为 0 —— 与「该路未召回」区分开，
        // 避免上层按 source 判断来源时丢信息。
        assertEquals(0.0, a.getScore(), EPS);
        assertEquals("b", items.get(0).getKey());
    }

    @Test
    void nonPositiveKUsesDefault() {
        List<RRFusion.FusedItem> zero = RRFusion.fuse(List.of("a"), List.of("b"), 1.0, 1.0, 0, 10);
        List<RRFusion.FusedItem> neg = RRFusion.fuse(List.of("a"), List.of("b"), 1.0, 1.0, -3, 10);
        List<RRFusion.FusedItem> std = RRFusion.fuse(List.of("a"), List.of("b"), 1.0, 1.0,
                RRFusion.DEFAULT_K, 10);

        assertEquals(std.get(0).getScore(), zero.get(0).getScore(), EPS);
        assertEquals(std.get(0).getScore(), neg.get(0).getScore(), EPS);
        assertEquals(1.0 / (RRFusion.DEFAULT_K + 1), std.get(0).getScore(), EPS);
    }

    @Test
    void topKTruncatesAndNonPositiveTopKKeepsEverything() {
        List<String> sparse = Arrays.asList("a", "b", "c", "d");
        assertEquals(2, RRFusion.fuse(sparse, null, 1.0, 1.0, 60, 2).size());
        assertEquals(4, RRFusion.fuse(sparse, null, 1.0, 1.0, 60, 0).size());
        assertEquals(4, RRFusion.fuse(sparse, null, 1.0, 1.0, 60, -1).size());
        // 截断取的是前 K 名，不是任意 K 条
        assertEquals("a", RRFusion.fuse(sparse, null, 1.0, 1.0, 60, 2).get(0).getKey());
    }

    @Test
    void nullAndEmptyInputsYieldEmptyNotNull() {
        assertNotNull(RRFusion.fuse(null, null, 1.0, 1.0, 60, 5));
        assertTrue(RRFusion.fuse(null, null, 1.0, 1.0, 60, 5).isEmpty());
        assertTrue(RRFusion.fuse(Collections.emptyList(), Collections.emptyList(),
                1.0, 1.0, 60, 5).isEmpty());
    }

    @Test
    void nullAndEmptyKeysAreSkipped() {
        // 向量库元数据里 chunk_id 可能是 null 或空串，别让它们变成一条「无主结果」
        // 混进融合结果（既污染 both_hits 统计，也会占掉一个名次）。
        List<RRFusion.FusedItem> items = RRFusion.fuse(
                Arrays.asList(null, "", "a"), null, 1.0, 1.0, 60, 10);

        assertEquals(1, items.size());
        assertEquals("a", items.get(0).getKey());
        // 空项若被当成有效项，a 会掉到第 3 名（分数变小）
        assertEquals(oneRank(60, 1), items.get(0).getScore(), EPS);
    }

    @Test
    void sourceFlagsMatchWhereEachItemCameFrom() {
        List<RRFusion.FusedItem> items = RRFusion.fuse(
                Arrays.asList("both", "sparse"), Arrays.asList("both", "dense"),
                1.0, 1.0, 60, 10);

        assertEquals("both", byKey(items, "both").get(0).source());
        assertEquals("sparse", byKey(items, "sparse").get(0).source());
        assertEquals("dense", byKey(items, "dense").get(0).source());
        // 取值与 HybridRetriever.RetrievedDoc.source 保持同一套语义
        assertTrue(byKey(items, "sparse").get(0).fromSparse());
        assertFalse(byKey(items, "sparse").get(0).fromDense());
    }

    @Test
    void countBothCountsOnlyItemsRecalledByBothPaths() {
        List<RRFusion.FusedItem> items = RRFusion.fuse(
                Arrays.asList("a", "b"), Arrays.asList("b", "c"), 1.0, 1.0, 60, 10);

        assertEquals(1, RRFusion.countBoth(items));
        assertEquals(0, RRFusion.countBoth(null));
        assertEquals(0, RRFusion.countBoth(Collections.emptyList()));
    }

    @Test
    void mergeByScoreDescOrdersByScoreAndKeepsInsertionOrderOnTies() {
        // 跨知识库合并：A 库第 1 与 B 库第 1 必须先归并成一条名次，
        // 否则两个「第 1 名」并列，RRF 的排名语义直接失效。
        Map<String, Double> scored = new LinkedHashMap<>();
        scored.put("low", 0.2);
        scored.put("high", 0.9);
        scored.put("midA", 0.5);
        scored.put("midB", 0.5);

        List<String> keys = RRFusion.mergeByScoreDesc(scored);
        assertEquals(List.of("high", "midA", "midB", "low"), keys);
    }

    @Test
    void mergeByScoreDescSkipsNullsAndHandlesEmpty() {
        Map<String, Double> scored = new LinkedHashMap<>();
        scored.put("a", null);
        scored.put(null, 0.9);
        scored.put("b", 0.3);

        assertEquals(List.of("b"), RRFusion.mergeByScoreDesc(scored));
        assertTrue(RRFusion.mergeByScoreDesc(null).isEmpty());
        assertTrue(RRFusion.mergeByScoreDesc(new LinkedHashMap<>()).isEmpty());
    }
}
