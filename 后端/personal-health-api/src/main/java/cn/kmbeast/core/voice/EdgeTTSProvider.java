package cn.kmbeast.core.voice;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Edge TTS 供应商
 */
@Slf4j
@Component
public class EdgeTTSProvider {

    public byte[] synthesize(String text, String voice) {
        log.info("EdgeTTS synthesize: text={}, voice={}", text, voice);
        return new byte[0];
    }
}
