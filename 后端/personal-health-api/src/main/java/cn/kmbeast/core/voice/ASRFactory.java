package cn.kmbeast.core.voice;

import cn.kmbeast.core.voice.VoiceConfigHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;

/**
 * ASR（语音识别）工厂
 * 
 * 根据配置选择不同的 ASR 供应商：
 * - funasr: 阿里云 DashScope 的 FunASR 服务
 * - whisper: OpenAI 的 Whisper 服务
 */
@Slf4j
@Component
public class ASRFactory {

    @Resource
    private FunASRProvider funASRProvider;

    @Resource
    private WhisperASRProvider whisperASRProvider;

    @Autowired
    private VoiceConfigHolder voiceConfigHolder;

    /**
     * 识别音频数据
     * 
     * @param audioData 音频字节数据
     * @param format 音频格式（wav, mp3, pcm 等）
     * @return 识别结果
     */
    public VoiceResult<String> recognize(byte[] audioData, String format) {
        if (audioData == null || audioData.length == 0) {
            return VoiceResult.invalidInput("音频数据为空");
        }

        log.info("[ASRFactory] 开始识别，provider={}, format={}, audioSize={} bytes",
                voiceConfigHolder.getAsrProvider(), format, audioData.length);

        String provider = voiceConfigHolder.getAsrProvider();
        switch (provider.toLowerCase()) {
            case "funasr":
            case "dashscope":
                return funASRProvider.recognize(audioData, format);
            case "whisper":
                return whisperASRProvider.recognize(audioData, format);
            default:
                log.warn("[ASRFactory] 未知的 ASR 供应商: {}，使用默认 FunASR", provider);
                return funASRProvider.recognize(audioData, format);
        }
    }
}
