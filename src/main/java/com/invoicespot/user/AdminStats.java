package com.invoicespot.user;

import com.invoicespot.user.dto.AdminStatsResponse;
import java.sql.Timestamp;
import java.time.Instant;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

// Counts for the administration page. Sign-ins come from the audit log, so
// they go back only as far as it does.
@Repository
class AdminStats {

    private final JdbcClient jdbc;

    AdminStats(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    AdminStatsResponse since(Instant since) {
        return jdbc.sql("""
                        select (select count(*) from users) as accounts,
                               (select count(*) from users where active) as active,
                               (select count(distinct r.user_pkid) from user_roles r
                                   join users u on u.pkid = r.user_pkid
                                   where r.role = 'Admin' and u.active) as admins,
                               (select count(*) from users where not is_email_verified) as unverified,
                               (select count(*) from users where created_at >= :since) as joined,
                               (select count(distinct user_pkid) from audit_events
                                   where type = 'SIGN_IN' and user_pkid is not null
                                     and occurred_at >= :since) as signed_in
                        """)
                .param("since", Timestamp.from(since))
                .query((result, rowNumber) -> new AdminStatsResponse(
                        true,
                        result.getLong("accounts"),
                        result.getLong("active"),
                        result.getLong("admins"),
                        result.getLong("unverified"),
                        result.getLong("joined"),
                        result.getLong("signed_in")))
                .single();
    }
}
