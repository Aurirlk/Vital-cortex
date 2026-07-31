package cn.kmbeast.websocket;

import cn.kmbeast.utils.JwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.websocket.*;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;
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

    /**
     * 发送消息给指定用户
     */
    public static void sendToUser(String userId, String message) {
        Session session = SESSIONS.get(userId);
        if (session != null && session.isOpen()) {
            try {
                session.getBasicRemote().sendText(message);
            } catch (IOException e) {
                log.error("发送消息失败: userId={}", userId, e);
            }
        }
    }

    /**
     * 广播消息给所有用户
     */
    public static void broadcast(String message) {
        SESSIONS.forEach((userId, session) -> {
            if (session.isOpen()) {
                try {
                    session.getBasicRemote().sendText(message);
                } catch (IOException e) {
                    log.error("广播消息失败: userId={}", userId, e);
                }
            }
        });
    }

    /**
     * 获取在线用户数
     */
    public static int getOnlineCount() {
        return SESSIONS.size();
    }
}
