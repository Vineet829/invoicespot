package com.invoicespot.auth;

import com.invoicespot.config.AppProperties;
import com.invoicespot.user.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private final SecretKey accessKey;
    private final SecretKey refreshKey;
    private final Duration accessTokenTtl;
    private final Duration refreshTokenTtl;

    public JwtService(AppProperties properties) {
        AppProperties.Jwt jwt = properties.jwt();
        this.accessKey = Keys.hmacShaKeyFor(jwt.accessSecret().getBytes(StandardCharsets.UTF_8));
        this.refreshKey = Keys.hmacShaKeyFor(jwt.refreshSecret().getBytes(StandardCharsets.UTF_8));
        this.accessTokenTtl = jwt.accessTokenTtl();
        this.refreshTokenTtl = jwt.refreshTokenTtl();
    }

    public String issueAccessToken(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(user.getId().toString())
                .claim("roles", user.getRoles())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessTokenTtl)))
                .signWith(accessKey)
                .compact();
    }

    public String issueRefreshToken(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(user.getId().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(refreshTokenTtl)))
                .signWith(refreshKey)
                .compact();
    }

    public Optional<UUID> readAccessSubject(String token) {
        return readSubject(token, accessKey);
    }

    public Optional<UUID> readRefreshSubject(String token) {
        return readSubject(token, refreshKey);
    }

    private Optional<UUID> readSubject(String token, SecretKey key) {
        try {
            Claims claims =
                    Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            return Optional.of(UUID.fromString(claims.getSubject()));
        } catch (JwtException | IllegalArgumentException exception) {
            return Optional.empty();
        }
    }
}
