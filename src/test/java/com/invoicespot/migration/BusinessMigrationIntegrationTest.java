package com.invoicespot.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.invoicespot.IntegrationTest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

// Accounts, clients, documents and payments made before businesses existed,
// migrated to the latest version.
class BusinessMigrationIntegrationTest extends IntegrationTest {

    @Autowired
    private PostgreSQLContainer<?> postgres;

    private MigrationDatabase database;
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        database = MigrationDatabase.create(postgres);
        database.migrateTo("21");
        jdbc = database.jdbc();
    }

    @AfterEach
    void tearDown() {
        database.close();
    }

    private long user(String handle, String first, String last, String businessName) {
        return jdbc.queryForObject("""
                insert into users (id, email, username, first_name, last_name, business_name, phone_number,
                                   address, city, country, avatar, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, '+91 20 5555 0100', '12 MG Road', 'Pune', 'India', '/uploads/logo.png',
                        timestamptz '2025-01-02 10:00:00+00', timestamptz '2025-03-04 10:00:00+00')
                returning pkid
                """, Long.class, UUID.randomUUID(), handle + "@example.test", handle, first, last, businessName);
    }

    private void records(long owner) {
        long customer = jdbc.queryForObject("""
                insert into customers (id, created_by_pkid, name, email, account_no, phone_number)
                values (?, ?, 'Acme', ?, 'CUS-100001', '555 0100') returning pkid
                """, Long.class, UUID.randomUUID(), owner, "acme-" + owner + "@clients.test");
        long document = jdbc.queryForObject("""
                insert into documents (id, created_by_pkid, customer_pkid, customer_name, customer_email,
                                       document_type, document_number, status, sub_total, sales_tax, total, rates,
                                       currency, total_amount_received)
                values (?, ?, ?, 'Acme', 'acme@clients.test', 'Invoice', 'INV-0001', 'Not Fully Paid',
                        1000.00, 180.00, 1180.00, 18, 'INR', 500.00)
                returning pkid
                """, Long.class, UUID.randomUUID(), owner, customer);
        jdbc.update("""
                insert into payments (document_pkid, date_paid, amount_paid, payment_method)
                values (?, date '2025-02-01', 500.00, 'Bank Transfer')
                """, document);
        jdbc.update("""
                insert into document_events (document_pkid, type, occurred_at)
                values (?, 'CREATED', timestamptz '2025-01-05 10:00:00+00')
                """, document);
        jdbc.update("""
                insert into catalog_items (id, created_by_pkid, name, unit_price) values (?, ?, 'Design', 100)
                """, UUID.randomUUID(), owner);
        jdbc.update("insert into business_settings (id, user_pkid) values (?, ?)", UUID.randomUUID(), owner);
        jdbc.update("""
                insert into notifications (user_pkid, document_pkid, type, message)
                values (?, ?, 'DOCUMENT_VIEWED', 'Acme viewed Invoice INV-0001')
                """, owner, document);
        jdbc.update("""
                insert into recurring_schedules (created_by_pkid, template_pkid, frequency, start_date)
                values (?, ?, 'MONTHLY', date '2025-03-01')
                """, owner, document);
    }

    private long ownedBusiness(long userPkid) {
        return ((Number) businessOf(userPkid).get("pkid")).longValue();
    }

    @Test
    @DisplayName("every record joins its author's business, which then keeps it if the author's account goes")
    void recordsJoinBusinesses() {
        long named = user("named", "Ada", "Lovelace", "Ada Analytics");
        long unnamed = user("unnamed", "Grace", "Hopper", null);
        records(named);
        records(unnamed);

        database.migrateToLatest();

        for (long user : new long[] {named, unnamed}) {
            long business = ownedBusiness(user);
            for (String table : List.of("customers", "documents", "catalog_items", "recurring_schedules")) {
                assertThat(jdbc.queryForList(
                                "select business_pkid from " + table + " where created_by_pkid = ?", Long.class, user))
                        .as(table)
                        .containsOnly(business);
            }
            assertThat(jdbc.queryForObject(
                            "select count(*) from business_settings where business_pkid = ?", Long.class, business))
                    .isEqualTo(1);
            assertThat(jdbc.queryForList(
                            "select business_pkid from notifications where user_pkid = ?", Long.class, user))
                    .containsOnly(business);
        }

        long namedBusiness = ownedBusiness(named);
        long foreignClient = jdbc.queryForObject(
                "select pkid from customers where business_pkid = ?", Long.class, ownedBusiness(unnamed));
        assertThatThrownBy(() -> jdbc.update(
                        "update documents set customer_pkid = ? where business_pkid = ?", foreignClient, namedBusiness))
                .isInstanceOf(DataIntegrityViolationException.class);

        long member = jdbc.queryForObject("""
                insert into users (id, email, username, first_name, last_name)
                values (?, 'member@example.test', 'member', 'Mary', 'Jackson') returning pkid
                """, Long.class, UUID.randomUUID());
        jdbc.update("""
                insert into business_members (business_pkid, user_pkid, role) values (?, ?, 'MEMBER')
                """, namedBusiness, member);
        jdbc.update("update customers set created_by_pkid = ? where business_pkid = ?", member, namedBusiness);
        jdbc.update("delete from business_members where user_pkid = ?", member);
        jdbc.update("delete from users where pkid = ?", member);
        assertThat(jdbc.queryForList(
                        "select created_by_pkid from customers where business_pkid = ?", Long.class, namedBusiness))
                .containsExactly((Long) null);
    }

    @Test
    @DisplayName("what happened to a document before now names no one, rather than a guess at who did it")
    void pastEventsNameNoOne() {
        long author = user("author", "Ada", "Lovelace", "Ada Analytics");
        records(author);

        database.migrateToLatest();

        assertThat(jdbc.queryForList("""
                        select e.actor_pkid from document_events e join documents d on d.pkid = e.document_pkid
                        where d.created_by_pkid = ?
                        """, Long.class, author))
                .containsExactly((Long) null);
    }

    @Test
    @DisplayName("every account owns a business named as its documents named the sender, with its details")
    void accountsGetBusinesses() {
        long named = user("named", "Ada", "Lovelace", "Ada Analytics");
        long unnamed = user("unnamed", "Grace", "Hopper", "   ");
        long nameless = user("nameless", " ", " ", null);
        records(named);
        records(unnamed);
        String before = database.recordsFingerprint();

        database.migrateToLatest();

        assertThat(businessOf(named)).containsEntry("name", "Ada Analytics")
                .containsEntry("email", "named@example.test")
                .containsEntry("phone_number", "+91 20 5555 0100")
                .containsEntry("address", "12 MG Road")
                .containsEntry("city", "Pune")
                .containsEntry("country", "India")
                .containsEntry("logo", "/uploads/logo.png");
        assertThat(businessOf(unnamed)).containsEntry("name", "Grace Hopper");
        assertThat(businessOf(nameless)).containsEntry("name", "nameless");
        assertThat(jdbc.queryForList("select role from business_members", String.class))
                .containsExactly("OWNER", "OWNER", "OWNER");
        assertThat(jdbc.queryForList(
                        "select column_name from information_schema.columns where table_name = 'users'",
                        String.class))
                .doesNotContain("business_name", "phone_number", "address", "city", "country", "avatar");
        assertThat(database.recordsFingerprint()).isEqualTo(before);
    }

    private Map<String, Object> businessOf(long userPkid) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                select b.* from businesses b join business_members m on m.business_pkid = b.pkid
                where m.user_pkid = ? and m.role = 'OWNER'
                """, userPkid);
        assertThat(rows).hasSize(1);
        return rows.get(0);
    }
}
