package com.invoicespot.jobs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.invoicespot.IntegrationTest;
import com.invoicespot.auth.TokenCleanup;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

// The daily jobs turned on, as on a server, but never due on their own while
// the tests run: each call to run() is one instance's daily tick.
@TestPropertySource(properties = {"app.jobs.enabled=true", "app.jobs.cron=-"})
class DailyJobsLockIntegrationTest extends IntegrationTest {

    @Autowired
    private DailyJobs dailyJobs;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private TokenCleanup tokenCleanup;

    // Each test starts with the lock free. Its row is run out rather than
    // deleted, since ShedLock only updates a lock row once it has made one.
    @BeforeEach
    void freeTheLock() {
        jdbcTemplate.update("update shedlock set lock_until = timezone('utc', now())");
    }

    @Test
    @DisplayName("the migrations add the table the job locks are kept in")
    void lockTable() {
        List<String> columns = jdbcTemplate.queryForList("""
                select column_name || ' ' || data_type from information_schema.columns
                where table_name = 'shedlock' order by ordinal_position
                """, String.class);

        assertThat(columns).containsExactly(
                "name character varying",
                "lock_until timestamp without time zone",
                "locked_at timestamp without time zone",
                "locked_by character varying");
    }

    @Test
    @DisplayName("a run is skipped while another instance holds the lock, and goes ahead once that lock has run out")
    void skippedWhileAnotherInstanceHoldsTheLock() {
        jdbcTemplate.update("""
                insert into shedlock (name, lock_until, locked_at, locked_by)
                values ('daily-jobs', timezone('utc', now()) + interval '1 hour', timezone('utc', now()), 'other')
                on conflict (name) do update
                set lock_until = excluded.lock_until, locked_at = excluded.locked_at, locked_by = excluded.locked_by
                """);

        dailyJobs.run();
        verify(tokenCleanup, never()).removeExpired();

        freeTheLock();
        dailyJobs.run();
        verify(tokenCleanup).removeExpired();
    }

    @Test
    @DisplayName("a second run straight after the first is skipped, so a late instance doesn't repeat the day's jobs")
    void runsOncePerTick() {
        dailyJobs.run();
        dailyJobs.run();

        verify(tokenCleanup).removeExpired();
    }
}
