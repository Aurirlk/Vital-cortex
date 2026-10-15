package cn.kmbeast.crm.config;

import cn.kmbeast.crm.rag.KbDocumentIngestService;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 检索配置的生效值单测。
 *
 * <p>这些值来自 {@code application.yml}，改配置不经过编译检查：写错一个负号
 * 或一个 0，轻则候选集为空、检索永远返回「没查到」，重则灌库请求次数暴涨。
 * 钳位逻辑因此必须独立于 Spring 被锁住 ——
 * 也正因为如此，{@link RagProperties} 的每个字段都带同值初始化器：
 * 纯单测里 {@code @Value} 不会注入，靠的就是这些初始化器。</p>
 */
class RagPropertiesTest {

    @Test
    void defaultsMatchThePreRefactorBehaviour() {
        // 默认值必须等于改造前的硬编码值，否则「只加配置不改行为」这个前提就破了
        RagProperties p = new RagProperties();
        assertEquals(1.0, p.effectiveSparseWeight());
        assertEquals(1.0, p.effectiveDenseWeight());
        assertEquals(60, p.getRrfK());
        assertEquals(2, p.effectiveCandidateFactor());
        assertTrue(p.isDenseEnabled());
        assertTrue(p.isLikeFallbackEnabled());
    }

    @Test
    void negativeWeightsClampToZeroInsteadOfThrowing() {
        // 负权重在 RRF 里没有意义（会把命中文档往下压，等于奖励未命中）。
        // 钳为 0 而不是抛异常：为一个手滑的负号让服务起不来不划算。
        RagProperties p = new RagProperties();
        p.setSparseWeight(-1.5);
        p.setDenseWeight(-0.001);
        assertEquals(0.0, p.effectiveSparseWeight());
        assertEquals(0.0, p.effectiveDenseWeight());
    }

    @Test
    void nonPositiveFactorsClampToOne() {
        RagProperties p = new RagProperties();
        p.setCandidateFactor(0);
        assertEquals(1, p.effectiveCandidateFactor());
        p.setCandidateFactor(-3);
        assertEquals(1, p.effectiveCandidateFactor());

        p.setIngestBatchSize(0);
        assertEquals(1, p.effectiveIngestBatchSize());
        p.setIngestBatchSize(-8);
        assertEquals(1, p.effectiveIngestBatchSize());
    }

    @Test
    void positiveValuesPassThroughUnchanged() {
        RagProperties p = new RagProperties();
        p.setSparseWeight(0.5);
        p.setDenseWeight(2.5);
        p.setCandidateFactor(5);
        p.setIngestBatchSize(64);
        assertEquals(0.5, p.effectiveSparseWeight());
        assertEquals(2.5, p.effectiveDenseWeight());
        assertEquals(5, p.effectiveCandidateFactor());
        assertEquals(64, p.effectiveIngestBatchSize());
    }

    @Test
    void sha256MatchesTheStandardVectors() {
        // 用公开已知答案而不是「跑两遍结果一致」—— 后者连「换了算法」都测不出来。
        // 指纹是重复检测的唯一依据，认错了就等于把两份不同文档当成同一份。
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                KbDocumentIngestService.sha256("abc".getBytes(StandardCharsets.UTF_8)));
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                KbDocumentIngestService.sha256(new byte[0]));
        assertNull(KbDocumentIngestService.sha256(null));
    }

    @Test
    void sha256IsSensitiveToEveryByte() {
        String a = KbDocumentIngestService.sha256("用法用量：一次一片".getBytes(StandardCharsets.UTF_8));
        String b = KbDocumentIngestService.sha256("用法用量：一次两片".getBytes(StandardCharsets.UTF_8));
        assertNotNull(a);
        assertEquals(64, a.length());
        assertNotEquals(a, b);
    }
}
