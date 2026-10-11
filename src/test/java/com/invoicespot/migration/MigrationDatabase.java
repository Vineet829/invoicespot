package com.invoicespot.migration;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

// A database of its own in the tests' Postgres, migrated up to a chosen
// version, so a later migration can be run over data shaped the way it was
// before that migration.
final class MigrationDatabase implements AutoCloseable {

    private final PostgreSQLContainer<?> postgres;
    private final String name;
    private final String url;
    private final JdbcTemplate jdbc;

    private MigrationDatabase(PostgreSQLContainer<?> postgres, String name) {
        this.postgres = postgres;
        this.name = name;
        this.url = "jdbc:postgresql://" + postgres.getHost() + ":" + postgres.getMappedPort(5432) + "/" + name;
        this.jdbc = new JdbcTemplate(new DriverManagerDataSource(url, postgres.getUsername(), postgres.getPassword()));
    }

    static MigrationDatabase create(PostgreSQLContainer<?> postgres) {
        String name = "migration_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        execute(postgres, "CREATE DATABASE " + name);
        return new MigrationDatabase(postgres, name);
    }

    JdbcTemplate jdbc() {
        return jdbc;
    }

    void migrateTo(String version) {
        migrate(MigrationVersion.fromVersion(version));
    }

    void migrateToLatest() {
        migrate(MigrationVersion.LATEST);
    }

    // The records a migration must carry over unchanged, as they were at V21:
    // clients, documents and payments, column by column. The one change is
    // V62's: a document without a currency takes its business's default,
    // which is USD for these.
    String recordsFingerprint() {
        return jdbc.queryForObject("""
                select md5(coalesce((select string_agg(row(pkid, id, created_by_pkid, name, email, account_no,
                                vat_tin_no, address, city, country, phone_number, created_at, updated_at)::text,
                                E'\\n' order by pkid) from customers), '')
                        || coalesce((select string_agg(row(pkid, id, created_by_pkid, customer_name, customer_email,
                                customer_account_no, customer_vat_tin_no, customer_address, customer_city,
                                customer_country, customer_phone_number, document_type, document_number, due_date,
                                additional_info, terms_conditions, status, sub_total, sales_tax, total,
                                coalesce(nullif(currency, ''), 'USD'),
                                total_amount_received, customer_pkid, source_pkid, accepted_at, reminders_sent,
                                last_reminded_at, overdue_alerted_at, created_at, updated_at)::text,
                                E'\\n' order by pkid) from documents), '')
                        || coalesce((select string_agg(row(pkid, id, document_pkid, paid_by, date_paid, amount_paid,
                                payment_method, additional_info, created_at, updated_at)::text,
                                E'\\n' order by pkid) from payments), ''))
                """, String.class);
    }

    @Override
    public void close() {
        execute(postgres, "DROP DATABASE " + name + " WITH (FORCE)");
    }

    private void migrate(MigrationVersion target) {
        Flyway.configure()
                .dataSource(url, postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .target(target)
                .load()
                .migrate();
    }

    private static void execute(PostgreSQLContainer<?> postgres, String sql) {
        try (Connection connection = DriverManager.getConnection(
                        postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
                Statement statement = connection.createStatement()) {
            statement.execute(sql);
        } catch (SQLException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
