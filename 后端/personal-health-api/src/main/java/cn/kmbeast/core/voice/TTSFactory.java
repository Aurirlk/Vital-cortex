package cn.kmbeast.core.voice;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * TTS（语音合成）工厂
 */
@Slf4j
@Component
public class TTSFactory {

    public byte[] synthesize(String text) {
        // 语音合成实现（需要集成第三方 TTS 服务）
        log.info("TTS synthesize called, text length: {}", text.length());
        return new byte[0]; // 占位实现
    }
}
