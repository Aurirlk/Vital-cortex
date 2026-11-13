package cn.kmbeast.core.auth;

import cn.kmbeast.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
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

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 阶段 11 · A1：refresh token 的签发 / 轮换 / 撤销不变式。
 *
 * <p>用「内存假 Redis」而不是逐次 stub 返回值：{@code getAndDelete} 真的从 map 里删 key，
 * 因此重放测试测的是<b>行为</b>而非 mock 编排。若把
 * {@link AuthSessionManager#rotateRefreshToken} 里的 {@code getAndDelete} 改成 {@code get}
 * （不删），「重放拒绝」用例必须真红 —— 这是本测试的反向验证点。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("refresh token 轮换与重放防护")
class AuthSessionRefreshTest {

    private static final String VER_KEY = "auth:user:7:ver";
    private static final String STATE_KEY = "auth:user:7:state";

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOps;

    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private AuthSessionManager manager;

    /** 假 Redis：getAndDelete 会真的删除，用来验证一次性语义。 */
    private final Map<String, String> store = new HashMap<>();

    @BeforeEach
    void setUp() {
        store.clear();
        store.put(VER_KEY, "3");   // 当前会话版本
        store.put(STATE_KEY, "0"); // 账号正常
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
        // set(K,V,Duration) 是 void 方法，必须用 doAnswer 而非 when(...).thenAnswer
        doAnswer(inv -> {
            store.put(inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(valueOps).set(anyString(), anyString(), any(Duration.class));
        when(valueOps.get(anyString())).thenAnswer(inv -> store.get((String) inv.getArgument(0)));
        when(valueOps.getAndDelete(anyString()))
                .thenAnswer(inv -> store.remove((String) inv.getArgument(0)));
    }

    /** 签一枚 refresh token 并确认它真的落进了 Redis。 */
    private String seedRefreshToken() {
        String token = manager.issueRefreshToken(7, 2, 3);
        assertNotNull(token, "Redis 可用时应成功签发 refresh token");
        return token;
    }

    @Test
    @DisplayName("轮换成功：换出新 token、保持 ver 不变（不顶掉其它设备）、身份来自服务端存储")
    void rotateReturnsNewTokenAndKeepsVersion() {
        String oldToken = seedRefreshToken();

        AuthSessionManager.RefreshIdentity identity = manager.rotateRefreshToken(oldToken);

        assertNotNull(identity);
        assertEquals(7, identity.userId());
        assertEquals(2, identity.roleId());
        assertEquals(3, identity.ver(), "refresh 不应递增会话版本，否则会把其它设备的 access token 顶掉");
        assertNotNull(identity.refreshToken());
        assertNotEquals(oldToken, identity.refreshToken(), "每次轮换必须换发新 token");
        verify(valueOps).getAndDelete(anyString());
    }

    @Test
    @DisplayName("重放：旧 refresh token 轮换后再次使用必须被拒")
    void replayOfOldTokenIsRejected() {
        String oldToken = seedRefreshToken();
        assertNotNull(manager.rotateRefreshToken(oldToken));

        assertNull(manager.rotateRefreshToken(oldToken),
                "🔴 旧 refresh 仍可用 = 令牌轮换形同虚设，被盗的 token 可无限续期");
    }

    @Test
    @DisplayName("登出/改密后会话版本递增：旧 refresh 立即失效，且不换发新 token")
    void versionMismatchRejectsWithoutIssuingNewToken() {
        String oldToken = seedRefreshToken();
        store.put(VER_KEY, "4"); // 模拟 logout / updatePwd 调用 nextVersion

        assertNull(manager.rotateRefreshToken(oldToken));
        assertEquals("4", store.get(VER_KEY));
    }

    @Test
    @DisplayName("账号被停用：不能靠 refresh 续命")
    void lockedAccountRejected() {
        String oldToken = seedRefreshToken();
        store.put(STATE_KEY, "1");

        assertNull(manager.rotateRefreshToken(oldToken));
    }

    @Test
    @DisplayName("Redis 不可用：fail-closed，刷新失败而不是放行")
    void redisFailureFailsClosed() {
        String oldToken = seedRefreshToken();
        when(valueOps.getAndDelete(anyString()))
                .thenThrow(new RuntimeException("Redis connection refused"));

        assertNull(manager.rotateRefreshToken(oldToken));
    }

    @Test
    @DisplayName("空 token / 伪造 token：拒绝，且不产生任何 Redis 访问")
    void blankOrForgedTokenRejected() {
        assertNull(manager.rotateRefreshToken(null));
        assertNull(manager.rotateRefreshToken("   "));
        verify(valueOps, never()).getAndDelete(anyString());
        assertNull(manager.rotateRefreshToken("never-issued-token"));
    }

    @Test
    @DisplayName("载荷损坏：拒绝，不抛异常")
    void corruptedPayloadRejected() {
        String oldToken = seedRefreshToken();
        store.entrySet().stream()
                .filter(e -> e.getKey().startsWith("auth:refresh:"))
                .forEach(e -> e.setValue("not-a-json"));

        assertNull(manager.rotateRefreshToken(oldToken));
    }

    @Test
    @DisplayName("签发时 Redis 不可用：返回 null，登录主流程不被阻断")
    void issueReturnsNullWhenRedisDown() {
        doThrow(new RuntimeException("Redis connection refused"))
                .when(valueOps).set(anyString(), anyString(), any(Duration.class));

        assertNull(manager.issueRefreshToken(7, 2, 3));
    }

    @Test
    @DisplayName("显式撤销：删除后该 token 无法再轮换")
    void revokeRemovesToken() {
        String token = seedRefreshToken();
        when(stringRedisTemplate.delete(anyString())).thenAnswer(inv -> {
            store.remove((String) inv.getArgument(0));
            return true;
        });

        manager.revokeRefreshToken(token);

        assertNull(manager.rotateRefreshToken(token));
        verify(stringRedisTemplate).delete(any(String.class));
    }
}
