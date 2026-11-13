package cn.kmbeast.config;

import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

/**
 * 数据智能体（受控 NL2SQL）专用的只读数据源<b>提供者</b>。
 *
 * <p><b>为什么需要第二个数据源</b>：NL2SQL 会把模型生成的 SQL 打到真实库上执行。
 * 应用层已有两道兜底 —— {@code MySqlAiSqlGuard}（词法守卫：禁 DML/DDL/黑名单表）
 * 与 {@code TenantSqlRewriter}（逐表租户覆盖），<b>但词法守卫是正则级的，不能作为唯一防线</b>。
 * 在账号层再兜一层只读权限，即使守卫被绕过也无法写入或删库。
 *
 * <p>⛔ <b>不要把主数据源切成只读</b>（即 {@code spring.datasource}）——那会让登录、下单、
 * 发帖等所有写操作全部失败。本类管理的是【第二个】【独立】连接池，只供
 * {@code DataAgentPipeline} 执行查询、{@code JdbcSchemaIntrospector} 内省表结构、
 * {@code Nl2SqlEvidenceService} 读指标字典使用；会话落库与审计写入仍走主数据源。
 *
 * <h3>⚠️ 为什么本类返回的不是 {@code DataSource} bean（2026-10-12 实测教训）</h3>
 * <p>此前该池由 {@code Nl2SqlReadOnlyDataSourceConfig} 以 {@code @Bean DataSource} 形式注册，
 * 并用 {@code @ConditionalOnProperty(name = "url")} 试图「仅配置了才生效」。
 * 这导致<b>整个后端在任何 profile 下都无法启动</b>，原因是两条叠加：
 * <ol>
 *   <li>Spring Boot 的 {@code DataSourceAutoConfiguration} 带
 *       {@code @ConditionalOnMissingBean(DataSource.class)} —— 容器里一旦已有任何
 *       {@code DataSource} bean 定义，自动配置<b>整体退避</b>，主数据源永不创建，
 *       随后 {@code @Resource(name = "dataSource")} 装配失败：<br>
 *       {@code A component required a bean named 'dataSource' that could not be found.}</li>
 *   <li>{@code @ConditionalOnProperty} 判「属性存在」而非「属性非空」，
 *       而 {@code application.yml} 写的是 {@code url: ${NL2SQL_RO_URL:}}，
 *       默认解析为空串 —— <b>空串同样算「存在」</b>，条件恒真。
 *       实测：显式把 url 置空，仍打印「已启用只读数据源」然后启动失败。</li>
 * </ol>
 *
 * <p>因此改为：本类是个普通的 {@code @Component}，<b>声明类型不是 {@code DataSource}</b>，
 * 不会触发自动配置退避；池仅在 url 非空白时才真正创建，未配置时 {@link #get()} 返回
 * {@code null}，由注入方回落主数据源（{@code execDataSource()}）。
 * 这样「配了 / 没配 / 配了但为空」三种情况都能正常启动。
 */
@Component(Nl2SqlReadOnlyDataSourceProvider.BEAN_NAME)
public class Nl2SqlReadOnlyDataSourceProvider {

    /** bean 名，注入方用 {@code @Qualifier(Nl2SqlReadOnlyDataSourceProvider.BEAN_NAME)} 引用。 */
    public static final String BEAN_NAME = "nl2sqlReadOnlyDataSourceProvider";

    private static final Logger log = LoggerFactory.getLogger(Nl2SqlReadOnlyDataSourceProvider.class);

    @Value("${crm.ai.nl2sql.read-only.url:}")
    private String url;

    @Value("${crm.ai.nl2sql.read-only.username:ai_ro}")
    private String username;

    @Value("${crm.ai.nl2sql.read-only.password:}")
    private String password;

    /** 连接池；未配置只读 URL 时为 {@code null}。 */
    private volatile HikariDataSource dataSource;

    @PostConstruct
    public void init() {
        if (url == null || url.trim().isEmpty()) {
            log.info("[NL2SQL] 未配置 crm.ai.nl2sql.read-only.url，数据智能体查询将回落主数据源。"
                    + "生产环境建议执行 Data/02_迁移脚本/09_create_ai_ro_user.sql 并配置只读账号。");
            return;
        }
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(url.trim());
        ds.setUsername(username);
        ds.setPassword(password);
        ds.setDriverClassName("com.mysql.cj.jdbc.Driver");
        // 连接级只读：MySQL 驱动会把会话事务置为 READ ONLY（与账号 GRANT 双重保险）
        ds.setReadOnly(true);
        ds.setMaximumPoolSize(5);      // NL2SQL 并发低，小池即可
        ds.setMinimumIdle(1);
        ds.setPoolName("nl2sql-readonly");
        this.dataSource = ds;
        log.info("[NL2SQL] 已启用只读数据源（user={}）：数据智能体查询走独立只读账号。", username);
    }

    /**
     * 取只读数据源。
     *
     * @return 只读 {@link DataSource}；未配置 {@code crm.ai.nl2sql.read-only.url} 时返回 {@code null}，
     *         调用方应回落主数据源
     */
    public DataSource get() {
        return dataSource;
    }

    /** 是否已配置并启用了独立的只读数据源。 */
    public boolean isConfigured() {
        return dataSource != null;
    }

    /**
     * 容器关闭时释放连接池。
     *
     * <p>原实现（{@code @Bean DataSource}）由 Spring 自动调用 {@code close()}；
     * 改成普通组件后必须显式声明，否则关闭时连接池不被回收。
     */
    @PreDestroy
    public void close() {
        HikariDataSource ds = this.dataSource;
        if (ds != null) {
            try {
                ds.close();
                log.info("[NL2SQL] 只读数据源连接池已关闭。");
            } catch (Exception e) {
                log.warn("[NL2SQL] 关闭只读数据源连接池时异常：{}", e.getMessage());
            }
        }
    }
}
