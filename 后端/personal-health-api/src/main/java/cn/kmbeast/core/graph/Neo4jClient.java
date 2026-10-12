package cn.kmbeast.core.graph;

import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.*;

import org.neo4j.driver.Record;

/**
 * Neo4j 客户端
 * 知识图谱数据库连接和操作
 */
@Slf4j
@Component
public class Neo4jClient {

    @Value("${neo4j.uri:bolt://localhost:7687}")
    private String uri;

    @Value("${neo4j.username:neo4j}")
    private String username;

    /**
     * 2026-10-10 修复：原默认值是字面量 {@code ***REMOVED***} —— 一次密钥脱敏脚本
     * 把原硬编码默认密码替换成了这个占位符，于是「未配置」时程序会拿着
     * {@code ***REMOVED***} 去连 Neo4j。改为空默认：空即「知识图谱未启用」。
     */
    @Value("${neo4j.password:}")
    private String password;

    private Driver driver;

    /** 真实连通性，仅当驱动创建成功且探活通过才为 true */
    private volatile boolean connected = false;

    @PostConstruct
    public void init() {
        if (password == null || password.isBlank()) {
            log.info("Neo4j 未配置（neo4j.password 为空），知识图谱功能停用，图谱查询将返回空结果");
            return;
        }
        try {
            driver = GraphDatabase.driver(uri, AuthTokens.basic(username, password));
            // 2026-10-10：GraphDatabase.driver() 是<b>惰性</b>的，构造成功并不代表连得上，
            // 原实现因此把「连不上」伪装成「连上了但没数据」（isConnected 恒为 true）。
            // 这里显式探活一次，让失败在启动日志里就可见。
            driver.verifyConnectivity();
            connected = true;
            log.info("Neo4j 连接成功: {}", uri);
        } catch (Exception e) {
            log.warn("Neo4j 连接失败，知识图谱功能停用: {}", e.getMessage());
            if (driver != null) {
                try {
                    driver.close();
                } catch (Exception ignored) {
                    // 关闭失败无需处理
                }
                driver = null;
            }
            connected = false;
        }
    }

    @PreDestroy
    public void close() {
        if (driver != null) {
            driver.close();
        }
    }

    public List<Map<String, Object>> query(String cypher, Map<String, Object> params) {
        if (!connected || driver == null) return Collections.emptyList();
        List<Map<String, Object>> results = new ArrayList<>();
        try (Session session = driver.session()) {
            session.readTransaction(tx -> {
                org.neo4j.driver.Result result = tx.run(cypher, params);
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
        }
        return results;
    }

    public boolean isConnected() {
        // 2026-10-10：原为 driver != null，驱动惰性构造成功即恒为 true，
        // 调用方无法区分「图谱没数据」与「图谱挂了」。
        return connected;
    }
}
