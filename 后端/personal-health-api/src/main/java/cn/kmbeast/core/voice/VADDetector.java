package cn.kmbeast.core.voice;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * VAD（语音活动检测）检测器
 */
@Slf4j
@Component
public class VADDetector {

    public boolean detectSpeech(byte[] audioData) {
        // 语音活动检测实现
        log.info("VAD detectSpeech called, audio size: {} bytes", audioData.length);
        return true; // 占位实现
    }
}
