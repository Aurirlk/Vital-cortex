package cn.kmbeast.crm.sqlite;

import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;

@Slf4j
@Service
public class SqliteChatHistoryService {

    @Resource
    private SqliteConnectionManager connectionManager;

    private static final int MAX_QUERY_ROWS = 200;
    private static final int QUERY_TIMEOUT_SECONDS = 10;

    public void saveMessage(String phoneNumber, String sessionId, String role,
                            String content, Integer intentCode, Map<String, Object> metadata) {
        String sql = "INSERT INTO chat_history (phone_number, session_id, role, content, intent_code, metadata) " +
                "VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = connectionManager.getReadWriteConnection().prepareStatement(sql)) {
            ps.setString(1, phoneNumber);
            ps.setString(2, sessionId);
            ps.setString(3, role);
            ps.setString(4, content);
            if (intentCode != null) ps.setInt(5, intentCode); else ps.setNull(5, java.sql.Types.INTEGER);
            ps.setString(6, metadata != null ? JSON.toJSONString(metadata) : null);
            ps.executeUpdate();
        } catch (Exception e) {
            log.error("[CRM-SQLite] 保存消息失败", e);
        }
    }

    public List<Map<String, Object>> getHistory(String phoneNumber, int limit) {
        String sql = "SELECT id, phone_number, session_id, role, content, intent_code, metadata, created_at " +
                "FROM chat_history WHERE phone_number = ? ORDER BY id DESC LIMIT ?";
        List<Map<String, Object>> results = new ArrayList<>();
        try (PreparedStatement ps = connectionManager.getReadOnlyConnection().prepareStatement(sql)) {
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
            log.error("[CRM-SQLite] 查询历史失败", e);
        }
        Collections.reverse(results);
        return results;
    }

    public boolean isNewUser(String phoneNumber) {
        String sql = "SELECT COUNT(*) FROM chat_history WHERE phone_number = ?";
        try (PreparedStatement ps = connectionManager.getReadOnlyConnection().prepareStatement(sql)) {
            ps.setString(1, phoneNumber);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1) == 0;
            }
        } catch (Exception e) {
            log.error("[CRM-SQLite] 检查新用户失败", e);
        }
        return true;
    }

    public List<Map<String, Object>> executeQuery(String sql) {
        String upper = sql.trim().toUpperCase();
        if (!upper.startsWith("SELECT") && !upper.startsWith("WITH")) {
            throw new IllegalArgumentException("仅允许只读查询(SELECT)");
        }
        if (upper.contains("DROP") || upper.contains("DELETE") ||
                upper.contains("UPDATE") || upper.contains("INSERT") ||
                upper.contains("ALTER") || upper.contains("CREATE") ||
                upper.contains("ATTACH") || upper.contains("DETACH") ||
                upper.contains("PRAGMA") || upper.contains("REINDEX") ||
                upper.contains("VACUUM")) {
            throw new IllegalArgumentException("仅允许只读查询(SELECT)");
        }

        List<Map<String, Object>> results = new ArrayList<>();
        try (PreparedStatement ps = connectionManager.getReadOnlyConnection().prepareStatement(sql)) {
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
        } catch (Exception e) {
            log.error("[CRM-SQLite] SQL查询失败: {}", sql, e);
            throw new RuntimeException("SQL查询失败: " + e.getMessage());
        }
        return results;
    }

    public int getTotalMessages(String phoneNumber) {
        String sql = "SELECT COUNT(*) FROM chat_history WHERE phone_number = ?";
        try (PreparedStatement ps = connectionManager.getReadOnlyConnection().prepareStatement(sql)) {
            ps.setString(1, phoneNumber);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (Exception e) {
            log.error("[CRM-SQLite] 统计消息数失败", e);
        }
        return 0;
    }

    public Map<String, Object> getStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        try {
            String totalSql = "SELECT COUNT(*) as total FROM chat_history";
            try (PreparedStatement ps = connectionManager.getReadOnlyConnection().prepareStatement(totalSql);
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) stats.put("total_messages", rs.getInt("total"));
            }

            String userSql = "SELECT COUNT(DISTINCT phone_number) as users FROM chat_history";
            try (PreparedStatement ps = connectionManager.getReadOnlyConnection().prepareStatement(userSql);
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) stats.put("total_users", rs.getInt("users"));
            }

            String sessionSql = "SELECT COUNT(DISTINCT session_id) as sessions FROM chat_history";
            try (PreparedStatement ps = connectionManager.getReadOnlyConnection().prepareStatement(sessionSql);
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) stats.put("total_sessions", rs.getInt("sessions"));
            }
        } catch (Exception e) {
            log.error("[CRM-SQLite] 统计失败", e);
        }
        return stats;
    }
}
