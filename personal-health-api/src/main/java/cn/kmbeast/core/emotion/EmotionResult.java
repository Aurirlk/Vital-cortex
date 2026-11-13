package cn.kmbeast.core.emotion;

import lombok.Data;

/**
 * 情感分析结果
 */
@Data
public class EmotionResult {
    /** 情感类型: positive/negative/neutral */
    private String emotion;
    /** 置信度 0-1 */
    private double confidence;
    /** 情感标签 */
    private String label;
}
