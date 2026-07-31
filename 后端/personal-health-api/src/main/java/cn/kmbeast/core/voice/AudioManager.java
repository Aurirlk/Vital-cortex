package cn.kmbeast.core.voice;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 音频管理器
 */
@Slf4j
@Component
public class AudioManager {

    public byte[] loadAudio(String path) {
        log.info("AudioManager loadAudio: {}", path);
        return new byte[0];
    }

    public void saveAudio(byte[] data, String path) {
        log.info("AudioManager saveAudio: {} bytes to {}", data.length, path);
    }
}
