package cn.kmbeast.core.guard;

import org.springframework.stereotype.Component;

/**
 * 信号检测器
 * 检测用户输入中的特殊信号
 */
@Component
public class SignalDetector {

    /**
     * 检测是否为紧急情况
     */
    public boolean isUrgent(String message) {
        if (message == null) return false;
        String[] urgentKeywords = {"急救", "120", "紧急", "危重", "昏迷", "大出血"};
        for (String keyword : urgentKeywords) {
            if (message.contains(keyword)) return true;
        }
        return false;
    }

    /**
     * 检测是否需要转人工
     */
    public boolean needHumanTransfer(String message) {
        if (message == null) return false;
        String[] keywords = {"转人工", "人工客服", "找医生", "看医生"};
        for (String keyword : keywords) {
            if (message.contains(keyword)) return true;
        }
        return false;
    }
}
