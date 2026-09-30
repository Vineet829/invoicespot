package com.invoicespot.demo;

import static org.assertj.core.api.Assertions.assertThat;

import com.invoicespot.IntegrationTest;
import com.invoicespot.user.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class DemoDataSeederIntegrationTest extends IntegrationTest {

    @Autowired
    private DemoDataSeeder seeder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private int count(String sql, Object... args) {
        return jdbcTemplate.queryForObject(sql, Integer.class, args);
    }

    @Test
    @DisplayName("seeding fills an account with a year of clients, items, documents and payments, exactly once")
    void seedsOnce() {
        User demo = createUser();

        DemoDataSeeder.Result result = seeder.seed(demo.getEmail());

        assertThat(result.seeded()).isTrue();
        assertThat(result.clients()).isEqualTo(12);
        assertThat(result.items()).isEqualTo(10);
        assertThat(result.documents()).isEqualTo(48);
        assertThat(result.payments()).isPositive();
        Long owner = demo.getPkid();
        assertThat(count(
                        "select count(distinct date_trunc('month', created_at)) from documents"
                                + " where created_by_pkid = ?",
                        owner))
                .isGreaterThanOrEqualTo(11);
        assertThat(count("""
                select count(*) from documents d
                left join business_settings s on s.business_pkid = d.business_pkid
                where d.created_by_pkid = ?
                  and d.issue_date <> (d.created_at at time zone coalesce(s.time_zone, 'UTC'))::date
                """, owner)).isZero();
        assertThat(count(
                        "select count(*) from documents where created_by_pkid = ? and document_number = 'INV-1001'",
                        owner))
                .isEqualTo(1);
        assertThat(count("""
                select count(*) from documents d
                where d.created_by_pkid = ? and d.total_amount_received <>
                      coalesce((select sum(p.amount_paid) from payments p where p.document_pkid = d.pkid), 0)
                  and d.document_type <> 'Receipt'
                """, owner)).isZero();
        assertThat(count("""
                select count(*) from documents where created_by_pkid = ? and document_type = 'Invoice'
                  and status <> 'Paid' and due_date < now()
                """, owner)).isPositive();
        assertThat(count("select count(*) from payments p join documents d on d.pkid = p.document_pkid"
                        + " where d.created_by_pkid = ? and p.date_paid > current_date", owner))
                .isZero();
        assertThat(count("""
                select count(*) from document_events e join documents d on d.pkid = e.document_pkid
                left join payments p on p.pkid = e.payment_pkid
                where d.created_by_pkid = ?
                  and e.occurred_at <> case when e.type = 'CREATED' then d.created_at else p.created_at end
                """, owner)).isZero();
        assertThat(count("""
                select count(*) from document_events e join documents d on d.pkid = e.document_pkid
                where d.created_by_pkid = ? and e.type = 'PAYMENT_RECORDED'
                """, owner)).isEqualTo(result.payments());

        DemoDataSeeder.Result again = seeder.seed(demo.getEmail());
        assertThat(again.seeded()).isFalse();
        assertThat(count("select count(*) from customers where created_by_pkid = ?", owner)).isEqualTo(12);
    }

    @Test
    @DisplayName("an account that doesn't exist is left alone")
    void missingAccount() {
        assertThat(seeder.seed("nobody@invoicespot.test").seeded()).isFalse();
    }
}
