package cn.kmbeast.crm.rag.parse;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 电子表格解析器（.xlsx / .xls，Apache POI XSSF / HSSF）。
 *
 * <h3>为什么必须用 {@code DataFormatter} 而不是 {@code cell.toString()}</h3>
 * <p>Excel 里所有数字都以 double 存储，日期则以「1900 起算的天数」存储。
 * 直接用 {@code getNumericCellValue()} 打出来是 {@code 45000.0} 这种数字，
 * 管理员看到的是「体检日期 45000.0」；而 {@code cell.toString()}
 * 又会把整数打成 {@code 38.0}、把百分比打成 {@code 0.1}。
 * {@code DataFormatter} 按单元格的<b>显示格式</b>取值，得到的就是
 * 用户在 Excel 里看到的那个字符串（{@code 38}、{@code 10%}、{@code 2023-01-15}）。</p>
 *
 * <h3>上限</h3>
 * <p>表格是唯一可能「看起来很小但解析出极大文本」的格式：
 * 一个 200KB 的 xlsx 可以有十万行。这里对每表行数与总字符数双重设限，
 * 并把截断事实写进 warnings —— 静默截断会让管理员以为整份数据都进去了。</p>
 */
@Component
public class SheetDocumentParser implements DocumentParser {

    private static final Logger log = LoggerFactory.getLogger(SheetDocumentParser.class);

    /** 单个工作表最多提取的行数 */
    private static final int MAX_ROWS_PER_SHEET = 5_000;

    /** 整份工作簿最多提取的字符数 */
    private static final int MAX_CHARS = 2_000_000;

    /** 单行最多提取的列数，防止一份「宽表」把一行撑成一条看不见边的长串 */
    private static final int MAX_COLS_PER_ROW = 100;

    @Override
    public Set<String> extensions() {
        return Set.of("xlsx", "xls");
    }

    @Override
    public ExtractedText parse(byte[] content, String fileName) {
        if (content == null || content.length == 0) {
            throw new DocumentParseException("文件内容为空：" + fileName);
        }

        boolean xlsx = fileName != null && fileName.toLowerCase(java.util.Locale.ROOT).endsWith(".xlsx");
        StringBuilder sb = new StringBuilder();
        List<String> warnings = new ArrayList<>();
        int sheets;

        try (Workbook workbook = xlsx
                ? new XSSFWorkbook(new ByteArrayInputStream(content))
                : new HSSFWorkbook(new ByteArrayInputStream(content))) {

            sheets = workbook.getNumberOfSheets();
            DataFormatter formatter = new DataFormatter();

            for (int i = 0; i < sheets; i++) {
                if (sb.length() >= MAX_CHARS) {
                    warnings.add("工作簿内容超过 " + MAX_CHARS + " 字符，后面的工作表未被索引。");
                    break;
                }
                Sheet sheet = workbook.getSheetAt(i);
                sb.append("## ").append(sheet.getSheetName()).append('\n');

                int lastRow = Math.min(sheet.getLastRowNum(), MAX_ROWS_PER_SHEET - 1);
                for (int r = 0; r <= lastRow; r++) {
                    if (sb.length() >= MAX_CHARS) {
                        warnings.add("工作表「" + sheet.getSheetName() + "」内容超过上限，已截断。");
                        break;
                    }
                    Row row = sheet.getRow(r);
                    if (row == null) {
                        continue;
                    }
                    // getLastCellNum() 返回的是「最后一个单元格下标 + 1」，空行返回 -1
                    int lastCell = Math.min(row.getLastCellNum(), MAX_COLS_PER_ROW);
                    List<String> cells = new ArrayList<>();
                    for (int c = 0; c < lastCell; c++) {
                        Cell cell = row.getCell(c);
                        cells.add(cell == null ? "" : formatter.formatCellValue(cell).replace("\n", " ").trim());
                    }
                    if (cells.stream().allMatch(String::isEmpty)) {
                        continue;
                    }
                    // 去掉制表符分隔后行尾的空白列，避免大量「值\t\t\t\t」
                    while (!cells.isEmpty() && cells.get(cells.size() - 1).isEmpty()) {
                        cells.remove(cells.size() - 1);
                    }
                    sb.append(String.join("\t", cells)).append('\n');
                }
                if (sheet.getLastRowNum() >= MAX_ROWS_PER_SHEET) {
                    warnings.add("工作表「" + sheet.getSheetName() + "」共 " + (sheet.getLastRowNum() + 1)
                            + " 行，已按前 " + MAX_ROWS_PER_SHEET + " 行索引。");
                }
                sb.append('\n');
            }
        } catch (Exception e) {
            log.warn("[RAG-parse] 表格解析失败: file={}, err={}", fileName, e.toString());
            throw new DocumentParseException(
                    "表格解析失败（" + fileName + "）：" + e.getClass().getSimpleName()
                            + "。文件可能已损坏，或扩展名与实际格式不符"
                            + "（.xls 与 .xlsx 是两种不同格式，改后缀不会转换）。", e);
        }

        String text = sb.toString().replaceAll("\n{3,}", "\n\n").trim();
        return new ExtractedText(text, sheets, xlsx ? "xlsx" : "xls", warnings);
    }
}
