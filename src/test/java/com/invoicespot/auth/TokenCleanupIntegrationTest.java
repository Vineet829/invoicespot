package com.invoicespot.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.invoicespot.IntegrationTest;
import com.invoicespot.business.Business;
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

    private List<String> tokens(User user) {
        return jdbcTemplate.queryForList(
                "select token from verify_reset_tokens where user_pkid = ?", String.class, user.getPkid());
    }

    private List<UUID> sessions(User user) {
        return jdbcTemplate.queryForList(
                "select id from user_sessions where user_pkid = ?", UUID.class, user.getPkid());
    }

    // An invitation sent some days ago and open for a week, then accepted or
    // revoked some days ago when a column is given.
    private void invitation(Business business, String name, int sentDaysAgo, String ended, int endedDaysAgo) {
        Instant now = Instant.now();
        Instant sent = now.minus(Duration.ofDays(sentDaysAgo));
        jdbcTemplate.update("""
                insert into business_invitations (business_pkid, email, role, token_hash, sent_at, expires_at)
                values (?, ?, 'VIEWER', ?, ?, ?)
                """,
                business.getPkid(),
                name + "@example.test",
                UUID.randomUUID().toString(),
                Timestamp.from(sent),
                Timestamp.from(sent.plus(Duration.ofDays(7))));
        if (ended != null) {
            jdbcTemplate.update(
                    "update business_invitations set " + ended + " = ? where business_pkid = ? and email = ?",
                    Timestamp.from(now.minus(Duration.ofDays(endedDaysAgo))),
                    business.getPkid(),
                    name + "@example.test");
        }
    }

    private List<String> invitations(Business business) {
        return jdbcTemplate.queryForList(
                "select email from business_invitations where business_pkid = ? order by email",
                String.class,
                business.getPkid());
    }

    @Test
    @DisplayName("expired email links, and sessions that were signed out or ran out, are removed; the rest stay")
    void removesExpired() {
        User user = createUser();
        emailToken(user, "expired-link", Duration.ofMinutes(-5));
        emailToken(user, "live-link", Duration.ofMinutes(15));
        UUID live = sessionService.start(user).session().getId();
        UUID signedOut = sessionService.start(user).session().getId();
        UUID ranOut = sessionService.start(user).session().getId();
        jdbcTemplate.update("update user_sessions set revoked_at = now() where id = ?", signedOut);
        jdbcTemplate.update("update user_sessions set expires_at = now() - interval '1 minute' where id = ?", ranOut);

        assertThat(tokenCleanup.removeExpired()).isGreaterThanOrEqualTo(3);

        assertThat(tokens(user)).containsExactly("live-link");
        assertThat(sessions(user)).containsExactly(live);
    }

    @Test
    @DisplayName("invitations are removed a month after they were accepted, withdrawn or ran out")
    void removesFinishedInvitations() {
        Business business = businessOf(createUser());
        invitation(business, "waiting", 1, null, 0);
        invitation(business, "expired-lately", 10, null, 0);
        invitation(business, "expired-long-ago", 40, null, 0);
        invitation(business, "accepted-lately", 3, "accepted_at", 2);
        invitation(business, "accepted-long-ago", 40, "accepted_at", 35);
        invitation(business, "revoked-lately", 40, "revoked_at", 5);

        tokenCleanup.removeExpired();

        assertThat(invitations(business)).containsExactly(
                "accepted-lately@example.test",
                "expired-lately@example.test",
                "revoked-lately@example.test",
                "waiting@example.test");
    }
}
