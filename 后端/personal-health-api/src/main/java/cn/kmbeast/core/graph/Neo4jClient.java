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

    @Value("${neo4j.password:***REMOVED***}")
    private String password;

    private Driver driver;

    @PostConstruct
    public void init() {
        try {
            driver = GraphDatabase.driver(uri, AuthTokens.basic(username, password));
            log.info("Neo4j 连接成功: {}", uri);
        } catch (Exception e) {
            log.warn("Neo4j 连接失败: {}", e.getMessage());
        }
    }

    @PreDestroy
    public void close() {
        if (driver != null) {
            driver.close();
        }
    }

    public List<Map<String, Object>> query(String cypher, Map<String, Object> params) {
        if (driver == null) return Collections.emptyList();
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
        return driver != null;
    }
}
