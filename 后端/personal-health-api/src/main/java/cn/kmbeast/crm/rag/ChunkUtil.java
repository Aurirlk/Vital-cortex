package cn.kmbeast.crm.rag;

import java.util.ArrayList;
import java.util.List;

/**
 * 文本分块工具（RAG-04/RAG-11）。
 *
 * <p>替代原 `substring(0,300)` 硬截断与"整篇当一块"的做法：
 * 按中文句子边界切块，块大小约 200 字、重叠 50 字，保证语义完整
 * 且相邻块之间不丢上下文。用于 ingestion 灌库与检索上下文组装。
 */
public final class ChunkUtil {

    /** 目标块长度（字符） */
    public static final int CHUNK_SIZE = 200;
    /** 块间重叠（字符） */
    public static final int CHUNK_OVERLAP = 50;

    private ChunkUtil() {
    }

    /**
     * 将长文本切分为语义块（使用默认 200/50）。
     *
     * @param text 原始文本
     * @return 分块列表（空文本返回空列表）
     */
    public static List<String> split(String text) {
        return split(text, CHUNK_SIZE, CHUNK_OVERLAP);
    }

    /**
     * 按调用方给定的块参数切分（阶段 2：知识库级可配）。
     *
     * <p><b>为什么块参数必须可配</b>：不同语料的最优值差别很大 ——
     * 法规条文/药品说明书要小块（一段一条，混在一起会稀释语义），
     * 科普长文要大块（上下文断了结论就不成立）。全局写死 200 必然
     * 有一半场景是错的，而错的表现只是「召回质量一般」，没人能归因。</p>
     *
     * <p>参数做钳制而不是直接拒绝：管理端表单可能填 0 或负数，
     * 直接抛异常会让「保存知识库」这个动作失败在一个与用户意图无关的
     * 细节上。钳制后行为可预期，且下面的注释说明了边界依据。</p>
     *
     * @param text      原始文本
     * @param chunkSize 目标块长度（字符），钳制到 [20, 4000]
     * @param overlap   块间重叠（字符），钳制到 [0, chunkSize/2]
     */
    public static List<String> split(String text, int chunkSize, int overlap) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.trim().isEmpty()) {
            return chunks;
        }

        // 下限 20：低于此连一个中文句子都放不下，会被切成一堆无意义的碎片；
        // 上限 4000：超过它块本身就成了一篇长文，检索定位精度归零
        int size = Math.max(20, Math.min(chunkSize, 4000));
        // 重叠不得超过半块：否则相邻块大量重复，去重后信息量并没有增加，
        // 只是让向量库和检索结果里塞满了近似副本
        int step = Math.max(0, Math.min(overlap, size / 2));

        return splitInternal(text, size, step);
    }

    private static List<String> splitInternal(String text, int chunkSize, int overlap) {
        List<String> chunks = new ArrayList<>();

        // 先按段落拆（\n\n 或多行换行），段落内再按句子边界累积
        String[] paragraphs = text.split("\\n{2,}|\\r\\n\\r\\n");
        StringBuilder current = new StringBuilder();
        List<String> sentences = new ArrayList<>();

        for (String para : paragraphs) {
            // 句子级切分（保留分隔符）
            List<String> paraSentences = splitSentences(para.trim());
            if (paraSentences.isEmpty()) continue;
            sentences.addAll(paraSentences);
        }

        if (sentences.isEmpty()) {
            // 无句读的连续文本（如纯数字/符号），按固定长度硬切
            return hardSplit(text, chunkSize, overlap);
        }

        // 贪心累积句子到 chunkSize，超出时以重叠滑窗开新块
        StringBuilder buf = new StringBuilder();
        for (String s : sentences) {
            if (s.length() > chunkSize) {
                // 单句本身就超过一块 —— PDF 抽出来的表格、指标对照表、
                // 无句读的长段几乎全是这个样子。不切的话整段会变成一个
                // 超大块，超出嵌入模型的上下文后被静默截断，后面的内容等于没入库；
                // 而表现只是「召回质量一般」，没人能归因到分块这一步。
                if (buf.length() > 0) {
                    chunks.add(buf.toString().trim());
                    buf.setLength(0);
                }
                chunks.addAll(hardSplit(s, chunkSize, overlap));
                continue;
            }
            if (buf.length() + s.length() > chunkSize && buf.length() > 0) {
                chunks.add(buf.toString().trim());
                // 保留末尾 overlap 个字符作为下一块的起始
                String tail = buf.substring(Math.max(0, buf.length() - overlap));
                buf = new StringBuilder(tail);
            }
            buf.append(s);
        }
        if (buf.length() > 0) {
            chunks.add(buf.toString().trim());
        }
        return chunks;
    }

    /** 按中文句子分隔符切句（保留分隔符） */
    private static List<String> splitSentences(String text) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (char c : text.toCharArray()) {
            cur.append(c);
            if (c == '。' || c == '！' || c == '？' || c == '；' || c == '\n' || c == ';' || c == '.') {
                out.add(cur.toString());
                cur.setLength(0);
            }
        }
        if (cur.length() > 0) {
            out.add(cur.toString());
        }
        return out;
    }

    /** 无句读文本按固定窗口硬切 */
    private static List<String> hardSplit(String text, int chunkSize, int overlap) {
        List<String> chunks = new ArrayList<>();
        // step 至少 1：overlap == chunkSize 时步长为 0 会死循环
        int step = Math.max(1, chunkSize - overlap);
        for (int i = 0; i < text.length(); i += step) {
            int end = Math.min(i + chunkSize, text.length());
            chunks.add(text.substring(i, end));
            if (end == text.length()) break;
        }
        return chunks;
    }
}
