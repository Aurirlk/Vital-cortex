package cn.kmbeast.crm.sqlite;

import cn.kmbeast.crm.config.CrmConfig;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * CRM SQLite 连接管理。
 *
 * <p>AG-02 整改：原实现持有两个 <b>单例 Connection</b>（读写各一），
 * 并被所有请求线程共享。JDBC 的 {@code Connection} 不保证线程安全，
 * 在并发 SSE 对话下会出现 {@code SQLITE_BUSY}、结果集串读，
 * 且一旦某个线程把连接用坏（如异常后状态不干净），全局都受影响。
 * 更隐蔽的是：调用方用 {@code try (PreparedStatement ps = mgr.getXxxConnection().prepareStatement(..))}
 * 只关了 Statement，连接永远不释放，也就无从谈起故障恢复。
 *
 * <p>现改为两个 HikariCP 连接池：
 * <ul>
 *   <li>写池 {@code maximumPoolSize=1}——SQLite 同一时刻只允许一个写事务，
 *       用池大小把并发写串行化，好过让线程在 SQLITE_BUSY 上随机失败；</li>
 *   <li>读池允许并发，配合 WAL 模式实现「读不阻塞写、写不阻塞读」。</li>
 * </ul>
 * 调用方必须用 try-with-resources 关闭 Connection（归还连接池）。
 */
@Slf4j
@Component
public class SqliteConnectionManager {

    @Resource
    private CrmConfig crmConfig;

    private HikariDataSource readWritePool;
    private HikariDataSource readOnlyPool;

    @PostConstruct
    public void init() {
        try {
            Class.forName("org.sqlite.JDBC");
            String url = "jdbc:sqlite:" + crmConfig.getSqliteDbPath();

            readWritePool = buildPool(url, false, 1, "crm-sqlite-rw");
            initSchema();

            readOnlyPool = buildPool(url, true, 4, "crm-sqlite-ro");

            log.info("[CRM-SQLite] 连接池已就绪: {}（写池=1，读池=4，WAL）", crmConfig.getSqliteDbPath());
        } catch (Exception e) {
            log.error("[CRM-SQLite] 数据库初始化失败", e);
            throw new IllegalStateException("SQLite初始化失败", e);
        }
    }

    private HikariDataSource buildPool(String url, boolean readOnly, int maxPoolSize, String poolName) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setDriverClassName("org.sqlite.JDBC");
        config.setMaximumPoolSize(maxPoolSize);
        config.setMinimumIdle(1);
        config.setPoolName(poolName);
        config.setConnectionTimeout(10_000);
        config.setReadOnly(readOnly);
        // WAL：允许读写并发；busy_timeout：写冲突时等待而非立刻抛错
        config.addDataSourceProperty("journal_mode", "WAL");
        config.addDataSourceProperty("busy_timeout", "5000");
        config.addDataSourceProperty("synchronous", "NORMAL");
        return new HikariDataSource(config);
    }

    private void initSchema() throws SQLException {
        String[] statements = {
                "PRAGMA journal_mode=WAL",
                "CREATE TABLE IF NOT EXISTS chat_history ("
                        + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                        + "phone_number TEXT NOT NULL,"
                        + "session_id TEXT NOT NULL,"
                        + "role TEXT NOT NULL,"
                        + "content TEXT NOT NULL,"
                        + "intent_code INTEGER,"
                        + "metadata TEXT,"
                        + "created_at DATETIME DEFAULT CURRENT_TIMESTAMP)",
                "CREATE INDEX IF NOT EXISTS idx_phone ON chat_history(phone_number)",
                "CREATE INDEX IF NOT EXISTS idx_session ON chat_history(session_id)",
                // 历史查询按 phone + id 倒序，补一个组合索引避免全表扫描
                "CREATE INDEX IF NOT EXISTS idx_phone_id ON chat_history(phone_number, id DESC)"
        };
        try (Connection conn = readWritePool.getConnection();
             Statement stmt = conn.createStatement()) {
            for (String sql : statements) {
                stmt.execute(sql);
            }
        }
    }

    /**
     * 获取读写连接。<b>调用方必须关闭</b>（try-with-resources）。
     */
    public Connection getReadWriteConnection() throws SQLException {
        return readWritePool.getConnection();
    }

    /**
     * 获取只读连接。<b>调用方必须关闭</b>（try-with-resources）。
     */
    public Connection getReadOnlyConnection() throws SQLException {
        return readOnlyPool.getConnection();
    }

    @PreDestroy
    public void close() {
        closeQuietly(readWritePool, "读写");
        closeQuietly(readOnlyPool, "只读");
    }

    private void closeQuietly(HikariDataSource pool, String label) {
        try {
            if (pool != null && !pool.isClosed()) {
                pool.close();
                log.info("[CRM-SQLite] {}连接池已关闭", label);
            }
        } catch (Exception e) {
            log.warn("[CRM-SQLite] 关闭{}连接池异常", label, e);
        }
    }
}
