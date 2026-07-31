package cn.kmbeast.core.guard;

import org.springframework.stereotype.Component;

/**
 * 输出验证器
 * 验证 AI 输出的合规性和安全性
 */
@Component
public class OutputValidator {

    /**
     * 验证输出是否包含敏感信息
     */
    public boolean containsSensitiveInfo(String output) {
        if (output == null) return false;
        // 检测是否包含具体药物剂量建议
        String[] sensitivePatterns = {"\\d+mg", "\\d+ml", "一次\\d+片"};
        for (String pattern : sensitivePatterns) {
            if (output.matches(".*" + pattern + ".*")) {
                return true;
            }
        }
        return false;
    }

    /**
     * 验证输出是否需要免责声明
     */
    public boolean needDisclaimer(String output) {
        if (output == null) return false;
        String[] medicalKeywords = {"诊断", "处方", "用药", "治疗方案", "手术"};
        for (String keyword : medicalKeywords) {
            if (output.contains(keyword)) return true;
        }
        return false;
    }
}
