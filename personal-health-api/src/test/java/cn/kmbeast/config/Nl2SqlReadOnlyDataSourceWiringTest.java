package cn.kmbeast.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * NL2SQL 只读数据源的<b>装配防回归</b>测试（真实 Spring 上下文）。
 *
 * <h3>为什么要写成 {@code ApplicationContextRunner} 而不是反射断言</h3>
 * <p>本用例的前身 {@code ReadOnlyDataSourceConfigTest} 用<b>反射</b>断言配置类上有
 * {@code @ConditionalOnProperty} —— 结果把<b>错误的设计本身</b>固化成了护栏：
 * 该注解判的是「属性存在」而非「属性非空」，而 {@code application.yml} 里是
 * {@code url: ${NL2SQL_RO_URL:}}（默认空串），<b>空串同样算存在</b>，条件恒真。
 * 于是第二个 {@code DataSource} bean 恒被注册，Spring Boot 的
 * {@code DataSourceAutoConfiguration}（{@code @ConditionalOnMissingBean(DataSource.class)}）
 * 整体退避，主 {@code dataSource} bean 永不创建，应用<b>在任何 profile 下都启动失败</b>：
 * <pre>A component required a bean named 'dataSource' that could not be found.</pre>
 *
 * <p>教训：这类「bean 装配期」缺陷，<b>反射断言与纯 Mockito 单测都看不见</b>，
 * 必须真的把上下文拉起来。故本用例改为 {@code ApplicationContextRunner}，
 * 只装配 {@code DataSourceAutoConfiguration} + 被测配置，<b>不连真库</b>
 * （HikariCP 用 JavaBean 式构造，池在首次 {@code getConnection()} 前不建连接）。</p>
 */
class Nl2SqlReadOnlyDataSourceWiringTest {

    /** 模拟真实部署：主库 + 只读库都指向同一个 MySQL（不实际连接）。 */
    private static final String MAIN_URL = "jdbc:mysql://localhost:3306/personal_health";
    private static final String RO_URL = "jdbc:mysql://localhost:3306/personal_health";

    private ApplicationContextRunner baseRunner() {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(DataSourceAutoConfiguration.class))
                .withUserConfiguration(Nl2SqlReadOnlyDataSourceProvider.class)
                .withPropertyValues(
                        "spring.datasource.url=" + MAIN_URL,
                        "spring.datasource.username=root",
                        "spring.datasource.password=root");
    }

    @Test
    @DisplayName("配了只读 url 时，主 dataSource 仍必须存在（这是原实现炸掉的场景）")
    void primaryDataSourceSurvivesWhenReadOnlyUrlConfigured() {
        baseRunner()
                .withPropertyValues(
                        "crm.ai.nl2sql.read-only.url=" + RO_URL,
                        "crm.ai.nl2sql.read-only.username=ai_ro",
                        "crm.ai.nl2sql.read-only.password=secret")
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    assertThat(ctx).hasBean("dataSource");
                    assertThat(ctx.getBean("dataSource")).isInstanceOf(DataSource.class);

                    Nl2SqlReadOnlyDataSourceProvider provider =
                            ctx.getBean(Nl2SqlReadOnlyDataSourceProvider.class);
                    assertThat(provider.isConfigured())
                            .as("配了 url 就应该真的建出只读池")
                            .isTrue();
                    assertThat(provider.get()).isInstanceOf(DataSource.class);
                });
    }

    @Test
    @DisplayName("只读 url 为空白时，同样不能顶掉主 dataSource（空串曾被视为「已配置」）")
    void primaryDataSourceSurvivesWhenReadOnlyUrlBlank() {
        baseRunner()
                .withPropertyValues("crm.ai.nl2sql.read-only.url=")
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    assertThat(ctx).hasBean("dataSource");
                    assertThat(ctx.getBean(Nl2SqlReadOnlyDataSourceProvider.class).isConfigured())
                            .as("空白 url 不应建池，应由消费方回落主数据源")
                            .isFalse();
                });
    }

    @Test
    @DisplayName("完全不配只读 url 时，主 dataSource 存在且只读池不建")
    void primaryDataSourceSurvivesWhenReadOnlyUrlAbsent() {
        baseRunner()
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    assertThat(ctx).hasBean("dataSource");
                    assertThat(ctx.getBean(Nl2SqlReadOnlyDataSourceProvider.class).isConfigured())
                            .isFalse();
                });
    }

    @Test
    @DisplayName("提供者本身不得是 DataSource 类型——否则会再次触发自动配置退避")
    void providerMustNotBeADataSourceType() {
        assertThat(DataSource.class.isAssignableFrom(Nl2SqlReadOnlyDataSourceProvider.class))
                .as("只要这个 bean 的声明类型实现了 javax.sql.DataSource，"
                        + "DataSourceAutoConfiguration 就会整体退避，主数据源永不创建")
                .isFalse();
    }
}
