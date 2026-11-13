package cn.kmbeast.core.graph;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * Neo4j 配置
 */
@Configuration
public class Neo4jConfig {

    @Value("${neo4j.uri:bolt://localhost:7687}")
    private String uri;

    @Value("${neo4j.username:neo4j}")
    private String username;

    // 2026-10-10：原为字面量 ***REMOVED***（脱敏脚本残留），改为空默认，与 Neo4jClient 一致。
    @Value("${neo4j.password:}")
    private String password;

    // Neo4j 客户端在 Neo4jClient.java 中通过 @PostConstruct 初始化
}
