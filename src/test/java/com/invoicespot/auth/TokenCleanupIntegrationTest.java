package com.invoicespot.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.invoicespot.IntegrationTest;
import com.invoicespot.user.User;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class TokenCleanupIntegrationTest extends IntegrationTest {

    @Autowired
    private TokenCleanup tokenCleanup;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private void emailToken(User user, String token, Duration fromNow) {
        Instant now = Instant.now();
        jdbcTemplate.update(
                "insert into verify_reset_tokens (id, user_pkid, token, created_at, expires_at) values (?, ?, ?, ?, ?)",
                UUID.randomUUID(),
                user.getPkid(),
                token,
                Timestamp.from(now),
                Timestamp.from(now.plus(fromNow)));
    }

    private List<String> tokens(String table, User user) {
        return jdbcTemplate.queryForList(
                "select token from " + table + " where user_pkid = ?", String.class, user.getPkid());
    }

    @Test
    @DisplayName("expired email links and refresh tokens that no longer work are removed; the rest stay")
    void removesExpired() {
        User user = createUser();
        emailToken(user, "expired-link", Duration.ofMinutes(-5));
        emailToken(user, "live-link", Duration.ofMinutes(15));
        String live = jwtService.issueRefreshToken(user);
        jdbcTemplate.update("insert into user_refresh_tokens (user_pkid, token) values (?, ?)", user.getPkid(), live);
        jdbcTemplate.update(
                "insert into user_refresh_tokens (user_pkid, token) values (?, ?)", user.getPkid(), "not-a-token");

        assertThat(tokenCleanup.removeExpired()).isGreaterThanOrEqualTo(2);

        assertThat(tokens("verify_reset_tokens", user)).containsExactly("live-link");
        assertThat(tokens("user_refresh_tokens", user)).containsExactly(live);
    }
}
