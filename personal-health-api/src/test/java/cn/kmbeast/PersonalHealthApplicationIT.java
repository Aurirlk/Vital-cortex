package cn.kmbeast;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 全栈 Spring 上下文启动测试（2026-10-25 新增，补齐项目长期「零 {@code @SpringBootTest}」缺口）。
 *
 * <p><b>为什么必须有它</b>：
 * 项目既有 ~930 个用例全部是 Mockito 单测，从不启动 Spring 容器。因此「某个 {@code @Component}
 * 构造期就炸」「配置键拼写错导致 Bean 创建失败」「自动装配冲突」这类问题在 CI 里<b>永远发现不了</b>，
 * 只有真正把上下文拉起来才会暴露。本测试就是这道防线的第一块砖：把整个应用上下文真正启动一次，
 * 证明所有 Bean 能成功装配、所有配置能解析。
 *
 * <p><b>为什么是 {@code *IT} 后缀、不参与 {@code mvn test}</b>：
 * 启动<b>全量</b>上下文需要 MySQL + Redis + Neo4j 同时就绪（见 {@code application.yml}）。
 * 本地缺这些依赖时不应拖挂 {@code mvn test}（项目既有约定，见 {@code pom.xml} 的 surefire 注释：
 * 「集成测试默认不参与 mvn test，需显式跑 mvn verify 才会执行」）。
 * 本测试由 {@code mvn verify}（failsafe 插件）或具备完整依赖的环境执行。
 *
 * <p><b>覆盖范围</b>：仅断言上下文成功加载（不抛异常即过关）。
 * 具体子系统的接线正确性由各自的 *IT（如 {@code CoreBeanWiringIT}）进一步细化。
 */
class PersonalHealthApplicationIT extends BaseIntegrationTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void contextLoads() {
        // 上下文若能成功加载，说明所有 Bean 接线、配置解析、自动装配均无致命错误。
        // 真正有价值的动作是「能启动且不抛异常」；此处的非空断言只是显式化这一约束。
        assertNotNull(applicationContext, "Spring 应用上下文未能加载——存在 Bean 接线或配置错误");
    }
}
