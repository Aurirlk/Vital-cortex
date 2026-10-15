package cn.kmbeast.crm.rag;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 检索路径归类单测（{@link KbSearchService#classify}）。
 *
 * <p>这个判定看起来只是「打个标签」，但它是「退化可见」的<b>唯一出口</b>：
 * 向量服务挂掉、ngram 索引没建、正式库被静默降级成关键词检索 ——
 * 这些状态在 UI 上、在 {@code retrieval_log.path} 里全都只体现为这一个值。
 * 判错就等于把观测能力做没了，而且不会报任何错。所以单独锁住。</p>
 */
class KbSearchPathTest {

    @Test
    void noneWhenNothingSurvived() {
        assertEquals(KbSearchService.PATH_NONE, KbSearchService.classify(0, 0, 0, false));
        // 两路都召回了、但回读正本时全被丢弃（块已删/已停用）—— 对用户而言仍是「没查到」
        assertEquals(KbSearchService.PATH_NONE, KbSearchService.classify(5, 5, 0, false));
    }

    @Test
    void sparseOnlyWhenDenseRecalledNothing() {
        assertEquals(KbSearchService.PATH_SPARSE_ONLY, KbSearchService.classify(3, 0, 3, false));
    }

    @Test
    void sparseLikeIsASeparateDegradationSignal() {
        // 稠密路零召回 + 词法路走 LIKE 兜底 = 此刻系统只是一台关键词检索。
        // 必须与 sparse_only 区分：这个值长期偏高就是「向量服务一直是坏的」的铁证。
        assertEquals(KbSearchService.PATH_SPARSE_LIKE, KbSearchService.classify(3, 0, 3, true));
    }

    @Test
    void likedFallbackAloneIsNotADegradationWhenDenseWorked() {
        // ngram 索引暂不可用 -> LIKE 兜底召回；同时向量路也正常召回。
        // 这不是退化（语义检索还在工作），因此归类为 hybrid 而不是 sparse_like。
        assertEquals(KbSearchService.PATH_HYBRID, KbSearchService.classify(2, 2, 3, true));
    }

    @Test
    void denseOnlyForColloquialQuestions() {
        assertEquals(KbSearchService.PATH_DENSE_ONLY, KbSearchService.classify(0, 4, 4, false));
        // 稀疏路走了 LIKE 兜底但一条没召回，稠密路独立完成检索 —— 仍是 dense_only，
        // 退化标记只在「稠密路也没戏」时才有意义。
        assertEquals(KbSearchService.PATH_DENSE_ONLY, KbSearchService.classify(0, 4, 4, true));
    }

    @Test
    void hybridWhenBothPathsRecalled() {
        assertEquals(KbSearchService.PATH_HYBRID, KbSearchService.classify(3, 3, 5, false));
    }
}
