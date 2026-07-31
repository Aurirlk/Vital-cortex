package cn.kmbeast.controller;

import cn.kmbeast.aop.Protector;
import cn.kmbeast.pojo.api.ApiResult;
import cn.kmbeast.pojo.api.Result;
import cn.kmbeast.service.PdfParseService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.util.Map;

@RestController
@RequestMapping("/pdf")
public class PdfParseController {

    @Resource
    private PdfParseService pdfParseService;

    @Protector
    @PostMapping("/parse")
    public Result<Map<String, String>> parsePdf(@RequestParam("file") MultipartFile file) {
        String text = pdfParseService.extractText(file);
        Map<String, String> healthData = pdfParseService.parseHealthData(text);
        healthData.put("rawText", text);
        return ApiResult.success(healthData);
    }
}
