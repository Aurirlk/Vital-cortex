package cn.kmbeast.controller;

import cn.kmbeast.aop.Protector;
import cn.kmbeast.pojo.api.ApiResult;
import cn.kmbeast.pojo.api.Result;
import cn.kmbeast.service.PdfParseService;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

/**
 * PDF 解析接口。
 *
 * <p>MM-21 整改：原实现不校验文件类型与页数——任意文件均可送入 PDFBox，
 * 恶意构造的"PDF 炸弹"（超大页数 / 巨大交叉引用）可致内存溢出。
 * 现增加三道防线：
 * <ol>
 *   <li>扩展名 + Content-Type 白名单（仅 .pdf / application/pdf）；</li>
 *   <li>魔数校验（文件头必须为 {@code %PDF-}）；</li>
 *   <li>页数上限（默认 100 页，防 PDF 炸弹）。</li>
 * </ol>
 */
@RestController
@RequestMapping("/pdf")
public class PdfParseController {

    private static final int MAX_PDF_PAGES = 100;
    private static final byte[] PDF_MAGIC = new byte[]{'%', 'P', 'D', 'F', '-'};

    @Resource
    private PdfParseService pdfParseService;

    @Protector
    @PostMapping("/parse")
    public Result<Map<String, String>> parsePdf(@RequestParam("file") MultipartFile file) {
        // 1) 类型白名单
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".pdf")) {
            return ApiResult.error("仅支持 PDF 文件");
        }
        String contentType = file.getContentType();
        if (contentType != null && !contentType.isEmpty()
                && !contentType.toLowerCase().contains("pdf")) {
            return ApiResult.error("文件类型校验失败");
        }
        if (file.isEmpty()) {
            return ApiResult.error("文件内容为空");
        }
        if (file.getSize() > 50 * 1024 * 1024) {
            return ApiResult.error("PDF 文件过大（上限 50MB）");
        }

        // 2) 魔数校验：文件头必须是 %PDF-
        try (InputStream in = file.getInputStream()) {
            byte[] head = new byte[PDF_MAGIC.length];
            int read = in.read(head);
            if (read < PDF_MAGIC.length) {
                return ApiResult.error("文件内容不是有效的 PDF");
            }
            for (int i = 0; i < PDF_MAGIC.length; i++) {
                if (head[i] != PDF_MAGIC[i]) {
                    return ApiResult.error("文件内容不是有效的 PDF");
                }
            }
        } catch (IOException e) {
            return ApiResult.error("读取文件失败");
        }

        // 3) 页数上限（防 PDF 炸弹）
        try (PDDocument doc = PDDocument.load(file.getInputStream())) {
            if (doc.getNumberOfPages() > MAX_PDF_PAGES) {
                return ApiResult.error("PDF 页数超过上限（" + MAX_PDF_PAGES + " 页）");
            }
        } catch (IOException e) {
            return ApiResult.error("PDF 解析失败，文件可能已损坏");
        }

        String text = pdfParseService.extractText(file);
        Map<String, String> healthData = pdfParseService.parseHealthData(text);
        healthData.put("rawText", text);
        return ApiResult.success(healthData);
    }
}
