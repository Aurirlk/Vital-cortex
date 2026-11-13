package cn.kmbeast.service.impl;

import cn.kmbeast.service.PdfParseService;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class PdfParseServiceImpl implements PdfParseService {

    @Override
    public String extractText(MultipartFile file) {
        try (PDDocument document = PDDocument.load(file.getInputStream())) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);
            return text.trim();
        } catch (IOException e) {
            log.error("PDF 文本提取失败", e);
            throw new RuntimeException("PDF 文件读取失败，请确认文件未损坏");
        }
    }

    @Override
    public Map<String, String> parseHealthData(String text) {
        Map<String, String> data = new HashMap<>();
        if (text == null || text.isEmpty()) return data;

        // 提取血压
        Pattern bpPattern = Pattern.compile("(?:血压|BP)[：:]*\\s*(\\d{2,3})[/\\-](\\d{2,3})");
        Matcher bpMatcher = bpPattern.matcher(text);
        if (bpMatcher.find()) {
            data.put("systolic", bpMatcher.group(1));
            data.put("diastolic", bpMatcher.group(2));
        }

        // 提取血糖
        Pattern bgPattern = Pattern.compile("(?:血糖|GLU|FPG)[：:]*\\s*(\\d+\\.?\\d*)");
        Matcher bgMatcher = bgPattern.matcher(text);
        if (bgMatcher.find()) {
            data.put("bloodGlucose", bgMatcher.group(1));
        }

        // 提取体重
        Pattern weightPattern = Pattern.compile("(?:体重|Weight)[：:]*\\s*(\\d+\\.?\\d*)\\s*(?:kg|KG|千克)?");
        Matcher weightMatcher = weightPattern.matcher(text);
        if (weightMatcher.find()) {
            data.put("weight", weightMatcher.group(1));
        }

        // 提取身高
        Pattern heightPattern = Pattern.compile("(?:身高|Height)[：:]*\\s*(\\d+\\.?\\d*)\\s*(?:cm|CM|厘米)?");
        Matcher heightMatcher = heightPattern.matcher(text);
        if (heightMatcher.find()) {
            data.put("height", heightMatcher.group(1));
        }

        // 提取心率
        Pattern hrPattern = Pattern.compile("(?:心率|HR|Pulse)[：:]*\\s*(\\d{2,3})");
        Matcher hrMatcher = hrPattern.matcher(text);
        if (hrMatcher.find()) {
            data.put("heartRate", hrMatcher.group(1));
        }

        // 提取BMI
        Pattern bmiPattern = Pattern.compile("BMI[：:]*\\s*(\\d+\\.?\\d*)");
        Matcher bmiMatcher = bmiPattern.matcher(text);
        if (bmiMatcher.find()) {
            data.put("bmi", bmiMatcher.group(1));
        }

        return data;
    }
}
