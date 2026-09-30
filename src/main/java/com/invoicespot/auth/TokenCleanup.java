package com.invoicespot.auth;

import com.invoicespot.auth.session.UserSessionRepository;
import com.invoicespot.business.InvitationRepository;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Removes sign-in and email tokens that can no longer be used: verification
// and reset links past their expiry, sessions that were signed out or ran
// out, and invitations a month after they ended.
@Service
public class TokenCleanup {

    private final JdbcTemplate jdbcTemplate;
    private final UserSessionRepository sessionRepository;
    private final InvitationRepository invitationRepository;
    private final Clock clock;

    public TokenCleanup(
            JdbcTemplate jdbcTemplate,
            UserSessionRepository sessionRepository,
            InvitationRepository invitationRepository,
            Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.sessionRepository = sessionRepository;
        this.invitationRepository = invitationRepository;
        this.clock = clock;
    }

    @Transactional
    public int removeExpired() {
        Instant now = Instant.now(clock);
        int removed = jdbcTemplate.update("delete from verify_reset_tokens where expires_at < ?", Timestamp.from(now));
        // Invitations are kept a month after they end, so an old link still
        // says what became of it.
        return removed
                + sessionRepository.deleteEnded(now)
                + invitationRepository.deleteFinished(now.minus(Duration.ofDays(30)));
    }
}
