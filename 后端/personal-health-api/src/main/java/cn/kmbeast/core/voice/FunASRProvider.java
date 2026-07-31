package cn.kmbeast.core.voice;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * FunASR 供应商
 */
@Slf4j
@Component
public class FunASRProvider {

    public String recognize(byte[] audioData) {
        log.info("FunASR recognize: audio size={}", audioData.length);
        return "";
    }
}
