package com.invoicespot.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.invoicespot.config.AppProperties;
import com.invoicespot.user.User;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private JwtService jwtService;
    private JwtService otherSecretsService;

    private static User userWithId(UUID id) {
        User user = new User();
        user.setId(id);
        user.setRoles(Set.of("User"));
        return user;
    }

    private static AppProperties propertiesFor(String access, String refresh) {
        return new AppProperties(
                new AppProperties.Jwt(access, refresh, Duration.ofMinutes(10), Duration.ofDays(1)),
                new AppProperties.Cookie("jwt", "/", "None", true),
                new AppProperties.Site("Invoice Spot", "http://localhost:3000", "http://localhost:8080", "support@invoicespot.local"),
                new AppProperties.Verification(Duration.ofMinutes(15)),
                new AppProperties.Upload("uploads"));
    }

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(
                propertiesFor(
                        "test-access-secret-test-access-secret-0123456789",
                        "test-refresh-secret-test-refresh-secret-0123456789"),
                Clock.systemUTC());
        otherSecretsService = new JwtService(
                propertiesFor(
                        "different-access-secret-different-access-0123456789",
                        "different-refresh-secret-different-refresh-0123456789"),
                Clock.systemUTC());
    }

    @Test
    @DisplayName("an access token round-trips back to the issuing user id")
    void accessTokenRoundTrips() {
        UUID id = UUID.randomUUID();
        String token = jwtService.issueAccessToken(userWithId(id));

        assertThat(jwtService.readAccessSubject(token)).contains(id);
    }

    @Test
    @DisplayName("a refresh token round-trips back to the issuing user id")
    void refreshTokenRoundTrips() {
        UUID id = UUID.randomUUID();
        String token = jwtService.issueRefreshToken(userWithId(id));

        assertThat(jwtService.readRefreshSubject(token)).contains(id);
    }

    @Test
    @DisplayName("access and refresh keys are distinct so tokens do not cross-verify")
    void accessAndRefreshKeysDoNotCross() {
        User user = userWithId(UUID.randomUUID());

        assertThat(jwtService.readRefreshSubject(jwtService.issueAccessToken(user))).isEmpty();
        assertThat(jwtService.readAccessSubject(jwtService.issueRefreshToken(user))).isEmpty();
    }

    @Test
    @DisplayName("a token signed with foreign secrets is rejected")
    void foreignlySignedTokenIsRejected() {
        String foreign = otherSecretsService.issueAccessToken(userWithId(UUID.randomUUID()));

        assertThat(jwtService.readAccessSubject(foreign)).isEmpty();
    }

    @Test
    @DisplayName("garbage and tampered tokens are rejected rather than throwing")
    void malformedTokensAreRejected() {
        assertThat(jwtService.readAccessSubject("not-a-jwt")).isEmpty();

        String token = jwtService.issueAccessToken(userWithId(UUID.randomUUID()));
        String tampered = token.substring(0, token.length() - 2) + (token.endsWith("a") ? "bb" : "aa");
        Optional<UUID> subject = jwtService.readAccessSubject(tampered);
        assertThat(subject).isEmpty();
    }

    @Test
    @DisplayName("tokens follow the injected clock: valid within their lifetime, rejected after it")
    void tokensExpireByTheClock() {
        Instant issued = Instant.parse("2026-09-30T10:00:00Z");
        AppProperties properties = propertiesFor(
                "test-access-secret-test-access-secret-0123456789",
                "test-refresh-secret-test-refresh-secret-0123456789");
        UUID id = UUID.randomUUID();
        String token = new JwtService(properties, Clock.fixed(issued, ZoneOffset.UTC)).issueAccessToken(userWithId(id));

        JwtService nineMinutesLater = new JwtService(properties, Clock.fixed(issued.plusSeconds(540), ZoneOffset.UTC));
        JwtService elevenMinutesLater = new JwtService(properties, Clock.fixed(issued.plusSeconds(660), ZoneOffset.UTC));

        assertThat(nineMinutesLater.readAccessSubject(token)).contains(id);
        assertThat(elevenMinutesLater.readAccessSubject(token)).isEmpty();
    }
}
