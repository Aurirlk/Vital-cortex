package cn.kmbeast.crm;

public class CrmException extends RuntimeException {

    private final String errorCode;

    /**
     * 2026-10-07：LLM 调用失败时携带的 HTTP 状态码（可为 null）。
     *
     * <p><b>为什么加这个字段</b>：此前 {@code BaseReActAgent.isRetryableStatus()} 靠
     * {@code msg.contains("HTTP 429")} 这类<b>字符串嗅探</b>判断能否重试。问题有三：
     * <ol>
     *   <li>脆弱——文案一改（如「HTTP状态 429」）重试逻辑就静默失效；</li>
     *   <li>不准——网络异常、超时也会被包装成 {@code aiUnavailable}，只能靠猜；</li>
     *   <li>无法测试——单测要为重试行为构造带特定中文字符串的异常，极不合理。</li>
     * </ol>
     * 改为随异常携带原始状态码后，重试判定变为纯数值比较（429 / 5xx 可重试，
     * 其余 4xx 立即失败），既可单测也不受文案影响。</p>
     */
    private final Integer httpStatus;

    public CrmException(String errorCode, String message) {
        this(errorCode, message, null, null);
    }

    public CrmException(String errorCode, String message, Throwable cause) {
        this(errorCode, message, null, cause);
    }

    public CrmException(String errorCode, String message, Integer httpStatus) {
        this(errorCode, message, httpStatus, null);
    }

    public CrmException(String errorCode, String message, Integer httpStatus, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }

    public String getErrorCode() { return errorCode; }

    /** LLM 网关返回的 HTTP 状态码；非 HTTP 失败（网络异常、解析失败）为 null。 */
    public Integer getHttpStatus() { return httpStatus; }

    public static CrmException aiUnavailable(String detail) {
        return new CrmException("AI_UNAVAILABLE", "AI服务不可用: " + detail);
    }

    /**
     * 携带 HTTP 状态码的 AI 不可用异常（2026-10-07 新增）。
     *
     * @param detail 错误详情（响应体等）
     * @param status HTTP 状态码；调用方据此判断是否值得退避重试
     */
    public static CrmException aiUnavailable(String detail, Integer status) {
        return new CrmException("AI_UNAVAILABLE", "AI服务不可用: HTTP " + status + " " + detail, status);
    }

    /**
     * 响应被模型截断（finish_reason == "length"）。
     *
     * <p><b>为什么必须单独抛而不是返回半截内容</b>：ReAct 的末轮输出与
     * {@code callLLMPlain} 的轮次耗尽总结<b>都是要直接给用户看的医学结论</b>。
     * 半截结论（例如只列了 3 种并发症就断掉）在健康场景下比报错更危险 ——
     * 用户会以为那就是全部。宁可明确报错让用户重问。</p>
     */
    public static CrmException truncatedByLength(int requestedMaxTokens) {
        return new CrmException("AI_TRUNCATED",
                "AI回答因长度上限被截断（max_tokens=" + requestedMaxTokens
                        + "），内容不完整，请调整提问方式后重试");
    }

    /**
     * 协议层失败：响应结构不对（choices 为空、content 为空、JSON 解析不了）。
     *
     * <p>用独立的 errorCode 而非 {@code AI_UNAVAILABLE}，是为了让调用方能区分
     * 「可重试的瞬时故障」与「重试也没用的协议问题」——
     * 后者重试 3 次只会让用户多等 3 个退避周期。</p>
     */
    public static CrmException badResponse(String detail) {
        return new CrmException("AI_BAD_RESPONSE", "AI响应格式异常: " + detail);
    }

    /** 该错误是否代表「重试有意义」（协议层结构问题重试无用）。 */
    public boolean isProtocolFailure() {
        return "AI_BAD_RESPONSE".equals(errorCode) || "AI_TRUNCATED".equals(errorCode);
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