package cn.kmbeast.core.voice;

import lombok.Data;

/**
 * 语音操作结果类型
 * 
 * @param <T> 结果类型（String for ASR, byte[] for TTS）
 */
@Data
public class VoiceResult<T> {

    private final boolean success;
    private final T data;
    private final String errorMessage;
    private final ErrorType errorType;

    public enum ErrorType {
        NONE,
        API_KEY_MISSING,
        INVALID_INPUT,
        SERVICE_UNAVAILABLE,
        TIMEOUT,
        QUOTA_EXCEEDED,
        UNKNOWN
    }

    private VoiceResult(boolean success, T data, String errorMessage, ErrorType errorType) {
        this.success = success;
        this.data = data;
        this.errorMessage = errorMessage;
        this.errorType = errorType;
    }

    /**
     * 创建成功结果
     */
    public static <T> VoiceResult<T> ok(T data) {
        return new VoiceResult<>(true, data, null, ErrorType.NONE);
    }

    /**
     * 创建错误结果
     */
    public static <T> VoiceResult<T> error(ErrorType type, String message) {
        return new VoiceResult<>(false, null, message, type);
    }

    /**
     * 创建 API Key 缺失错误
     */
    public static <T> VoiceResult<T> apiKeyMissing() {
        return new VoiceResult<>(false, null, "API Key 未配置", ErrorType.API_KEY_MISSING);
    }

    /**
     * 创建输入无效错误
     */
    public static <T> VoiceResult<T> invalidInput(String message) {
        return new VoiceResult<>(false, null, message, ErrorType.INVALID_INPUT);
    }

    /**
     * 创建超时错误
     */
    public static <T> VoiceResult<T> timeout() {
        return new VoiceResult<>(false, null, "操作超时", ErrorType.TIMEOUT);
    }

    /**
     * 创建服务不可用错误
     */
    public static <T> VoiceResult<T> serviceUnavailable(String message) {
        return new VoiceResult<>(false, null, message, ErrorType.SERVICE_UNAVAILABLE);
    }

    /**
     * 是否为 API Key 缺失
     */
    public boolean isApiKeyMissing() {
        return errorType == ErrorType.API_KEY_MISSING;
    }

    /**
     * 是否为超时
     */
    public boolean isTimeout() {
        return errorType == ErrorType.TIMEOUT;
    }
}
