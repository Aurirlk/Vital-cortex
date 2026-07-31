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

    @Value("${neo4j.password:***REMOVED***}")
    private String password;

    // Neo4j 客户端在 Neo4jClient.java 中通过 @PostConstruct 初始化
}
