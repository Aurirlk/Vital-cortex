package cn.kmbeast.crm.rag;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * LIKE 兜底路的关键词抽取单测。
 *
 * <p>兜底路只在 ngram 全文索引不可用时走，但它的失败方式很隐蔽：
 * 关键词里混进一个 {@code %}，{@code LIKE CONCAT('%','%','%')} 就命中整表，
 * 结果「看起来有召回」，实际把精确度指标彻底打废。
 * 所以这里专门锁住元字符清洗与停用词过滤这两条 —— 它们是正确性前提，不是优化。</p>
 */
class KeywordExtractorTest {

    private static boolean containsAny(List<String> list, String... needles) {
        for (String s : list) {
            for (String n : needles) {
                if (s.contains(n)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Test
    void nullAndBlankYieldEmpty() {
        assertTrue(KeywordExtractor.extract(null).isEmpty());
        assertTrue(KeywordExtractor.extract("").isEmpty());
        assertTrue(KeywordExtractor.extract("   ").isEmpty());
        // 全是标点 -> strip 后为空 -> 同样返回空列表
        assertTrue(KeywordExtractor.extract("，。？！").isEmpty());
    }

    @Test
    void chineseBigramsExtractedAndStopwordsDropped() {
        List<String> kws = KeywordExtractor.extract("高血压患者应该注意什么");

        assertFalse(kws.isEmpty());
        // 术语被切出来
        assertTrue(kws.contains("血压"), "应切出「血压」，实际 " + kws);
        assertTrue(kws.contains("高血"), "应切出「高血」，实际 " + kws);
        // 功能词一个都不能留：留下「什么」会让 LIKE '%什么%' 把整库拉回来
        assertFalse(kws.contains("什么"), "停用词「什么」不应出现");
        assertFalse(kws.contains("应该"), "停用词「应该」不应出现");
        assertFalse(kws.contains("注意"), "停用词「注意」不应出现");
    }

    @Test
    void stopwordOnlyQuestionFallsBackToTruncatedWholeString() {
        // 「什么」整句都是停用词，切不出任何关键词。
        // 退回截断的整句：至少还能做一次子串匹配，比返回空列表强。
        assertEquals(List.of("什么"), KeywordExtractor.extract("什么"));
    }

    @Test
    void maxCapIsRespected() {
        String q = "高血压糖尿病冠心病脑梗死慢性阻塞性肺疾病";
        assertEquals(3, KeywordExtractor.extract(q, 3).size());
        assertEquals(5, KeywordExtractor.extract(q, 5).size());
        // 非正数取默认上限，而不是返回空
        assertEquals(KeywordExtractor.extract(q).size(), KeywordExtractor.extract(q, 0).size());
        assertEquals(KeywordExtractor.DEFAULT_MAX, KeywordExtractor.extract(q, 0).size());
    }

    @Test
    void likeMetaCharactersAreStripped() {
        // 短句保证含 % 的 bigram（「0%」）真的进入关键词列表，
        // 否则长句会被数量上限提前截断，测不到清洗这一步。
        List<String> kws = KeywordExtractor.extract("血氧90%");
        assertTrue(kws.contains("0"), "「0%」应被清洗成「0」，实际 " + kws);
        for (String kw : kws) {
            assertFalse(kw.contains("%"), "关键词不应含 LIKE 元字符 %，实际 " + kw);
            assertFalse(kw.contains("_"), "关键词不应含 LIKE 元字符 _，实际 " + kw);
        }
    }

    @Test
    void loneMetaCharacterYieldsNothing() {
        // 只输入一个「%」：strip 不删 %（它在正文里是正常字符），
        // 若这一步不清洗，就会变成 LIKE '%%%' —— 整表命中，兜底路等于没做。
        assertTrue(KeywordExtractor.extract("%").isEmpty());
        assertTrue(KeywordExtractor.extract("％").isEmpty());
        assertTrue(KeywordExtractor.extract("\\").isEmpty());
    }

    @Test
    void fullWidthPunctuationIsStrippedFromBigrams() {
        List<String> kws = KeywordExtractor.extract("高血压，怎么办？");

        assertFalse(kws.isEmpty());
        assertFalse(containsAny(kws, "，", "？", "。", "！", "、"),
                "全角标点不应被切进关键词，实际 " + kws);
        assertTrue(kws.contains("血压"));
    }

    @Test
    void singleCharacterInputIsKept() {
        assertEquals(List.of("高"), KeywordExtractor.extract("高"));
    }

    @Test
    void resultHasNoDuplicates() {
        List<String> kws = KeywordExtractor.extract("血压血压血压血压血压");
        assertEquals(kws.size(), kws.stream().distinct().count());
    }

    @Test
    void sanitizeDropsEmptyResultsAfterStripping() {
        assertTrue(KeywordExtractor.sanitize(null).isEmpty());
        assertTrue(KeywordExtractor.sanitize(new ArrayList<>()).isEmpty());
        // 清洗后为空的项必须被丢掉：否则 mapper 的 foreach 会拼出空条件
        assertTrue(KeywordExtractor.sanitize(Arrays.asList("_", "%", "\\", "  ")).isEmpty());
        assertEquals(List.of("ab"), KeywordExtractor.sanitize(List.of("a%b")));
        // 全角 ＿ 同样是元字符；null 与纯空白项都要被丢掉
        assertEquals(List.of("血压", "血糖"),
                KeywordExtractor.sanitize(Arrays.asList(null, "血＿压", "血糖", "  ")));
    }
}
