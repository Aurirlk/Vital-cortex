package cn.kmbeast.crm;

public class CrmException extends RuntimeException {

    private final String errorCode;

    public CrmException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public CrmException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() { return errorCode; }

    public static CrmException aiUnavailable(String detail) {
        return new CrmException("AI_UNAVAILABLE", "AI服务不可用: " + detail);
    }

    public static CrmException embeddingFailed(String detail) {
        return new CrmException("EMBEDDING_FAILED", "向量生成失败: " + detail);
    }

    public static CrmException toolTimeout(String toolName) {
        return new CrmException("TOOL_TIMEOUT", "工具执行超时: " + toolName);
    }

    public static CrmException sqlNotAllowed() {
        return new CrmException("SQL_DENIED", "仅允许只读查询");
    }
}
