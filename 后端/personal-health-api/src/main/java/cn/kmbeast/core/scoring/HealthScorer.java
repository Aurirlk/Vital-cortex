package cn.kmbeast.core.scoring;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 健康评分器
 * 7 维度健康评分
 */
@Slf4j
@Component
public class HealthScorer {

    /**
     * 计算综合健康评分
     */
    public Map<String, Object> calculateScore(Map<String, Double> healthData) {
        Map<String, Object> result = new HashMap<>();
        int totalScore = 0;
        Map<String, Integer> dimensionScores = new LinkedHashMap<>();

        // 1. 血压评分
        int bpScore = scoreBloodPressure(healthData.get("systolic"), healthData.get("diastolic"));
        dimensionScores.put("血压", bpScore);
        totalScore += bpScore;

        // 2. 血糖评分
        int bgScore = scoreBloodGlucose(healthData.get("bloodGlucose"));
        dimensionScores.put("血糖", bgScore);
        totalScore += bgScore;

        // 3. BMI 评分
        int bmiScore = scoreBMI(healthData.get("bmi"));
        dimensionScores.put("BMI", bmiScore);
        totalScore += bmiScore;

        // 4. 心率评分
        int hrScore = scoreHeartRate(healthData.get("heartRate"));
        dimensionScores.put("心率", hrScore);
        totalScore += hrScore;

        // 5. 胆固醇评分
        int cholScore = scoreCholesterol(healthData.get("cholesterol"));
        dimensionScores.put("胆固醇", cholScore);
        totalScore += cholScore;

        // 6. 运动评分（默认中等）
        dimensionScores.put("运动", 70);
        totalScore += 70;

        // 7. 睡眠评分（默认中等）
        dimensionScores.put("睡眠", 70);
        totalScore += 70;

        int averageScore = totalScore / 7;
        result.put("totalScore", averageScore);
        result.put("dimensions", dimensionScores);
        result.put("level", getHealthLevel(averageScore));
        return result;
    }

    private int scoreBloodPressure(Double systolic, Double diastolic) {
        if (systolic == null || diastolic == null) return 60;
        if (systolic >= 90 && systolic <= 140 && diastolic >= 60 && diastolic <= 90) return 100;
        if (systolic < 90 || systolic > 160) return 40;
        return 70;
    }

    private int scoreBloodGlucose(Double bg) {
        if (bg == null) return 60;
        if (bg >= 3.9 && bg <= 6.1) return 100;
        if (bg > 7.0) return 40;
        return 70;
    }

    private int scoreBMI(Double bmi) {
        if (bmi == null) return 60;
        if (bmi >= 18.5 && bmi < 24) return 100;
        if (bmi < 18.5) return 70;
        if (bmi < 28) return 60;
        return 40;
    }

    private int scoreHeartRate(Double hr) {
        if (hr == null) return 60;
        if (hr >= 60 && hr <= 100) return 100;
        return 60;
    }

    private int scoreCholesterol(Double chol) {
        if (chol == null) return 60;
        if (chol < 5.2) return 100;
        if (chol < 6.2) return 70;
        return 40;
    }

    private String getHealthLevel(int score) {
        if (score >= 90) return "优秀";
        if (score >= 75) return "良好";
        if (score >= 60) return "一般";
        return "需改善";
    }
}
