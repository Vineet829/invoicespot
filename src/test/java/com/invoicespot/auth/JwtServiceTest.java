package com.invoicespot.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.invoicespot.config.AppProperties;
import com.invoicespot.user.User;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private static final String SECRET = "test-access-secret-test-access-secret-0123456789";
    private static final Instant ISSUED = Instant.parse("2026-09-30T10:00:00Z");

    private static User userWithId(UUID id) {
        User user = new User();
        user.setId(id);
        user.setRoles(Set.of("User"));
        return user;
    }

    private static JwtService at(Instant now, String secret) {
        AppProperties properties = new AppProperties(
                new AppProperties.Jwt(secret, Duration.ofMinutes(10), Duration.ofDays(1)),
                new AppProperties.Cookie("jwt", "/", "None", true),
                new AppProperties.Site(
                        "Invoice Spot", "http://localhost:3000", "http://localhost:8080", "support@invoicespot.local"),
                new AppProperties.Verification(Duration.ofMinutes(15)),
                new AppProperties.Upload("uploads"));
        return new JwtService(properties, Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("an access token names its user, its session and when it was issued")
    void roundTrips() {
        UUID user = UUID.randomUUID();
        UUID session = UUID.randomUUID();
        String token = at(ISSUED, SECRET).issueAccessToken(userWithId(user), session);

        assertThat(at(ISSUED, SECRET).readAccessClaims(token))
                .contains(new JwtService.TokenClaims(user, session, ISSUED));
    }

    @Test
    @DisplayName("tokens signed with another secret, garbage and tampered tokens are rejected rather than throwing")
    void rejectsForeignAndMalformed() {
        JwtService service = at(ISSUED, SECRET);
        String foreign = at(ISSUED, "another-secret-another-secret-another-secret-0123")
                .issueAccessToken(userWithId(UUID.randomUUID()), UUID.randomUUID());
        String token = service.issueAccessToken(userWithId(UUID.randomUUID()), UUID.randomUUID());
        String tampered = token.substring(0, token.length() - 2) + (token.endsWith("a") ? "bb" : "aa");

        assertThat(service.readAccessClaims(foreign)).isEmpty();
        assertThat(service.readAccessClaims("not-a-jwt")).isEmpty();
        assertThat(service.readAccessClaims(tampered)).isEmpty();
    }

    @Test
    @DisplayName("a token from before sessions, without one, no longer works")
    void rejectsTokensWithoutSession() {
        String old = Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .issuedAt(Date.from(ISSUED))
                .expiration(Date.from(ISSUED.plusSeconds(600)))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();

        assertThat(at(ISSUED, SECRET).readAccessClaims(old)).isEmpty();
    }

    @Test
    @DisplayName("tokens follow the injected clock: valid within their lifetime, rejected after it")
    void tokensExpireByTheClock() {
        String token = at(ISSUED, SECRET).issueAccessToken(userWithId(UUID.randomUUID()), UUID.randomUUID());

        assertThat(at(ISSUED.plusSeconds(540), SECRET).readAccessClaims(token)).isPresent();
        assertThat(at(ISSUED.plusSeconds(660), SECRET).readAccessClaims(token)).isEmpty();
    }

    @Test
    @DisplayName("a two-factor challenge says how the first step was made, and passes for nothing else")
    void challengeCarriesTheFirstStep() {
        JwtService service = at(ISSUED, SECRET);
        UUID user = UUID.randomUUID();
        String google = service.issueTwoFactorChallenge(userWithId(user), "With Google");
        String password = service.issueTwoFactorChallenge(userWithId(user), null);

        assertThat(service.readTwoFactorChallenge(google)).contains(new JwtService.Challenge(user, "With Google"));
        assertThat(service.readTwoFactorChallenge(password)).contains(new JwtService.Challenge(user, null));
        assertThat(service.readAccessClaims(google)).isEmpty();
        assertThat(service.readSignedRequest(google)).isEmpty();
    }

    @Test
    @DisplayName("a signed request keeps its claims for its time, and is neither an access token nor a challenge")
    void signedRequests() {
        JwtService service = at(ISSUED, SECRET);
        String request = service.issueSignedRequest(Map.of("state", "s-1"), Duration.ofMinutes(10));

        assertThat(service.readSignedRequest(request)).hasValueSatisfying(
                claims -> assertThat(claims).containsEntry("state", "s-1"));
        assertThat(service.readAccessClaims(request)).isEmpty();
        assertThat(service.readTwoFactorChallenge(request)).isEmpty();
        assertThat(at(ISSUED.plus(Duration.ofMinutes(11)), SECRET).readSignedRequest(request)).isEmpty();
        assertThat(service.readSignedRequest(
                        service.issueAccessToken(userWithId(UUID.randomUUID()), UUID.randomUUID())))
                .isEmpty();
    }
}
