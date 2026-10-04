package com.invoicespot.migration;

import static org.assertj.core.api.Assertions.assertThat;

import com.invoicespot.IntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

// An invoice whose lines come to nothing has counted as paid since its
// payments are worked out that way, but invoices from before then were left
// unpaid, and nothing can be paid on them to change it. From V60 they read
// paid, as the app would have them.
class ZeroInvoiceMigrationIntegrationTest extends IntegrationTest {

    @Autowired
    private PostgreSQLContainer<?> postgres;

    @Test
    @DisplayName("an old invoice whose lines come to nothing is paid; one with money owed, or no lines, stays unpaid")
    void zeroInvoicesPaid() {
        try (MigrationDatabase database = MigrationDatabase.create(postgres)) {
            database.migrateTo("1");
            JdbcTemplate jdbc = database.jdbc();
            long owner = jdbc.queryForObject("""
                    insert into users (id, email, username, first_name, last_name)
                    values (?, 'zero@example.test', 'zero', 'Old', 'Owner')
                    returning pkid
                    """, Long.class, UUID.randomUUID());
            jdbc.update("""
                    insert into customers (id, created_by_pkid, name, email, phone_number)
                    values (?, ?, 'Acme', 'acme@clients.test', '+1')
                    """, UUID.randomUUID(), owner);
            long free = invoice(jdbc, owner, "INV-0001");
            line(jdbc, free, "0");
            long owed = invoice(jdbc, owner, "INV-0002");
            line(jdbc, owed, "100");
            long empty = invoice(jdbc, owner, "INV-0003");

            database.migrateTo("59");
            assertThat(status(jdbc, free)).isEqualTo("Not Paid");

            database.migrateToLatest();
            assertThat(status(jdbc, free)).isEqualTo("Paid");
            assertThat(status(jdbc, owed)).isEqualTo("Not Paid");
            assertThat(status(jdbc, empty)).isEqualTo("Not Paid");
        }
    }

    private static long invoice(JdbcTemplate jdbc, long owner, String number) {
        return jdbc.queryForObject("""
                insert into documents (id, created_by_pkid, customer_name, customer_email, document_type,
                                       document_number, rates, currency, created_at, updated_at)
                values (?, ?, 'Acme', 'acme@clients.test', 'Invoice', ?, '0', 'USD',
                        '2026-09-20T06:00:00Z', '2026-09-20T06:00:00Z')
                returning pkid
                """, Long.class, UUID.randomUUID(), owner, number);
    }

    private static void line(JdbcTemplate jdbc, long document, String price) {
        jdbc.update("""
                insert into document_billing_items (document_pkid, position, item_name, unit_price, quantity, discount)
                values (?, 1, 'Work', ?::double precision, 1, '0')
                """, document, price);
    }

    private static String status(JdbcTemplate jdbc, long document) {
        return jdbc.queryForObject("select status from documents where pkid = ?", String.class, document);
    }
}
