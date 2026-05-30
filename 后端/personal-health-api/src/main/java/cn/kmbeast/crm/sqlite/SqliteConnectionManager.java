package cn.kmbeast.crm.sqlite;

import cn.kmbeast.crm.config.CrmConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.sqlite.SQLiteConfig;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

@Slf4j
@Component
public class SqliteConnectionManager {

    @Resource
    private CrmConfig crmConfig;

    private Connection readWriteConnection;

    private Connection readOnlyConnection;

    @PostConstruct
    public void init() {
        try {
            Class.forName("org.sqlite.JDBC");

            readWriteConnection = DriverManager.getConnection(
                    "jdbc:sqlite:" + crmConfig.getSqliteDbPath());
            readWriteConnection.setAutoCommit(true);
            createTables();
            log.info("[CRM-SQLite] 读写连接已建立: {}", crmConfig.getSqliteDbPath());

            SQLiteConfig roConfig = new SQLiteConfig();
            roConfig.setReadOnly(true);
            readOnlyConnection = DriverManager.getConnection(
                    "jdbc:sqlite:" + crmConfig.getSqliteDbPath(),
                    roConfig.toProperties());
            log.info("[CRM-SQLite] 只读连接已建立");
        } catch (Exception e) {
            log.error("[CRM-SQLite] 数据库初始化失败", e);
            throw new RuntimeException("SQLite初始化失败", e);
        }
    }

    private void createTables() throws SQLException {
        String sql = "CREATE TABLE IF NOT EXISTS chat_history (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "phone_number TEXT NOT NULL," +
                "session_id TEXT NOT NULL," +
                "role TEXT NOT NULL," +
                "content TEXT NOT NULL," +
                "intent_code INTEGER," +
                "metadata TEXT," +
                "created_at DATETIME DEFAULT CURRENT_TIMESTAMP" +
                ");" +
                "CREATE INDEX IF NOT EXISTS idx_phone ON chat_history(phone_number);" +
                "CREATE INDEX IF NOT EXISTS idx_session ON chat_history(session_id);";
        try (Statement stmt = readWriteConnection.createStatement()) {
            stmt.executeUpdate(sql);
        }
    }

    public synchronized Connection getReadWriteConnection() {
        try {
            if (readWriteConnection == null || readWriteConnection.isClosed()) {
                readWriteConnection = DriverManager.getConnection(
                        "jdbc:sqlite:" + crmConfig.getSqliteDbPath());
                readWriteConnection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            log.error("[CRM-SQLite] 重新获取读写连接失败", e);
        }
        return readWriteConnection;
    }

    public synchronized Connection getReadOnlyConnection() {
        try {
            if (readOnlyConnection == null || readOnlyConnection.isClosed()) {
                SQLiteConfig roConfig = new SQLiteConfig();
                roConfig.setReadOnly(true);
                readOnlyConnection = DriverManager.getConnection(
                        "jdbc:sqlite:" + crmConfig.getSqliteDbPath(),
                        roConfig.toProperties());
            }
        } catch (SQLException e) {
            log.error("[CRM-SQLite] 重新获取只读连接失败", e);
        }
        return readOnlyConnection;
    }

    @PreDestroy
    public void close() {
        closeQuietly(readWriteConnection, "读写");
        closeQuietly(readOnlyConnection, "只读");
    }

    private void closeQuietly(Connection conn, String label) {
        try {
            if (conn != null && !conn.isClosed()) {
                conn.close();
                log.info("[CRM-SQLite] {}连接已关闭", label);
            }
        } catch (SQLException e) {
            log.warn("[CRM-SQLite] 关闭{}连接异常", label, e);
        }
    }
}

