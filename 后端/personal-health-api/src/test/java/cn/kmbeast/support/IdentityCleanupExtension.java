package cn.kmbeast.support;

import cn.kmbeast.context.LocalThreadHolder;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * 身份上下文自动清场扩展（2026-11-08 新增）。
 *
 * <p><b>为什么必须有它</b>：{@link LocalThreadHolder} 是 <b>进程级</b> ThreadLocal，
 * 而 surefire 默认单 fork、所有测试类共用同一个主线程。只要有测试类调了
 * {@code setUserId(...)} 却没在 {@code @AfterEach} 里清掉，身份就会残留给后续测试类。
 *
 * <p>这不是假设，是 CI 上真实发生过的失败（run 42436100407）：
 * <pre>
 * LocalThreadHolderTest.unsetShouldReturnNull:41  expected: &lt;null&gt; but was: &lt;3001&gt;
 * </pre>
 * 本地「931 例全绿」只是执行顺序的运气 —— 换个顺序（CI 的顺序与本地不同）就红。
 * 更危险的是：这种污染会让某些越权断言「因为当前用户恰好是管理员」而假通过。
 *
 * <p><b>做法</b>：在每个测试方法前后各清一次身份。注册方式见
 * {@code src/test/resources/META-INF/services/org.junit.jupiter.api.extension.Extension}
 * 配合 {@code junit-platform.properties} 的自动发现开关，<b>不需要</b>任何测试类去
 * {@code @ExtendWith} —— 存量 94 个测试类零改动即受保护。
 *
 * <p><b>为什么 before 也要清</b>：{@code @BeforeAll} 或静态初始化里设置的身份同样会残留；
 * 只在 after 清，无法保护「第一个跑的那个类」。两头都清才让测试与执行顺序彻底解耦。
 */
public class IdentityCleanupExtension implements BeforeEachCallback, AfterEachCallback {

    @Override
    public void beforeEach(ExtensionContext context) {
        // 建立干净前提：任何测试都不得继承上一个测试（或 @BeforeAll）留下的身份
        LocalThreadHolder.clear();
    }

    @Override
    public void afterEach(ExtensionContext context) {
        // 本测试结束后立即清场，避免泄漏给同线程后续测试
        LocalThreadHolder.clear();
    }
}
