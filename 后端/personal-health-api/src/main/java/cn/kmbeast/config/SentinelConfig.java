package cn.kmbeast.config;

import com.alibaba.csp.sentinel.annotation.aspectj.SentinelResourceAspect;
import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;

/**
 * Sentinel 限流配置（roadmap S3：无接口级限流缺陷修复）。
 *
 * <p>单机接入 sentinel-core + 控制台（可选）：
 * <ul>
 *   <li>资源名与 {@code @SentinelResource} 注解的 value 一一对应；</li>
 *   <li>规则走 {@link FlowRuleManager#loadRules} 代码加载（单机模式，重启恢复默认）；</li>
 *   <li>生产多实例时建议改用 Nacos 推模式持久化（后续演进项）。</li>
 * </ul>
 *
 * <p>覆盖高危入口：登录（防撞库）、AI 对话（防刷 token 成本）、文件上传（防刷盘）。
 */
@Slf4j
@Configuration
public class SentinelConfig {

    /** Sentinel 控制台地址（可选，不配置则不连接，仅本地限流生效） */
    @Value("${sentinel.dashboard:}")
    private String dashboard;

    // ============ 各资源 QPS 阈值（可按需调整） ============
    @Value("${sentinel.qps.login:10}")
    private int qpsLogin;

    @Value("${sentinel.qps.chat:5}")
    private int qpsChat;

    @Value("${sentinel.qps.upload:20}")
    private int qpsUpload;

    /**
     * 使 @SentinelResource 注解生效（依赖 spring-boot-starter-aop）。
     */
    @Bean
    public SentinelResourceAspect sentinelResourceAspect() {
        return new SentinelResourceAspect();
    }

    @PostConstruct
    public void init() {
        // 控制台地址（可选）：sentinel-transport-simple-http 心跳注册
        if (dashboard != null && !dashboard.trim().isEmpty()) {
            System.setProperty("csp.sentinel.dashboard.server", dashboard.trim());
            System.setProperty("csp.sentinel.api.port", "8719");
            log.info("[Sentinel] 已连接控制台: {}", dashboard);
        } else {
            log.info("[Sentinel] 未配置控制台地址（sentinel.dashboard），仅本地限流生效");
        }

        List<FlowRule> rules = new ArrayList<>();
        rules.add(flowRule("user:login", qpsLogin));
        rules.add(flowRule("ai:chat", qpsChat));
        rules.add(flowRule("ai:chatStream", qpsChat));
        rules.add(flowRule("file:upload", qpsUpload));
        FlowRuleManager.loadRules(rules);
        log.info("[Sentinel] 已加载限流规则: login={} QPS, chat={} QPS, upload={} QPS",
                qpsLogin, qpsChat, qpsUpload);
    }

    private FlowRule flowRule(String resource, int qps) {
        FlowRule rule = new FlowRule();
        rule.setResource(resource);
        rule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        rule.setCount(qps);
        rule.setControlBehavior(RuleConstant.CONTROL_BEHAVIOR_DEFAULT);
        return rule;
    }
}
