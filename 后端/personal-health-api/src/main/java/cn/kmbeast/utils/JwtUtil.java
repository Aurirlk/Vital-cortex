package cn.kmbeast.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * JWT Token 工具类
 *
 * <p>安全约束（SEC-01 整改）：
 * <ul>
 *   <li>密钥不再提供任何硬编码兜底值，必须由外部注入（环境变量 {@code JWT_SECRET} 或配置项 {@code jwt.secret}）。</li>
 *   <li>启动时强制校验：密钥非空、长度 ≥ 32 字节（HS256 要求 256 位）、且不在已泄露弱密钥黑名单中。</li>
 *   <li>校验不通过直接抛异常终止启动，避免"带着可伪造的令牌上线"。</li>
 * </ul>
 *
 * <p>本类是全系统唯一的 JWT 签发/校验入口。原 {@code JwtUtils} 静态类（硬编码密钥、
 * 且 subject 语义与本类不兼容）已废弃，WebSocket 通过 {@link #getInstance()} 复用本实例。
 */
@Component
public class JwtUtil {

    /**
     * 历史上已提交进代码仓库、必须视为公开泄露的密钥黑名单。
     * 注：早期版本中硬编码的 JWT 兜底密钥（曾在 b50acc7 提交中出现）已随
     * 2026-08 历史重写（git-filter-repo）从仓库中清除，故不再列入黑名单；
     * 若部署端仍使用该旧值，会因长度/强度校验被拒绝启动。
     */
    private static final List<String> LEAKED_SECRETS = Arrays.asList(
            "changeme",
            "secret",
            "your-secret-key"
    );

    /**
     * HS256 要求密钥至少 256 位 = 32 字节。
     */
    private static final int MIN_SECRET_BYTES = 32;

    private static volatile JwtUtil instance;

    @Value("${jwt.secret:}")
    private String privateKey;

    @Value("${jwt.expiration:604800000}")
    private Long expiration;

    private SecretKey signingKey;

    @PostConstruct
    public void init() {
        if (privateKey == null || privateKey.trim().isEmpty()) {
            throw new IllegalStateException(
                    "JWT 密钥未配置，应用拒绝启动。请设置环境变量 JWT_SECRET（建议 64 位随机字符串），" +
                            "或在 application-local.yml 中配置 jwt.secret。" +
                            "生成方式：openssl rand -base64 48");
        }
        String trimmed = privateKey.trim();
        if (LEAKED_SECRETS.contains(trimmed)) {
            throw new IllegalStateException(
                    "检测到使用了已泄露的默认 JWT 密钥，应用拒绝启动。该密钥曾提交进代码仓库，" +
                            "任何人都可据此伪造管理员 Token。请立即更换 JWT_SECRET。");
        }
        byte[] keyBytes = trimmed.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "JWT 密钥强度不足：当前 " + keyBytes.length + " 字节，HS256 要求至少 "
                            + MIN_SECRET_BYTES + " 字节。请更换更长的 JWT_SECRET。");
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        instance = this;
    }

    /**
     * 供非 Spring 托管场景（如 {@code @ServerEndpoint} WebSocket 端点）获取实例。
     *
     * @return 已完成初始化的单例
     */
    public static JwtUtil getInstance() {
        JwtUtil ref = instance;
        if (ref == null) {
            throw new IllegalStateException("JwtUtil 尚未初始化，请确认 Spring 容器已启动完成");
        }
        return ref;
    }

    /**
     * 生成 token（兼容入口，ver=0，等价于无会话版本约束）
     *
     * @param id   用户ID
     * @param role 用户角色
     * @return JWT token
     */
    public String toToken(Integer id, Integer role) {
        return toToken(id, role, 0);
    }

    /**
     * 生成 token（携带会话版本号 ver）
     * <p>roadmap §1.3：ver 由 AuthSessionManager.nextVersion() 签发；锁定 / 登出 / 改密
     * 时版本递增，旧 token 因 ver 不匹配而失效，无需维护黑名单。
     *
     * @param id   用户ID
     * @param role 用户角色
     * @param ver  会话版本号（登录时递增，默认 0）
     * @return JWT token
     */
    public String toToken(Integer id, Integer role, Integer ver) {
        JwtBuilder jwtBuilder = Jwts.builder();
        return jwtBuilder
                .id(UUID.randomUUID().toString())
                .subject(String.valueOf(id))
                .claim("id", id)
                .claim("role", role)
                .claim("ver", ver == null ? 0 : ver)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(signingKey)
                .compact();
    }

    /**
     * 解密 TOKEN
     *
     * @param token token信息
     * @return Claims 或 null（如果token无效）
     */
    public Claims fromToken(String token) {
        if (token == null || token.trim().isEmpty()) {
            return null;
        }
        try {
            return Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 从 token 中提取用户 ID。
     *
     * @param token JWT
     * @return 用户ID，token 无效时返回 null
     */
    public Integer getUserId(String token) {
        Claims claims = fromToken(token);
        if (claims == null) {
            return null;
        }
        return claims.get("id", Integer.class);
    }
}
