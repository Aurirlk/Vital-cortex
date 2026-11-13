package cn.kmbeast.core.voice;

import cn.kmbeast.core.voice.VoiceConfigHolder;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * FunASR 语音识别供应商（通过 DashScope API）
 *
 * 支持阿里云 DashScope 的语音识别服务，使用 Paraformer 模型
 * 采用异步提交 + 轮询结果模式，支持较长音频文件
 *
 * 文档：https://help.aliyun.com/zh/dashscope/developer-reference/api-details-13
 */
@Slf4j
@Component
public class FunASRProvider {

    private static final String DASHSCOPE_ASR_URL =
        "https://dashscope.aliyuncs.com/api/v1/services/audio/asr/recognition";

    private static final String DASHSCOPE_TASK_URL =
        "https://dashscope.aliyuncs.com/api/v1/tasks";

    /** 最大音频文件大小：10MB */
    private static final int MAX_AUDIO_SIZE = 10 * 1024 * 1024;

    /** 轮询间隔（毫秒） */
    private static final long POLL_INTERVAL_MS = 1000;

    /** 最大轮询次数（30秒） */
    private static final int MAX_POLL_ATTEMPTS = 30;

    @Autowired
    private VoiceConfigHolder voiceConfigHolder;

    private final OkHttpClient httpClient;

    /** MIME 类型映射 */
    private static final Map<String, String> MIME_MAP = new LinkedHashMap<>();
    static {
        MIME_MAP.put("wav", "audio/wav");
        MIME_MAP.put("mp3", "audio/mpeg");
        MIME_MAP.put("pcm", "audio/pcm");
        MIME_MAP.put("ogg", "audio/ogg");
        MIME_MAP.put("flac", "audio/flac");
        MIME_MAP.put("m4a", "audio/mp4");
        MIME_MAP.put("aac", "audio/aac");
    }

    public FunASRProvider() {
        this.httpClient = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build();
    }

    /**
     * 识别音频数据（异步提交 + 轮询结果）
     * 
     * @param audioData 音频字节数据
     * @param format 音频格式（wav, mp3, pcm 等）
     * @return 识别结果
     */
    public VoiceResult<String> recognize(byte[] audioData, String format) {
        // 参数校验
        if (voiceConfigHolder.getAsrApiKey() == null || voiceConfigHolder.getAsrApiKey().isEmpty()) {
            return VoiceResult.apiKeyMissing();
        }

        if (audioData == null || audioData.length == 0) {
            return VoiceResult.invalidInput("音频数据为空");
        }

        if (audioData.length > MAX_AUDIO_SIZE) {
            return VoiceResult.invalidInput("音频文件超过10MB限制");
        }

        try {
            // 1. 提交异步任务
            String taskId = submitTask(audioData, format);
            if (taskId == null) {
                return VoiceResult.serviceUnavailable("提交识别任务失败");
            }

            log.info("[FunASR] 任务已提交: taskId={}", taskId);

            // 2. 轮询等待结果
            return pollResult(taskId);
        } catch (IOException e) {
            log.error("[FunASR] 语音识别异常", e);
            return VoiceResult.serviceUnavailable("网络异常: " + e.getMessage());
        }
    }

    /**
     * 提交异步识别任务
     * 
     * @return 任务ID，失败返回 null
     */
    private String submitTask(byte[] audioData, String format) throws IOException {
        String audioBase64 = Base64.getEncoder().encodeToString(audioData);
        String mimeType = MIME_MAP.getOrDefault(format, "audio/wav");
        String requestJson = buildRequestJson(audioBase64, mimeType);

        Request request = new Request.Builder()
            .url(DASHSCOPE_ASR_URL)
            .addHeader("Authorization", "Bearer " + voiceConfigHolder.getAsrApiKey())
            .addHeader("Content-Type", "application/json")
            .addHeader("X-DashScope-Async", "enable")
            .post(RequestBody.create(requestJson, MediaType.parse("application/json")))
            .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                log.error("[FunASR] 提交任务失败: {} {}", response.code(), response.message());
                return null;
            }

            String responseBody = response.body() != null ? response.body().string() : "";
            JSONObject json = JSONObject.parseObject(responseBody);
            
            // 检查响应格式
            if (json.containsKey("output") && json.getJSONObject("output").containsKey("task_id")) {
                return json.getJSONObject("output").getString("task_id");
            }
            
            log.error("[FunASR] 响应格式异常: {}", responseBody);
            return null;
        }
    }

    /**
     * 轮询任务结果
     */
    private VoiceResult<String> pollResult(String taskId) throws IOException {
        String taskUrl = DASHSCOPE_TASK_URL + "/" + taskId;
        
        for (int i = 0; i < MAX_POLL_ATTEMPTS; i++) {
            try {
                Thread.sleep(POLL_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return VoiceResult.timeout();
            }

            Request request = new Request.Builder()
                .url(taskUrl)
                .addHeader("Authorization", "Bearer " + voiceConfigHolder.getAsrApiKey())
                .get()
                .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    log.error("[FunASR] 轮询失败: {} {}", response.code(), response.message());
                    continue;
                }

                String responseBody = response.body() != null ? response.body().string() : "";
                JSONObject json = JSONObject.parseObject(responseBody);
                
                if (!json.containsKey("output")) {
                    log.warn("[FunASR] 轮询响应无 output 字段: {}", responseBody);
                    continue;
                }

                JSONObject output = json.getJSONObject("output");
                String taskStatus = output.getString("task_status");
                
                if ("SUCCEEDED".equals(taskStatus)) {
                    // 任务成功，提取结果
                    String text = extractText(output);
                    return VoiceResult.ok(text);
                } else if ("FAILED".equals(taskStatus)) {
                    String message = output.getString("message");
                    return VoiceResult.serviceUnavailable("识别失败: " + message);
                }
                
                // 任务仍在处理中，继续轮询
                log.debug("[FunASR] 任务处理中: status={}, attempt={}", taskStatus, i + 1);
            }
        }

        return VoiceResult.timeout();
    }

    /**
     * 从输出中提取识别文本
     */
    private String extractText(JSONObject output) {
        // DashScope 返回格式：output.results[].transcription
        if (output.containsKey("results")) {
            var results = output.getJSONArray("results");
            if (results != null && !results.isEmpty()) {
                StringBuilder text = new StringBuilder();
                for (int i = 0; i < results.size(); i++) {
                    JSONObject result = results.getJSONObject(i);
                    if (result.containsKey("transcription")) {
                        if (text.length() > 0) text.append(" ");
                        text.append(result.getString("transcription"));
                    }
                }
                return text.toString();
            }
        }
        
        // 备用：直接提取 text 字段
        if (output.containsKey("text")) {
            return output.getString("text");
        }
        
        return "";
    }

    /**
     * 构建请求 JSON
     */
    private String buildRequestJson(String audioBase64, String mimeType) {
        return String.format("""
            {
                "model": "%s",
                "input": {
                    "audio": "data:%s;base64,%s"
                },
                "parameters": {
                    "language_hints": ["%s"]
                }
            }
            """, voiceConfigHolder.getAsrModel(), mimeType, audioBase64, voiceConfigHolder.getAsrLanguage());
    }
}
