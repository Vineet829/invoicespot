package com.invoicespot.migration;

import static org.assertj.core.api.Assertions.assertThat;

import com.invoicespot.IntegrationTest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

// A receipt is paid as it is made and a credit note takes its invoice's terms,
// so neither has a due date, but ones from before that rule kept the one they
// were given, and showed it. From V61 they don't; invoices keep theirs.
class ReceiptDueDateMigrationIntegrationTest extends IntegrationTest {

    @Autowired
    private PostgreSQLContainer<?> postgres;

    @Test
    @DisplayName("an old receipt or credit note loses its due date; an invoice keeps its own")
    void receiptsLoseDueDates() {
        try (MigrationDatabase database = MigrationDatabase.create(postgres)) {
            database.migrateTo("1");
            JdbcTemplate jdbc = database.jdbc();
            long owner = jdbc.queryForObject("""
                    insert into users (id, email, username, first_name, last_name)
                    values (?, 'due@example.test', 'due', 'Old', 'Owner')
                    returning pkid
                    """, Long.class, UUID.randomUUID());
            jdbc.update("""
                    insert into customers (id, created_by_pkid, name, email, phone_number)
                    values (?, ?, 'Acme', 'acme@clients.test', '+1')
                    """, UUID.randomUUID(), owner);
            long receipt = document(jdbc, owner, "Receipt", "REC-0001");
            long invoice = document(jdbc, owner, "Invoice", "INV-0001");

            database.migrateTo("59");
            // Credit notes came after V1, so this one, a draft given a due date,
            // is written before the V60/V61 cleanup.
            long creditNote = jdbc.queryForObject("""
                    insert into documents (id, business_pkid, document_type, document_number, credited_pkid, due_date)
                    select ?, business_pkid, 'Credit Note', 'CN-0001', pkid, due_date from documents where pkid = ?
                    returning pkid
                    """, Long.class, UUID.randomUUID(), invoice);
            assertThat(dueDate(jdbc, receipt)).isNotNull();
            assertThat(dueDate(jdbc, creditNote)).isNotNull();

            database.migrateToLatest();
            assertThat(dueDate(jdbc, receipt)).isNull();
            assertThat(dueDate(jdbc, creditNote)).isNull();
            assertThat(dueDate(jdbc, invoice)).isEqualTo(Instant.parse("2026-10-06T06:30:00Z"));
        }
    }

    private static long document(JdbcTemplate jdbc, long owner, String type, String number) {
        long document = jdbc.queryForObject("""
                insert into documents (id, created_by_pkid, customer_name, customer_email, document_type,
                                       document_number, due_date, rates, currency, created_at, updated_at)
                values (?, ?, 'Acme', 'acme@clients.test', ?, ?, '2026-10-06T06:30:00Z', '0', 'USD',
                        '2026-09-27T06:00:00Z', '2026-09-27T06:00:00Z')
                returning pkid
                """, Long.class, UUID.randomUUID(), owner, type, number);
        jdbc.update("""
                insert into document_billing_items (document_pkid, position, item_name, unit_price, quantity, discount)
                values (?, 1, 'Work', 750, 1, '0')
                """, document);
        return document;
    }

    private static Instant dueDate(JdbcTemplate jdbc, long document) {
        Timestamp due = jdbc.queryForObject("select due_date from documents where pkid = ?", Timestamp.class, document);
        return due == null ? null : due.toInstant();
    }
}
