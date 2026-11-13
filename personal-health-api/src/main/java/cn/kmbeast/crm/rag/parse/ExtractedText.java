package cn.kmbeast.crm.rag.parse;

import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 一次文档解析的产物。
 *
 * <p>{@link #warnings} 是刻意存在的：解析「成功」和解析「成功且可信」
 * 是两回事。典型场景是扫描件 PDF —— pdfbox 不会报错，只是提取出 0 或
 * 极少字符。没有 warnings 通道时，这种文件会安静地入库成一篇
 * 「内容为空白」的文档，之后每逢相关问题检索不到，没人知道为什么。</p>
 */
@Getter
public class ExtractedText {

    /** 提取出的正文 */
    private final String text;

    /** 结构化单元数（PDF 页数 / Excel 工作表数 / Word 段落+表格数），0 表示该格式无从统计 */
    private final int sectionCount;

    /** 解析格式名（pdf / docx / xlsx / txt …），用于日志与统计 */
    private final String format;

    /** 非致命告警：文本过少、被截断、部分内容跳过等 */
    private final List<String> warnings;

    public ExtractedText(String text, int sectionCount, String format, List<String> warnings) {
        this.text = text == null ? "" : text;
        this.sectionCount = sectionCount;
        this.format = format;
        this.warnings = warnings == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(warnings));
    }

    public static ExtractedText of(String text, int sectionCount, String format) {
        return new ExtractedText(text, sectionCount, format, Collections.emptyList());
    }

    /** 正文长度（字符） */
    public int length() {
        return text.length();
    }
}
