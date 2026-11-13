package cn.kmbeast.core.guard;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * 防端水引擎
 * 检测和防止 AI 输出中的重复、无意义内容
 */
@Slf4j
@Component
public class SynthesisGuard {

    private static final List<String> FILLER_PATTERNS = Arrays.asList(
            "嗯嗯", "好的好的", "是的是的", "对对对", "哈哈", "呵呵"
    );

    /**
     * 检测输出是否包含端水内容
     */
    public boolean detectFiller(String output) {
        if (output == null || output.isEmpty()) return false;
        for (String pattern : FILLER_PATTERNS) {
            if (output.contains(pattern)) {
                return true;
            }
        }
        // 检测重复句子
        String[] sentences = output.split("[。！？]");
        for (int i = 0; i < sentences.length - 1; i++) {
            for (int j = i + 1; j < sentences.length; j++) {
                if (sentences[i].trim().equals(sentences[j].trim()) && !sentences[i].trim().isEmpty()) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 验证输出质量
     */
    public boolean validateOutput(String output) {
        if (output == null || output.trim().isEmpty()) return false;
        if (output.length() < 10) return false;
        return !detectFiller(output);
    }
}
