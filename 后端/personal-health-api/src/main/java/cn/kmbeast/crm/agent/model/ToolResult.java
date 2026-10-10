package cn.kmbeast.crm.agent.model;

import com.alibaba.fastjson2.JSON;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 工具执行结果。
 *
 * <p><b>2026-10-07 改造（OpenAI schema 合规）</b>：新增 {@link #structuredContent} 字段。
 * 此前工具返回的 JSON 字符串会被 {@code BaseReActAgent.tryParseJson()} 解析成
 * {@code JSONObject}/{@code JSONArray} <b>直接塞进 messages 的 {@code content}</b>，
 * 而 OpenAI 规范要求 {@code role:"tool"} 的 {@code content} 必须是
 * {@code string | array of content parts} —— 传对象会被严格实现（vLLM / 新版网关）判 400。
 * <p>现在：{@code content} <b>恒为 String</b>（原样透传，不做二次包装），
 * 结构化数据走 {@link #structuredContent}，<b>不进 messages</b>，仅供日志 / 审计 / 前端消费。
 */
@Data
@AllArgsConstructor
public class ToolResult {

    private boolean success;

    /** 送进 LLM 的文本内容。{@code null} 会在回填时归一为 {@code ""}（OpenAI 不接受 null content）。 */
    private String content;

    private int contentLength;

    /**
     * 结构化结果（{@code JSONObject} / {@code JSONArray} / 任意 POJO）。
     *
     * <p><b>不参与 OpenAI 请求体</b>。存在的意义是：工具内部已经算出了结构化数据，
     * 若为了迁就协议而把它序列化成字符串再塞回 content，会浪费 token 且降低模型解析质量；
     * 而完全不保留又会让日志与审计只能拿到一大坨字符串。</p>
     */
    private Object structuredContent;

    public ToolResult(boolean success, String content, int contentLength) {
        this(success, content, contentLength, null);
    }

    public static ToolResult ok(String content) {
        return new ToolResult(true, content, content != null ? content.length() : 0);
    }

    /**
     * 带结构化结果的工厂。
     *
     * @param content     送进 LLM 的文本（应为 JSON 字符串或可读摘要）
     * @param structured  结构化副本，<b>不进 messages</b>
     */
    public static ToolResult ok(String content, Object structured) {
        return new ToolResult(true, content, content != null ? content.length() : 0, structured);
    }

    public static ToolResult error(String message) {
        return new ToolResult(false, message, message != null ? message.length() : 0, null);
    }

    public String toJson() {
        return JSON.toJSONString(this);
    }
}