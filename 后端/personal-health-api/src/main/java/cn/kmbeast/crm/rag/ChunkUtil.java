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
     * 将长文本切分为语义块。
     *
     * @param text 原始文本
     * @return 分块列表（空文本返回空列表）
     */
    public static List<String> split(String text) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.trim().isEmpty()) {
            return chunks;
        }

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
            return hardSplit(text);
        }

        // 贪心累积句子到 CHUNK_SIZE，超出时以重叠滑窗开新块
        StringBuilder buf = new StringBuilder();
        for (String s : sentences) {
            if (buf.length() + s.length() > CHUNK_SIZE && buf.length() > 0) {
                chunks.add(buf.toString().trim());
                // 保留末尾 overlap 个字符作为下一块的起始
                String tail = buf.substring(Math.max(0, buf.length() - CHUNK_OVERLAP));
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
    private static List<String> hardSplit(String text) {
        List<String> chunks = new ArrayList<>();
        for (int i = 0; i < text.length(); i += CHUNK_SIZE - CHUNK_OVERLAP) {
            int end = Math.min(i + CHUNK_SIZE, text.length());
            chunks.add(text.substring(i, end));
            if (end == text.length()) break;
        }
        return chunks;
    }
}
