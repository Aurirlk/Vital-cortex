package cn.kmbeast.crm.rag.parse;

import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Word 文档解析器（.docx，Apache POI XWPF）。
 *
 * <h3>为什么用 {@code getBodyElements()} 而不是 {@code getParagraphs()}</h3>
 * <p>{@code XWPFDocument.getParagraphs()} <b>只返回段落，表格整体丢失</b>，
 * 且不报任何错。而医学/健康类 docx 里，体征表、用药表、检验结果表
 * 恰恰是信息密度最高的部分 —— 用 getParagraphs() 会安静地丢掉它们，
 * 入库后表现为「这份文档好像少了一半内容」。</p>
 *
 * <p>{@code getBodyElements()} 按<b>文档实际顺序</b>返回段落与表格的混合序列，
 * 既保住了表格，也保住了「表格在正文的哪个位置」这个上下文。</p>
 *
 * <h3>不支持的 .doc</h3>
 * <p>老式二进制 .doc 需要 {@code poi-scratchpad}（HWPF），本项目未引入。
 * 与其引入一个只为读 .doc 的依赖，不如给出明确的转换指引 ——
 * 另存为 .docx 是一次点击的事。</p>
 */
@Component
public class WordDocumentParser implements DocumentParser {

    private static final Logger log = LoggerFactory.getLogger(WordDocumentParser.class);

    /** 单文档提取上限，防止一份超大文档把切块与嵌入彻底拖死 */
    private static final int MAX_CHARS = 2_000_000;

    @Override
    public Set<String> extensions() {
        return Set.of("docx");
    }

    @Override
    public ExtractedText parse(byte[] content, String fileName) {
        if (content == null || content.length == 0) {
            throw new DocumentParseException("文件内容为空：" + fileName);
        }

        StringBuilder sb = new StringBuilder();
        int sections = 0;
        boolean truncated = false;

        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(content))) {
            for (IBodyElement element : document.getBodyElements()) {
                if (sb.length() >= MAX_CHARS) {
                    truncated = true;
                    break;
                }
                if (element instanceof XWPFParagraph paragraph) {
                    String line = paragraph.getText();
                    if (line != null && !line.isBlank()) {
                        sb.append(line.trim()).append('\n');
                    }
                    sections++;
                } else if (element instanceof XWPFTable table) {
                    appendTable(sb, table);
                    sections++;
                }
            }

            // 页眉页脚不单独提取：它们通常是公司抬头/页码这类噪音，
            // 每个块里混进一句「XX医院 第 3 页」只会稀释检索信号。
        } catch (DocumentParseException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[RAG-parse] docx 解析失败: file={}, err={}", fileName, e.toString());
            throw new DocumentParseException(
                    "Word 文档解析失败（" + fileName + "）：" + e.getClass().getSimpleName()
                            + "。文件可能已损坏，或实际不是 .docx 格式"
                            + "（老式 .doc 请先在 Word 中另存为 .docx）。", e);
        }

        List<String> warnings = new ArrayList<>();
        if (truncated) {
            warnings.add("文档超过 " + MAX_CHARS + " 字符，已截断处理，仅索引了前半部分内容。"
                    + "建议拆分为多份文档上传。");
        }

        String text = sb.toString().replaceAll("\n{3,}", "\n\n").trim();
        return new ExtractedText(text, sections, "docx", warnings);
    }

    /**
     * 把表格按「行 = 一行，单元格 = 制表符分隔」拍平成文本。
     *
     * <p>拍平而非结构化保留，是因为下游检索单位是自然语言块：
     * 制表符分隔的表格行既能被人读，也能被 ngram 全文索引切开，
     * 而 JSON 化会把「头痛\t38.5℃」变成一堆引号冒号，反而切坏词。</p>
     */
    private static void appendTable(StringBuilder sb, XWPFTable table) {
        for (XWPFTableRow row : table.getRows()) {
            List<String> cells = new ArrayList<>();
            for (XWPFTableCell cell : row.getTableCells()) {
                String value = cell.getText();
                cells.add(value == null ? "" : value.replace("\n", " ").trim());
            }
            // 整行皆空的行没有信息量，跳过（表格常用来做留白排版）
            if (cells.stream().allMatch(String::isEmpty)) {
                continue;
            }
            sb.append(String.join("\t", cells)).append('\n');
        }
        sb.append('\n');
    }
}
