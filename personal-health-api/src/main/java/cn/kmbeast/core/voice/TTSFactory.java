package cn.kmbeast.core.voice;

import cn.kmbeast.core.voice.VoiceConfigHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.util.concurrent.CompletableFuture;

/**
 * TTS（语音合成）工厂
 * 
 * 根据配置选择不同的 TTS 供应商：
 * - edgetts: 微软 Edge TTS（免费，无需 API Key）
 * - cosyvoice: 阿里云 DashScope 的 CosyVoice 服务
 * - minimax: MiniMax TTS 服务
 */
@Slf4j
@Component
public class TTSFactory {

    @Resource
    private EdgeTTSProvider edgeTTSProvider;

    @Autowired
    private VoiceConfigHolder voiceConfigHolder;

    /**
     * 合成语音（同步）
     * 
     * @param text 要合成的文本
     * @return 识别结果
     */
    public VoiceResult<byte[]> synthesize(String text) {
        return synthesize(text, null);
    }

    /**
     * 合成语音（同步）
     * 
     * @param text 要合成的文本
     * @param voice 语音名称（如 zh-CN-XiaoxiaoNeural）
     * @return 识别结果
     */
    public VoiceResult<byte[]> synthesize(String text, String voice) {
        if (text == null || text.isEmpty()) {
            return VoiceResult.invalidInput("文本为空");
        }

        log.info("[TTSFactory] 开始合成，provider={}, textLength={}, voice={}",
                voiceConfigHolder.getTtsProvider(), text.length(), voice);

        switch (voiceConfigHolder.getTtsProvider().toLowerCase()) {
            case "edgetts":
                return edgeTTSProvider.synthesize(text, voice);
            case "cosyvoice":
                // TODO: 实现 CosyVoice 供应商
                log.warn("[TTSFactory] CosyVoice 供应商暂未实现，回退到 EdgeTTS");
                return edgeTTSProvider.synthesize(text, voice);
            case "minimax":
                // TODO: 实现 MiniMax 供应商
                log.warn("[TTSFactory] MiniMax 供应商暂未实现，回退到 EdgeTTS");
                return edgeTTSProvider.synthesize(text, voice);
            default:
                log.warn("[TTSFactory] 未知的 TTS 供应商: {}，使用默认 EdgeTTS", voiceConfigHolder.getTtsProvider());
                return edgeTTSProvider.synthesize(text, voice);
        }
    }

    /**
     * 合成语音（异步）
     * 
     * @param text 要合成的文本
     * @param voice 语音名称
     * @return CompletableFuture
     */
    public CompletableFuture<VoiceResult<byte[]>> synthesizeAsync(String text, String voice) {
        // 2026-10-03 修复：原实现未传 Executor，会落到 ForkJoinPool.commonPool()。
        // 语音合成是阻塞式网络调用（数秒），并发上来会占满 commonPool 并拖垮其他 ForkJoin 业务。
        // 现指定语音合成专用线程池（有界队列 + CallerRuns 降级），见 VoiceSynthesisExecutor。
        return CompletableFuture.supplyAsync(
                () -> synthesize(text, voice),
                VoiceSynthesisExecutor.getInstance());
    }
}
