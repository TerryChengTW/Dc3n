package com.exchange.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

@Component
public class JwtUtil {

    // 從 application.properties 中加載密鑰和過期時間
    @Value("${jwt.secret}")
    private String secretKey;

    @Value("${jwt.expiration}")
    private long jwtExpirationMs;

    // 生成 SecretKey
    private SecretKey getKey() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(secretKey));
    }

    // 生成 JWT，將用戶名存入 claims
    public String generateToken(String userId, String username) {
        return Jwts.builder()
                .claim("username", username)
                .claim("userId", userId)
                .setIssuedAt(new Date())  // 使用 java.util.Date
                .setExpiration(new Date(System.currentTimeMillis() + jwtExpirationMs))  // 設置過期時間
                .signWith(getKey(), SignatureAlgorithm.HS256)  // 使用密鑰和演算法
                .compact();
    }

    // 從 JWT 中提取所有 Claims
    public Claims extractAllClaims(String token) {
        try {
            return Jwts.parserBuilder()
                    .setSigningKey(getKey())  // 設置密鑰
                    .build()  // 構建 JwtParser
                    .parseClaimsJws(token)  // 解析 JWT
                    .getBody();
        } catch (JwtException | IllegalArgumentException e) {
            // 記錄具體的異常信息，並返回具體的錯誤
            System.out.println("JWT Token 驗證失敗: " + e.getMessage());
            return null;
        }
    }

    // 提取用戶名
    public String extractUsername(String token) {
        Claims claims = extractAllClaims(token);
        return claims != null ? claims.get("username", String.class) : null;
    }

    // 提取 userId
    public String extractUserId(String token) {
        return extractAllClaims(token).get("userId", String.class);
    }

    // 驗證 JWT 是否過期
    public boolean isTokenExpired(String token) {
        Claims claims = extractAllClaims(token);
        return claims != null && claims.getExpiration().before(new Date());
    }

    // 驗證 JWT 的合法性
    public boolean validateToken(String token, String username) {
        final String extractedUsername = extractUsername(token);
        return (extractedUsername != null && extractedUsername.equals(username) && !isTokenExpired(token));
    }
}
