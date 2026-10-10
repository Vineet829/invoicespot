package com.invoicespot.migration;

import static org.assertj.core.api.Assertions.assertThat;

import com.invoicespot.IntegrationTest;
import com.invoicespot.common.SecretTokens;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

// Buckets kept under a plain hash before V58, which anyone could match by
// trying every address, are gone after it, even one that would never expire.
class RateLimitIdsMigrationIntegrationTest extends IntegrationTest {

    @Autowired
    private PostgreSQLContainer<?> postgres;

    @Test
    @DisplayName("buckets kept under a plain hash of an address are removed, expiring or not")
    void forgetsUnkeyedIds() {
        try (MigrationDatabase database = MigrationDatabase.create(postgres)) {
            database.migrateTo("57");
            JdbcTemplate jdbc = database.jdbc();
            jdbc.update(
                    "insert into rate_limit_buckets (id, state, expires_at) values (?, ?, ?)",
                    "SIGN_UP:" + SecretTokens.hash("203.0.113.7"),
                    new byte[] {1, 2, 3},
                    System.currentTimeMillis() + 60_000);
            // Its first write was cut short, so the daily cleanup never matches it.
            jdbc.update(
                    "insert into rate_limit_buckets (id, state, expires_at) values (?, null, null)",
                    "FAILED_SIGN_IN:" + SecretTokens.hash("203.0.113.7|ada@example.test"));

            database.migrateToLatest();

            assertThat(jdbc.queryForObject("select count(*) from rate_limit_buckets", Integer.class)).isZero();
        }
    }
}
