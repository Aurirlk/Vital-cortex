package cn.kmbeast.crm.rag;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 从自然语言问题里抽取关键词，供 {@code LIKE} 兜底路使用。
 *
 * <h3>为什么不引第三方分词器</h3>
 * <p>兜底路只在 ngram 全文索引不可用时走，是个<b>降级路径</b>：
 * 为它引入 jieba / hanlp 这类带词典和模型的分词依赖（几十 MB、启动加载、
 * 词典还要维护），性价比明显不成立。中文二元切分（bigram）对
 * 「疾病名」「药品名」这类 2~4 字术语的召回已经够用，且零依赖、零启动成本。</p>
 *
 * <h3>为什么要去停用词</h3>
 * <p>「高血压患者应该注意什么」切出的 bigram 里有「什么」「应该」这类功能词，
 * 它们在语料里几乎处处命中 —— 作为 {@code OR} 条件之一会让
 * {@code LIKE '%什么%'} 把整个知识库拉回来，兜底路就彻底失效了。
 * 所以停用词不是优化，是<b>正确性前提</b>。</p>
 */
public final class KeywordExtractor {

    /** 中文提问里的高频功能词（二元切分后出现的形态）。 */
    private static final Set<String> STOP_BIGRAMS = Set.of(
            "什么", "怎么", "如何", "可以", "是否", "哪些", "多少", "为什",
            "应该", "需要", "注意", "有没", "没有", "这个", "那个", "一下",
            "请问", "我想", "我要", "我们", "你们", "他们", "它呢", "的话",
            "吗呢", "呢吗", "以及", "或者", "还是", "但是", "因为",
            "所以", "如果", "就是", "这样", "那样", "一般", "普通", "相关");

    /** 默认关键词上限。太多会把 LIKE 条件堆成一大段，命中率反而上升、精确度下降。 */
    public static final int DEFAULT_MAX = 8;

    private KeywordExtractor() {
    }

    /** 抽取关键词（默认上限 {@link #DEFAULT_MAX}）。 */
    public static List<String> extract(String question) {
        return extract(question, DEFAULT_MAX);
    }

    /**
     * 抽取关键词。
     *
     * @param question 用户问题
     * @param max      关键词上限（&le;0 时取默认值）
     * @return 去重后的关键词（可能为空，调用方必须处理空的情况）
     */
    public static List<String> extract(String question, int max) {
        List<String> result = new ArrayList<>();
        if (question == null) {
            return result;
        }
        String cleaned = strip(question);
        if (cleaned.isEmpty()) {
            return result;
        }
        int cap = max <= 0 ? DEFAULT_MAX : max;

        if (cleaned.length() < 2) {
            // 同样要过 sanitize：strip() 不删 % 与 ＿（它们在正文里是正常字符），
            // 只输入一个「%」时会走到这里，不清洗就变成 LIKE '%%%' 命中整表。
            result.add(cleaned);
            return sanitize(result);
        }

        Set<String> bigrams = new LinkedHashSet<>();
        for (int i = 0; i + 2 <= cleaned.length(); i++) {
            String bigram = cleaned.substring(i, i + 2);
            if (STOP_BIGRAMS.contains(bigram)) {
                continue;
            }
            bigrams.add(bigram);
            if (bigrams.size() >= cap) {
                break;
            }
        }

        if (bigrams.isEmpty()) {
            // 整句都是功能词，或问题短到切不出非停用词。
            // 退回「截断的整句」—— 至少还能做一次子串匹配，比返回空列表强。
            result.add(cleaned.length() > 8 ? cleaned.substring(0, 8) : cleaned);
            return result;
        }
        result.addAll(bigrams);
        return sanitize(result);
    }

    /**
     * 去掉 LIKE 元字符。
     *
     * <p>{@code % _ \} 在 {@code LIKE} 模式里有特殊含义。用户输入「50%」这类内容时，
     * 关键词里的 {@code %} 会变成通配符 —— 轻则召回过宽，重则
     * {@code LIKE '%%%'} 把整表拖出来。这里把它们直接删除而不是转义：
     * 转义需要同时指定 {@code ESCAPE 子句}，而兜底路的关键词本就只是近似匹配，
     * 少一个符号字符不影响召回。</p>
     */
    public static List<String> sanitize(List<String> raw) {
        List<String> out = new ArrayList<>();
        if (raw == null) {
            return out;
        }
        for (String kw : raw) {
            if (kw == null) {
                continue;
            }
            String s = kw.replace("%", "").replace("_", "").replace("\\", "")
                    .replace("％", "").replace("＿", "").trim();
            if (!s.isEmpty()) {
                out.add(s);
            }
        }
        return out;
    }

    /**
     * 去掉标点与空白。
     *
     * <p>显式列出全角标点：Java 的 {@code \p{Punct}} 默认只覆盖 ASCII，
     * 中文问句里的「，。？！」一个都匹配不到，不列出来就会被当成正文切进 bigram。</p>
     */
    private static String strip(String text) {
        StringBuilder sb = new StringBuilder(text.length());
        String punctuation = "，。？！；：、（）【】《》“”‘’…—～·　"
                + " .,?!;:()[]<>\"'`~-_*#@$&+=|{}^/\\";
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isWhitespace(c)) {
                continue;
            }
            if (punctuation.indexOf(c) >= 0) {
                continue;
            }
            sb.append(c);
        }
        return sb.toString();
    }
}
