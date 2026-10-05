package cn.kmbeast.controller;

import cn.kmbeast.aop.Protector;
import cn.kmbeast.core.voice.VoiceResult;
import cn.kmbeast.pojo.api.ApiResult;
import cn.kmbeast.pojo.api.Result;
import cn.kmbeast.service.VoiceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.alibaba.fastjson2.JSONObject;
import jakarta.annotation.Resource;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 语音控制器
 * 
 * 提供 ASR（语音识别）和 TTS（语音合成）接口
 */
@Slf4j
@RestController
@RequestMapping("/voice")
public class VoiceController {

    /** ASR 最大音频文件大小：10MB */
    private static final long MAX_ASR_AUDIO_SIZE = 10 * 1024 * 1024;

    /** TTS 最大文本长度：5000字符 */
    private static final int MAX_TTS_TEXT_LENGTH = 5000;

    @Resource
    private VoiceService voiceService;

    /**
     * 语音识别（ASR）
     * 
     * @param file 音频文件（支持 wav, mp3, pcm 等格式）
     * @return 识别出的文本
     */
    @Protector
    @PostMapping("/asr")
    public Result<String> recognize(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ApiResult.error("音频文件不能为空");
        }

        // 文件大小校验
        if (file.getSize() > MAX_ASR_AUDIO_SIZE) {
            return ApiResult.error("音频文件超过10MB限制");
        }

        try {
            String format = getAudioFormat(file.getOriginalFilename());
            VoiceResult<String> result = voiceService.recognize(file.getBytes(), format);
            
            if (!result.isSuccess()) {
                return ApiResult.error(result.getErrorMessage());
            }
            
            return ApiResult.success(result.getData());
        } catch (IOException e) {
            log.error("[VoiceController] 读取音频文件失败", e);
            return ApiResult.error("读取音频文件失败: " + e.getMessage());
        }
    }

    /**
     * 语音合成（TTS）
     * 
     * @param text 要合成的文本
     * @param voice 语音名称（可选，默认使用配置的语音）
     * @return 音频数据（mp3 格式）
     */
    @Protector
    @PostMapping("/tts")
    public ResponseEntity<?> synthesize(
            @RequestParam("text") String text,
            @RequestParam(value = "voice", required = false) String voice) {
        
        if (text == null || text.isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResult.error("文本不能为空"));
        }

        // 文本长度校验
        if (text.length() > MAX_TTS_TEXT_LENGTH) {
            return ResponseEntity.badRequest().body(ApiResult.error("文本超过" + MAX_TTS_TEXT_LENGTH + "字符限制"));
        }

        VoiceResult<byte[]> result = voiceService.synthesize(text, voice);
        
        if (!result.isSuccess()) {
            return ResponseEntity.internalServerError().body(ApiResult.error(result.getErrorMessage()));
        }

        byte[] audioData = result.getData();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("audio/mpeg"));
        headers.setContentLength(audioData.length);
        headers.set("Content-Disposition", "inline; filename=tts_output.mp3");

        return ResponseEntity.ok()
            .headers(headers)
            .body(audioData);
    }

    /**
     * 获取语音配置
     * 
     * @return 语音配置信息
     */
    @Protector(role = "管理员")
    @GetMapping("/config")
    public Result<Map<String, Object>> getVoiceConfig() {
        return ApiResult.success(voiceService.getVoiceConfig());
    }

    /**
     * 更新语音配置
     * 
     * @param config 配置信息
     * @return 操作结果
     */
    @Protector(role = "管理员")
    @PostMapping("/config")
    public Result<Void> updateVoiceConfig(@RequestBody Map<String, Object> config) {
        try {
            voiceService.updateVoiceConfig(config);
            return ApiResult.success("语音配置更新成功");
        } catch (Exception e) {
            log.error("[VoiceController] 更新语音配置失败", e);
            return ApiResult.error("更新语音配置失败: " + e.getMessage());
        }
    }

    /**
     * 获取可用的 TTS 语音列表
     * 
     * @return 语音列表
     */
    @Protector
    @GetMapping("/voices")
    public Result<Map<String, String>> getAvailableVoices() {
        return ApiResult.success(voiceService.getAvailableVoices());
    }

    /**
     * 语音链路连通性自测（管理员专用）
     *
     * <p>用途：管理端「系统配置 → 语音」保存配置后，一键验证服务端到 TTS/ASR 供应商是否真的出网
     * 可用。因为服务端网络（公司防火墙／国际出口）往往拦掉微软／OpenAI 域名，前端调用必然失败，
     * 必须能在服务端侧独立探测。
     *
     * @param type "tts" 只测合成；"asr"（默认/其他）只测识别
     * @return {success, provider, durationMs, audioBytes|text|message}
     */
    @Protector(role = "管理员")
    @PostMapping("/test")
    public Result<Map<String, Object>> testConnection(
            @RequestParam(value = "type", defaultValue = "asr") String type) {

        Map<String, Object> report = new LinkedHashMap<>();
        long start = System.currentTimeMillis();
        try {
            if ("tts".equalsIgnoreCase(type)) {
                VoiceResult<byte[]> result = voiceService.synthesize("语音连通性测试", null);
                report.put("success", result.isSuccess());
                report.put("durationMs", System.currentTimeMillis() - start);
                report.put("type", "tts");
                if (result.isSuccess()) {
                    report.put("provider", resolveVoiceProvider("tts"));
                    report.put("audioBytes", result.getData() != null ? result.getData().length : 0);
                    report.put("message", "TTS 合成成功，音频已可下发前端播放");
                } else {
                    report.put("message", result.getErrorMessage());
                }
            } else {
                // 用 200ms 静音 WAV 做探测：能拿到结果即证明服务端已连通对应 ASR 供应商
                byte[] probe = buildSilentWav(200);
                VoiceResult<String> result = voiceService.recognize(probe, "wav");
                report.put("success", result.isSuccess());
                report.put("durationMs", System.currentTimeMillis() - start);
                report.put("type", "asr");
                if (result.isSuccess()) {
                    report.put("provider", resolveVoiceProvider("asr"));
                    report.put("text", result.getData());
                    report.put("message", "ASR 链路连通（静音样本，text 为空属正常）");
                } else {
                    report.put("provider", resolveVoiceProvider("asr"));
                    report.put("message", result.getErrorMessage());
                }
            }
            return ApiResult.success(report);
        } catch (Exception e) {
            log.error("[VoiceController] 语音连通性自测失败, type={}", type, e);
            report.put("success", false);
            report.put("durationMs", System.currentTimeMillis() - start);
            report.put("message", "自测异常: " + e.getMessage());
            return ApiResult.success(report);
        }
    }

    /** 从当前配置读出生效的供应商名（供自测报告展示） */
    private String resolveVoiceProvider(String section) {
        try {
            Map<String, Object> config = voiceService.getVoiceConfig();
            if (config != null) {
                Object node = config.get(section);
                String provider = null;
                if (node instanceof JSONObject) {
                    provider = ((JSONObject) node).getString("provider");
                } else if (node instanceof Map) {
                    provider = (String) ((Map<?, ?>) node).get("provider");
                }
                if (provider != null && !provider.isEmpty()) {
                    return provider;
                }
            }
        } catch (Exception e) {
            log.debug("[VoiceController] 读取 {} provider 失败: {}", section, e.getMessage());
        }
        return "tts".equals(section) ? "edgetts" : "funasr";
    }

    /**
     * 构造最小静音 WAV（16bit / 16kHz / 单声道），仅用于 ASR 连通性探测。
     */
    private static byte[] buildSilentWav(int durationMs) throws IOException {
        final int sampleRate = 16000;
        final int channels = 1;
        final int bits = 16;
        final int byteRate = sampleRate * channels * bits / 8;
        final int dataSize = byteRate * durationMs / 1000;

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeLe32(out, 36 + dataSize);          // RIFF chunk size
        out.write("WAVE".getBytes(StandardCharsets.US_ASCII));
        out.write("fmt ".getBytes(StandardCharsets.US_ASCII));
        writeLe32(out, 16);                     // fmt chunk size
        writeLe16(out, 1);                      // audioFormat = PCM
        writeLe16(out, channels);
        writeLe32(out, sampleRate);
        writeLe32(out, byteRate);
        writeLe16(out, channels * bits / 8);    // blockAlign
        writeLe16(out, bits);
        out.write("data".getBytes(StandardCharsets.US_ASCII));
        writeLe32(out, dataSize);
        out.write(new byte[dataSize]);          // 静音负载
        return out.toByteArray();
    }

    private static void writeLe8(ByteArrayOutputStream out, int v) {
        out.write(v & 0xFF);
    }

    private static void writeLe16(ByteArrayOutputStream out, int v) {
        writeLe8(out, v & 0xFF);
        writeLe8(out, (v >> 8) & 0xFF);
    }

    private static void writeLe32(ByteArrayOutputStream out, int v) {
        writeLe16(out, v & 0xFFFF);
        writeLe16(out, (v >> 16) & 0xFFFF);
    }

    /**
     * 获取音频格式
     */
    private String getAudioFormat(String filename) {
        if (filename == null) {
            return "wav";
        }
        
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex > 0) {
            return filename.substring(dotIndex + 1).toLowerCase();
        }
        
        return "wav";
    }
}
