package cn.kmbeast.core.graph;

import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Config;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Neo4j 客户端。
 *
 * <p><b>2026-10-07 增强（阶段 0-B / B2）</b>。此前本类<b>只有 {@code readTransaction}</b> ——
 * 物理上无法写入，这是"图谱至今 0 个节点"的直接原因。本次补齐写路径，并修掉三个隐患：
 *
 * <table>
 *   <caption>修复项</caption>
 *   <tr><th>#</th><th>问题</th><th>修法</th></tr>
 *   <tr>
 *     <td>1</td>
 *     <td>{@code isConnected()} 返回 {@code driver != null}，而
 *         {@code GraphDatabase.driver()} <b>只是建对象、不发一个字节</b> ——
 *         Neo4j 没启动时它照样返回 true。于是 {@link GraphRAG} 以为连上了，
 *         每次查询都真跑一次 bolt 握手、超时、抛异常、返回空列表，
 *         表现为"图谱功能静默失效"。</td>
 *     <td>{@code init()} 建连后立即 {@code verifyConnectivity()}；
 *         {@link #isConnected()} 改为 {@code driver != null && !degraded}。
 *         另加 15s 冷却的 {@link #isHealthy()} 供运行期探活。</td>
 *   </tr>
 *   <tr>
 *     <td>2</td>
 *     <td>连接失败只 {@code log.warn} —— 与"降级成功"的 INFO 混在一起，
 *         线上根本看不出图谱是坏的。</td>
 *     <td>改 {@code log.error} + 置 {@code degraded}，
 *         可由 {@link Neo4jGraphHealthIndicator} 暴露到 /actuator/health。</td>
 *   </tr>
 *   <tr>
 *     <td>3</td>
 *     <td>无写能力、无 schema 能力、无统计能力</td>
 *     <td>新增 {@link #write} / {@link #writeBatch} / {@link #applySchema} / {@link #stats}</td>
 *   </tr>
 * </table>
 *
 * <p><b>兼容性</b>：{@link #query(String, Map)} 行为完全不变（仍是 readTransaction），
 * 因此 {@link GraphRAG} / {@link KnowledgeGraphService} 零回归。
 */
@Slf4j
@Component
public class Neo4jClient {

    @Value("${neo4j.uri:bolt://localhost:7687}")
    private String uri;

    @Value("${neo4j.username:neo4j}")
    private String username;

    @Value("${neo4j.password:***REMOVED***}")
    private String password;

    /** 建连超时（秒）。Neo4j 未启动时不应让应用启动卡住。 */
    private static final int CONNECT_TIMEOUT_SEC = 5;

    /** 运行期探活冷却（毫秒）。避免每个请求都发一次 bolt 握手。 */
    private static final long HEALTH_CHECK_COOLDOWN_MS = 15_000L;

    private volatile Driver driver;

    /** 已确认不可用。置位后 {@link #isConnected()} 立即返回 false，不再空跑。 */
    private final AtomicBoolean degraded = new AtomicBoolean(false);

    private volatile long lastHealthCheckAt = 0L;
    private volatile boolean lastHealthResult = false;

    @PostConstruct
    public void init() {
        try {
            // 显式超时：默认 connectTimeout 是 30s，Neo4j 未启动时会让启动流程明显变慢
            Config config = Config.builder()
                    .withConnectionTimeout(CONNECT_TIMEOUT_SEC, TimeUnit.SECONDS)
                    .build();
            driver = GraphDatabase.driver(uri, AuthTokens.basic(username, password), config);
            // 🔴 关键：driver() 不发网络包，只有 verifyConnectivity() 才真正握手
            driver.verifyConnectivity();
            degraded.set(false);
            log.info("Neo4j 连接成功: {}", uri);
        } catch (Exception e) {
            // 改 error：这是"图谱能力整体不可用"的严重信号，不能淹没在 warn 里
            log.error("Neo4j 连接失败，知识图谱功能将不可用（读取/写入均降级）: uri={}, msg={}",
                    uri, e.getMessage());
            degraded.set(true);
        }
    }

    @PreDestroy
    public void close() {
        Driver d = driver;
        if (d != null) {
            try {
                d.close();
            } catch (Exception e) {
                log.warn("Neo4j 关闭异常: {}", e.getMessage());
            }
        }
    }

    // ==================== 读 ====================

    /**
     * 只读查询（行为与改造前完全一致，保证 GraphRAG 零回归）。
     *
     * @return 结果行；连接不可用或查询异常返回空列表（<b>不抛异常</b>，调用方自然降级）
     */
    public List<Map<String, Object>> query(String cypher, Map<String, Object> params) {
        if (!isConnected()) {
            return Collections.emptyList();
        }
        List<Map<String, Object>> results = new ArrayList<>();
        try (Session session = driver.session()) {
            session.executeRead(tx -> {
                Result result = tx.run(cypher, params == null ? Map.of() : params);
                while (result.hasNext()) {
                    Record record = result.next();
                    Map<String, Object> row = new HashMap<>();
                    record.keys().forEach(key -> row.put(key, record.get(key).asObject()));
                    results.add(row);
                }
                return null;
            });
        } catch (Exception e) {
            log.error("Neo4j 查询失败: {}", e.getMessage());
            degraded.set(true);
        }
        return results;
    }

    // ==================== 写 ====================

    /**
     * 单条写语句（DDL / 小批量 DML）。
     *
     * @throws IllegalStateException Neo4j 不可用时抛出 —— 写操作<b>不能</b>像读操作那样
     *                               静默返回空，否则导入端点会返回"成功"而实际什么都没写
     */
    public void write(String cypher, Map<String, Object> params) {
        requireAvailable();
        try (Session session = driver.session()) {
            session.executeWrite(tx -> {
                tx.run(cypher, params == null ? Map.of() : params).consume();
                return null;
            });
        } catch (Exception e) {
            degraded.set(true);
            throw new IllegalStateException("Neo4j 写入失败: " + e.getMessage(), e);
        }
    }

    /**
     * 批量写（{@code UNWIND $rows AS row ...}）。
     *
     * @return 受影响的写操作数（Neo4j 的 {@code counters().nodesCreated()} 等汇总），
     *         用于 {@code /graph/import*} 报告实际写入量
     */
    public int writeBatch(String cypher, Map<String, Object> params) {
        requireAvailable();
        try (Session session = driver.session()) {
            return session.executeWrite(tx -> {
                org.neo4j.driver.summary.ResultSummary summary =
                        tx.run(cypher, params == null ? Map.of() : params).consume();
                return summary.counters().nodesCreated()
                        + summary.counters().relationshipsCreated()
                        + summary.counters().propertiesSet();
            });
        } catch (Exception e) {
            degraded.set(true);
            throw new IllegalStateException("Neo4j 批量写入失败: " + e.getMessage(), e);
        }
    }

    /**
     * 应用 Schema（约束/索引）。逐条执行，单条失败只记日志不中断 ——
     * 例如 Neo4j 4.x 遇到 5.x 语法会报错，此时不应让整个导入失败。
     *
     * @return 成功执行的条数
     */
    public int applySchema(List<String> statements) {
        if (statements == null || statements.isEmpty()) {
            return 0;
        }
        requireAvailable();
        int ok = 0;
        for (String stmt : statements) {
            try {
                write(stmt, Map.of());
                ok++;
            } catch (Exception e) {
                log.error("Neo4j Schema 语句执行失败（已跳过）: stmt={}, msg={}", stmt, e.getMessage());
            }
        }
        return ok;
    }

    // ==================== 状态 ====================

    /**
     * 是否<b>当前可用</b>。
     *
     * <p>🔴 与旧实现的差别：旧实现 {@code return driver != null} 是<b>假阳性</b> ——
     * {@code GraphDatabase.driver()} 不产生任何网络 I/O，Neo4j 没启动也返回非空 Driver。
     *
     * <p>本方法不发起网络请求（只读 degraded 标记），可安全高频调用。
     */
    public boolean isConnected() {
        return driver != null && !degraded.get();
    }

    /**
     * 主动探活（真发一次 bolt 握手），带 15 秒冷却。
     *
     * <p>用途：Neo4j 起来之后无需重启应用即可恢复图谱能力。
     * 冷却是为了避免"每个聊天请求都握手一次"。
     *
     * @return 当前是否真的连通
     */
    public boolean isHealthy() {
        long now = System.currentTimeMillis();
        if (now - lastHealthCheckAt < HEALTH_CHECK_COOLDOWN_MS) {
            return lastHealthResult;
        }
        boolean healthy;
        try {
            if (driver == null) {
                healthy = false;
            } else {
                driver.verifyConnectivity();
                healthy = true;
            }
        } catch (Exception e) {
            log.warn("Neo4j 探活失败: {}", e.getMessage());
            healthy = false;
        }
        // 探活成功即摘掉 degraded（支持"Neo4j 后启动"场景）
        if (healthy && degraded.compareAndSet(true, false)) {
            log.info("Neo4j 已恢复可用: {}", uri);
        }
        if (!healthy) {
            degraded.set(true);
        }
        lastHealthCheckAt = now;
        lastHealthResult = healthy;
        return healthy;
    }

    /**
     * 是否已进入降级态（供 HealthIndicator 与 /graph/stats 展示）。
     */
    public boolean isDegraded() {
        return degraded.get();
    }

    /**
     * 图谱统计：节点总数、关系总数、各标签计数、各关系类型计数。
     *
     * <p>返回空 Map 表示不可用 —— 调用方需区分"图谱为空"与"图谱挂了"，
     * 见 {@code /graph/stats} 的 {@code status} / {@code backend} 分离设计。
     */
    public Map<String, Object> stats() {
        // 用 LinkedHashMap 而非 Map.of：值需要后续 put，且 Map.of 不可变
        Map<String, Object> out = new LinkedHashMap<>();
        boolean healthy = isHealthy();
        out.put("connected", healthy);
        out.put("uri", uri);
        out.put("degraded", degraded.get());
        if (!healthy) {
            out.put("nodeCount", 0);
            out.put("relationshipCount", 0);
            out.put("labels", Collections.emptyList());
            out.put("relationshipTypes", Collections.emptyList());
            return out;
        }
        try {
            out.put("nodeCount", countScalar("MATCH (n) RETURN count(n) AS c", "c"));
            out.put("relationshipCount", countScalar("MATCH ()-[r]->() RETURN count(r) AS c", "c"));

            List<Map<String, Object>> labels = query(
                    "MATCH (n) UNWIND labels(n) AS l RETURN l AS label, count(*) AS c ORDER BY c DESC",
                    Map.of());
            List<Map<String, Object>> relTypes = query(
                    "MATCH ()-[r]->() RETURN type(r) AS type, count(r) AS c ORDER BY c DESC",
                    Map.of());
            out.put("labels", labels);
            out.put("relationshipTypes", relTypes);
        } catch (Exception e) {
            log.error("Neo4j 统计失败: {}", e.getMessage());
            degraded.set(true);
        }
        return out;
    }

    /**
     * 执行返回单个数值的 Cypher。
     */
    private long countScalar(String cypher, String key) {
        List<Map<String, Object>> rows = query(cypher, Map.of());
        if (rows.isEmpty()) {
            return 0L;
        }
        Object v = rows.get(0).get(key);
        return v instanceof Number n ? n.longValue() : 0L;
    }

    private void requireAvailable() {
        if (driver == null) {
            throw new IllegalStateException("Neo4j 未初始化（driver 为空）");
        }
        if (degraded.get() && !isHealthy()) {
            throw new IllegalStateException("Neo4j 不可用（已降级），无法执行写操作");
        }
    }
}
