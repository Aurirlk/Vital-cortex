package cn.kmbeast.service.impl;

import cn.kmbeast.service.AiService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 流式输出「接收端」单元测试 —— 2026-10-10 新增。
 *
 * <p>背景：客户端中途断开时，{@code AiController} 的 SSE 回调会抛
 * {@code RuntimeException("SSE client disconnected")}。原实现让这个异常一直冒到
 * {@code chatStream} 的 catch，直接 return —— <b>整轮 AI 回复既不推送也不入库</b>，
 * 用户切个标签页就永久丢掉一条（可能是深度思考跑了几十秒的）回答。
 *
 * <p>{@code StreamSink} 的职责就是把「客户端断开」与「真的出错了」区分开：
 * 前者只记标记、继续生成以便入库 + 事后通知；后者照旧抛出，不许被伪装成
 * 「用户离开了」。
 *
 * <p>这几条断言是「AI 对话通知」能成立的前提 —— 如果断开异常又被吞成别的样子，
 * 通知要么永远发不出去，要么在真正的故障里乱发。
 */
@DisplayName("AI 流式输出的断开处理")
class AiStreamSinkTest {

    private static final String DISCONNECTED = "SSE client disconnected";

    /** 记录收到的所有事件，可选地在中途抛异常模拟各种故障 */
    private static final class RecordingCallback implements AiService.StreamCallback {
        final List<String> events = new ArrayList<>();
        private final String throwOnEvent;
        private final RuntimeException toThrow;

        RecordingCallback(String throwOnEvent, RuntimeException toThrow) {
            this.throwOnEvent = throwOnEvent;
            this.toThrow = toThrow;
        }

        @Override
        public void onEvent(String eventName, String jsonData) {
            events.add(eventName);
            if (throwOnEvent != null && throwOnEvent.equals(eventName)) {
                throw toThrow;
            }
        }
    }

    @Test
    @DisplayName("正常推送：原样转发，标记保持未断开")
    void shouldForwardNormally() {
        RecordingCallback callback = new RecordingCallback(null, null);
        AiServiceImpl.StreamSink sink = new AiServiceImpl.StreamSink(callback);

        sink.emit("answer_chunk", "{}");
        sink.emit("answer_done", "{}");

        assertEquals(List.of("answer_chunk", "answer_done"), callback.events);
        assertFalse(sink.clientGone());
    }

    @Test
    @DisplayName("客户端断开：不抛出，标记置位，后续事件不再尝试推送")
    void disconnectShouldFlipFlagAndStopForwarding() {
        RecordingCallback callback =
                new RecordingCallback("answer_chunk", new RuntimeException(DISCONNECTED));
        AiServiceImpl.StreamSink sink = new AiServiceImpl.StreamSink(callback);

        // 不抛异常 = 生成循环能继续跑完，回复才存得下来
        assertDoesNotThrow(() -> sink.emit("answer_chunk", "{}"));
        assertTrue(sink.clientGone());

        sink.emit("answer_chunk", "{}");
        sink.emit("answer_done", "{}");

        assertEquals(List.of("answer_chunk"), callback.events,
                "断开后不该再往一个已经没人收的连接上写");
    }

    @Test
    @DisplayName("其它异常照旧向上抛，不能被伪装成「用户离开了」")
    void otherFailuresMustPropagate() {
        RecordingCallback callback =
                new RecordingCallback("answer_chunk", new RuntimeException("JSON 序列化失败"));
        AiServiceImpl.StreamSink sink = new AiServiceImpl.StreamSink(callback);

        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> sink.emit("answer_chunk", "{}"));
        assertEquals("JSON 序列化失败", thrown.getMessage());
        assertFalse(sink.clientGone(), "真故障不该被记成客户端断开");
    }

    @Test
    @DisplayName("异常消息里只是「包含」这句话也算断开（与 AiController 判定口径一致）")
    void wrappedDisconnectMessageShouldAlsoCount() {
        RecordingCallback callback = new RecordingCallback("error",
                new RuntimeException("java.io.IOException: " + DISCONNECTED + " during write"));
        AiServiceImpl.StreamSink sink = new AiServiceImpl.StreamSink(callback);

        assertDoesNotThrow(() -> sink.emit("error", "{}"));
        assertTrue(sink.clientGone());
    }

    @Test
    @DisplayName("消息为 null 的异常：不属于断开，向上抛")
    void nullMessageMustPropagate() {
        RecordingCallback callback = new RecordingCallback("error", new RuntimeException());
        AiServiceImpl.StreamSink sink = new AiServiceImpl.StreamSink(callback);

        assertThrows(RuntimeException.class, () -> sink.emit("error", "{}"));
        assertFalse(sink.clientGone());
    }
}
