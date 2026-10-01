package com.invoicespot.migration;

import static org.assertj.core.api.Assertions.assertThat;

import com.invoicespot.IntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

// The one email message a business could write before V49 becomes its
// message for every kind of email it was used for.
class EmailTemplateMigrationIntegrationTest extends IntegrationTest {

    @Autowired
    private PostgreSQLContainer<?> postgres;

    private static long business(JdbcTemplate jdbc, String name, String emailMessage) {
        long business = jdbc.queryForObject(
                "insert into businesses (name) values (?) returning pkid", Long.class, name);
        jdbc.update(
                "insert into business_settings (id, business_pkid, email_message) values (?, ?, ?)",
                UUID.randomUUID(), business, emailMessage);
        return business;
    }

    @Test
    @DisplayName("a saved message goes to every kind of email but reminders, with the subject they had")
    void copiesTheMessage() {
        try (MigrationDatabase database = MigrationDatabase.create(postgres)) {
            database.migrateTo("48");
            JdbcTemplate jdbc = database.jdbc();
            long written = business(jdbc, "Ada Studio", "Hi {client},\n\n{document} is attached.\n\n{business}");
            long blank = business(jdbc, "Blank Studio", "  \n ");
            long none = business(jdbc, "Quiet Studio", null);

            database.migrateToLatest();

            assertThat(jdbc.queryForList(
                            "select kind from email_templates where business_pkid = ? order by kind", String.class,
                            written))
                    .containsExactly("CREDIT_NOTE", "INVOICE", "PAYMENT_RECEIPT", "QUOTATION", "RECEIPT", "STATEMENT");
            assertThat(jdbc.queryForList(
                            "select distinct subject || '|' || body from email_templates where business_pkid = ?",
                            String.class, written))
                    .containsExactly(
                            "{document} from {business}|Hi {client},\n\n{document} is attached.\n\n{business}");
            assertThat(jdbc.queryForObject(
                            "select count(*) from email_templates where business_pkid in (?, ?)", Long.class,
                            blank, none))
                    .isZero();
            assertThat(jdbc.queryForObject("""
                    select count(*) from information_schema.columns
                    where table_name = 'business_settings' and column_name = 'email_message'
                    """, Long.class))
                    .isZero();
        }
    }
}
