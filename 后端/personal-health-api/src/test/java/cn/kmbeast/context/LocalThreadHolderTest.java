package cn.kmbeast.context;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 身份 ThreadLocal 单元测试（2026-10-04 构建）
 *
 * <p>{@code LocalThreadHolder} 用 ThreadLocal 暂存当前请求的用户身份，
 * 由 {@code ProtectorAspect} 在 finally 中清理。这里锁定两个真实事故点：
 * <ul>
 *   <li>未清理时，Tomcat 线程复用会把上一个请求的身份带给下一个请求（越权）</li>
 *   <li>clear() 必须真正 remove()，而不是置 null</li>
 * </ul>
 */
@DisplayName("身份上下文（ThreadLocal）")
class LocalThreadHolderTest {

    @AfterEach
    void tearDown() {
        LocalThreadHolder.clear();
    }

    @Test
    @DisplayName("设置后可读出用户 ID 与角色")
    void setThenGet() {
        LocalThreadHolder.setUserId(7, 2);

        assertEquals(7, LocalThreadHolder.getUserId());
        assertEquals(2, LocalThreadHolder.getRoleId());
    }

    @Test
    @DisplayName("未设置时返回 null 而不是抛异常")
    void unsetShouldReturnNull() {
        assertNull(LocalThreadHolder.getUserId());
        assertNull(LocalThreadHolder.getRoleId());
    }

    @Test
    @DisplayName("clear() 后必须读不到旧身份")
    void clearShouldRemoveIdentity() {
        LocalThreadHolder.setUserId(1, 1);
        LocalThreadHolder.clear();

        assertNull(LocalThreadHolder.getUserId(), "clear 后残留身份会导致线程复用时越权");
        assertNull(LocalThreadHolder.getRoleId());
    }

    @Test
    @DisplayName("重复 set 以最后一次为准（不能残留上一次的身份）")
    void repeatedSetShouldOverwrite() {
        LocalThreadHolder.setUserId(1, 1);
        LocalThreadHolder.setUserId(3001, 3);

        assertEquals(3001, LocalThreadHolder.getUserId());
        assertEquals(3, LocalThreadHolder.getRoleId());
    }

    @Test
    @DisplayName("医生身份与用户身份互不污染（role=3 vs role=2）")
    void doctorAndUserIdentitiesShouldNotMix() {
        LocalThreadHolder.setUserId(3001, 3);
        assertEquals(3, LocalThreadHolder.getRoleId());

        LocalThreadHolder.clear();
        LocalThreadHolder.setUserId(7, 2);
        assertEquals(2, LocalThreadHolder.getRoleId());
        assertEquals(7, LocalThreadHolder.getUserId());
    }

    @Test
    @DisplayName("跨线程隔离：子线程读不到父线程身份")
    void shouldNotLeakAcrossThreads() throws Exception {
        LocalThreadHolder.setUserId(1, 1);

        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Integer leaked = pool.submit(LocalThreadHolder::getUserId).get(3, TimeUnit.SECONDS);
            assertNull(leaked, "ThreadLocal 不得跨线程泄漏");
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("模拟 Tomcat 线程复用：清理后新请求读不到上一个用户的身份")
    void threadReuseMustNotLeakPreviousIdentity() throws Exception {
        // 单线程池 = 固定线程复用，与 Tomcat 的行为一致
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            // 请求 1：管理员
            pool.submit(() -> {
                LocalThreadHolder.setUserId(1, 1);
                return LocalThreadHolder.getRoleId();
            }).get(3, TimeUnit.SECONDS);

            // 请求 2：切面鉴权失败走了 finally 清理
            Integer role = pool.submit(() -> {
                LocalThreadHolder.clear();          // 模拟 ProtectorAspect 的 finally
                return LocalThreadHolder.getRoleId();
            }).get(3, TimeUnit.SECONDS);

            assertNull(role, "复用线程时若读到 role=1，普通请求会被当作管理员 —— 严重越权");
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("null 身份也能设置，不抛异常（token 缺 id 的降级场景）")
    void nullIdentityShouldNotThrow() {
        assertDoesNotThrow(() -> LocalThreadHolder.setUserId(null, null));
        assertNull(LocalThreadHolder.getUserId());
        assertNull(LocalThreadHolder.getRoleId());
    }
}
