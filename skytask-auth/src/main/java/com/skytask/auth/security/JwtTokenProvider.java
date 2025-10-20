package com.skytask.auth.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.security.Key;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenProvider {

    private final Key signingKey;
    private final long accessTokenTtlMinutes;
    private final long refreshTokenTtlHours;

    @Autowired
    public JwtTokenProvider(
            @Value("${skytask.auth.jwt-secret}") String secret,
            @Value("${skytask.auth.access-token-ttl-minutes:60}") long accessTokenTtlMinutes,
            @Value("${skytask.auth.refresh-token-ttl-hours:24}") long refreshTokenTtlHours) {
        byte[] keyBytes;
        try {
            keyBytes = Decoders.BASE64.decode(secret);
        } catch (IllegalArgumentException ex) {
            keyBytes = secret.getBytes();
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        this.accessTokenTtlMinutes = accessTokenTtlMinutes;
        this.refreshTokenTtlHours = refreshTokenTtlHours;
    }

    public String generateAccessToken(
            Long userId,
            String username,
            Long tenantId,
            String tenantCode,
            List<String> roles,
            List<String> permissions) {
        Instant now = Instant.now();
        Instant expiry = now.plus(accessTokenTtlMinutes, ChronoUnit.MINUTES);
        return Jwts.builder()
                .setSubject(String.valueOf(userId))
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(expiry))
                .addClaims(Map.of(
                        "username", username,
                        "tenantId", tenantId,
                        "tenant", tenantCode,
                        "roles", roles,
                        "permissions", permissions,
                        "type", "access"))
                .signWith(signingKey, SignatureAlgorithm.HS256)
                .compact();
    }

    public String generateRefreshToken(Long userId, String tenantCode) {
        Instant now = Instant.now();
        Instant expiry = now.plus(refreshTokenTtlHours, ChronoUnit.HOURS);
        return Jwts.builder()
                .setSubject(String.valueOf(userId))
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(expiry))
                .addClaims(Map.of(
                        "tenant", tenantCode,
                        "type", "refresh"))
                .signWith(signingKey, SignatureAlgorithm.HS256)
                .compact();
    }

    public Jws<Claims> parseToken(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(signingKey)
                .build()
                .parseClaimsJws(token);
    }

    public long getRefreshTokenTtlHours() {
        return refreshTokenTtlHours;
    }

    public long getAccessTokenTtlMinutes() {
        return accessTokenTtlMinutes;
    }
}
