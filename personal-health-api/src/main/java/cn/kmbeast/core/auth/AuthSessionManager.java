package cn.kmbeast.core.auth;

import cn.kmbeast.mapper.UserMapper;
import cn.kmbeast.pojo.entity.User;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;

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
 * 降级策略：Redis 异常时 ver 校验跳过（仅保留 JWT 签名校验）、状态与 VIP 直接查库；
 * <b>账号状态连查库都失败时按「已锁定」拒绝</b>（fail-closed，2026-10-24 定），VIP 判定失败仍按非 VIP。
 */
@Slf4j
@Component
public class AuthSessionManager {

    private static final String KEY_VER = "auth:user:%d:ver";
    private static final String KEY_STATE = "auth:user:%d:state";
    private static final String KEY_VIP = "auth:user:%d:vip";
    private static final Duration STATE_TTL = Duration.ofSeconds(60);
    private static final Duration VIP_TTL = Duration.ofSeconds(60);

    /** refresh token 存储键前缀，后面拼 token 的 SHA-256（Redis 里不落明文凭证）。 */
    private static final String KEY_REFRESH = "auth:refresh:";
    private static final Duration REFRESH_TTL = Duration.ofDays(7);
    private static final int REFRESH_TOKEN_BYTES = 32;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

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
     * 账号状态。Redis 缓存 miss 时查库回填；Redis 异常时直接查库。
     *
     * <p><b>查不出状态时返回 {@link AccountState#UNKNOWN}，调用方必须按「已锁定」拒绝（fail-closed）</b>：
     * 被停用的账号不该因为数据库报错就恢复访问。代价是数据库抖动的几分钟里正常用户也会被拒 —— 这是
     * 2026-10-24 明确选定的取向，理由见 {@link #queryStateFromDb}。但<b>拒绝措辞要分开</b>：
     * 对真被封禁的人说「已被禁用」是对的，对只是赶上库抖动的人说这句是冤枉，
     * 所以 {@code JwtInterceptor} 按 UNKNOWN 单独回「系统繁忙，请稍后重试」。
     */
    public AccountState accountState(Integer userId) {
        if (userId == null) {
            return AccountState.NORMAL;
        }
        String cached = null;
        try {
            cached = stringRedisTemplate.opsForValue().get(key(KEY_STATE, userId));
        } catch (Exception e) {
            log.warn("[Auth] 读取账号状态缓存失败，降级查库: userId={}", userId);
        }
        if (cached != null) {
            return "1".equals(cached) ? AccountState.LOCKED : AccountState.NORMAL;
        }
        AccountState state = queryStateFromDb(userId);
        if (state == AccountState.UNKNOWN) {
            // 不回填缓存：把一次查库失败写成 60s 的「锁定」，等于把抖动放大成整分钟的全员拒绝，
            // 且数据库恢复后还要等 TTL 到期才放行。下一个请求会重新尝试回源。
            return state;
        }
        setAccountState(userId, state == AccountState.LOCKED);
        return state;
    }

    /**
     * 是否该按「已锁定」拒绝：真锁定（LOCKED）与读不出状态（UNKNOWN）都拒绝（fail-closed）。
     */
    public boolean isAccountLocked(Integer userId) {
        return accountState(userId) != AccountState.NORMAL;
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

    private AccountState queryStateFromDb(Integer userId) {
        try {
            User user = userMapper.getByActive(User.builder().id(userId).build());
            if (user == null) {
                return AccountState.NORMAL;
            }
            return Boolean.TRUE.equals(user.getIsLogin()) ? AccountState.LOCKED : AccountState.NORMAL;
        } catch (Exception e) {
            log.error("[Auth] 查库判定账号状态失败，本次请求按「已锁定」拒绝（fail-closed）: userId={}, err={}",
                    userId, e.getMessage());
            return AccountState.UNKNOWN;
        }
    }

    /**
     * 账号状态（三态）。{@link #UNKNOWN} 只用于「基础设施读不出状态」（Redis 与 MySQL 都失败），
     * 绝不写成缓存。调用方要用它区分两件事：<b>真被封禁</b>（LOCKED）与<b>系统暂时读不出</b>
     * （UNKNOWN），两者的拒绝措辞不同 —— 后者对用户说「账号已被禁用」是冤枉。
     */
    public enum AccountState {
        LOCKED, NORMAL, UNKNOWN
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

    // ==================== refresh token（阶段 11 · A1） ====================

    /**
     * 签发 refresh token（登录成功后调用），TTL 7 天。
     *
     * <p>Redis 里只存 token 的 SHA-256 作键，不存明文；值携带签发时的 {@code ver}，
     * 用于轮换时与当前会话版本比对。<b>Redis 不可用返回 null</b>：登录主流程不因此中断
     * （退化为「无 refresh，access token 到期后重新登录」的旧行为），但绝不在存储不可用时
     * 签出一个无法撤销的本地凭证。
     */
    public String issueRefreshToken(Integer userId, Integer roleId, Integer ver) {
        if (userId == null) {
            return null;
        }
        String token = newRefreshToken();
        JSONObject payload = new JSONObject();
        payload.put("u", userId);
        payload.put("r", roleId);
        payload.put("v", ver == null ? 0 : ver);
        try {
            stringRedisTemplate.opsForValue().set(refreshKey(token), payload.toJSONString(), REFRESH_TTL);
            return token;
        } catch (Exception e) {
            log.warn("[Auth] 签发 refresh token 失败，本次登录不带 refresh token: userId={}", userId);
            return null;
        }
    }

    /**
     * 轮换 refresh token（{@code POST /auth/refresh} 调用）。
     *
     * <p>四道检查，任一不过即返回 null（fail-closed）：
     * <ol>
     *   <li><b>原子取删</b> {@code getAndDelete}：同一枚 token 最多成功一次，
     *       重放 / 并发双刷新只有一个能赢；这是「防重放」的唯一执行点。</li>
     *   <li>载荷完整（u/r/v 均非空）。</li>
     *   <li><b>与既有会话版本号共存</b>：登出 / 改密 / 重新登录都会 {@link #nextVersion}，
     *       ver 不匹配说明会话已作废，旧 refresh 立即失效——无需额外黑名单。</li>
     *   <li>账号未被锁定（被停用账号不能靠 refresh 续命）。</li>
     * </ol>
     * 通过后签发一枚新的 refresh token 返回；旧 token 已被删除，不可再用。
     * Redis 异常一律按「刷新失败」处理，不降级放行。
     */
    public RefreshIdentity rotateRefreshToken(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return null;
        }
        final String key;
        try {
            key = refreshKey(refreshToken);
        } catch (IllegalStateException e) {
            return null;
        }
        String raw;
        try {
            raw = stringRedisTemplate.opsForValue().getAndDelete(key);
        } catch (Exception e) {
            log.warn("[Auth] refresh token 存储不可用，按失效处理（fail-closed）");
            return null;
        }
        if (raw == null) {
            // 不存在 / 已消费（重放） / 已过期，统一拒绝且不区分原因，避免枚举信号
            log.warn("[Auth] refresh token 无效或已被使用，拒绝刷新");
            return null;
        }
        final Integer userId;
        final Integer roleId;
        final Integer ver;
        try {
            JSONObject payload = JSONObject.parseObject(raw);
            userId = payload.getInteger("u");
            roleId = payload.getInteger("r");
            ver = payload.getInteger("v");
        } catch (Exception e) {
            log.warn("[Auth] refresh token 载荷损坏，拒绝刷新");
            return null;
        }
        if (userId == null || roleId == null || ver == null) {
            return null;
        }
        Integer currentVer = getVersion(userId);
        if (currentVer != null && !currentVer.equals(ver)) {
            log.warn("[Auth] refresh token 会话版本不匹配（已登出/改密/重新登录），拒绝刷新: userId={}", userId);
            return null;
        }
        if (isAccountLocked(userId)) {
            log.warn("[Auth] 账号已锁定，拒绝刷新: userId={}", userId);
            return null;
        }
        String next = issueRefreshToken(userId, roleId, ver);
        if (next == null) {
            return null;
        }
        return new RefreshIdentity(userId, roleId, ver, next);
    }

    /** 显式撤销单枚 refresh token（登出时可配合 {@link #nextVersion} 把所有会话一并作废）。 */
    public void revokeRefreshToken(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }
        try {
            stringRedisTemplate.delete(refreshKey(refreshToken));
        } catch (Exception e) {
            log.warn("[Auth] 撤销 refresh token 失败: {}", e.getMessage());
        }
    }

    private String newRefreshToken() {
        byte[] buf = new byte[REFRESH_TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(buf);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }

    private String refreshKey(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return KEY_REFRESH + Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    /** 轮换成功后交给控制器签新 access token 的身份信息（ver 保持不变，避免顶掉其它设备）。 */
    public record RefreshIdentity(Integer userId, Integer roleId, Integer ver, String refreshToken) {
    }

    private String key(String pattern, Integer userId) {
        return String.format(pattern, userId);
    }
}
