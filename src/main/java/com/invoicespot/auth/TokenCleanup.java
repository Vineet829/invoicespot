package com.invoicespot.auth;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Removes sign-in and email tokens that can no longer be used: verification
// and reset links past their expiry, and refresh tokens that are expired or
// no longer valid.
@Service
public class TokenCleanup {

    private record StoredToken(long userPkid, String token) {}

    private final JdbcTemplate jdbcTemplate;
    private final JwtService jwtService;
    private final Clock clock;

    public TokenCleanup(JdbcTemplate jdbcTemplate, JwtService jwtService, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.jwtService = jwtService;
        this.clock = clock;
    }

    @Transactional
    public int removeExpired() {
        int removed = jdbcTemplate.update(
                "delete from verify_reset_tokens where expires_at < ?", Timestamp.from(Instant.now(clock)));
        List<StoredToken> tokens = jdbcTemplate.query(
                "select user_pkid, token from user_refresh_tokens",
                (row, number) -> new StoredToken(row.getLong("user_pkid"), row.getString("token")));
        for (StoredToken stored : tokens) {
            if (jwtService.readRefreshClaims(stored.token()).isEmpty()) {
                removed += jdbcTemplate.update(
                        "delete from user_refresh_tokens where user_pkid = ? and token = ?",
                        stored.userPkid(),
                        stored.token());
            }
        }
        return removed;
    }
}
