package com.invoicespot.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.invoicespot.IntegrationTest;
import com.invoicespot.user.User;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

class DemoDataSeederIntegrationTest extends IntegrationTest {

    // Where the sample data is dated from.
    private static final ZoneId ZONE = ZoneId.of("America/Los_Angeles");

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
        assertThat(count("select count(*) from expenses where recorded_by_pkid = ?", owner)).isZero();

        DemoDataSeeder.Result again = seeder.seed(demo.getEmail());
        assertThat(again.seeded()).isFalse();
        assertThat(count("select count(*) from customers where created_by_pkid = ?", owner)).isEqualTo(12);
    }

    @Test
    @DisplayName("an account with the rest of the sample data gets a year of sample expenses in dollars, once")
    void expensesOnce() {
        User demo = createUser();
        Long business = businessOf(demo).getPkid();
        seeder.seed(demo.getEmail());
        // A currency of its own since; the sample expenses are in dollars, as the sample documents are.
        jdbcTemplate.update("update business_settings set default_currency = 'EUR' where business_pkid = ?", business);

        DemoDataSeeder.ExpenseResult result = seeder.seedExpenses(demo.getEmail());

        assertThat(result.seeded()).isTrue();
        assertThat(result.expenses()).isEqualTo(14);
        sampleExpenses(demo);

        DemoDataSeeder.ExpenseResult again = seeder.seedExpenses(demo.getEmail());
        assertThat(again.seeded()).isFalse();
        assertThat(again.reason()).isEqualTo("the demo account already has the sample expenses");
        assertThat(seeder.seed(demo.getEmail()).seeded()).isFalse();
        assertThat(count("select count(*) from expenses where business_pkid = ?", business)).isEqualTo(14);
        assertThat(count("select count(*) from customers where business_pkid = ?", business)).isEqualTo(12);
        assertThat(count("select count(*) from documents where business_pkid = ?", business)).isEqualTo(48);
    }

    // The fourteen sample expenses over the last 12 months, in dollars, in
    // five categories, some with tax and one with tax that can be claimed
    // back, each recorded by the account on the day it was paid.
    private void sampleExpenses(User demo) {
        Long business = businessOf(demo).getPkid();
        LocalDate today = LocalDate.now(ZONE);
        assertThat(count("select count(*) from expenses where business_pkid = ?", business)).isEqualTo(14);
        assertThat(jdbcTemplate.queryForList(
                        "select distinct category from expenses where business_pkid = ?", String.class, business))
                .containsExactlyInAnyOrder(
                        "Contractors", "Software and subscriptions", "Marketing", "Travel", "Office");
        assertThat(count("select count(*) from expenses where business_pkid = ? and currency <> 'USD'", business))
                .isZero();
        assertThat(count(
                        "select count(*) from expenses where business_pkid = ? and expense_date between ? and ?",
                        business,
                        today.withDayOfMonth(1).minusMonths(11),
                        today))
                .isEqualTo(14);
        assertThat(count("select count(*) from expenses where business_pkid = ? and tax_amount > 0", business))
                .isEqualTo(6);
        assertThat(count("select count(*) from expenses where business_pkid = ? and tax_reclaimable", business))
                .isEqualTo(1);
        assertThat(count("select count(*) from expenses where business_pkid = ? and vendor is null", business))
                .isZero();
        assertThat(count(
                        "select count(*) from expenses where business_pkid = ? and recorded_by_pkid = ?",
                        business,
                        demo.getPkid()))
                .isEqualTo(14);
        assertThat(count("""
                select count(*) from expenses
                where business_pkid = ? and expense_date <> (created_at at time zone 'America/Los_Angeles')::date
                """, business)).isZero();
    }

    @Test
    @DisplayName("an account with expenses of its own gets no sample ones, with or without the rest of the sample data")
    void ownExpenses() {
        User demo = createUser();
        Long business = businessOf(demo).getPkid();
        jdbcTemplate.update("""
                insert into expenses (business_pkid, expense_date, category, amount, currency)
                values (?, ?, 'Office', 5, 'USD')
                """, business, LocalDate.now(ZONE));

        DemoDataSeeder.ExpenseResult before = seeder.seedExpenses(demo.getEmail());
        assertThat(before.seeded()).isFalse();
        assertThat(before.reason()).isEqualTo("the demo account already has expenses of its own");
        assertThat(count("select count(*) from expenses where business_pkid = ?", business)).isEqualTo(1);

        // The rest of the sample data is added as before, and the sample expenses still aren't.
        assertThat(seeder.seed(demo.getEmail()).seeded()).isTrue();
        DemoDataSeeder.ExpenseResult after = seeder.seedExpenses(demo.getEmail());
        assertThat(after.seeded()).isFalse();
        assertThat(after.reason()).isEqualTo("the demo account already has expenses of its own");
        assertThat(count("select count(*) from expenses where business_pkid = ?", business)).isEqualTo(1);
        assertThat(count("select count(*) from documents where business_pkid = ?", business)).isEqualTo(48);
    }

    @Test
    @DisplayName("the sample expenses add up alike on the overview, the profit report and the expenses list")
    void figuresAgree() throws Exception {
        User demo = createUser();
        seeder.seed(demo.getEmail());
        seeder.seedExpenses(demo.getEmail());
        String session = bearer(demo);

        // The last 90 days hold all five categories.
        read(session, get("/api/v1/report/overview").param("period", "90d"))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.spending.total").value(5914.67))
                .andExpect(jsonPath(
                        "$.spending.categories[*].category",
                        contains("Contractors", "Travel", "Software and subscriptions", "Marketing", "Office")))
                .andExpect(jsonPath("$.spending.categories[*].amount", contains(3600.0, 847.84, 743.88, 450.45, 272.5)))
                .andExpect(jsonPath("$.spending.categories[*].expenses", contains(2, 2, 2, 1, 2)))
                .andExpect(jsonPath("$.spending.others").value(nullValue()));
        for (String period : List.of("30d", "90d", "12m", "ytd")) {
            String overview = body(read(session, get("/api/v1/report/overview").param("period", period)));
            BigDecimal total = new BigDecimal(JsonPath.read(overview, "$.spending.total").toString());
            assertThat(sum(overview, "$.trend[*].expenses")).as(period).isEqualByComparingTo(total);
            assertThat(sum(overview, "$.trend[*].income").subtract(total))
                    .as(period)
                    .isEqualByComparingTo(JsonPath.read(overview, "$.totals.profit.current").toString());
            String profit = body(read(session, get("/api/v1/report/profit").param("period", period)));
            List<Object> spent = JsonPath.read(profit, "$.currencies[?(@.currency == 'USD')].expenses");
            assertThat(new BigDecimal(spent.get(0).toString())).as(period).isEqualByComparingTo(total);
            // The expenses list for each category and the same days lists the
            // expenses behind its amount.
            List<Map<String, Object>> categories = JsonPath.read(overview, "$.spending.categories");
            for (Map<String, Object> category : categories) {
                MockHttpServletRequestBuilder list = get("/api/v1/expense")
                        .param("size", "100")
                        .param("currency", "USD")
                        .param("category", (String) category.get("category"));
                if (period.equals("ytd")) {
                    LocalDate today = LocalDate.now(ZONE);
                    list.param("from", today.withDayOfYear(1).toString()).param("to", today.toString());
                } else {
                    list.param("period", period);
                }
                String listed = body(read(session, list));
                assertThat((Integer) JsonPath.read(listed, "$.totalExpenses"))
                        .as(period + " " + category.get("category"))
                        .isEqualTo(category.get("expenses"));
                assertThat(sum(listed, "$.totals[*].spent"))
                        .as(period + " " + category.get("category"))
                        .isEqualByComparingTo(category.get("amount").toString());
            }
        }
    }

    private ResultActions read(String session, MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, session)).andExpect(status().isOk());
    }

    private static String body(ResultActions result) throws Exception {
        return result.andReturn().getResponse().getContentAsString();
    }

    private static BigDecimal sum(String body, String path) {
        List<Object> amounts = JsonPath.read(body, path);
        return amounts.stream()
                .map(amount -> new BigDecimal(amount.toString()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Test
    @DisplayName("an account that doesn't exist is left alone")
    void missingAccount() {
        assertThat(seeder.seed("nobody@invoicespot.test").seeded()).isFalse();
        DemoDataSeeder.ExpenseResult expenses = seeder.seedExpenses("nobody@invoicespot.test");
        assertThat(expenses.seeded()).isFalse();
        assertThat(expenses.reason()).isEqualTo("the demo account nobody@invoicespot.test does not exist");
    }
}
