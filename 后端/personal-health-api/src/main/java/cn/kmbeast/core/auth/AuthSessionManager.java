package cn.kmbeast.core.auth;

import cn.kmbeast.mapper.UserMapper;
import cn.kmbeast.pojo.entity.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 登录态与会话管理（roadmap §1.3 实现，设计见 docs/defect-roadmap.md §1.3）。
 *
 * <p>三个 Redis 键（均带降级，Redis 不可用时不影响主流程）：
 * <ul>
 *   <li>{@code auth:user:{id}:ver}   —— 会话版本号。登录时递增并写入 JWT claim {@code ver}；
 *       锁定 / 登出 / 改密时再次递增，使旧 token 全部失效（无需黑名单）。</li>
 *   <li>{@code auth:user:{id}:state} —— 账号状态缓存（"1"=锁定，"0"=正常），TTL 60s，
 *       miss 时查库回填，避免 JwtInterceptor 每请求查库。</li>
 *   <li>{@code auth:user:{id}:vip}   —— VIP 标记缓存（"1"=VIP），TTL 60s，miss 查库回填，
 *       供上下文窗口分级（roadmap §1.2 Phase B）与能力下发使用。</li>
 * </ul>
 * 降级策略：Redis 异常时 ver 校验跳过（仅保留 JWT 签名校验）、状态与 VIP 直接查库。
 */
@Slf4j
@Component
public class AuthSessionManager {

    private static final String KEY_VER = "auth:user:%d:ver";
    private static final String KEY_STATE = "auth:user:%d:state";
    private static final String KEY_VIP = "auth:user:%d:vip";
    private static final Duration STATE_TTL = Duration.ofSeconds(60);
    private static final Duration VIP_TTL = Duration.ofSeconds(60);

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private UserMapper userMapper;

    /**
     * 递增会话版本号并返回新值（登录签发 / 锁定 / 登出 / 改密时调用）。
     * Redis 不可用时返回 0（对应旧 token 的 ver=0，仍可通过签名校验）。
     */
    public int nextVersion(Integer userId) {
        if (userId == null) {
            return 0;
        }
        try {
            Long v = stringRedisTemplate.opsForValue().increment(key(KEY_VER, userId));
            return v == null ? 0 : v.intValue();
        } catch (Exception e) {
            log.warn("[Auth] Redis 不可用，会话版本降级为 0: userId={}", userId);
            return 0;
        }
    }

    /**
     * 读取当前会话版本；Redis 不可用或未登录时返回 null（调用方跳过版本校验）。
     */
    public Integer getVersion(Integer userId) {
        if (userId == null) {
            return null;
        }
        try {
            String v = stringRedisTemplate.opsForValue().get(key(KEY_VER, userId));
            return v == null ? null : Integer.valueOf(v);
        } catch (Exception e) {
            log.warn("[Auth] 读取会话版本失败，跳过版本校验: userId={}", userId);
            return null;
        }
    }

    /**
     * 账号是否被锁定（is_login=1）。Redis 缓存 miss 时查库回填；Redis 异常时直接查库。
     */
    public boolean isAccountLocked(Integer userId) {
        if (userId == null) {
            return false;
        }
        try {
            String cached = stringRedisTemplate.opsForValue().get(key(KEY_STATE, userId));
            if (cached != null) {
                return "1".equals(cached);
            }
            boolean locked = queryLockedFromDb(userId);
            setAccountState(userId, locked);
            return locked;
        } catch (Exception e) {
            log.warn("[Auth] 读取账号状态缓存失败，降级查库: userId={}", userId);
            return queryLockedFromDb(userId);
        }
    }

    /**
     * 写账号状态缓存（backUpdate 锁定/解锁后调用，保证即时生效）。
     */
    public void setAccountState(Integer userId, boolean locked) {
        if (userId == null) {
            return;
        }
        try {
            stringRedisTemplate.opsForValue().set(
                    key(KEY_STATE, userId), locked ? "1" : "0", STATE_TTL);
        } catch (Exception e) {
            log.warn("[Auth] 写账号状态缓存失败: userId={}", userId);
        }
    }

    /**
     * 是否 VIP（is_vip=1 且未过期）。Redis 缓存 miss 查库回填；Redis 异常时直接查库。
     */
    public boolean isVip(Integer userId) {
        if (userId == null) {
            return false;
        }
        try {
            String cached = stringRedisTemplate.opsForValue().get(key(KEY_VIP, userId));
            if (cached != null) {
                return "1".equals(cached);
            }
            boolean vip = queryVipFromDb(userId);
            setVip(userId, vip);
            return vip;
        } catch (Exception e) {
            log.warn("[Auth] 读取 VIP 缓存失败，降级查库: userId={}", userId);
            return queryVipFromDb(userId);
        }
    }

    /**
     * 写 VIP 缓存（非关键路径，失败仅记日志）。
     */
    public void setVip(Integer userId, boolean vip) {
        if (userId == null) {
            return;
        }
        try {
            stringRedisTemplate.opsForValue().set(
                    key(KEY_VIP, userId), vip ? "1" : "0", VIP_TTL);
        } catch (Exception e) {
            log.debug("[Auth] 写 VIP 缓存失败: userId={}", userId);
        }
    }

    private boolean queryLockedFromDb(Integer userId) {
        try {
            User user = userMapper.getByActive(User.builder().id(userId).build());
            return user != null && Boolean.TRUE.equals(user.getIsLogin());
        } catch (Exception e) {
            log.warn("[Auth] 查库判定账号状态失败，按未锁定处理: userId={}", userId);
            return false;
        }
    }

    private boolean queryVipFromDb(Integer userId) {
        try {
            User user = userMapper.getByActive(User.builder().id(userId).build());
            if (user == null || !Boolean.TRUE.equals(user.getIsVip())) {
                return false;
            }
            LocalDateTime expire = user.getVipExpireTime();
            return expire == null || expire.isAfter(LocalDateTime.now());
        } catch (Exception e) {
            log.warn("[Auth] 查库判定 VIP 失败，按非 VIP 处理: userId={}", userId);
            return false;
        }
    }

    private String key(String pattern, Integer userId) {
        return String.format(pattern, userId);
    }
}
