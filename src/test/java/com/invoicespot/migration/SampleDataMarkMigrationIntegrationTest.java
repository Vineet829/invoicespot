package com.invoicespot.migration;

import static org.assertj.core.api.Assertions.assertThat;

import com.invoicespot.IntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

// From V64 the business the demo's sample data was added to is marked, where
// nothing in the app changes the mark. A business that held the sample data
// before is found by any of its sample clients still there.
class SampleDataMarkMigrationIntegrationTest extends IntegrationTest {

    @Autowired
    private PostgreSQLContainer<?> postgres;

    @Test
    @DisplayName("a business with a sample client is marked, whichever one is left; others are not")
    void marksTheSampleBusiness() {
        try (MigrationDatabase database = MigrationDatabase.create(postgres)) {
            database.migrateTo("1");
            JdbcTemplate jdbc = database.jdbc();
            long demo = owner(jdbc, "demo");
            long renamed = owner(jdbc, "renamed");
            long real = owner(jdbc, "real");
            client(jdbc, demo, "accounts@harborandpine.example");
            client(jdbc, demo, "billing@bluefin.example");
            // Its first sample client renamed, another one left.
            client(jdbc, renamed, "someone@clients.test");
            client(jdbc, renamed, "Team@MarigoldEvents.example");
            client(jdbc, real, "orders@clients.test");

            database.migrateToLatest();

            assertThat(marked(jdbc, demo)).isTrue();
            assertThat(marked(jdbc, renamed)).isTrue();
            assertThat(marked(jdbc, real)).isFalse();
        }
    }

    private static long owner(JdbcTemplate jdbc, String name) {
        return jdbc.queryForObject("""
                insert into users (id, email, username, first_name, last_name)
                values (?, ?, ?, 'Old', 'Owner')
                returning pkid
                """, Long.class, UUID.randomUUID(), name + "@example.test", name);
    }

    private static void client(JdbcTemplate jdbc, long owner, String email) {
        jdbc.update("""
                insert into customers (id, created_by_pkid, name, email, phone_number)
                values (?, ?, 'Client', ?, '+1')
                """, UUID.randomUUID(), owner, email);
    }

    private static boolean marked(JdbcTemplate jdbc, long owner) {
        return jdbc.queryForObject("""
                select b.sample_data from businesses b
                join business_members m on m.business_pkid = b.pkid
                where m.user_pkid = ? and m.role = 'OWNER'
                """, Boolean.class, owner);
    }
}
