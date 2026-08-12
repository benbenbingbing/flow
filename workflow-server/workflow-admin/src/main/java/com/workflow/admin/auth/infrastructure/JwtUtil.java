package com.workflow.admin.auth.infrastructure;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
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
import java.time.Instant;

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

    /** JWT 外部签名密钥允许的最小字节数。 */
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
     * 为指定刷新会话签发短期 Access Token。
     *
     * @param userId 用户 ID
     * @param username 用户名
     * @param tokenVersion 用户全局令牌版本
     * @param sessionId 刷新会话 ID
     * @param sessionAbsoluteExpiry 刷新会话绝对过期时间
     * @return Access Token 及其过期时间
     */
    public static JwtAccessToken issueAccessToken(
            String userId,
            String username,
            long tokenVersion,
            String sessionId,
            Instant sessionAbsoluteExpiry) {
        return issueAccessToken(
                userId,
                username,
                tokenVersion,
                sessionId,
                Instant.now(),
                sessionAbsoluteExpiry);
    }

    /**
     * 使用指定签发时间生成 Access Token，供会话服务和边界测试使用。
     *
     * @param userId 用户 ID
     * @param username 用户名
     * @param tokenVersion 用户全局令牌版本
     * @param sessionId 刷新会话 ID
     * @param issuedAt Access Token 签发时间
     * @param sessionAbsoluteExpiry 刷新会话绝对过期时间
     * @return Access Token 及其过期时间
     */
    public static JwtAccessToken issueAccessToken(
            String userId,
            String username,
            long tokenVersion,
            String sessionId,
            Instant issuedAt,
            Instant sessionAbsoluteExpiry) {
        Instant configuredExpiry =
                issuedAt.plusMillis(STATIC_EXPIRATION);
        Instant expiry = configuredExpiry.isBefore(sessionAbsoluteExpiry)
                ? configuredExpiry
                : sessionAbsoluteExpiry;
        if (!expiry.isAfter(issuedAt)) {
            throw new IllegalArgumentException("登录会话已过期");
        }

        String token = Jwts.builder()
                .subject(userId)
                .claim("username", username)
                .claim("tokenVersion", tokenVersion)
                .claim("sid", sessionId)
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiry))
                .signWith(STATIC_KEY, Jwts.SIG.HS512)
                .compact();
        return new JwtAccessToken(token, expiry);
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

    public static Long getTokenVersionFromToken(String token) {
        Claims claims = parseToken(token);
        Object value = claims == null ? null : claims.get("tokenVersion");
        return value instanceof Number number ? number.longValue() : null;
    }

    /**
     * 从 Token 中获取刷新会话 ID。
     *
     * @param token JWT Token
     * @return 刷新会话 ID，Token 无效时返回 null
     */
    public static String getSessionIdFromToken(String token) {
        Claims claims = parseToken(token);
        return claims != null ? claims.get("sid", String.class) : null;
    }

    /**
     * 区分有效、过期和非法 JWT，并提取认证需要的声明。
     *
     * @param token JWT Token
     * @return JWT 检查结果
     */
    public static JwtTokenInspection inspectToken(String token) {
        if (token == null || token.isBlank()) {
            return JwtTokenInspection.invalid();
        }
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(STATIC_KEY)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return inspection(
                    JwtTokenInspection.Status.VALID,
                    claims);
        } catch (ExpiredJwtException exception) {
            return inspection(
                    JwtTokenInspection.Status.EXPIRED,
                    exception.getClaims());
        } catch (Exception exception) {
            log.debug(
                    "JWT validation failed: {}",
                    exception.getClass().getSimpleName());
            return JwtTokenInspection.invalid();
        }
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
        return inspectToken(token).status()
                == JwtTokenInspection.Status.VALID;
    }

    private static JwtTokenInspection inspection(
            JwtTokenInspection.Status status,
            Claims claims) {
        if (claims == null) {
            return JwtTokenInspection.invalid();
        }
        String userId = claims.getSubject();
        String username = claims.get("username", String.class);
        Object versionValue = claims.get("tokenVersion");
        Long tokenVersion = versionValue instanceof Number number
                ? number.longValue()
                : null;
        String sessionId = claims.get("sid", String.class);
        Date expiration = claims.getExpiration();
        if (userId == null
                || userId.isBlank()
                || username == null
                || username.isBlank()
                || tokenVersion == null
                || sessionId == null
                || sessionId.isBlank()
                || expiration == null) {
            return JwtTokenInspection.invalid();
        }
        return new JwtTokenInspection(
                status,
                userId,
                username,
                tokenVersion,
                sessionId,
                expiration.toInstant());
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
