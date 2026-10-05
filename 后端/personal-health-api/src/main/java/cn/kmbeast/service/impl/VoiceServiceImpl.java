package cn.kmbeast.service.impl;

import cn.kmbeast.core.voice.VoiceSynthesisExecutor;
import cn.kmbeast.core.voice.ASRFactory;
import cn.kmbeast.core.voice.TTSFactory;
import cn.kmbeast.core.voice.VoiceConfigHolder;
import cn.kmbeast.core.voice.VoiceResult;
import cn.kmbeast.service.VoiceService;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 语音服务实现
 *
 * <p>配置统一由 {@link VoiceConfigHolder} 管理（动态、可持久化），
 * 本类不再持有 {@code @Value} 启动期配置。
 */
@Slf4j
@Service
public class VoiceServiceImpl implements VoiceService {

    @Resource
    private ASRFactory asrFactory;

    @Resource
    private TTSFactory ttsFactory;

    @Autowired
    private VoiceConfigHolder voiceConfigHolder;

    @Override
    public VoiceResult<String> recognize(byte[] audioData, String format) {
        if (audioData == null || audioData.length == 0) {
            return VoiceResult.invalidInput("音频数据为空");
        }

        log.info("[VoiceService] 开始语音识别，format={}, size={} bytes", format, audioData.length);
        return asrFactory.recognize(audioData, format);
    }

    @Override
    public VoiceResult<byte[]> synthesize(String text, String voice) {
        if (text == null || text.isEmpty()) {
            return VoiceResult.invalidInput("文本为空");
        }

        log.info("[VoiceService] 开始语音合成，textLength={}, voice={}", text.length(), voice);
        return ttsFactory.synthesize(text, voice);
    }

    @Override
    public CompletableFuture<VoiceResult<byte[]>> synthesizeAsync(String text, String voice) {
        // 2026-10-03 修复：原实现未传 Executor，会落到 ForkJoinPool.commonPool()。
        // 语音合成是阻塞式网络调用（数秒），并发上来会占满 commonPool 并拖垮其他 ForkJoin 业务。
        // 现指定语音合成专用线程池（有界队列 + CallerRuns 降级），见 VoiceSynthesisExecutor。
        return CompletableFuture.supplyAsync(
                () -> synthesize(text, voice),
                VoiceSynthesisExecutor.getInstance());
    }

    @Override
    public Map<String, Object> getVoiceConfig() {
        JSONObject cfg = voiceConfigHolder.getConfig();
        JSONObject out = new JSONObject();

        JSONObject asr = new JSONObject(cfg.getJSONObject("asr"));
        asr.put("apiKey", maskApiKey(asr.getString("apiKey")));
        out.put("asr", asr);

        JSONObject tts = new JSONObject(cfg.getJSONObject("tts"));
        tts.put("apiKey", maskApiKey(tts.getString("apiKey")));
        out.put("tts", tts);

        if (cfg.containsKey("vad")) {
            out.put("vad", cfg.getJSONObject("vad"));
        }

        // 能力状态
        out.put("asrEnabled", !voiceConfigHolder.getAsrApiKey().isEmpty());
        out.put("ttsEnabled", true); // Edge TTS 总是可用

        return out;
    }

    @Override
    public void updateVoiceConfig(Map<String, Object> config) {
        // 完整树转换，确保嵌套 Map 也转为 JSONObject（否则后续 getJSONObject 取到 null）
        JSONObject incoming = JSONObject.parseObject(JSONObject.toJSONString(config));
        JSONObject current = voiceConfigHolder.getConfig();

        // 脱敏保护：若前端回传的 apiKey 为空或仍是脱敏串（含 *），保留真实值，避免被覆盖成无效 key
        preserveRealKey(incoming, current, "asr");
        preserveRealKey(incoming, current, "tts");

        voiceConfigHolder.save(incoming);
        log.info("[VoiceService] 语音配置已更新并持久化");
    }

    private void preserveRealKey(JSONObject incoming, JSONObject current, String section) {
        JSONObject inSec = incoming.getJSONObject(section);
        if (inSec == null) {
            return;
        }
        String key = inSec.getString("apiKey");
        if (key == null || key.isEmpty() || key.contains("*")) {
            JSONObject curSec = current.getJSONObject(section);
            if (curSec != null) {
                inSec.put("apiKey", curSec.getString("apiKey"));
            }
        }
    }

    @Override
    public Map<String, String> getAvailableVoices() {
        Map<String, String> voices = new LinkedHashMap<>();

        // 中文语音
        voices.put("zh-CN-XiaoxiaoNeural", "晓晓（女声，通用）");
        voices.put("zh-CN-YunxiNeural", "云希（男声，通用）");
        voices.put("zh-CN-YunjianNeural", "云健（男声，新闻）");
        voices.put("zh-CN-XiaoyiNeural", "晓伊（女声，童声）");
        voices.put("zh-CN-YunyangNeural", "云扬（男声，新闻）");

        // 英文语音
        voices.put("en-US-JennyNeural", "Jenny (Female, General)");
        voices.put("en-US-GuyNeural", "Guy (Male, General)");
        voices.put("en-US-AriaNeural", "Aria (Female, General)");

        return voices;
    }

    /**
     * 遮蔽 API Key
     */
    private String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.isEmpty()) {
            return "";
        }
        if (apiKey.length() <= 11) {
            return "****";
        }
        return apiKey.substring(0, 7) + "****" + apiKey.substring(apiKey.length() - 4);
    }
}
