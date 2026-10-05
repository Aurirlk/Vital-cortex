package cn.kmbeast.core.voice;

import cn.kmbeast.core.voice.VoiceResult.ErrorType;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Whisper 语音识别供应商（OpenAI 兼容协议）
 *
 * <p>走 OpenAI 的 {@code POST /v1/audio/transcriptions}（form-data）接口。由于协议是 OpenAI 兼容的，
 * 除 OpenAI 官方外，任何兼容端点（Azure OpenAI、通义千问兼容模式、本地 vLLM/FunASR OpenAI 插件等）
 * 只需在管理端把 {@code asr.baseUrl} 改成对应地址即可复用。
 *
 * <p>与 {@link FunASRProvider}（DashScope 异步提交+轮询）不同，Whisper 是<strong>同步</strong>返回结果，
 * 单次请求直接拿到文本，更适合短语音输入场景。
 *
 * <p>注意：OpenAI 的 language 参数只接受 ISO-639-1 单语言码（zh），不接受 zh-CN，故需裁剪。
 */
@Slf4j
@Component
public class WhisperASRProvider {

    /** 默认 OpenAI 兼容端点（端点内部会补 /audio/transcriptions） */
    private static final String DEFAULT_BASE_URL = "https://api.openai.com/v1";

    /** Whisper 同步接口最大音频：24MB */
    private static final int MAX_AUDIO_SIZE = 24 * 1024 * 1024;

    private static final String TIMEOUT_MESSAGE = "语音识别超时，请缩短音频或稍后重试";

    /** 扩展名 → MIME */
    private static final Map<String, String> MIME_MAP = new LinkedHashMap<>();
    static {
        MIME_MAP.put("wav", "audio/wav");
        MIME_MAP.put("mp3", "audio/mpeg");
        MIME_MAP.put("pcm", "audio/pcm");
        MIME_MAP.put("ogg", "audio/ogg");
        MIME_MAP.put("opus", "audio/ogg");
        MIME_MAP.put("flac", "audio/flac");
        MIME_MAP.put("m4a", "audio/mp4");
        MIME_MAP.put("aac", "audio/aac");
        MIME_MAP.put("webm", "audio/webm");
    }

    @Autowired
    private VoiceConfigHolder voiceConfigHolder;

    private final OkHttpClient httpClient;

    public WhisperASRProvider() {
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(120, TimeUnit.SECONDS)
                .build();
    }

    /**
     * 识别音频
     *
     * @param audioData 音频字节数据
     * @param format    音频扩展名（wav/mp3/...），仅用于探测 MIME
     * @return 识别文本
     */
    public VoiceResult<String> recognize(byte[] audioData, String format) {
        if (audioData == null || audioData.length == 0) {
            return VoiceResult.invalidInput("音频数据为空");
        }
        if (audioData.length > MAX_AUDIO_SIZE) {
            return VoiceResult.invalidInput("音频文件超过 24MB 限制");
        }

        String apiKey = voiceConfigHolder.getAsrApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            return VoiceResult.apiKeyMissing();
        }

        String baseUrl = resolveBaseUrl();
        String model = voiceConfigHolder.getAsrModel();
        if (model == null || model.isBlank()) {
            model = "whisper-1";
        }
        String language = normalizeLanguage(voiceConfigHolder.getAsrLanguage());

        String mime = MIME_MAP.getOrDefault(normalizeFormat(format), "audio/wav");
        String extension = normalizeFormat(format);

        RequestBody fileBody = RequestBody.create(audioData, MediaType.parse(mime));
        MultipartBody multipart = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", "sample." + extension, fileBody)
                .addFormDataPart("model", model)
                .addFormDataPart("language", language)
                .addFormDataPart("response_format", "json")
                .addFormDataPart("temperature", "0")
                .build();

        Request request = new Request.Builder()
                .url(baseUrl + "/audio/transcriptions")
                .addHeader("Authorization", "Bearer " + apiKey)
                .post(multipart)
                .build();

        long start = System.currentTimeMillis();
        try (Response response = httpClient.newCall(request).execute()) {
            String body = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                log.warn("[WhisperASR] 识别失败 HTTP={}, body={}", response.code(), truncate(body));
                return VoiceResult.error(ErrorType.SERVICE_UNAVAILABLE,
                        "Whisper 识别失败（HTTP " + response.code() + "）: " + truncate(body));
            }
            JSONObject json = JSON.parseObject(body);
            String text = json != null ? json.getString("text") : null;
            if (text == null) {
                return VoiceResult.error(ErrorType.UNKNOWN, "Whisper 返回格式异常: " + truncate(body));
            }
            log.info("[WhisperASR] 识别完成，耗时={}ms，文本长度={}",
                    System.currentTimeMillis() - start, text.length());
            return VoiceResult.ok(text.trim());
        } catch (IOException e) {
            // SocketTimeoutException 是 IOException 子类，此处统一归类为超时
            log.warn("[WhisperASR] 请求异常: {}", e.getMessage());
            if (String.valueOf(e.getMessage()).toLowerCase().contains("timeout")) {
                return VoiceResult.timeout();
            }
            return VoiceResult.serviceUnavailable("无法连接 Whisper 服务: " + e.getMessage());
        }
    }

    /**
     * 取 baseUrl：{@code asr.baseUrl} 优先，回落 {@code asr.apiUrl}（兼容旧配置直接填完整端点），
     * 都为空时用官方端点。容忍前后多余斜杠，并剔除已写死的 /audio/transcriptions 后缀避免重复拼接。
     */
    private String resolveBaseUrl() {
        String baseUrl = voiceConfigHolder.getAsrBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = voiceConfigHolder.getAsrApiUrl();
        }
        if (baseUrl == null || baseUrl.isBlank()) {
            return DEFAULT_BASE_URL;
        }
        baseUrl = baseUrl.trim().replaceAll("/+$", "");
        return baseUrl.replaceFirst("/audio/transcriptions$", "");
    }

    /** OpenAI language 仅接受单语言码，zh-CN → zh */
    private String normalizeLanguage(String language) {
        if (language == null || language.isBlank()) {
            return "zh";
        }
        String code = language.trim().split("[-_]")[0].toLowerCase();
        return code.isEmpty() ? "zh" : code;
    }

    private String normalizeFormat(String format) {
        if (format == null || format.isBlank()) {
            return "wav";
        }
        return format.trim().toLowerCase();
    }

    private String truncate(String s) {
        if (s == null) return "";
        return s.length() > 300 ? s.substring(0, 300) + "..." : s;
    }
}
