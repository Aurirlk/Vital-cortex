package cn.kmbeast.websocket;

import cn.kmbeast.utils.JwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.websocket.*;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@ServerEndpoint("/ws/notification/{token}")
public class WebSocketServer {

    private static final ConcurrentHashMap<String, Session> SESSIONS = new ConcurrentHashMap<>();
    private String userId;

    @OnOpen
    public void onOpen(Session session, @PathParam("token") String token) {
        try {
            // SEC-01 整改：统一走 JwtUtil（外部注入密钥），并取 claim "id" 而非 subject。
            // 原实现取 getSubject()，而签发端 subject 恒为固定字符串"用户认证"，
            // 导致所有在线用户共用同一个 SESSIONS key、互相顶掉连接并串收消息。
            Integer uid = JwtUtil.getInstance().getUserId(token);
            if (uid == null) {
                log.warn("WebSocket 认证失败：token 无效或已过期");
                closeQuietly(session);
                return;
            }
            this.userId = String.valueOf(uid);
            Session previous = SESSIONS.put(this.userId, session);
            if (previous != null && previous.isOpen() && !previous.equals(session)) {
                closeQuietly(previous);
            }
            log.info("WebSocket 连接建立: userId={}", userId);
        } catch (Exception e) {
            log.error("WebSocket 认证异常", e);
            closeQuietly(session);
        }
    }

    private static void closeQuietly(Session session) {
        try {
            if (session != null && session.isOpen()) {
                session.close();
            }
        } catch (IOException ignored) {
            // 关闭失败无需处理
        }
    }

    @OnClose
    public void onClose(Session session) {
        if (userId != null) {
            // 仅当映射中仍是当前这条连接时才移除，避免旧连接关闭时误删用户的新连接
            SESSIONS.remove(userId, session);
            log.info("WebSocket 连接关闭: userId={}", userId);
        }
    }

    @OnError
    public void onError(Session session, Throwable error) {
        log.error("WebSocket 错误: userId={}", userId, error);
    }

    @OnMessage
    public void onMessage(Session session, String message) {
        // 心跳保活：客户端每隔 30s 发送 {"type":"heartbeat"}，服务端回包以维持连接活跃。
        // 前端 ws.onmessage 会忽略 type=heartbeat 的回包，不影响业务消息分发（D-004 整改）。
        if (session != null && session.isOpen() && message != null && message.contains("heartbeat")) {
            sendSafely(session, "{\"type\":\"heartbeat\"}", "心跳回包");
        }
    }

    /**
     * 发送消息给指定用户
     */
    public static void sendToUser(String userId, String message) {
        Session session = SESSIONS.get(userId);
        if (session != null && session.isOpen()) {
            sendSafely(session, message, "单发 userId=" + userId);
        }
    }

    /**
     * 广播消息给所有用户
     */
    public static void broadcast(String message) {
        SESSIONS.forEach((userId, session) -> {
            if (session.isOpen()) {
                sendSafely(session, message, "广播 userId=" + userId);
            }
        });
    }

    /**
     * 唯一的发送出口：<b>按 session 串行</b>写帧。
     *
     * <p>JSR-356 规定 {@code session.getBasicRemote()} 是同步且<b>非线程安全</b>的：
     * 同一个 session 上并发调用 {@code sendText}，容器要么直接抛
     * {@code IllegalStateException}，要么把两帧交错写出去。本类的三个调用点恰好来自
     * 三种不同线程 —— 心跳回包在容器线程、{@code sendToUser} 在业务请求线程
     * （发通知时同步调用）、{@code broadcast} 在管理员/定时任务线程。
     * 用户端的表现为「通知铃从此不再更新」，而服务端只有一行 error 日志；
     * 帧交错时前端还会收到一条解析失败的脏消息。
     *
     * <p>锁对象用 session 本身：每条连接一把锁，互不阻塞，也不需要额外的 map。
     */
    private static void sendSafely(Session session, String message, String context) {
        try {
            synchronized (session) {
                session.getBasicRemote().sendText(message);
            }
        } catch (IOException e) {
            log.error("WebSocket 发送失败({}): {}", context, e.getMessage(), e);
        } catch (RuntimeException e) {
            // session 已关闭 / 容器状态异常等：不能让一次通知投递把调用方的业务带崩
            log.error("WebSocket 发送异常({}): {}", context, e.getMessage(), e);
        }
    }

    /**
     * 获取在线用户数
     */
    public static int getOnlineCount() {
        return SESSIONS.size();
    }
}
