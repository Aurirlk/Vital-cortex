package cn.kmbeast.crm.rag.parse;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * PDF 解析器（Apache PDFBox 2.0.29，项目原有依赖）。
 *
 * <h3>两个必须处理的现实</h3>
 * <ol>
 *   <li><b>扫描件 PDF 提取不出文字</b>。PDFBox 只抽文本层，扫描件里是一张图，
 *       没有文本层。它<b>不会报错</b>，只是返回空串或零星几个字。
 *       本项目未集成 OCR，所以这里必须显式告诉管理员「这份文件是扫描件，
 *       需要 OCR，本项目暂不支持」，否则它会安静入库成一枚空文档。</li>
 *   <li><b>加密 PDF 会抛 {@code InvalidPasswordException}</b>，
 *       原始异常信息是 {@code Cannot decrypt PDF, the password was not
 *       supplied.}，管理员基本看不懂，这里翻成人话。</li>
 * </ol>
 */
@Component
public class PdfDocumentParser implements DocumentParser {

    private static final Logger log = LoggerFactory.getLogger(PdfDocumentParser.class);

    /**
     * 每页平均字符数低于该值即怀疑是扫描件。
     *
     * <p>取 15：正常中文 PDF 每页几百字起步；表单类 PDF（大量空白+少量标注）
     * 也普遍高于这个值。真正取到 15 以下，基本只可能是图片型页面。</p>
     */
    private static final int MIN_CHARS_PER_PAGE = 15;

    @Override
    public Set<String> extensions() {
        return Set.of("pdf");
    }

    @Override
    public ExtractedText parse(byte[] content, String fileName) {
        if (content == null || content.length == 0) {
            throw new DocumentParseException("文件内容为空：" + fileName);
        }

        int pages;
        String text;
        try (PDDocument document = PDDocument.load(content)) {
            pages = document.getNumberOfPages();
            if (pages == 0) {
                throw new DocumentParseException(
                        "PDF 没有任何页面（" + fileName + "），文件可能已损坏。");
            }
            PDFTextStripper stripper = new PDFTextStripper();
            // 按坐标排序：不做这一步时，分栏排版（论文、报纸式折行）会被
            // 按流顺序抽取，左右栏交错成一句话，语义完全错乱。
            stripper.setSortByPosition(true);
            text = stripper.getText(document);
        } catch (InvalidPasswordException e) {
            throw new DocumentParseException(
                    "PDF 已加密，需要密码才能打开（" + fileName + "）。"
                            + "请用阅读器去除密码保护后重新上传。", e);
        } catch (DocumentParseException e) {
            throw e;
        } catch (Exception e) {
            // PDFBox 对损坏文件抛的异常类型很杂（IOException / IllegalArgumentException /
            // IndexOutOfBounds…），逐个匹配没有意义，统一翻成可操作的消息
            log.warn("[RAG-parse] PDF 解析失败: file={}, err={}", fileName, e.toString());
            throw new DocumentParseException(
                    "PDF 解析失败（" + fileName + "）：" + e.getClass().getSimpleName()
                            + "。文件可能已损坏，或使用了不受支持的 PDF 特性。", e);
        }

        String normalized = normalize(text);
        List<String> warnings = new ArrayList<>();
        if (normalized.length() < (long) pages * MIN_CHARS_PER_PAGE) {
            warnings.add("PDF 共 " + pages + " 页，但只提取到 " + normalized.length()
                    + " 个字符，疑似扫描件/图片型 PDF。本项目未集成 OCR，"
                    + "这类文件无法参与检索，建议先用 OCR 工具转成可复制文本再上传。");
        }

        return new ExtractedText(normalized, pages, "pdf", warnings);
    }

    /** 去掉 PDFBox 抽出的多余空行与页尾空白。 */
    private static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw.replace("\r\n", "\n").replace('\r', '\n');
        // PDFBox 每页之间会补换行，压缩避免切块时切出一堆空块
        return s.replaceAll("[ \t]+\n", "\n").replaceAll("\n{3,}", "\n\n").trim();
    }
}
