package cn.kmbeast.core.emotion;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * 情感分析器
 * 基于关键词的简单情感分析
 */
@Slf4j
@Component
public class EmotionAnalyzer {

    private static final List<String> POSITIVE_WORDS = Arrays.asList(
            "开心", "快乐", "高兴", "满意", "感谢", "好的", "谢谢", "不错", "优秀", "健康"
    );
    private static final List<String> NEGATIVE_WORDS = Arrays.asList(
            "疼痛", "难受", "不舒服", "焦虑", "担心", "害怕", "失眠", "头疼", "发烧", "咳嗽"
    );

    public EmotionResult analyze(String text) {
        EmotionResult result = new EmotionResult();
        if (text == null || text.isEmpty()) {
            result.setEmotion("neutral");
            result.setConfidence(0.5);
            result.setLabel("中性");
            return result;
        }

        long positiveCount = POSITIVE_WORDS.stream().filter(text::contains).count();
        long negativeCount = NEGATIVE_WORDS.stream().filter(text::contains).count();

        if (positiveCount > negativeCount) {
            result.setEmotion("positive");
            result.setConfidence(Math.min(0.9, 0.5 + positiveCount * 0.1));
            result.setLabel("积极");
        } else if (negativeCount > positiveCount) {
            result.setEmotion("negative");
            result.setConfidence(Math.min(0.9, 0.5 + negativeCount * 0.1));
            result.setLabel("消极");
        } else {
            result.setEmotion("neutral");
            result.setConfidence(0.5);
            result.setLabel("中性");
        }
        return result;
    }
}
