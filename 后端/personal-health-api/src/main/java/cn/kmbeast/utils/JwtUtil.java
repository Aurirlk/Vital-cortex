package cn.kmbeast.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

/**
 * JWT Token 工具类
 * 密钥和过期时间通过环境变量配置
 */
@Component
public class JwtUtil {

    @Value("${jwt.secret:${JWT_SECRET:***REMOVED***}}")
    private String privateKey;

    @Value("${jwt.expiration:${JWT_EXPIRATION:604800000}}")
    private Long expiration;

    /**
     * 获取签名密钥
     */
    private SecretKey getSigningKey() {
        // 确保密钥至少256位（32字节）
        byte[] keyBytes = privateKey.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * 生成 token
     *
     * @param id   用户ID
     * @param role 用户角色
     * @return JWT token
     */
    public String toToken(Integer id, Integer role) {
        JwtBuilder jwtBuilder = Jwts.builder();
        return jwtBuilder
                .id(UUID.randomUUID().toString())
                .subject("用户认证")
                .claim("id", id)
                .claim("role", role)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * 解密 TOKEN
     *
     * @param token token信息
     * @return Claims 或 null（如果token无效）
     */
    public Claims fromToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (Exception e) {
            return null;
        }
    }
}
