package com.invoicespot.migration;

import static org.assertj.core.api.Assertions.assertThat;

import com.invoicespot.IntegrationTest;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

// Bucket4j writes a bucket in two steps: it inserts the id alone, then sets the
// state and expires_at. A row whose second step never came had no expiry, so
// the daily cleanup never matched it. From V59 such a row expires a day on.
class RateLimitExpiryMigrationIntegrationTest extends IntegrationTest {

    private static final long DAY = Duration.ofDays(1).toMillis();
    private static final long MINUTE = Duration.ofMinutes(1).toMillis();

    @Autowired
    private PostgreSQLContainer<?> postgres;

    @Test
    @DisplayName("a bucket whose first write was cut short expires a day on; one written in full keeps its expiry")
    void cutShortBucketsExpire() {
        try (MigrationDatabase database = MigrationDatabase.create(postgres)) {
            database.migrateTo("58");
            JdbcTemplate jdbc = database.jdbc();
            long expiresAt = System.currentTimeMillis() + MINUTE;
            jdbc.update(
                    "insert into rate_limit_buckets (id, state, expires_at) values (?, ?, ?)",
                    "SIGN_UP:written",
                    new byte[] {1, 2, 3},
                    expiresAt);
            // Bucket4j's first write, with no second.
            jdbc.update("insert into rate_limit_buckets (id, state) values (?, null)", "SIGN_UP:cut-short");

            database.migrateToLatest();

            assertThat(timeLeft(jdbc, "SIGN_UP:cut-short")).isBetween(DAY - MINUTE, DAY);
            assertThat(jdbc.queryForObject(
                            "select expires_at from rate_limit_buckets where id = ?", Long.class, "SIGN_UP:written"))
                    .isEqualTo(expiresAt);

            jdbc.update("insert into rate_limit_buckets (id, state) values (?, null)", "FAILED_SIGN_IN:cut-short");
            assertThat(timeLeft(jdbc, "FAILED_SIGN_IN:cut-short")).isBetween(DAY - MINUTE, DAY);
        }
    }

    // Milliseconds until the row expires, by the database's clock.
    private static Long timeLeft(JdbcTemplate jdbc, String id) {
        return jdbc.queryForObject(
                "select expires_at - (extract(epoch from now()) * 1000)::bigint from rate_limit_buckets where id = ?",
                Long.class,
                id);
    }
}
