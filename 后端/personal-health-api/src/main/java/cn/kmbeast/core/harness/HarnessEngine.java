package cn.kmbeast.core.harness;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 数据引导引擎
 * 引导用户提供缺失的健康数据
 */
@Slf4j
@Component
public class HarnessEngine {

    /**
     * 检查用户健康数据完整性
     */
    public Map<String, String> checkDataCompleteness(Map<String, String> existingData) {
        Map<String, String> guidance = new LinkedHashMap<>();
        String[] requiredFields = {"gender", "age", "height", "weight", "chronicDiseases"};
        for (String field : requiredFields) {
            if (!existingData.containsKey(field) ||
                existingData.get(field) == null ||
                existingData.get(field).trim().isEmpty()) {
                guidance.put(field, generateGuidance(field));
            }
        }
        return guidance;
    }

    private String generateGuidance(String dataType) {
        switch (dataType) {
            case "gender": return "请告诉我您的性别（男/女），以便我为您提供更准确的健康建议。";
            case "age": return "请告诉我您的年龄，以便我为您评估健康风险。";
            case "height": return "请告诉我您的身高（厘米），以便我计算您的BMI。";
            case "weight": return "请告诉我您的体重（公斤），以便我计算您的BMI。";
            case "chronicDiseases": return "请问您是否有慢性疾病（如高血压、糖尿病等）？";
            default: return "请提供相关信息以便我为您提供更好的服务。";
        }
    }

    public Double calculateBMI(Double heightCm, Double weightKg) {
        if (heightCm == null || weightKg == null || heightCm <= 0 || weightKg <= 0) return null;
        double heightM = heightCm / 100.0;
        return Math.round(weightKg / (heightM * heightM) * 10.0) / 10.0;
    }

    public String evaluateBMI(Double bmi) {
        if (bmi == null) return "未知";
        if (bmi < 18.5) return "偏瘦";
        if (bmi < 24.0) return "正常";
        if (bmi < 28.0) return "超重";
        return "肥胖";
    }
}
