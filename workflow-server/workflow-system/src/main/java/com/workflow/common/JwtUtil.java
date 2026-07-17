package com.workflow.common;

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
 */
@Slf4j
@Component
public class JwtUtil {
    
    @Value("${jwt.secret:workflow-secret-key-2024}")
    private String secret;
    
    @Value("${jwt.expiration:86400000}")
    private Long expiration;
    
    private static String STATIC_SECRET;
    private static Long STATIC_EXPIRATION;
    private static SecretKey STATIC_KEY;
    
    @PostConstruct
    public void init() {
        STATIC_SECRET = secret;
        STATIC_EXPIRATION = expiration;
        STATIC_KEY = buildSigningKey(secret);
    }
    
    /**
     * 生成JWT Token
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
     */
    public static String getUserIdFromToken(String token) {
        Claims claims = parseToken(token);
        return claims != null ? claims.getSubject() : null;
    }
    
    /**
     * 从Token中获取用户名
     */
    public static String getUsernameFromToken(String token) {
        Claims claims = parseToken(token);
        return claims != null ? claims.get("username", String.class) : null;
    }
    
    /**
     * 解析Token
     */
    public static Claims parseToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(STATIC_KEY)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (Exception e) {
            log.warn("JWT解析失败: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * 验证Token是否有效
     */
    public static boolean validateToken(String token) {
        Claims claims = parseToken(token);
        if (claims == null) {
            return false;
        }
        return !claims.getExpiration().before(new Date());
    }

    private static SecretKey buildSigningKey(String secret) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-512")
                    .digest(secret.getBytes(StandardCharsets.UTF_8));
            return Keys.hmacShaKeyFor(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JWT签名初始化失败", e);
        }
    }
}
