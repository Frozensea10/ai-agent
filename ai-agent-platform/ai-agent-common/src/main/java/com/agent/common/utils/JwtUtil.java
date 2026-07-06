package com.agent.common.utils;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Slf4j
@Component
public class JwtUtil {

    private static final String DEFAULT_SECRET_NOT_ALLOWED = "JWT Secret 未配置，请配置 jwt.secret 属性";
    private static final String TOKEN_TYPE_ACCESS = "access";
    private static final String TOKEN_TYPE_REFRESH = "refresh";
    private static final String CLAIM_USERNAME = "username";
    private static final String CLAIM_TYPE = "type";

    private static final long DEFAULT_ACCESS_TOKEN_EXPIRE = 86400000L;
    private static final long DEFAULT_REFRESH_TOKEN_EXPIRE = 604800000L;

    @Value("${jwt.secret:}")
    private String jwtSecret;

    @Value("${jwt.access-token-expire:" + DEFAULT_ACCESS_TOKEN_EXPIRE + "}")
    private long accessTokenExpire;

    @Value("${jwt.refresh-token-expire:" + DEFAULT_REFRESH_TOKEN_EXPIRE + "}")
    private long refreshTokenExpire;

    private static SecretKey KEY;
    private static long ACCESS_TOKEN_EXPIRE;
    private static long REFRESH_TOKEN_EXPIRE;

    @PostConstruct
    public void init() {
        // 避免多上下文测试或重复初始化导致静态字段被覆盖产生污染
        if (KEY != null) {
            return;
        }
        if (jwtSecret == null || jwtSecret.isBlank()) {
            throw new IllegalStateException(DEFAULT_SECRET_NOT_ALLOWED);
        }
        if (jwtSecret.length() < 32) {
            throw new IllegalStateException("JWT Secret 长度不足 32 字符，请配置安全的密钥");
        }
        KEY = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        ACCESS_TOKEN_EXPIRE = accessTokenExpire;
        REFRESH_TOKEN_EXPIRE = refreshTokenExpire;
    }

    public static String generateAccessToken(Long userId, String username) {
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(CLAIM_USERNAME, username)
                .claim(CLAIM_TYPE, TOKEN_TYPE_ACCESS)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + ACCESS_TOKEN_EXPIRE))
                .signWith(KEY)
                .compact();
    }

    public static String generateRefreshToken(Long userId) {
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(CLAIM_TYPE, TOKEN_TYPE_REFRESH)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + REFRESH_TOKEN_EXPIRE))
                .signWith(KEY)
                .compact();
    }

    public static Claims parseToken(String token) {
        // verifyWith(KEY) 已自动根据密钥长度限制为对应的 HS 算法，
        // 无需显式 setAllowedAlgorithms（jjwt 0.12.5 该方法签名与高版本不兼容）
        return Jwts.parser()
                .verifyWith(KEY)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public static boolean validateToken(String token) {
        try {
            parseToken(token);
            return true;
        } catch (ExpiredJwtException e) {
            log.warn("Token 已过期: {}", e.getMessage());
            return false;
        } catch (UnsupportedJwtException e) {
            log.warn("Token 格式不支持: {}", e.getMessage());
            return false;
        } catch (MalformedJwtException e) {
            log.warn("Token 格式错误: {}", e.getMessage());
            return false;
        } catch (Exception e) {
            log.warn("Token 验证失败: {}", e.getMessage());
            return false;
        }
    }

    public static Long getUserId(String token) {
        Claims claims = parseToken(token);
        return Long.valueOf(claims.getSubject());
    }

    public static String getTokenType(String token) {
        Claims claims = parseToken(token);
        return claims.get(CLAIM_TYPE, String.class);
    }

    public static boolean isAccessToken(String token) {
        return TOKEN_TYPE_ACCESS.equals(getTokenType(token));
    }

    public static boolean isRefreshToken(String token) {
        return TOKEN_TYPE_REFRESH.equals(getTokenType(token));
    }
}
