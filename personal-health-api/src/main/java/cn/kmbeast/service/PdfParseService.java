package cn.kmbeast.service;

import org.springframework.web.multipart.MultipartFile;
import java.util.Map;

public interface PdfParseService {
    String extractText(MultipartFile file);
    Map<String, String> parseHealthData(String text);
}
