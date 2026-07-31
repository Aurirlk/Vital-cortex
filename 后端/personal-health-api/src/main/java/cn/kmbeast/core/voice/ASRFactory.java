package cn.kmbeast.core.voice;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * ASR（语音识别）工厂
 */
@Slf4j
@Component
public class ASRFactory {

    public String recognize(byte[] audioData) {
        // 语音识别实现（需要集成第三方 ASR 服务）
        log.info("ASR recognize called, audio size: {} bytes", audioData.length);
        return ""; // 占位实现
    }
}
