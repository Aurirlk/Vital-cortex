package cn.kmbeast.core.auth;

import cn.kmbeast.mapper.UserMapper;
import cn.kmbeast.pojo.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 账号状态校验的降级方向（bug.md §7.2 第 1 条，2026-10-24 定：查不出状态就拒绝）。
 *
 * <p>改前的两处症状：{@code queryLockedFromDb} 的 catch 回 {@code false}（=「没被锁定」），
 * 于是 <b>MySQL 一抖，被停用的账号在 JWT 过期前照常畅通</b>；更糟的是这个 {@code false}
 * 还会被 {@code setAccountState} 当成真实状态写进 Redis，把「查不出来」缓存成 60 秒的「正常」——
 * 期间就算管理员刚把账号禁用，也还得等 TTL 到期才生效。
 *
 * <p>本测试锁两条不变式：<b>读不出状态 → 拒绝</b>，且<b>读不出状态 → 不写缓存</b>。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("账号状态校验：读不出状态按已锁定拒绝")
class AuthSessionLockFailClosedTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOps;

    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private AuthSessionManager manager;

    /** Redis 读缓存返回给定值（null = miss）。 */
    private void cacheReturns(String cached) {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn(cached);
    }

    private void cacheReadFails() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenThrow(new RuntimeException("Redis connection refused"));
    }

    private void dbReturns(User user) {
        when(userMapper.getByActive(any(User.class))).thenReturn(user);
    }

    private void dbFails() {
        when(userMapper.getByActive(any(User.class))).thenThrow(new RuntimeException("Communications link failure"));
    }

    private static User user(Boolean isLogin) {
        return User.builder().id(7).isLogin(isLogin).build();
    }

    @Test
    @DisplayName("查库失败：按「已锁定」拒绝，不再放行被封账号")
    void dbFailureRejects() {
        cacheReturns(null);
        dbFails();

        assertTrue(manager.isAccountLocked(7),
                "🔴 查不出账号状态时放行 = 被停用的账号在数据库报错那一刻照常可用");
    }

    @Test
    @DisplayName("查库失败：不把「查不出来」写成缓存，否则抖动被放大成 60 秒")
    void dbFailureMustNotPoisonCache() {
        cacheReturns(null);
        dbFails();

        manager.isAccountLocked(7);

        verify(stringRedisTemplate.opsForValue(), never())
                .set(anyString(), anyString(), any(java.time.Duration.class));
    }

    @Test
    @DisplayName("Redis 读失败 + 查库也失败：仍然拒绝")
    void bothLayersFailRejects() {
        cacheReadFails();
        dbFails();

        assertTrue(manager.isAccountLocked(7));
    }

    @Test
    @DisplayName("明确锁定：拒绝并回填缓存")
    void lockedAccountRejects() {
        cacheReturns(null);
        dbReturns(user(true));

        assertTrue(manager.isAccountLocked(7));
        verify(valueOps).set(eq("auth:user:7:state"), eq("1"), any(java.time.Duration.class));
    }

    @Test
    @DisplayName("正常账号：放行并回填「0」，下次不再查库")
    void normalAccountAllowsAndCaches() {
        cacheReturns(null);
        dbReturns(user(false));

        assertFalse(manager.isAccountLocked(7));
        verify(valueOps).set(eq("auth:user:7:state"), eq("0"), any(java.time.Duration.class));
    }

    @Test
    @DisplayName("Redis 读失败但库正常：照常放行（fail-closed 只针对「查不出状态」）")
    void redisFailureFallsBackToDbAndAllows() {
        cacheReadFails();
        dbReturns(user(false));

        assertFalse(manager.isAccountLocked(7), "数据库能查出状态时不该把用户挡在外面");
    }

    @Test
    @DisplayName("缓存命中直接采用缓存值，不再查库")
    void cacheHitSkipsDb() {
        cacheReturns("1");

        assertTrue(manager.isAccountLocked(7));
        verify(userMapper, never()).getByActive(any(User.class));
    }

    @Test
    @DisplayName("userId 为空：放行且不查库（登录前/医生端不该被打扰）")
    void nullUserIdSkipsEverything() {
        assertFalse(manager.isAccountLocked(null));
        verify(stringRedisTemplate, never()).opsForValue();
        verify(userMapper, never()).getByActive(any(User.class));
    }
}
