package com.invoicespot.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import com.invoicespot.IntegrationTest;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

// Documents in the shapes the first versions left behind: text tax rates over
// 100 or not numbers at all, lines without a quantity, no lines, no number, no
// currency, and payments of nothing. They are written into the first schema
// and carried through every migration since. On the way their numbers, money
// and payments never change. Their businesses' time zones are saved as Java
// names, including ones the database doesn't know or reads differently, and
// one owner has a business name, a city and a logo while the others don't.
class LegacyDocumentsMigrationIntegrationTest extends IntegrationTest {

    @Autowired
    private PostgreSQLContainer<?> postgres;

    private MigrationDatabase database;
    private JdbcTemplate jdbc;

    @BeforeEach
    void legacyRecords() {
        database = MigrationDatabase.create(postgres);
        database.migrateTo("1");
        jdbc = database.jdbc();

        // One owner later chose India's time zone under the name Chrome gives
        // it, one chose an offset, and one never chose a time zone.
        long kolkata = user("kolkata");
        long utc = user("utc");
        long offset = user("offset");
        jdbc.update("update users set business_name = 'Chai & Co', city = 'Kolkata', avatar = '/uploads/chai.png'"
                + " where pkid = ?", kolkata);
        customer(kolkata, "acme@clients.test");
        customer(utc, "zenith@clients.test");
        customer(offset, "orbit@clients.test");

        // Made at 20:00 UTC, already the next morning in Kolkata.
        long partPaid = document(kolkata, "acme@clients.test", "Invoice", "INV-0001", "18", "INR",
                "2025-03-31T20:00:00Z");
        line(partPaid, 1, "Design", "1000", 1, "10");
        payment(partPaid, 1, "2025-04-05", "400", "Bank Transfer");

        long nothingPaid = document(kolkata, "acme@clients.test", "Invoice", "INV-0002", "7.25", "INR",
                "2025-05-10T06:00:00Z");
        line(nothingPaid, 1, "Support", "250", null, "0");
        line(nothingPaid, 2, "Hosting", "99.99", 3, "a tenth");
        payment(nothingPaid, 1, "2025-05-12", "0", "Cash");
        payment(nothingPaid, 2, null, null, null);

        document(kolkata, "acme@clients.test", "Invoice", "INV-0003", "150", "INR", "2025-06-01T00:00:00Z");

        long unnumbered = document(utc, "zenith@clients.test", "Invoice", null, "eighteen", null,
                "2025-03-31T23:30:00Z");
        line(unnumbered, 1, "Consulting", "80", 2, "5");
        long receipt = document(utc, "zenith@clients.test", "Receipt", "REC-0001", "0", "USD",
                "2025-07-04T12:00:00Z");
        line(receipt, 1, "Workshop", "500", 1, "0");
        long quotation = document(utc, "zenith@clients.test", "Quotation", "QUO-0001", "10", "USD",
                "2025-08-15T09:00:00Z");
        line(quotation, 1, "Retainer", "1200", 1, "0");

        // 20:00 UTC is already 01:30 the next day at UTC+05:30.
        long ahead = document(offset, "orbit@clients.test", "Invoice", "INV-0100", "5", "EUR", "2025-01-31T20:00:00Z");
        line(ahead, 1, "Audit", "300", 1, "0");

        database.migrateTo("21");
        settings(kolkata, "Asia/Calcutta", "GST");
        settings(offset, "UTC+05:30", "VAT");
    }

    private void settings(long owner, String timeZone, String taxLabel) {
        jdbc.update("insert into business_settings (id, user_pkid, time_zone, tax_label) values (?, ?, ?, ?)",
                UUID.randomUUID(), owner, timeZone, taxLabel);
    }

    @AfterEach
    void tearDown() {
        database.close();
    }

    private long user(String handle) {
        return jdbc.queryForObject("""
                insert into users (id, email, username, first_name, last_name) values (?, ?, ?, 'Old', 'Owner')
                returning pkid
                """, Long.class, UUID.randomUUID(), handle + "@example.test", handle);
    }

    private void customer(long owner, String email) {
        jdbc.update("""
                insert into customers (id, created_by_pkid, name, email, phone_number) values (?, ?, ?, ?, '+91 1')
                """, UUID.randomUUID(), owner, email.substring(0, email.indexOf('@')), email);
    }

    private long document(long owner, String clientEmail, String type, String number, String rates, String currency,
            String createdAt) {
        return jdbc.queryForObject("""
                insert into documents (id, created_by_pkid, customer_name, customer_email, document_type,
                                       document_number, rates, currency, created_at, updated_at)
                values (?, ?, 'Client', ?, ?, ?, ?, ?, ?::timestamptz, ?::timestamptz)
                returning pkid
                """, Long.class, UUID.randomUUID(), owner, clientEmail, type, number, rates, currency, createdAt,
                createdAt);
    }

    private void line(long document, int position, String name, String price, Integer quantity, String discount) {
        jdbc.update("""
                insert into document_billing_items (document_pkid, position, item_name, unit_price, quantity, discount)
                values (?, ?, ?, ?::double precision, ?, ?)
                """, document, position, name, price, quantity, discount);
    }

    private void payment(long document, int position, String date, String amount, String method) {
        jdbc.update("""
                insert into document_payment_records (document_pkid, position, paid_by, date_paid, amount_paid,
                                                      payment_method)
                values (?, ?, 'Client', ?, ?::double precision, ?)
                """, document, position, date, amount, method);
    }

    private Map<String, LocalDate> issueDates() {
        Map<String, LocalDate> dates = new LinkedHashMap<>();
        jdbc.query("select coalesce(document_number, '') as number, issue_date from documents order by pkid",
                row -> {
                    dates.put(row.getString("number"), row.getObject("issue_date", LocalDate.class));
                });
        return dates;
    }

    @Test
    @DisplayName("old documents keep their numbers, amounts and payments, payments of nothing included")
    void recordsKept() {
        String before = database.recordsFingerprint();

        database.migrateToLatest();

        assertThat(database.recordsFingerprint()).isEqualTo(before);
        assertThat(jdbc.queryForObject("select count(*) from payments where amount_paid = 0", Long.class))
                .isEqualTo(2);
    }

    @Test
    @DisplayName("each old payment keeps the receipt number it showed, by date, and the next comes after")
    void receiptNumbersKept() {
        database.migrateToLatest();

        assertThat(jdbc.queryForList("""
                        select receipt_number = row_number() over (partition by document_pkid order by date_paid, pkid)
                        from payments
                        """, Boolean.class))
                .hasSize(3)
                .containsOnly(true);
        assertThat(jdbc.queryForList("""
                        select d.receipts_numbered from documents d
                        where exists (select 1 from payments p where p.document_pkid = d.pkid) order by d.pkid
                        """, Integer.class))
                .containsExactly(1, 2);
        // The payments of nothing stay, and the amount check still holds for
        // what comes after them.
        assertThat(jdbc.queryForObject("""
                        select convalidated from pg_constraint where conname = 'chk_payments_amount'
                        """, Boolean.class))
                .isFalse();
    }

    @Test
    @DisplayName("each old document is dated on the day it was made, in its business's time zone")
    void datedAsMade() {
        database.migrateToLatest();

        assertThat(issueDates()).containsExactly(
                entry("INV-0001", LocalDate.of(2025, 4, 1)),
                entry("INV-0002", LocalDate.of(2025, 5, 10)),
                entry("INV-0003", LocalDate.of(2025, 6, 1)),
                entry("", LocalDate.of(2025, 3, 31)),
                entry("REC-0001", LocalDate.of(2025, 7, 4)),
                entry("QUO-0001", LocalDate.of(2025, 8, 15)),
                entry("INV-0100", LocalDate.of(2025, 2, 1)));
    }

    @Test
    @DisplayName("each old document keeps the business's details and the name of its tax as it shows them today")
    void sellerKept() {
        database.migrateToLatest();

        assertThat(jdbc.queryForList("""
                select concat_ws(' | ', coalesce(document_number, ''), seller_name, seller_email, seller_city,
                                 seller_logo, tax_label)
                from documents order by pkid
                """, String.class)).containsExactly(
                "INV-0001 | Chai & Co | kolkata@example.test | Kolkata | /uploads/chai.png | GST",
                "INV-0002 | Chai & Co | kolkata@example.test | Kolkata | /uploads/chai.png | GST",
                "INV-0003 | Chai & Co | kolkata@example.test | Kolkata | /uploads/chai.png | GST",
                " | Old Owner | utc@example.test | Tax",
                "REC-0001 | Old Owner | utc@example.test | Tax",
                "QUO-0001 | Old Owner | utc@example.test | Tax",
                "INV-0100 | Old Owner | offset@example.test | VAT");
    }

    @Test
    @DisplayName("every old document counts as issued, when it was made")
    void issuedAsMade() {
        database.migrateToLatest();

        assertThat(jdbc.queryForObject("select count(*) from documents", Long.class)).isEqualTo(7);
        assertThat(jdbc.queryForObject(
                        "select count(*) from documents where issued_at is distinct from created_at", Long.class))
                .isZero();
    }

    @Test
    @DisplayName("each old line keeps its quantity, and one saved without a quantity counts 0, as it always did")
    void quantitiesKept() {
        database.migrateToLatest();

        assertThat(jdbc.queryForList(
                        "select item_name || ' ' || quantity from document_billing_items order by document_pkid, position",
                        String.class))
                .containsExactly(
                        "Design 1.0000",
                        "Support 0.0000",
                        "Hosting 3.0000",
                        "Consulting 2.0000",
                        "Workshop 1.0000",
                        "Retainer 1.0000",
                        "Audit 1.0000");
    }

    @Test
    @DisplayName("each old line keeps its document's rate, and each document's tax at it is what it showed")
    void ratesKept() {
        String before = database.recordsFingerprint();

        database.migrateToLatest();

        assertThat(database.recordsFingerprint()).isEqualTo(before);
        assertThat(jdbc.queryForList("""
                        select coalesce(d.document_number, '') || ' ' || s.rate || ' '
                               || (s.taxable = d.sub_total and s.tax = d.sales_tax)
                        from documents d join document_tax_summaries s on s.document_pkid = d.pkid
                        order by d.pkid
                        """, String.class))
                .containsExactly(
                        "INV-0001 18.0000 true",
                        "INV-0002 7.2500 true",
                        "INV-0003 150.0000 true",
                        " 0.0000 true",
                        "REC-0001 0.0000 true",
                        "QUO-0001 10.0000 true",
                        "INV-0100 5.0000 true");
        assertThat(jdbc.queryForList(
                        "select item_name || ' ' || tax_rate from document_billing_items order by document_pkid, position",
                        String.class))
                .containsExactly(
                        "Design 18.0000",
                        "Support 7.2500",
                        "Hosting 7.2500",
                        "Consulting 0.0000",
                        "Workshop 0.0000",
                        "Retainer 10.0000",
                        "Audit 5.0000");
    }
}
