package cn.kmbeast.core.nl2sql;

import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 阶段 4（DataAgent 资产摘取）· 受控 NL2SQL 配置。
 *
 * <p>零新 Maven 依赖，所有值带安全默认值；总开关 {@link #enabled} 默认 <b>false</b>，
 * 开启前必须满足：① 配好 {@code ai_ro} 只读账号 ② 通过 20 条恶意 SQL 拦截测试。</p>
 *
 * <p>参考 DataAgent {@code DataAgentProperties} 收敛而来（仅保留 NL2SQL 相关项）。</p>
 */
@Component
@Getter
@Setter
public class Nl2SqlProperties {

    /** 总开关。默认关闭，绝不默认开启受控 NL2SQL。 */
    @Value("${crm.ai.nl2sql.enabled:false}")
    private boolean enabled;

    /** SQL 方言，传给 prompt 做语法约束。 */
    @Value("${crm.ai.nl2sql.dialect:MySQL}")
    private String dialect;

    /**
     * SQL 生成/执行失败后，故障修复最大重试次数。
     *
     * <p>⚠️ 2026-10-07 修正：原 key 写成 {@code crm.ai.nl2sql.maxSqlRetryCount}（驼峰），
     * 而 application.yml 里是 {@code max-sql-retry-count}（kebab-case）。
     * <b>{@code @Value} 不支持 relaxed binding</b>（那是 {@code @ConfigurationProperties} 的能力），
     * 因此该项一直取不到 yml 的值、只能落到默认值 —— 属于「配置看起来能配、实际不生效」的隐性缺陷。
     * 同批修正的还有 maxColumnsPerTable / maxRows / queryTimeout / sampleLimit。</p>
     */
    @Value("${crm.ai.nl2sql.max-sql-retry-count:10}")
    private int maxSqlRetryCount;

    /**
     * 故障修复重试上限 —— <b>流水线实际使用的那个</b>。
     *
     * <p>与 {@link #maxSqlRetryCount} 的区别：后者只被已废弃的
     * {@link MySqlText2SqlService} 消费；现役的 {@link DataAgentPipeline} 用的是
     * {@code MAX_FIX_RETRY = 2} 硬编码。现把真实值暴露为可配置项，钳制在 0~5
     * （见 {@code DataAgentConfigHolder#getMaxFixRetry}）——上限刻意收紧，
     * 避免管理员误填 10 造成 11 次 LLM 调用的放大攻击。</p>
     */
    @Value("${crm.ai.nl2sql.max-fix-retry:2}")
    private int maxFixRetry;

    /** 单表最大列数（超出截断，防止 prompt 体积爆炸）。 */
    @Value("${crm.ai.nl2sql.max-columns-per-table:50}")
    private int maxColumnsPerTable;

    /** 查询结果最大返回行数（DataAgent 为 1000，本项目收紧到 200）。 */
    @Value("${crm.ai.nl2sql.max-rows:200}")
    private int maxRows;

    /** 单条 SQL 执行超时（秒，DataAgent 为 30，本项目收紧到 10）。 */
    @Value("${crm.ai.nl2sql.query-timeout:10}")
    private int queryTimeout;

    /**
     * 列值采样条数（用于生成枚举示例，可设 0 关闭）。
     *
     * <p>⚠️ 当前<b>未接线</b>：列值采样能力尚未实现，全仓无消费者。保留该项供后续扩展。</p>
     */
    @Value("${crm.ai.nl2sql.sample-limit:99}")
    private int sampleLimit;
}
