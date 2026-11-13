package cn.kmbeast.service;

import cn.kmbeast.core.voice.VoiceResult;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 语音服务接口
 */
public interface VoiceService {

    /**
     * 语音识别（ASR）
     * 
     * @param audioData 音频字节数据
     * @param format 音频格式（wav, mp3, pcm 等）
     * @return 识别结果
     */
    VoiceResult<String> recognize(byte[] audioData, String format);

    /**
     * 语音合成（TTS）
     * 
     * @param text 要合成的文本
     * @param voice 语音名称（如 zh-CN-XiaoxiaoNeural）
     * @return 合成结果
     */
    VoiceResult<byte[]> synthesize(String text, String voice);

    /**
     * 语音合成（异步）
     * 
     * @param text 要合成的文本
     * @param voice 语音名称
     * @return CompletableFuture
     */
    CompletableFuture<VoiceResult<byte[]>> synthesizeAsync(String text, String voice);

    /**
     * 获取语音配置
     * 
     * @return 语音配置信息
     */
    Map<String, Object> getVoiceConfig();

    /**
     * 更新语音配置
     * 
     * @param config 配置信息
     */
    void updateVoiceConfig(Map<String, Object> config);

    /**
     * 获取可用的 TTS 语音列表
     * 
     * @return 语音列表
     */
    Map<String, String> getAvailableVoices();
}
