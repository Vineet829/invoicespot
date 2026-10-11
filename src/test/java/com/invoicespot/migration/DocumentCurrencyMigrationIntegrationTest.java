package com.invoicespot.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.invoicespot.IntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

// A document made through the API without a currency had none. From V62 each
// has its business's default currency, and none can be without one again.
class DocumentCurrencyMigrationIntegrationTest extends IntegrationTest {

    @Autowired
    private PostgreSQLContainer<?> postgres;

    @Test
    @DisplayName("a document without a currency takes its business's default one; others keep theirs")
    void documentsWithoutCurrency() {
        try (MigrationDatabase database = MigrationDatabase.create(postgres)) {
            database.migrateTo("1");
            JdbcTemplate jdbc = database.jdbc();
            long rupees = owner(jdbc, "rupees");
            long plain = owner(jdbc, "plain");
            long missing = document(jdbc, rupees, null, "INV-0001");
            long blank = document(jdbc, rupees, "", "INV-0002");
            long euros = document(jdbc, rupees, "EUR", "INV-0003");
            long noSettings = document(jdbc, plain, null, "INV-0001");

            database.migrateTo("61");
            jdbc.update("""
                    insert into business_settings (id, business_pkid, default_currency)
                    select gen_random_uuid(), business_pkid, 'INR' from business_members
                    where user_pkid = ? and role = 'OWNER'
                    """, rupees);

            database.migrateToLatest();
            assertThat(currency(jdbc, missing)).isEqualTo("INR");
            assertThat(currency(jdbc, blank)).isEqualTo("INR");
            assertThat(currency(jdbc, euros)).isEqualTo("EUR");
            assertThat(currency(jdbc, noSettings)).isEqualTo("USD");
            assertThatThrownBy(() -> jdbc.update("update documents set currency = null where pkid = ?", euros))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    private static long owner(JdbcTemplate jdbc, String name) {
        return jdbc.queryForObject("""
                insert into users (id, email, username, first_name, last_name)
                values (?, ?, ?, 'Old', 'Owner')
                returning pkid
                """, Long.class, UUID.randomUUID(), name + "@example.test", name);
    }

    private static long document(JdbcTemplate jdbc, long owner, String currency, String number) {
        return jdbc.queryForObject("""
                insert into documents (id, created_by_pkid, customer_name, customer_email, document_type,
                                       document_number, rates, currency, created_at, updated_at)
                values (?, ?, 'Acme', 'acme@clients.test', 'Invoice', ?, '0', ?,
                        '2026-09-27T06:00:00Z', '2026-09-27T06:00:00Z')
                returning pkid
                """, Long.class, UUID.randomUUID(), owner, number, currency);
    }

    private static String currency(JdbcTemplate jdbc, long document) {
        return jdbc.queryForObject("select currency from documents where pkid = ?", String.class, document);
    }
}
