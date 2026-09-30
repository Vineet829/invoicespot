package com.invoicespot.auth;

import com.invoicespot.config.AppProperties;
import com.invoicespot.user.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

// Short-lived access tokens. Each names its user and the session it was
// issued for, so ending the session ends the token with it. A sign-in that
// still needs a two-factor code gets a challenge instead: it names the user
// for five minutes, and has no session, so it opens nothing else.
@Service
public class JwtService {

    private static final Duration CHALLENGE_TTL = Duration.ofMinutes(5);
    private static final String CHALLENGE = "two-factor";

    private final SecretKey accessKey;
    private final Duration accessTokenTtl;
    private final Clock clock;

    public JwtService(AppProperties properties, Clock clock) {
        this.clock = clock;
        AppProperties.Jwt jwt = properties.jwt();
        this.accessKey = Keys.hmacShaKeyFor(jwt.accessSecret().getBytes(StandardCharsets.UTF_8));
        this.accessTokenTtl = jwt.accessTokenTtl();
    }

    public String issueAccessToken(User user, UUID sessionId) {
        Instant now = clock.instant();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(user.getId().toString())
                .claim("sid", sessionId.toString())
                .claim("roles", user.getRoles())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessTokenTtl)))
                .signWith(accessKey)
                .compact();
    }

    public String issueTwoFactorChallenge(User user) {
        Instant now = clock.instant();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(user.getId().toString())
                .claim("purpose", CHALLENGE)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(CHALLENGE_TTL)))
                .signWith(accessKey)
                .compact();
    }

    public Optional<UUID> readTwoFactorChallenge(String token) {
        try {
            Claims claims = parse(token);
            return CHALLENGE.equals(claims.get("purpose", String.class))
                    ? Optional.of(UUID.fromString(claims.getSubject()))
                    : Optional.empty();
        } catch (JwtException | IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    public record TokenClaims(UUID subject, UUID sessionId, Instant issuedAt) {}

    // A token without a session is from before sessions, and no longer works.
    public Optional<TokenClaims> readAccessClaims(String token) {
        try {
            Claims claims = parse(token);
            String sessionId = claims.get("sid", String.class);
            if (sessionId == null) {
                return Optional.empty();
            }
            Instant issuedAt = claims.getIssuedAt() == null ? Instant.EPOCH : claims.getIssuedAt().toInstant();
            return Optional.of(new TokenClaims(
                    UUID.fromString(claims.getSubject()), UUID.fromString(sessionId), issuedAt));
        } catch (JwtException | IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(accessKey)
                .clock(() -> Date.from(clock.instant()))
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
