package cn.kmbeast.core.voice;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * 语音配置中心（单例 Bean）。
 *
 * <p>职责：
 * <ol>
 *   <li>作为语音配置的唯一可变数据源（替代原先散落在各 Provider/Service 的 {@code @Value} 启动注入）；</li>
 *   <li>启动时从 {@code ./config/voice-config.json} 加载，缺失则用内置默认值；</li>
 *   <li>{@link #save(JSONObject)} 写回同一文件，保证重启不丢（与 AiPromptConfig 的 prompts.json 同理）。</li>
 * </ol>
 *
 * <p>字段命名与前端 {@code SystemConfigManage.vue} 的 {@code voiceConfig} 保持一致：
 * TTS 用 {@code speed}（而非 rate），ASR 用 {@code apiKey/apiUrl/model/language}。
 */
@Slf4j
@Component
public class VoiceConfigHolder {

    /** 配置文件路径：系统属性 voice.config.file 或 ./config/voice-config.json */
    private static final File CONFIG_FILE = new File(
            System.getProperty("voice.config.file", "./config/voice-config.json"));

    /** 内存中的实时配置（root 含 asr / tts / vad 三个子对象） */
    private JSONObject root;

    @PostConstruct
    public void init() {
        if (CONFIG_FILE.exists()) {
            try {
                String json = new String(Files.readAllBytes(CONFIG_FILE.toPath()), StandardCharsets.UTF_8);
                JSONObject loaded = JSON.parseObject(json);
                if (loaded != null && loaded.getJSONObject("asr") != null && loaded.getJSONObject("tts") != null) {
                    this.root = loaded;
                    log.info("[VoiceConfig] 已从外部文件加载语音配置: {}", CONFIG_FILE.getAbsolutePath());
                    return;
                }
                log.warn("[VoiceConfig] 语音配置文件格式不正确，使用内置默认: {}", CONFIG_FILE.getAbsolutePath());
            } catch (Exception e) {
                log.warn("[VoiceConfig] 语音配置加载失败，使用内置默认: {}", e.getMessage());
            }
        }
        this.root = defaultConfig();
        log.info("[VoiceConfig] 使用内置默认语音配置");
    }

    /** 返回当前配置的只读视图（用于 GET /voice/config） */
    public JSONObject getConfig() {
        return root;
    }

    /**
     * 持久化配置（用于 POST /voice/config）。
     * 仅接受 asr/tts（含 vad 透传），缺字段以当前值补齐。
     */
    public synchronized void save(JSONObject incoming) {
        JSONObject merged = defaultConfig();
        if (incoming != null) {
            mergeSection(merged, incoming, "asr");
            mergeSection(merged, incoming, "tts");
            mergeSection(merged, incoming, "vad");
        }
        this.root = merged;
        persistToFile(merged);
    }

    private void mergeSection(JSONObject target, JSONObject incoming, String key) {
        JSONObject src = incoming.getJSONObject(key);
        if (src != null) {
            target.put(key, src);
        }
    }

    private void persistToFile(JSONObject config) {
        try {
            File parent = CONFIG_FILE.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            JSONObject out = new JSONObject();
            out.put("version", "1");
            out.put("updatedAt", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
            out.put("asr", config.getJSONObject("asr"));
            out.put("tts", config.getJSONObject("tts"));
            if (config.containsKey("vad")) {
                out.put("vad", config.getJSONObject("vad"));
            }
            Files.write(CONFIG_FILE.toPath(),
                    JSON.toJSONString(out, com.alibaba.fastjson2.JSONWriter.Feature.PrettyFormat)
                            .getBytes(StandardCharsets.UTF_8));
            log.info("[VoiceConfig] 语音配置已写回: {}", CONFIG_FILE.getAbsolutePath());
        } catch (Exception e) {
            log.error("[VoiceConfig] 语音配置写回失败: {}", e.getMessage(), e);
        }
    }

    // ============ ASR 访问器 ============
    public String getAsrProvider() {
        return str(root.getJSONObject("asr"), "provider", "funasr");
    }

    public String getAsrApiKey() {
        return str(root.getJSONObject("asr"), "apiKey", "");
    }

    public String getAsrApiUrl() {
        return str(root.getJSONObject("asr"), "apiUrl",
                "https://dashscope.aliyuncs.com/api/v1/services/audio/asr/recognition");
    }

    public String getAsrModel() {
        return str(root.getJSONObject("asr"), "model", "paraformer-zh");
    }

    /**
     * OpenAI 兼容 ASR 端点基址（仅 whisper 使用）。
     * 为空时 {@link WhisperASRProvider} 回退官方 https://api.openai.com/v1。
     */
    public String getAsrBaseUrl() {
        return str(root.getJSONObject("asr"), "baseUrl", "");
    }

    public String getAsrLanguage() {
        return str(root.getJSONObject("asr"), "language", "zh-CN");
    }

    // ============ TTS 访问器 ============
    public String getTtsProvider() {
        return str(root.getJSONObject("tts"), "provider", "edgetts");
    }

    public String getTtsApiKey() {
        return str(root.getJSONObject("tts"), "apiKey", "");
    }

    public String getTtsApiUrl() {
        return str(root.getJSONObject("tts"), "apiUrl", "");
    }

    public String getTtsVoice() {
        return str(root.getJSONObject("tts"), "voice", "zh-CN-XiaoxiaoNeural");
    }

    /** 语速倍率，对应前端 tts.speed（0.5~2.0，1.0 为正常） */
    public double getTtsSpeed() {
        return dbl(root.getJSONObject("tts"), "speed", 1.0);
    }

    public int getTtsVolume() {
        return intv(root.getJSONObject("tts"), "volume", 100);
    }

    public String getTtsFormat() {
        return str(root.getJSONObject("tts"), "format", "mp3");
    }

    // ============ 工具 ============
    private static String str(JSONObject obj, String key, String def) {
        if (obj == null) return def;
        String v = obj.getString(key);
        return v != null ? v : def;
    }

    private static double dbl(JSONObject obj, String key, double def) {
        if (obj == null || !obj.containsKey(key)) return def;
        Object v = obj.get(key);
        if (v instanceof Number) return ((Number) v).doubleValue();
        try {
            return Double.parseDouble(String.valueOf(v));
        } catch (Exception e) {
            return def;
        }
    }

    private static int intv(JSONObject obj, String key, int def) {
        if (obj == null || !obj.containsKey(key)) return def;
        Object v = obj.get(key);
        if (v instanceof Number) return ((Number) v).intValue();
        try {
            return Integer.parseInt(String.valueOf(v));
        } catch (Exception e) {
            return def;
        }
    }

    private static JSONObject defaultConfig() {
        JSONObject asr = new JSONObject();
        asr.put("provider", "funasr");
        asr.put("apiKey", "");
        asr.put("apiUrl", "https://dashscope.aliyuncs.com/api/v1/services/audio/asr/recognition");
        asr.put("model", "paraformer-zh");
        asr.put("language", "zh-CN");
        asr.put("timeout", 30000);

        JSONObject tts = new JSONObject();
        tts.put("provider", "edgetts");
        tts.put("apiKey", "");
        tts.put("apiUrl", "");
        tts.put("voice", "zh-CN-XiaoxiaoNeural");
        tts.put("speed", 1.0);
        tts.put("volume", 100);
        tts.put("format", "mp3");
        tts.put("timeout", 60000);

        JSONObject vad = new JSONObject();
        vad.put("enabled", true);
        vad.put("sensitivity", 0.5);

        JSONObject root = new JSONObject();
        root.put("asr", asr);
        root.put("tts", tts);
        root.put("vad", vad);
        return root;
    }
}
