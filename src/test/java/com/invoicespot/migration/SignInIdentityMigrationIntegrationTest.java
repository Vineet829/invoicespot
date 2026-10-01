package com.invoicespot.migration;

import static org.assertj.core.api.Assertions.assertThat;

import com.invoicespot.IntegrationTest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

// V55 moves each Google ID into a sign-in identity: trimmed, a blank one
// dropped, and one two accounts shared kept by the older account.
class SignInIdentityMigrationIntegrationTest extends IntegrationTest {

    @Autowired
    private PostgreSQLContainer<?> postgres;

    private static long user(JdbcTemplate jdbc, String handle, String googleId) {
        return jdbc.queryForObject("""
                insert into users (id, email, username, first_name, last_name, google_id)
                values (?, ?, ?, 'Old', 'User', ?) returning pkid
                """, Long.class, UUID.randomUUID(), handle + "@example.test", handle, googleId);
    }

    @Test
    @DisplayName("Google IDs become identities, and the old column goes")
    void movesGoogleIds() {
        try (MigrationDatabase database = MigrationDatabase.create(postgres)) {
            database.migrateTo("54");
            JdbcTemplate jdbc = database.jdbc();
            long plain = user(jdbc, "plain", " g-1 ");
            user(jdbc, "blank", "  ");
            long older = user(jdbc, "older", "g-shared");
            user(jdbc, "newer", "g-shared");
            user(jdbc, "none", null);

            database.migrateToLatest();

            List<Map<String, Object>> identities = jdbc.queryForList(
                    "select user_pkid, provider, subject, email from user_identities order by subject");
            assertThat(identities).hasSize(2);
            assertThat(identities.get(0)).containsEntry("user_pkid", plain).containsEntry("provider", "google")
                    .containsEntry("subject", "g-1").containsEntry("email", "plain@example.test");
            assertThat(identities.get(1)).containsEntry("user_pkid", older).containsEntry("subject", "g-shared");
            assertThat(jdbc.queryForObject("""
                    select count(*) from information_schema.columns
                    where table_name = 'users' and column_name = 'google_id'
                    """, Long.class)).isZero();
        }
    }
}
