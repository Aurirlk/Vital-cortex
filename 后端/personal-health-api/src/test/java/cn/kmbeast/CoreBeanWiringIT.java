package cn.kmbeast;

import cn.kmbeast.core.graph.Neo4jClient;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 核心基础设施 Bean 接线验证（2026-10-25 新增，{@code *IT} 后缀，由 {@code mvn verify} 执行）。
 *
 * <p>在 {@link PersonalHealthApplicationIT}「上下文能起来」的基础上，进一步确认三个最易在
 * 启动期出错、且 Mockito 单测覆盖不到的集成点确实装配成功：
 * <ul>
 *   <li>MyBatis 的 {@link SqlSessionFactory}（Mapper 扫描、XML 绑定都在这一步完成）；</li>
 *   <li>自定义图数据库客户端 {@link Neo4jClient}（项目未用 Spring Data Neo4j，是手写 bean）；</li>
 *   <li>Redis 的 {@link RedisTemplate}（D-006 整改后新增的缓存层，依赖 RedisConnectionFactory）。</li>
 * </ul>
 *
 * <p>任一 Bean 缺失 / 装配失败，上下文启动即抛异常，本测试随之变红——这正是此前纯单测永远测不到的盲区。
 */
class CoreBeanWiringIT extends BaseIntegrationTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void mybatisSqlSessionFactoryIsWired() {
        assertNotNull(applicationContext.getBean(SqlSessionFactory.class),
                "SqlSessionFactory 未装配——检查 @MapperScan 与 mapper-locations 配置");
    }

    @Test
    void neo4jClientIsWired() {
        assertNotNull(applicationContext.getBean(Neo4jClient.class),
                "自定义 Neo4jClient bean 未装配——检查其 @Component/@Bean 定义");
    }

    @Test
    void redisTemplateIsWired() {
        // 按名获取 RedisConfig 定义的 RedisTemplate<String,Object>（避免与 StringRedisTemplate
        // 产生 NoUniqueBeanDefinitionException，后者是 RedisTemplate 的子类型）。
        assertNotNull(applicationContext.getBean("redisTemplate", RedisTemplate.class),
                "RedisConfig 定义的 RedisTemplate 未装配——检查 RedisConfig 与 spring.data.redis 配置");
        assertNotNull(applicationContext.getBean(StringRedisTemplate.class),
                "StringRedisTemplate 未装配——LoginAttemptGuard / AuthSessionManager 依赖它");
    }
}
