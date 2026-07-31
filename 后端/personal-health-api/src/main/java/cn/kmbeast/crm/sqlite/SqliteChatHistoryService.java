package cn.kmbeast.crm.sqlite;

import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;

/**
 * CRM 聊天历史存储服务。
 *
 * <p>AG-02 整改：连接改为从连接池获取、用完即还（try-with-resources），
 * 不再持有长期共享的裸 Connection。
 *
 * <p>SEC-04 整改：{@link #executeQuery} 由 {@link SqlGuard} 统一校验，
 * 支持按当前会话手机号强制租户隔离。
 */
@Slf4j
@Service
public class SqliteChatHistoryService {

    @Resource
    private SqliteConnectionManager connectionManager;

    private static final int MAX_QUERY_ROWS = 200;
    private static final int QUERY_TIMEOUT_SECONDS = 10;

    /**
     * 保存一条消息。
     *
     * <p>AG-10 整改：原实现 catch 后仅 log 即吞掉异常，调用方无法感知聊天记录丢失。
     * 现改为返回 boolean，失败时由调用方决定处理（记录告警，不阻塞主流程）。
     *
     * @return true 保存成功；false 保存失败（调用方应记录审计日志）
     */
    public boolean saveMessage(String phoneNumber, String sessionId, String role,
                               String content, Integer intentCode, Map<String, Object> metadata) {
        String sql = "INSERT INTO chat_history (phone_number, session_id, role, content, intent_code, metadata) " +
                "VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = connectionManager.getReadWriteConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, phoneNumber);
            ps.setString(2, sessionId);
            ps.setString(3, role);
            ps.setString(4, content);
            if (intentCode != null) ps.setInt(5, intentCode); else ps.setNull(5, java.sql.Types.INTEGER);
            ps.setString(6, metadata != null ? JSON.toJSONString(metadata) : null);
            ps.executeUpdate();
            return true;
        } catch (Exception e) {
            log.error("[CRM-SQLite] 保存消息失败: phone={}, session={}, role={}",
                    phoneNumber, sessionId, role, e);
            return false;
        }
    }

    public List<Map<String, Object>> getHistory(String phoneNumber, int limit) {
        String sql = "SELECT id, phone_number, session_id, role, content, intent_code, metadata, created_at " +
                "FROM chat_history WHERE phone_number = ? ORDER BY id DESC LIMIT ?";
        List<Map<String, Object>> results = new ArrayList<>();
        try (Connection conn = connectionManager.getReadOnlyConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, phoneNumber);
            ps.setInt(2, Math.min(limit, 100));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", rs.getInt("id"));
                    row.put("phone_number", rs.getString("phone_number"));
                    row.put("session_id", rs.getString("session_id"));
                    row.put("role", rs.getString("role"));
                    row.put("content", rs.getString("content"));
                    row.put("intent_code", rs.getObject("intent_code"));
                    row.put("metadata", rs.getString("metadata"));
                    row.put("created_at", rs.getString("created_at"));
                    results.add(row);
                }
            }
        } catch (Exception e) {
            log.error("[CRM-SQLite] 查询历史失败: phone={}", phoneNumber, e);
        }
        Collections.reverse(results);
        return results;
    }

    public boolean isNewUser(String phoneNumber) {
        String sql = "SELECT COUNT(*) FROM chat_history WHERE phone_number = ?";
        try (Connection conn = connectionManager.getReadOnlyConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, phoneNumber);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1) == 0;
            }
        } catch (Exception e) {
            log.error("[CRM-SQLite] 检查新用户失败: phone={}", phoneNumber, e);
        }
        return true;
    }

    /**
     * 执行只读查询（已通过只读校验 + 可选租户隔离）。
     *
     * @param sql                查询语句
     * @param tenantPhoneNumber  强制租户隔离用的手机号；传 null 表示管理员全量查询（不做隔离）
     */
    public List<Map<String, Object>> executeQuery(String sql, String tenantPhoneNumber) {
        SqlGuard.validateReadOnly(sql);
        SqlGuard.enforceTenantIsolation(sql, tenantPhoneNumber);

        List<Map<String, Object>> results = new ArrayList<>();
        try (Connection conn = connectionManager.getReadOnlyConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setMaxRows(MAX_QUERY_ROWS);
            ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            try (ResultSet rs = ps.executeQuery()) {
                int colCount = rs.getMetaData().getColumnCount();
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= colCount; i++) {
                        row.put(rs.getMetaData().getColumnName(i), rs.getObject(i));
                    }
                    results.add(row);
                }
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("[CRM-SQLite] SQL查询失败", e);
            throw new RuntimeException("SQL查询失败");
        }
        return results;
    }

    public int getTotalMessages(String phoneNumber) {
        String sql = "SELECT COUNT(*) FROM chat_history WHERE phone_number = ?";
        try (Connection conn = connectionManager.getReadOnlyConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, phoneNumber);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (Exception e) {
            log.error("[CRM-SQLite] 统计消息数失败: phone={}", phoneNumber, e);
        }
        return 0;
    }

    public Map<String, Object> getStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        try (Connection conn = connectionManager.getReadOnlyConnection()) {
            String totalSql = "SELECT COUNT(*) as total FROM chat_history";
            try (PreparedStatement ps = conn.prepareStatement(totalSql);
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) stats.put("total_messages", rs.getInt("total"));
            }

            String userSql = "SELECT COUNT(DISTINCT phone_number) as users FROM chat_history";
            try (PreparedStatement ps = conn.prepareStatement(userSql);
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) stats.put("total_users", rs.getInt("users"));
            }

            String sessionSql = "SELECT COUNT(DISTINCT session_id) as sessions FROM chat_history";
            try (PreparedStatement ps = conn.prepareStatement(sessionSql);
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) stats.put("total_sessions", rs.getInt("sessions"));
            }
        } catch (Exception e) {
            log.error("[CRM-SQLite] 统计失败", e);
        }
        return stats;
    }
}
