package cn.kmbeast.crm.rag;

import cn.kmbeast.pojo.entity.RagEvalCase;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 评测集输入解析与校验单测。
 *
 * <p>这几列（{@code expected_refs} / {@code expected_keywords}）是<b>管理员手填</b>的。
 * 解析太严格会让一个全角逗号把「保存」弄失败；太宽松则会让一条什么都标不上的
 * 废用例混进评测集 —— 它会被跑一遍、占用时间，却对召回率毫无贡献，
 * 而且从列表上完全看不出来。所以这里既锁「容忍混用分隔符」，
 * 也锁「两者皆空必须被拒」。</p>
 */
class RagEvalParsingTest {

    // ------------------------------------------------------------------ parseRefs

    @Test
    void parseRefsHandlesNullAndBlank() {
        assertTrue(RagRetrievalEvalService.parseRefs(null).isEmpty());
        assertTrue(RagRetrievalEvalService.parseRefs("").isEmpty());
        assertTrue(RagRetrievalEvalService.parseRefs("   ").isEmpty());
    }

    @Test
    void parseRefsToleratesMixedSeparators() {
        assertEquals(List.of(1L, 2L, 3L), RagRetrievalEvalService.parseRefs("1,2,3"));
        // 全角逗号 / 中英文分号 / 空格混用：手填列必然会遇到
        assertEquals(List.of(1L, 2L, 3L, 4L), RagRetrievalEvalService.parseRefs("1，2；3 4"));
        assertEquals(List.of(7L, 8L), RagRetrievalEvalService.parseRefs("  7 ; 8 ; "));
    }

    @Test
    void parseRefsDeduplicatesPreservingOrder() {
        assertEquals(List.of(1L, 2L), RagRetrievalEvalService.parseRefs("1,1,2,1"));
    }

    @Test
    void parseRefsIgnoresNonNumericFragments() {
        // 金标串里混进说明文字是常见手滑，为它整条用例失败不如忽略后继续
        assertEquals(List.of(1L, 2L), RagRetrievalEvalService.parseRefs("1,abc,2"));
        assertTrue(RagRetrievalEvalService.parseRefs("abc,def").isEmpty());
    }

    // ------------------------------------------------------------------ parseKbIds

    @Test
    void parseKbIdsSameTolerance() {
        assertTrue(RagRetrievalEvalService.parseKbIds(null).isEmpty());
        assertEquals(List.of(1, 3), RagRetrievalEvalService.parseKbIds("1,3"));
        assertEquals(List.of(1, 3), RagRetrievalEvalService.parseKbIds("1，3"));
        assertEquals(List.of(1, 3), RagRetrievalEvalService.parseKbIds("1,x,3"));
        // 去重：同一个库被写两遍不该变成两次集合查询
        assertEquals(List.of(1), RagRetrievalEvalService.parseKbIds("1,1"));
    }

    // ------------------------------------------------------------------ splitKeywords

    @Test
    void splitKeywordsHandlesNullAndBlank() {
        assertTrue(RagRetrievalEvalService.splitKeywords(null).isEmpty());
        assertTrue(RagRetrievalEvalService.splitKeywords("  ,  ; ").isEmpty());
    }

    @Test
    void splitKeywordsKeepsTermsVerbatim() {
        // 关键词是中文短语，不做 bigram 切分也不去停用词 —— 那是金标，不是查询
        assertEquals(List.of("用法用量", "禁忌", "孕妇"),
                RagRetrievalEvalService.splitKeywords("用法用量,禁忌 孕妇"));
    }

    // ------------------------------------------------------------------ validateCase

    private static RagEvalCase build(String question, String refs, String keywords, String level) {
        return RagEvalCase.builder()
                .question(question)
                .expectedRefs(refs)
                .expectedKeywords(keywords)
                .matchLevel(level)
                .build();
    }

    @Test
    void validateCaseRejectsNullAndBlankQuestion() {
        assertEquals("参数不能为空", RagRetrievalEvalService.validateCase(null));
        assertNotNull(RagRetrievalEvalService.validateCase(build("  ", "1", null, "chunk")));
    }

    @Test
    void validateCaseNormalizesMatchLevel() {
        // 大小写/前后空格都要能接受，且归一化后写回实体（存库前就统一）
        RagEvalCase upper = build("问题", "1", null, "DOC");
        assertNull(RagRetrievalEvalService.validateCase(upper));
        assertEquals("doc", upper.getMatchLevel());

        RagEvalCase blank = build("问题", "1", null, null);
        assertNull(RagRetrievalEvalService.validateCase(blank));
        assertEquals("chunk", blank.getMatchLevel());
    }

    @Test
    void validateCaseRejectsUnknownMatchLevel() {
        String msg = RagRetrievalEvalService.validateCase(build("问题", "1", null, "paragraph"));
        assertNotNull(msg);
        assertTrue(msg.contains("chunk") && msg.contains("doc"), "错误信息应说清可选值，实际 " + msg);
    }

    @Test
    void validateCaseRequiresAtLeastOneScoringBasis() {
        // 两者皆空的用例「不可评测」：跑一遍占时间，却对结果毫无贡献，且列表上看不出来
        String msg = RagRetrievalEvalService.validateCase(build("问题", null, null, "chunk"));
        assertNotNull(msg);
        assertTrue(msg.contains("expected_refs") && msg.contains("expected_keywords"),
                "错误信息应指出两个字段名，实际 " + msg);

        // 只有分隔符、解析后为空，等同于没填
        assertNotNull(RagRetrievalEvalService.validateCase(build("问题", " , ; ", " , ", "chunk")));
        // 金标串里全是非数字，也等同于没填
        assertNotNull(RagRetrievalEvalService.validateCase(build("问题", "abc", null, "chunk")));
    }

    @Test
    void validateCaseAcceptsEitherBasisAlone() {
        // 只有金标 -> 可算召回率
        assertNull(RagRetrievalEvalService.validateCase(build("问题", "10,11", null, "chunk")));
        // 只有关键词 -> 只能算精确率，但仍是有效用例
        assertNull(RagRetrievalEvalService.validateCase(build("问题", null, "用法用量", "chunk")));
    }

    @Test
    void validateCaseNormalizesRefsToCanonicalForm() {
        // 归一化成「英文逗号 + 无空格」：否则同一个金标集只因空格差异
        // 看起来就是两条不同的用例，去重与对比都会失效
        RagEvalCase one = build("问题", " 10 ，11 ; 10 ", null, "chunk");
        assertNull(RagRetrievalEvalService.validateCase(one));
        assertEquals("10,11", one.getExpectedRefs());
    }
}
