package com.invoicespot.auth;

import com.invoicespot.auth.session.UserSessionRepository;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Removes sign-in and email tokens that can no longer be used: verification
// and reset links past their expiry, and sessions that were signed out or ran
// out.
@Service
public class TokenCleanup {

    private final JdbcTemplate jdbcTemplate;
    private final UserSessionRepository sessionRepository;
    private final Clock clock;

    public TokenCleanup(JdbcTemplate jdbcTemplate, UserSessionRepository sessionRepository, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.sessionRepository = sessionRepository;
        this.clock = clock;
    }

    @Transactional
    public int removeExpired() {
        Instant now = Instant.now(clock);
        int removed = jdbcTemplate.update("delete from verify_reset_tokens where expires_at < ?", Timestamp.from(now));
        return removed + sessionRepository.deleteEnded(now);
    }
}
