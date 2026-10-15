package cn.kmbeast.crm.rag;

import cn.kmbeast.core.nl2sql.MySqlAiSqlGuard;
import cn.kmbeast.mapper.KbDataSourceMapper;
import cn.kmbeast.pojo.entity.KbDataSource;
import cn.kmbeast.utils.AESUtils;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 知识库数据源管理：本地主库 / 远端 MySQL 的连接解析、连通性探测、只读取数。
 *
 * <h3>🔴 为什么这里<b>不能</b>注册 {@code DataSource} bean</h3>
 * <p>Spring Boot 的 {@code DataSourceAutoConfiguration} 带
 * {@code @ConditionalOnMissingBean(DataSource.class)} —— 容器里一旦存在<b>任何</b>
 * {@code DataSource} bean 定义，自动配置<b>整体退避</b>，主数据源永不创建，
 * 应用在任何 profile 下都启动失败。这个坑本项目已经踩过一次
 * （见 {@code Nl2SqlReadOnlyDataSourceProvider} 的类注释，2026-10-12 实测）。
 * 因此本类持有的远端连接池全部是<b>普通字段</b>，只通过 {@link #resolve} 方法
 * 以 {@code DataSource} 类型<b>返回</b>，绝不出现在任何 {@code @Bean} 上。</p>
 *
 * <h3>密码存储</h3>
 * <p>落库的是 {@code AESUtils}（AES-256-GCM）密文，不是明文；
 * 实体上的 {@code passwordCipher} 字段标了 {@code @JsonIgnore}，
 * 查询接口不会把密文吐给前端。{@code decryptIfNeeded} 兼容历史明文行。</p>
 */
@Slf4j
@Service
public class KbDataSourceService {

    /** 远端池上限。取数场景并发极低（管理端人工触发），大池纯属浪费连接。 */
    private static final int REMOTE_POOL_SIZE = 3;

    /** 单次取数的行数硬上限，防止一条 SELECT 把整个远端库拉进内存。 */
    private static final int MAX_FETCH_ROWS = 5000;

    /** 连通性探测超时（秒）。 */
    private static final int PROBE_TIMEOUT_SECONDS = 5;

    @Resource(name = "dataSource")
    private DataSource primaryDataSource;

    @Resource
    private KbDataSourceMapper kbDataSourceMapper;

    /** dataSourceId → 远端连接池（惰性创建）。 */
    private final Map<Integer, HikariDataSource> remotePools = new ConcurrentHashMap<>();

    /** 数据源不可用。 */
    public static class KbDataSourceException extends RuntimeException {
        public KbDataSourceException(String message) {
            super(message);
        }

        public KbDataSourceException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * 解析数据源。
     *
     * @param dataSourceId {@code kb_data_source.id}；{@code null} 或 0 表示主库
     * @return 可用的 {@link DataSource}
     * @throws KbDataSourceException 配置缺失、类型未知、密文解不开
     */
    public DataSource resolve(Integer dataSourceId) {
        if (dataSourceId == null || dataSourceId <= 0) {
            return primaryDataSource;
        }
        KbDataSource config = kbDataSourceMapper.getById(dataSourceId);
        if (config == null) {
            throw new KbDataSourceException("数据源不存在：id=" + dataSourceId);
        }
        if (Boolean.FALSE.equals(config.getEnabled())) {
            throw new KbDataSourceException("数据源已停用：" + config.getCode());
        }
        // local 类型：就是本应用自己的库，不需要也不应该再建一个池
        if (!"remote".equalsIgnoreCase(config.getType())) {
            return primaryDataSource;
        }
        return remotePool(config);
    }

    private DataSource remotePool(KbDataSource config) {
        return remotePools.computeIfAbsent(config.getId(), id -> {
            String url = trim(config.getJdbcUrl());
            if (url.isEmpty()) {
                throw new KbDataSourceException("远端数据源缺少 JDBC URL：" + config.getCode());
            }
            String password = decryptPassword(config);
            HikariDataSource ds = new HikariDataSource();
            ds.setJdbcUrl(url);
            ds.setUsername(trim(config.getUsername()));
            ds.setPassword(password);
            ds.setDriverClassName("com.mysql.cj.jdbc.Driver");
            // 连接级只读：MySQL 驱动会把会话事务置为 READ ONLY，
            // 与「SQL 必须过 MySqlAiSqlGuard」形成双重保险
            ds.setReadOnly(true);
            ds.setMaximumPoolSize(REMOTE_POOL_SIZE);
            ds.setMinimumIdle(0);
            ds.setConnectionTimeout(PROBE_TIMEOUT_SECONDS * 1000L);
            ds.setPoolName("kb-remote-" + id);
            log.info("[RAG-ds] 已创建远端连接池: id={}, code={}, url={}", id, config.getCode(), url);
            return ds;
        });
    }

    /**
     * 解开密码密文。
     *
     * <p>解密失败<b>不</b>回落明文：那会让一个配错的密文静默变成「用密文当密码去连库」，
     * 报出来的错是「Access denied」，查半天才知道是解密环节。直接抛，
     * 错误信息里点明是解密失败。</p>
     */
    private String decryptPassword(KbDataSource config) {
        String cipher = config.getPasswordCipher();
        if (cipher == null || cipher.isEmpty()) {
            return "";
        }
        try {
            return AESUtils.decryptIfNeeded(cipher);
        } catch (Exception e) {
            throw new KbDataSourceException(
                    "数据源密码解密失败（" + config.getCode() + "）：请重新保存密码，"
                            + "或确认 AES_SECRET_KEY 与保存时一致", e);
        }
    }

    /**
     * 连通性探测：连上并校验连接有效，结果写回 {@code last_check_*}。
     *
     * <p>探测是有副作用的主动作（会写库），管理端必须显式点「测试连接」触发，
     * 不做启动期自动探测 —— 远端库不可达时不该拖慢本应用启动。</p>
     */
    public Map<String, Object> check(Integer dataSourceId) {
        Map<String, Object> result = new LinkedHashMap<>();
        boolean ok;
        String message;
        try {
            DataSource ds = resolve(dataSourceId);
            try (Connection conn = ds.getConnection()) {
                ok = conn.isValid(PROBE_TIMEOUT_SECONDS);
                message = ok ? "连接正常" : "连接建立但 isValid 返回 false";
            }
        } catch (Exception e) {
            // 池建失败时要清掉缓存，否则下一次「测试连接」会一直拿到那个坏池，
            // 用户改了密码也永远连不上
            evict(dataSourceId);
            ok = false;
            message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            log.warn("[RAG-ds] 连通性探测失败: id={}, {}", dataSourceId, message);
        }
        if (dataSourceId != null && dataSourceId > 0) {
            try {
                kbDataSourceMapper.updateCheckResult(dataSourceId, ok, truncate(message, 500));
            } catch (Exception e) {
                log.warn("[RAG-ds] 写回探测结果失败: id={}, {}", dataSourceId, e.getMessage());
            }
        }
        result.put("ok", ok);
        result.put("message", message);
        return result;
    }

    /**
     * 只读取数：{@code SELECT} 语句，结果集转成 {@code List<Map>}。
     *
     * <p>SQL 必须先过 {@link MySqlAiSqlGuard#assertSafe}（fail-closed，不过就抛）。
     * 这不是「顺手加个校验」——本方法对远端库执行任意 SQL，
     * 拒绝策略必须是默认拒绝，而不是默认放行后再黑名单过滤。</p>
     *
     * @param dataSourceId 数据源 id（null = 主库）
     * @param sql          只读查询
     * @param limit        期望行数上限（内部再钳到 {@link #MAX_FETCH_ROWS}）
     */
    public List<Map<String, Object>> query(Integer dataSourceId, String sql, int limit) {
        if (sql == null || sql.trim().isEmpty()) {
            throw new KbDataSourceException("SQL 不能为空");
        }
        try {
            MySqlAiSqlGuard.assertSafe(sql);
        } catch (RuntimeException e) {
            throw new KbDataSourceException("只读取数被拒绝：" + e.getMessage(), e);
        }

        int rows = Math.min(limit <= 0 ? 500 : limit, MAX_FETCH_ROWS);
        DataSource ds = resolve(dataSourceId);
        List<Map<String, Object>> out = new ArrayList<>();
        try (Connection conn = ds.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.setFetchSize(Math.min(rows, 500));
            stmt.setMaxRows(rows);
            try (ResultSet rs = stmt.executeQuery(sql.trim())) {
                ResultSetMetaData meta = rs.getMetaData();
                int cols = meta.getColumnCount();
                while (rs.next() && out.size() < rows) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= cols; i++) {
                        row.put(meta.getColumnLabel(i), rs.getObject(i));
                    }
                    out.add(row);
                }
            }
        } catch (Exception e) {
            throw new KbDataSourceException("执行只读查询失败：" + e.getMessage(), e);
        }
        return out;
    }

    /** 关闭并移除某个远端池（配置变更后必须调用，否则改的 URL/密码不会生效）。 */
    public void evict(Integer dataSourceId) {
        if (dataSourceId == null) {
            return;
        }
        HikariDataSource ds = remotePools.remove(dataSourceId);
        if (ds != null) {
            try {
                ds.close();
                log.info("[RAG-ds] 已关闭远端连接池: id={}", dataSourceId);
            } catch (Exception e) {
                log.warn("[RAG-ds] 关闭远端连接池异常: id={}, {}", dataSourceId, e.getMessage());
            }
        }
    }

    /** 容器关闭时释放全部远端池。 */
    @PreDestroy
    public void closeAll() {
        for (Integer id : new ArrayList<>(remotePools.keySet())) {
            evict(id);
        }
    }

    /** 加密后的密码密文（保存数据源时调用）。 */
    public static String encryptPassword(String plain) {
        if (plain == null || plain.isEmpty()) {
            return "";
        }
        return AESUtils.encryptIfNeeded(plain);
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
