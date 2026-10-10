package cn.kmbeast.config;

import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * 数据智能体（受控 NL2SQL）专用的只读数据源。
 *
 * <p><b>为什么需要第二个数据源</b>：NL2SQL 会把模型生成的 SQL 打到真实库上执行。
 * 应用层已有两道兜底 —— {@code MySqlAiSqlGuard}（词法守卫：禁 DML/DDL/黑名单表）
 * 与 {@code TenantSqlRewriter}（逐表租户覆盖），<b>但词法守卫是正则级的，不能作为唯一防线</b>。
 * 在账号层再兜一层只读权限，即使守卫被绕过也无法写入或删库。
 *
 * <p>⛔ <b>不要把主数据源切成只读</b>（即 {@code spring.datasource}）——那会让登录、下单、
 * 发帖等所有写操作全部失败。本类注册的是【第二个】【独立】数据源，只供
 * {@code DataAgentPipeline} 执行查询与 {@code JdbcSchemaIntrospector} 内省表结构使用；
 * 会话落库（{@code ai_conversation}/{@code ai_chat_record}）与审计写入（{@code audit_log}）
 * 仍然走主数据源。
 *
 * <h3>⚠️ 为什么必须加 {@code @ConditionalOnProperty}（2026-10-07 实测教训）</h3>
 * <p>本类原本<b>无条件</b>注册 {@code @Bean DataSource}，并让方法参数注入主数据源以做「未配置时回落」。
 * 结果<b>整个应用启动失败</b>：Spring Boot 的 {@code DataSourceAutoConfiguration} 带
 * {@code @ConditionalOnMissingBean(DataSource.class)} —— 一旦容器里已存在任何 DataSource
 * bean 定义，自动配置就<b>整体跳过</b>，主数据源永远不会创建，随后
 * {@code aiConfigMapper → SqlSessionFactory → DataSource} 报
 * {@code No qualifying bean of type 'javax.sql.DataSource'} 而启动中止。
 *
 * <p>因此改为：<b>仅在真的配置了只读 URL 时才注册第二个数据源</b>，且 {@code @Bean} 方法
 * <b>不接收主数据源参数</b>（接收它等于在配置类解析阶段就强制提前实例化主数据源）。
 * 未配置时由<b>注入方</b>回落（见 {@code DataAgentPipeline#execDataSource()}），
 * 于是未配置环境的行为与改造前完全一致。
 */
@Configuration
@ConditionalOnProperty(prefix = "crm.ai.nl2sql.read-only", name = "url")
public class Nl2SqlReadOnlyDataSourceConfig {

    /** bean 名，注入方用 {@code @Qualifier(Nl2SqlReadOnlyDataSourceConfig.BEAN_NAME)} 引用。 */
    public static final String BEAN_NAME = "nl2sqlReadOnlyDataSource";

    private static final Logger log = LoggerFactory.getLogger(Nl2SqlReadOnlyDataSourceConfig.class);

    @Value("${crm.ai.nl2sql.read-only.url:}")
    private String url;

    @Value("${crm.ai.nl2sql.read-only.username:ai_ro}")
    private String username;

    @Value("${crm.ai.nl2sql.read-only.password:}")
    private String password;

    /**
     * 只读数据源。
     *
     * <p><b>本方法刻意不接收主数据源</b> —— 一旦接收，Spring 会在解析本配置类时就实例化主数据源，
     * 与 {@code DataSourceAutoConfiguration} 的 {@code @ConditionalOnMissingBean} 条件冲突，
     * 导致主数据源根本不被创建（2026-10-07 实测：应用直接启动失败）。
     */
    @Bean(BEAN_NAME)
    public DataSource nl2sqlReadOnlyDataSource() {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(url == null ? "" : url.trim());
        ds.setUsername(username);
        ds.setPassword(password);
        ds.setDriverClassName("com.mysql.cj.jdbc.Driver");
        // 连接级只读：MySQL 驱动会把会话事务置为 READ ONLY（与账号 GRANT 双重保险）
        ds.setReadOnly(true);
        ds.setMaximumPoolSize(5);      // NL2SQL 并发低，小池即可
        ds.setMinimumIdle(1);
        ds.setPoolName("nl2sql-readonly");
        log.info("[NL2SQL] 已启用只读数据源（user={}）：数据智能体查询走独立只读账号。"
                + "未配置 crm.ai.nl2sql.read-only.url 时本 bean 不存在，查询自动回落主数据源。", username);
        return ds;
    }
}
