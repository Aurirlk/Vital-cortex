package cn.kmbeast.core.auth;

import cn.kmbeast.config.Nl2SqlReadOnlyDataSourceConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * NL2SQL 只读数据源的<b>防回归</b>测试。
 *
 * <p><b>背景（2026-10-07 实测事故）</b>：本配置类原本<b>无条件</b>注册
 * {@code @Bean DataSource}，并让方法参数注入主数据源以实现「未配置时回落」。
 * 结果<b>整个应用启动失败</b>：Spring Boot 的 {@code DataSourceAutoConfiguration} 带
 * {@code @ConditionalOnMissingBean(DataSource.class)}，只要容器里存在任何 DataSource
 * bean 定义，自动配置就整体跳过 → 主数据源永不创建 →
 * {@code aiConfigMapper → SqlSessionFactory → DataSource} 报
 * {@code No qualifying bean of type 'javax.sql.DataSource'}。
 *
 * <p>这是<b>编译期与单元测试都发现不了</b>的缺陷 —— 436 个用例全绿，
 * 只有真正启动 Spring 上下文才会暴露。故用反射把三条约束固化下来。</p>
 */
class ReadOnlyDataSourceConfigTest {

    @Test
    @DisplayName("必须带 @ConditionalOnProperty：否则会顶掉 Spring Boot 的 DataSource 自动配置")
    void mustBeConditional() {
        ConditionalOnProperty cond = Nl2SqlReadOnlyDataSourceConfig.class.getAnnotation(ConditionalOnProperty.class);
        assertNotNull(cond,
                "Nl2SqlReadOnlyDataSourceConfig 缺 @ConditionalOnProperty —— 会导致 DataSource 自动配置被跳过、应用启动失败");
        assertEquals("crm.ai.nl2sql.read-only", cond.prefix());
        assertTrue(java.util.Arrays.asList(cond.name()).contains("url"),
                "条件字段应为 url（未配置只读地址时不注册第二个数据源）");
    }

    @Test
    @DisplayName("@Bean 方法不得接收 DataSource 参数：接收即等于强制提前实例化主数据源")
    void beanMethodMustNotTakeDataSource() throws Exception {
        Method m = Nl2SqlReadOnlyDataSourceConfig.class.getMethod("nl2sqlReadOnlyDataSource");
        assertNotNull(m.getAnnotation(Bean.class));
        for (Parameter p : m.getParameters()) {
            assertTrue(!javax.sql.DataSource.class.isAssignableFrom(p.getType()),
                    "@Bean 方法不应注入 DataSource —— 那会让主数据源在配置类解析阶段被提前实例化，"
                            + "与 DataSourceAutoConfiguration 的 @ConditionalOnMissingBean 条件冲突");
        }
    }

    @Test
    @DisplayName("注入侧必须用 @Autowired(required=false)：未配置只读源时 bean 不存在，不能强注入")
    void injectionMustBeOptional() throws Exception {
        for (Class<?> c : new Class<?>[]{
                cn.kmbeast.core.nl2sql.DataAgentPipeline.class,
                cn.kmbeast.core.nl2sql.JdbcSchemaIntrospector.class}) {
            boolean found = false;
            for (java.lang.reflect.Field f : c.getDeclaredFields()) {
                if (!javax.sql.DataSource.class.isAssignableFrom(f.getType())) {
                    continue;
                }
                Autowired a = f.getAnnotation(Autowired.class);
                if (a != null) {
                    assertTrue(!a.required(),
                            c.getSimpleName() + "." + f.getName()
                                    + " 的只读数据源注入必须 required=false —— 未配置 crm.ai.nl2sql.read-only.url 时该 bean 根本不存在");
                    found = true;
                }
            }
            assertTrue(found, c.getSimpleName() + " 未找到 @Autowired(required=false) 的只读数据源字段");
        }
    }
}
