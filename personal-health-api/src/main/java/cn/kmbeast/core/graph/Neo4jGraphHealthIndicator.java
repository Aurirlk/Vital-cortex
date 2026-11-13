package cn.kmbeast.core.graph;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Neo4j 图谱健康指示器（{@code /actuator/health/neo4jGraph}）。
 *
 * <p><b>2026-10-07 新增（阶段 0-B / B2）</b>。
 * {@link Neo4jClient#init()} 在连接失败时只打了一条日志，运维侧<b>无任何可见信号</b>。
 * 现在降级状态会显式暴露为 {@code DOWN}，并带上本地兜底是否可用 ——
 * 这样"图谱挂了但应用能跑"和"图谱正常"在健康页一眼可分。
 *
 * <p>⚠️ 注意：Neo4j 不可用<b>不代表应用不健康</b>。本项目对它是"可选增强"
 * （有 {@link LocalTripleStore} 兜底），所以这里返回 {@code DOWN} 时
 * 只要本地兜底有数据就仍标 {@code degraded} 而非致命 ——
 * Spring Boot 默认只有 {@code DOWN} 会让整体健康变 {@code DOWN}，
 * 因此本指示器刻意用 {@code UNKNOWN} 表示"可选依赖不可用"。
 */
@Component
public class Neo4jGraphHealthIndicator implements HealthIndicator {

    @Resource
    private Neo4jClient neo4jClient;

    @Resource
    private LocalTripleStore localTripleStore;

    @Override
    public Health health() {
        Map<String, Object> detail = new LinkedHashMap<>();
        boolean connected = neo4jClient.isHealthy();
        detail.put("connected", connected);
        detail.put("degraded", neo4jClient.isDegraded());
        int localTriples = localTripleStore.size();
        detail.put("localTriples", localTriples);
        detail.put("fallback", "LocalTripleStore");

        if (connected) {
            return Health.up().withDetails(detail).build();
        }
        // 可选依赖不可用 → UNKNOWN（不会让 /actuator/health 整体变 DOWN）
        if (localTriples > 0) {
            return Health.status("DEGRADED")
                    .withDetail("connected", false)
                    .withDetail("reason", "Neo4j 不可用，已降级到本地三元组存储")
                    .withDetails(detail)
                    .build();
        }
        return Health.unknown().withDetails(detail)
                .withDetail("reason", "Neo4j 不可用且本地兜底为空，图谱能力整体失效")
                .build();
    }
}
