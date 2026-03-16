package com.workflow.common;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
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
    
    @PostConstruct
    public void init() {
        STATIC_SECRET = secret;
        STATIC_EXPIRATION = expiration;
    }
    
    /**
     * 生成JWT Token
     */
    public static String generateToken(String userId, String username) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + STATIC_EXPIRATION);
        
        return Jwts.builder()
                .setSubject(userId)
                .claim("username", username)
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(SignatureAlgorithm.HS512, STATIC_SECRET)
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
                    .setSigningKey(STATIC_SECRET)
                    .parseClaimsJws(token)
                    .getBody();
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
}
