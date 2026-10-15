package cn.kmbeast.crm.rag.parse;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 文档解析链路的单测：格式分派、编码判定、失败消息。
 *
 * <p>本组用例是<b>真实往返</b>（round-trip）而不是打桩：docx/xlsx/pdf 的
 * 字节都由 POI/PDFBox 现场写出来再读回去。这样做的原因很实际 ——
 * 解析这件事的全部风险都在「真实文件长什么样」上，
 * 用 Mockito 把解析器换成假的，等于什么都没测。</p>
 *
 * <p>三处最容易被做错、因此必须锁住的地方：</p>
 * <ol>
 *   <li><b>中文 txt 的编码</b>。中文 Windows 导出的 txt/csv 默认是 GBK。
 *       按 UTF-8 硬读不会报错，只会产出替换字符，然后正常入库、正常检索 ——
 *       只是永远命中不了任何中文词。</li>
 *   <li><b>docx 里的表格</b>。用 {@code getParagraphs()} 会静默丢掉全部表格。</li>
 *   <li><b>Excel 的数字格式</b>。不用 {@code DataFormatter} 会把 38 读成 38.0。</li>
 * </ol>
 */
@DisplayName("文档解析链路")
class DocumentParserRegistryTest {

    // ==================== 测试夹具：现场生成真实文件字节 ====================

    private static byte[] docxBytes() throws Exception {
        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            doc.createParagraph().createRun().setText("高血压患者的日常管理");
            XWPFTable table = doc.createTable(2, 2);
            table.getRow(0).getCell(0).setText("指标");
            table.getRow(0).getCell(1).setText("参考范围");
            table.getRow(1).getCell(0).setText("收缩压");
            table.getRow(1).getCell(1).setText("90-120mmHg");
            doc.write(out);
            return out.toByteArray();
        }
    }

    private static byte[] xlsxBytes() throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            var sheet = wb.createSheet("体征");
            var header = sheet.createRow(0);
            header.createCell(0).setCellValue("项目");
            header.createCell(1).setCellValue("数值");
            var row = sheet.createRow(1);
            row.createCell(0).setCellValue("体温");
            row.createCell(1).setCellValue(38);       // 整数，必须读成 "38" 而不是 "38.0"
            wb.write(out);
            return out.toByteArray();
        }
    }

    /** 一页带文字层的 PDF。 */
    private static byte[] pdfWithTextBytes() throws Exception {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA, 14);
                cs.newLineAtOffset(60, 700);
                cs.showText("Blood pressure management for hypertension patients.");
                cs.endText();
            }
            doc.save(out);
            return out.toByteArray();
        }
    }

    /** 无文字层的 PDF —— 扫描件的等价物。 */
    private static byte[] pdfWithoutTextBytes() throws Exception {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            doc.addPage(new PDPage());
            doc.save(out);
            return out.toByteArray();
        }
    }

    private static DocumentParserRegistry registry(String... extra) {
        return new DocumentParserRegistry(java.util.List.of(
                new PlainTextParser(), new PdfDocumentParser(),
                new WordDocumentParser(), new SheetDocumentParser()));
    }

    // ==================== 扩展名解析 ====================

    @Test
    @DisplayName("T1 扩展名解析：大小写、完整路径、无扩展名、结尾点、null")
    void extensionParsing() {
        assertEquals("pdf", DocumentParserRegistry.extensionOf("指南.pdf"));
        assertEquals("docx", DocumentParserRegistry.extensionOf("A.DOCX"), "大小写必须归一，否则大写后缀会被判为不支持");
        assertEquals("txt", DocumentParserRegistry.extensionOf("C:\\Users\\ly\\资料.TXT"),
                "部分浏览器会把完整路径塞进 filename");
        assertEquals("csv", DocumentParserRegistry.extensionOf("/tmp/a/b/报表.csv"));
        assertEquals("", DocumentParserRegistry.extensionOf("README"));
        assertEquals("", DocumentParserRegistry.extensionOf("trailing."), "结尾点不是扩展名");
        assertEquals("", DocumentParserRegistry.extensionOf(null));
    }

    @Test
    @DisplayName("T2 两个解析器抢同一个扩展名 → 启动即失败，不留下随机分派")
    void duplicateExtensionFailsFast() {
        DocumentParser greedy = new DocumentParser() {
            @Override
            public java.util.Set<String> extensions() {
                return java.util.Set.of("pdf");
            }

            @Override
            public ExtractedText parse(byte[] content, String fileName) {
                return ExtractedText.of("x", 1, "fake");
            }
        };

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> new DocumentParserRegistry(java.util.List.of(new PdfDocumentParser(), greedy)));
        assertTrue(e.getMessage().contains("pdf"));
        assertTrue(e.getMessage().contains("冲突"));
    }

    @Test
    @DisplayName("T3 装配结果可枚举：支持格式清单与实际分派能力一致")
    void supportedExtensionsAreEnumerable() {
        DocumentParserRegistry r = registry();
        assertTrue(r.supports("a.pdf"));
        assertTrue(r.supports("a.xlsx"));
        assertFalse(r.supports("a.doc"),
                ".doc（HWPF）在 poi-scratchpad 里，本项目未引入 —— 不能谎称支持，"
                        + "否则管理员传上来只会拿到一个解析失败");
        assertFalse(r.supports("a.doc_"), "doc_ 不是已知扩展名");
        assertFalse(r.supports("a.exe"));
        assertTrue(r.supportedExtensions().containsAll(java.util.List.of("pdf", "docx", "xlsx", "xls", "txt")));
        assertFalse(r.supportedExtensions().contains("doc"),
                ".doc（HWPF）需要 poi-scratchpad，本项目未引入，不能谎称支持");
    }

    // ==================== 拒绝路径 ====================

    @Test
    @DisplayName("T4 不支持的格式 → 消息里列出当前支持哪些，而不是一句「不支持」")
    void unsupportedFormatListsWhatIsSupported() {
        DocumentParseException e = assertThrows(DocumentParseException.class,
                () -> registry().parse(new byte[]{1, 2, 3}, "病毒.exe"));

        assertTrue(e.getMessage().contains(".exe"));
        assertTrue(e.getMessage().contains("pdf"), "必须告诉管理员能传什么：" + e.getMessage());
        assertTrue(e.getMessage().contains("docx"));
    }

    @Test
    @DisplayName("T5 空文件 / 空内容分别被拒")
    void emptyContentRejected() {
        assertThrows(DocumentParseException.class, () -> registry().parse(new byte[0], "空.txt"));
        assertThrows(DocumentParseException.class, () -> registry().parse(null, "空.txt"));
    }

    @Test
    @DisplayName("T6 超出体积上限 → 在上限处拒绝，并给出人话单位")
    void oversizedFileRejected() {
        DocumentParserRegistry r = registry();
        ReflectionTestUtils.setField(r, "maxBytes", 10L);   // 10 字节

        DocumentParseException e = assertThrows(DocumentParseException.class,
                () -> r.parse("这是远远超过十个字节的中文内容".getBytes(StandardCharsets.UTF_8), "大.txt"));
        assertTrue(e.getMessage().contains("超过上限"), e.getMessage());
    }

    @Test
    @DisplayName("T7 解析出空正文 → 失败，绝不能入库成一枚空文档")
    void blankTextIsAFailureNotAnEmptyDocument() throws Exception {
        DocumentParseException e = assertThrows(DocumentParseException.class,
                () -> registry().parse(pdfWithoutTextBytes(), "扫描件.pdf"));
        assertTrue(e.getMessage().contains("未能从"), e.getMessage());
        assertTrue(e.getMessage().contains("OCR"), "要说清是扫描件而不是甩一句「解析失败」：" + e.getMessage());
    }

    @Test
    @DisplayName("T8 直接粘贴的空白文本同样被拒（粘贴不能成为塞空文档的旁路）")
    void blankPastedTextRejected() {
        assertThrows(DocumentParseException.class, () -> registry().parseText("   \n\n  ", "标题"));
        assertThrows(DocumentParseException.class, () -> registry().parseText(null, "标题"));
    }

    // ==================== 纯文本与编码 ====================

    @Test
    @DisplayName("T9 UTF-8 文本往返无损")
    void utf8TextRoundTrip() {
        String text = "高血压患者的日常饮食应当低盐低脂。";
        ExtractedText r = registry().parse(text.getBytes(StandardCharsets.UTF_8), "饮食.md");

        assertEquals(text, r.getText());
        assertEquals("text", r.getFormat());
        assertTrue(r.getWarnings().isEmpty(), "UTF-8 不该有任何告警：" + r.getWarnings());
    }

    @Test
    @DisplayName("T10 GBK 文本必须被正确识别 —— 按 UTF-8 硬读会得到一堆替换字符")
    void gbkTextIsDecodedCorrectly() {
        String original = "血糖监测应当在餐后两小时进行。";
        byte[] gbk = original.getBytes(Charset.forName("GBK"));
        // 前提校验：这段 GBK 字节确实不是合法 UTF-8，否则本用例测不出东西
        assertFalse(isValidUtf8(gbk), "前提不成立：该文本用 GBK 编码后恰好也是合法 UTF-8");

        ExtractedText r = registry().parse(gbk, "血糖.txt");

        assertEquals(original, r.getText(),
                "解错编码不会报错，只会安静地入库一堆乱码 —— 然后永远检索不到");
        assertTrue(r.getWarnings().stream().anyMatch(w -> w.contains("GBK")),
                "解码走了兜底路径就该告警：" + r.getWarnings());
    }

    @Test
    @DisplayName("T11 UTF-8 BOM 被剥掉（BOM 混进正文会粘在第一个词上，让首块检索失真）")
    void utf8BomIsStripped() {
        byte[] bom = new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] body = "维生素D缺乏的常见表现。".getBytes(StandardCharsets.UTF_8);
        byte[] file = new byte[bom.length + body.length];
        System.arraycopy(bom, 0, file, 0, bom.length);
        System.arraycopy(body, 0, file, bom.length, body.length);

        ExtractedText r = registry().parse(file, "bom.txt");
        assertEquals("维生素D缺乏的常见表现。", r.getText());
        assertFalse(r.getText().startsWith("\uFEFF"), "BOM 残留会让「维生素D」这个词的首字符错位");
    }

    @Test
    @DisplayName("T12 段落数按空行统计，供管理端展示结构化程度")
    void sectionCountFromBlankLines() {
        ExtractedText r = registry().parse("第一段。\n\n第二段。\n\n第三段。".getBytes(StandardCharsets.UTF_8), "a.txt");
        assertEquals(3, r.getSectionCount());
    }

    // ==================== Word ====================

    @Test
    @DisplayName("T13 docx 往返：段落与表格内容都在（getParagraphs() 会静默丢掉表格）")
    void docxKeepsTables() throws Exception {
        DocumentParser word = new WordDocumentParser();
        ExtractedText r = word.parse(docxBytes(), "指南.docx");

        assertTrue(r.getText().contains("高血压患者的日常管理"), r.getText());
        assertTrue(r.getText().contains("收缩压"),
                "表格是医学文档信息密度最高的部分，丢了它等于索引了一个残缺文档：" + r.getText());
        assertTrue(r.getText().contains("90-120mmHg"), r.getText());
        assertEquals(2, r.getSectionCount(), "1 个段落 + 1 张表格");
    }

    @Test
    @DisplayName("T14 非 docx 字节喂给 docx 解析器 → 可操作的错误，不是原始异常名")
    void docxGarbageInputGivesActionableError() {
        DocumentParseException e = assertThrows(DocumentParseException.class,
                () -> new WordDocumentParser().parse("这不是一个 zip 包".getBytes(StandardCharsets.UTF_8), "假.docx"));
        assertTrue(e.getMessage().contains("假.docx"), "错误消息必须指明是哪个文件");
        assertTrue(e.getMessage().contains("docx"));
    }

    // ==================== Excel ====================

    @Test
    @DisplayName("T15 xlsx 往返：整数读成 38 而不是 38.0（DataFormatter 的作用）")
    void xlsxUsesDisplayFormatNotRawDouble() throws Exception {
        DocumentParser sheet = new SheetDocumentParser();
        ExtractedText r = sheet.parse(xlsxBytes(), "体征.xlsx");

        assertTrue(r.getText().contains("## 体征"), "工作表名要作为分组标题保留：" + r.getText());
        assertTrue(r.getText().contains("体温"), r.getText());
        assertTrue(r.getText().contains("38"), r.getText());
        assertFalse(r.getText().contains("38.0"),
                "Excel 里所有数字都以 double 存储，取原始值会得到 38.0 —— 与用户在表格里看到的不一致");
        assertEquals(1, r.getSectionCount());
    }

    @Test
    @DisplayName("T16 表格行用制表符分隔并拍平，便于 ngram 切词与人工阅读")
    void xlsxRowsAreTabSeparated() throws Exception {
        ExtractedText r = new SheetDocumentParser().parse(xlsxBytes(), "体征.xlsx");
        assertTrue(r.getText().lines().anyMatch(l -> l.startsWith("项目\t数值")), r.getText());
    }

    // ==================== PDF ====================

    @Test
    @DisplayName("T17 PDF 文字层被提取，页数被统计")
    void pdfTextIsExtracted() throws Exception {
        ExtractedText r = new PdfDocumentParser().parse(pdfWithTextBytes(), "guide.pdf");

        assertTrue(r.getText().contains("hypertension"), r.getText());
        assertEquals(1, r.getSectionCount(), "页数");
        assertEquals("pdf", r.getFormat());
        assertTrue(r.getWarnings().isEmpty(), "正常的文字型 PDF 不该被告警：" + r.getWarnings());
    }

    @Test
    @DisplayName("T18 无文字层的 PDF → 解析器给出「疑为扫描件」告警（这是它唯一能自救的机会）")
    void pdfWithoutTextLayerWarnsAboutScannedFiles() throws Exception {
        ExtractedText r = new PdfDocumentParser().parse(pdfWithoutTextBytes(), "扫描件.pdf");

        assertTrue(r.getText().isBlank());
        assertEquals(1, r.getSectionCount(), "页数照常能统计出来");
        assertTrue(r.getWarnings().stream().anyMatch(w -> w.contains("扫描件")),
                "PDFBox 不会为扫描件报错，只会返回空串。不告警的话它就安静地变成一篇空文档：" + r.getWarnings());
    }

    @Test
    @DisplayName("T19 损坏的 PDF → 消息可读，且带上文件名")
    void brokenPdfGivesReadableError() {
        DocumentParseException e = assertThrows(DocumentParseException.class,
                () -> new PdfDocumentParser().parse("%PDF-1.4 broken".getBytes(StandardCharsets.UTF_8), "坏文件.pdf"));
        assertTrue(e.getMessage().contains("坏文件.pdf"), e.getMessage());
        assertTrue(e.getMessage().contains("PDF"), e.getMessage());
    }

    // ==================== 辅助 ====================

    private static boolean isValidUtf8(byte[] bytes) {
        try {
            StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                    .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
                    .decode(java.nio.ByteBuffer.wrap(bytes));
            return true;
        } catch (java.nio.charset.CharacterCodingException e) {
            return false;
        }
    }
}
