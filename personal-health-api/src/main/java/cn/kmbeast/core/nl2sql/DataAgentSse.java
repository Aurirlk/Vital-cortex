package cn.kmbeast.core.nl2sql;

import com.alibaba.fastjson2.JSON;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;

/**
 * 数据智能体 SSE 输出支持。
 *
 * <p>与 {@code CrmChatController} 的既有约定保持一致：手写 {@code event:/data:} 帧，
 * 设置 {@code X-Accel-Buffering: no} 以避免 Nginx 缓冲导致前端拿不到流式更新。</p>
 */
public final class DataAgentSse {

    /** 步骤事件名。 */
    public static final String EVENT_STEP = "step";
    /** 最终结果事件名。 */
    public static final String EVENT_RESULT = "result";
    /** 错误事件名。 */
    public static final String EVENT_ERROR = "error";

    private DataAgentSse() {
    }

    /** 设置 SSE 响应头并返回 writer。 */
    public static PrintWriter prepare(HttpServletResponse response) throws IOException {
        response.setContentType("text/event-stream");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("Connection", "keep-alive");
        response.setHeader("X-Accel-Buffering", "no");
        PrintWriter writer = response.getWriter();
        writer.flush();
        return writer;
    }

    /** 发送一个事件帧。 */
    public static void send(PrintWriter writer, String event, Object data) {
        if (writer == null) {
            return;
        }
        String payload;
        try {
            payload = data instanceof String ? (String) data : JSON.toJSONString(data);
        } catch (Exception e) {
            payload = "{\"error\":\"序列化失败\"}";
        }
        // SSE 规范：data 内的换行必须逐行加 "data:" 前缀
        writer.write("event: " + event + "\n");
        for (String line : payload.split("\n", -1)) {
            writer.write("data: " + line + "\n");
        }
        writer.write("\n");
        writer.flush();
    }

    /** 构造把步骤事件推给前端的 sink。 */
    public static StepSink sink(PrintWriter writer) {
        return event -> send(writer, EVENT_STEP, event);
    }
}
