package com.workflow.admin.auth.infrastructure;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;

/**
 * JWT工具类
 * <p>
 * 负责生成、解析、校验 JWT Token。使用 HS512 算法签名，
 * 配置项 jwt.secret、jwt.expiration 在 application 中配置。
 * 由于对外暴露静态方法，Bean 属性经 @PostConstruct 转存到静态字段供静态方法使用。
 * </p>
 */
@Slf4j
@Component
public class JwtUtil {
    
    private static final int MINIMUM_SECRET_BYTES = 32;

    /** JWT 签名密钥，必须由外部 Secret 提供。 */
    @Value("${jwt.secret}")
    private String secret;
    
    /** Access Token 有效期，默认 15 分钟，最长 1 小时。 */
    @Value("${jwt.expiration:900000}")
    private Long expiration;
    
    /** 静态化的 Token 有效期 */
    private static Long STATIC_EXPIRATION;
    /** 静态化的签名密钥对象 */
    private static SecretKey STATIC_KEY;
    
    /**
     * Bean 初始化时将配置项转存到静态字段并构建签名密钥
     */
    @PostConstruct
    public void init() {
        validateConfiguration(secret, expiration);
        STATIC_EXPIRATION = expiration;
        STATIC_KEY = buildSigningKey(secret);
    }
    
    /**
     * 生成JWT Token
     *
     * @param userId   用户ID（作为 subject）
     * @param username 用户名（作为 username claim）
     * @return 签名后的 JWT Token 字符串
     */
    public static String generateToken(String userId, String username) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + STATIC_EXPIRATION);
        
        return Jwts.builder()
                .subject(userId)
                .claim("username", username)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(STATIC_KEY, Jwts.SIG.HS512)
                .compact();
    }
    
    /**
     * 从Token中获取用户ID
     *
     * @param token JWT Token
     * @return 用户ID，Token 无效返回 null
     */
    public static String getUserIdFromToken(String token) {
        Claims claims = parseToken(token);
        return claims != null ? claims.getSubject() : null;
    }
    
    /**
     * 从Token中获取用户名
     *
     * @param token JWT Token
     * @return 用户名，Token 无效返回 null
     */
    public static String getUsernameFromToken(String token) {
        Claims claims = parseToken(token);
        return claims != null ? claims.get("username", String.class) : null;
    }
    
    /**
     * 解析Token
     *
     * @param token JWT Token
     * @return Claims 载荷，解析失败返回 null
     */
    public static Claims parseToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(STATIC_KEY)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (Exception e) {
            log.debug("JWT validation failed: {}", e.getClass().getSimpleName());
            return null;
        }
    }
    
    /**
     * 验证Token是否有效
     *
     * @param token JWT Token
     * @return Token 合法且未过期返回 true，否则 false
     */
    public static boolean validateToken(String token) {
        Claims claims = parseToken(token);
        if (claims == null) {
            return false;
        }
        return !claims.getExpiration().before(new Date());
    }

    /**
     * 根据密钥字符串构建签名密钥（对密钥做 SHA-512 摘要以满足 HS512 的密钥长度要求）
     *
     * @param secret 密钥字符串
     * @return SecretKey 签名密钥
     * @throws IllegalStateException 不支持 SHA-512 算法时抛出
     */
    private static SecretKey buildSigningKey(String secret) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-512")
                    .digest(secret.getBytes(StandardCharsets.UTF_8));
            return Keys.hmacShaKeyFor(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JWT签名初始化失败", e);
        }
    }

    private static void validateConfiguration(String configuredSecret, Long configuredExpiration) {
        if (configuredSecret == null
                || configuredSecret.getBytes(StandardCharsets.UTF_8).length < MINIMUM_SECRET_BYTES) {
            throw new IllegalStateException("JWT_SECRET 必须至少包含 32 字节");
        }
        String normalized = configuredSecret.trim().toLowerCase(java.util.Locale.ROOT);
        if (normalized.contains("workflow-secret-key")
                || normalized.contains("replace-with")
                || normalized.contains("changeme")) {
            throw new IllegalStateException("JWT_SECRET 不能使用公开示例值");
        }
        if (configuredExpiration == null
                || configuredExpiration < 60_000L
                || configuredExpiration > 3_600_000L) {
            throw new IllegalStateException("JWT access token 有效期必须在 1 分钟到 1 小时之间");
        }
    }
}
