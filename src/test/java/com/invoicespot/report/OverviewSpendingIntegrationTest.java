package com.invoicespot.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.invoicespot.IntegrationTest;
import com.invoicespot.user.User;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

// The overview's spending: what the period's expenses in its currency cost, as
// its profit counts them, by category, the five that cost most first and the
// rest together. It adds up to the trend's expenses, and each category to the
// expenses list narrowed to it for the same days.
class OverviewSpendingIntegrationTest extends IntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User owner;
    private LocalDate today;

    @BeforeEach
    void owner() {
        owner = createUser();
        today = LocalDate.now(ZoneOffset.UTC);
    }

    // Puts the business in the time zone, and dates what follows from its today.
    private void inZone(ZoneId zone) throws Exception {
        send(put("/api/v1/settings"), """
                {"defaultCurrency": "USD", "paymentTermsDays": 15, "defaultTaxRate": 0,
                 "taxLabel": "Tax", "timeZone": "%s"}
                """.formatted(zone.getId()))
                .andExpect(status().isOk());
        today = LocalDate.now(zone);
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String json) throws Exception {
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, bearer(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private static String body(ResultActions result) throws Exception {
        return result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    // An expense in the currency: what it cost before tax, and the tax paid
    // on it, which can be claimed back or not.
    private void expense(
            LocalDate date, String category, String currency, String amount, String tax, boolean reclaimable)
            throws Exception {
        send(post("/api/v1/expense"), """
                {"date": "%s", "category": "%s", "currency": "%s", "amount": %s, "taxAmount": %s,
                 "taxReclaimable": %s}
                """.formatted(date, category, currency, amount, tax, reclaimable))
                .andExpect(status().isCreated());
    }

    private void expense(LocalDate date, String category, String amount) throws Exception {
        expense(date, category, "USD", amount, "0", false);
    }

    // An invoice or a receipt dated the day, for one amount.
    private void issue(String type, String currency, String amount, LocalDate issued) throws Exception {
        String client = JsonPath.read(
                body(send(post("/api/v1/customer/create"), """
                        {"name": "Acme", "email": "acme-%s@clients.test", "phoneNumber": "+1"}
                        """.formatted(UUID.randomUUID()))),
                "$.createdCustomer._id");
        send(post("/api/v1/document/create"), """
                {"customerId": "%s", "documentType": "%s", "currency": "%s", "issueDate": "%s",
                 "billingItems": [{"itemName": "Work", "unitPrice": %s, "quantity": 1}]}
                """.formatted(client, type, currency, issued, amount))
                .andExpect(status().isOk());
    }

    private ResultActions overview(String... params) throws Exception {
        return send(with(get("/api/v1/report/overview"), params), "");
    }

    // The expenses list over the days one of the overview's periods covers,
    // every match on one page: this year to date is a pair of dates there.
    private ResultActions expensesIn(String period, String... params) throws Exception {
        MockHttpServletRequestBuilder request = get("/api/v1/expense").param("size", "100");
        if (period.equals("ytd")) {
            request.param("from", today.withDayOfYear(1).toString()).param("to", today.toString());
        } else {
            request.param("period", period);
        }
        return send(with(request, params), "");
    }

    private static MockHttpServletRequestBuilder with(MockHttpServletRequestBuilder request, String... params) {
        for (int index = 0; index < params.length; index += 2) {
            request.param(params[index], params[index + 1]);
        }
        return request;
    }

    // The first amount the path finds.
    private static BigDecimal amount(String body, String path) {
        Object found = JsonPath.read(body, path);
        return new BigDecimal((found instanceof List<?> list ? list.get(0) : found).toString());
    }

    // The amounts at the path added up.
    private static BigDecimal sum(String body, String path) {
        List<Object> amounts = JsonPath.read(body, path);
        return amounts.stream()
                .map(amount -> new BigDecimal(amount.toString()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @ParameterizedTest(name = "in {0}")
    @ArgumentsSource(AwayFromUtc.class)
    @DisplayName("the five categories that cost most come first, largest first, and the rest are added up together")
    void topFive(ZoneId zone) throws Exception {
        inZone(zone);
        // In the last 90 days, with tax that can be claimed back and tax that can't.
        expense(today.minusDays(1), "Contractors", "600");
        expense(today.minusDays(89), "Contractors", "400");
        expense(today.minusDays(10), "Software", "USD", "500", "90", true);
        expense(today.minusDays(5), "Travel", "USD", "200", "36", false);
        expense(today.minusDays(30), "Travel", "100");
        expense(today.minusDays(20), "Marketing", "250");
        expense(today.minusDays(2), "Office", "USD", "80", "20", false);
        expense(today.minusDays(3), "Office", "50");
        expense(today.minusDays(40), "Insurance", "120");
        expense(today.minusDays(60), "Bank fees", "15");
        expense(today, "Bank fees", "5");
        // Never counted: the day before the period, another currency, and another business.
        expense(today.minusDays(90), "Contractors", "999");
        expense(today.minusDays(1), "Contractors", "EUR", "5000", "0", false);
        User mine = owner;
        owner = createUser();
        expense(LocalDate.now(ZoneOffset.UTC), "Contractors", "777");
        owner = mine;

        overview("period", "90d")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.spending.total").value(2376.0))
                .andExpect(jsonPath(
                        "$.spending.categories[*].category",
                        contains("Contractors", "Software", "Travel", "Marketing", "Office")))
                .andExpect(jsonPath("$.spending.categories[*].amount", contains(1000.0, 500.0, 336.0, 250.0, 150.0)))
                .andExpect(jsonPath("$.spending.categories[*].expenses", contains(2, 1, 2, 1, 2)))
                .andExpect(jsonPath("$.spending.others.amount").value(140.0))
                .andExpect(jsonPath("$.spending.others.expenses").value(3))
                .andExpect(jsonPath("$.spending.others.categories").value(2));
        // The last 30 days leave out what was paid before their first day.
        overview("period", "30d")
                .andExpect(jsonPath("$.spending.total").value(1741.0))
                .andExpect(jsonPath(
                        "$.spending.categories[*].category",
                        contains("Contractors", "Software", "Marketing", "Travel", "Office")))
                .andExpect(jsonPath("$.spending.categories[*].amount", contains(600.0, 500.0, 250.0, 236.0, 150.0)))
                .andExpect(jsonPath("$.spending.categories[*].expenses", contains(1, 1, 1, 1, 2)))
                .andExpect(jsonPath("$.spending.others.amount").value(5.0))
                .andExpect(jsonPath("$.spending.others.expenses").value(1))
                .andExpect(jsonPath("$.spending.others.categories").value(1));
    }

    @Test
    @DisplayName("categories that cost the same go by name, whatever its case, as the profit report lists them")
    void ties() throws Exception {
        expense(today, "delta", "300");
        expense(today, "Fees", "50");
        expense(today, "ads", "50");
        expense(today, "Books", "50");
        expense(today, "cloud", "50");
        // Each 50 once tax that can't be claimed back is added and tax that can is left out.
        expense(today, "Equipment", "USD", "40", "10", false);
        expense(today, "Dues", "USD", "50", "5", true);

        overview()
                .andExpect(jsonPath("$.spending.total").value(600.0))
                .andExpect(jsonPath(
                        "$.spending.categories[*].category", contains("delta", "ads", "Books", "cloud", "Dues")))
                .andExpect(jsonPath("$.spending.categories[*].amount", contains(300.0, 50.0, 50.0, 50.0, 50.0)))
                .andExpect(jsonPath("$.spending.categories[*].expenses", contains(1, 1, 1, 1, 1)))
                .andExpect(jsonPath("$.spending.others.amount").value(100.0))
                .andExpect(jsonPath("$.spending.others.expenses").value(2))
                .andExpect(jsonPath("$.spending.others.categories").value(2));
        send(get("/api/v1/report/profit").param("period", "90d"), "")
                .andExpect(jsonPath(
                        "$.currencies[0].categories[*].category",
                        contains("delta", "ads", "Books", "cloud", "Dues", "Equipment", "Fees")));
    }

    @Test
    @DisplayName("a category in two spellings is one row, spelled as first used, with the expenses its list holds")
    void twoSpellings() throws Exception {
        // Two members can save a new category at the same moment, each in
        // their own spelling; otherwise a new expense takes the one in use.
        Long business = businessOf(owner).getPkid();
        for (String spelling : List.of("Travel", "travel", "travel")) {
            jdbcTemplate.update("""
                    insert into expenses (business_pkid, expense_date, category, amount, currency)
                    values (?, ?, ?, 100, 'USD')
                    """, business, today, spelling);
        }
        expense(today, "Office", "50");

        overview("period", "30d")
                .andExpect(jsonPath("$.spending.total").value(350.0))
                .andExpect(jsonPath("$.spending.categories[*].category", contains("Travel", "Office")))
                .andExpect(jsonPath("$.spending.categories[*].amount", contains(300.0, 50.0)))
                .andExpect(jsonPath("$.spending.categories[*].expenses", contains(3, 1)));
        expensesIn("30d", "currency", "USD", "category", "Travel")
                .andExpect(jsonPath("$.totalExpenses").value(3))
                .andExpect(jsonPath("$.totals[0].spent").value(300.0));
        send(get("/api/v1/report/profit").param("period", "30d"), "")
                .andExpect(jsonPath("$.currencies[0].categories[*].category", contains("Travel", "Office")))
                .andExpect(jsonPath("$.currencies[0].categories[*].expenses", contains(300.0, 50.0)));
    }

    @Test
    @DisplayName("a period without expenses lists none, and five categories leave no rest")
    void emptyAndFive() throws Exception {
        expense(today.minusDays(200), "Office", "70");
        overview("period", "30d")
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "\"spending\":{\"total\":0.00,\"categories\":[],\"others\":null}")));

        for (String category : List.of("Rent", "Travel", "Office", "Software", "Fees")) {
            expense(today, category, "25");
        }
        overview("period", "30d")
                .andExpect(jsonPath("$.spending.total").value(125.0))
                .andExpect(jsonPath(
                        "$.spending.categories[*].category", contains("Fees", "Office", "Rent", "Software", "Travel")))
                .andExpect(jsonPath("$.spending.others").value(nullValue()));

        // A new business has spent nothing in its default currency.
        owner = createUser();
        overview()
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(content().string(containsString(
                        "\"spending\":{\"total\":0.00,\"categories\":[],\"others\":null}")));
    }

    @Test
    @DisplayName("spending is in the overview's currency: the one asked for when the business bills in it")
    void currency() throws Exception {
        issue("Invoice", "USD", "1000", today);
        issue("Invoice", "EUR", "800", today);
        expense(today, "Travel", "100");
        expense(today, "Travel", "EUR", "70", "0", false);
        expense(today.minusDays(1), "Office", "EUR", "30", "5", false);
        expense(today, "Travel", "GBP", "999", "0", false);

        overview("currency", "EUR")
                .andExpect(jsonPath("$.currency").value("EUR"))
                .andExpect(jsonPath("$.spending.total").value(105.0))
                .andExpect(jsonPath("$.spending.categories[*].category", contains("Travel", "Office")))
                .andExpect(jsonPath("$.spending.categories[*].amount", contains(70.0, 35.0)))
                .andExpect(jsonPath("$.spending.others").value(nullValue()));
        overview()
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.spending.total").value(100.0))
                .andExpect(jsonPath("$.spending.categories[*].category", contains("Travel")));
        // Nothing is billed in pounds, so the overview stays in the default currency.
        overview("currency", "GBP")
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.spending.total").value(100.0));
    }

    @ParameterizedTest(name = "in {0}")
    @ArgumentsSource(AwayFromUtc.class)
    @DisplayName("in every period spending is the trend's expenses and profit is income less it, and each category"
            + " lists its expenses")
    void addsUp(ZoneId zone) throws Exception {
        inZone(zone);
        LocalDate year = today.withDayOfMonth(1).minusMonths(11);
        LocalDate january = today.withDayOfYear(1);
        issue("Invoice", "USD", "5000", today.minusDays(3));
        issue("Receipt", "USD", "1200", today.minusDays(45));
        issue("Invoice", "USD", "3000", year);
        // Two expenses on the first day of each period and the day before it,
        // in seven categories, with tax that can be claimed back and tax that
        // can't; and one in another currency.
        List<String> categories =
                List.of("Contractors", "Software", "Travel", "Marketing", "Office", "Insurance", "Fees");
        List<LocalDate> days = List.of(
                today,
                today.minusDays(29),
                today.minusDays(30),
                today.minusDays(89),
                today.minusDays(90),
                year,
                year.minusDays(1),
                january,
                january.minusDays(1));
        for (int index = 0; index < days.size(); index++) {
            expense(days.get(index), categories.get(index % 7), "USD", String.valueOf(100 + 37 * index),
                    index % 3 == 0 ? "12.50" : "0", index % 2 == 0);
            expense(days.get(index), categories.get((index + 3) % 7), "USD", String.valueOf(40 + 11 * index),
                    "4.25", false);
        }
        expense(today, "Contractors", "EUR", "700", "0", false);

        for (String period : List.of("30d", "90d", "12m", "ytd")) {
            String overview = body(overview("period", period, "currency", "USD").andExpect(status().isOk()));
            BigDecimal total = amount(overview, "$.spending.total");
            assertThat(total).as(period).isPositive();
            assertThat(sum(overview, "$.trend[*].expenses")).as(period).isEqualByComparingTo(total);
            assertThat(sum(overview, "$.trend[*].income").subtract(total))
                    .as(period)
                    .isEqualByComparingTo(amount(overview, "$.totals.profit.current"));
            BigDecimal rest = JsonPath.read(overview, "$.spending.others") == null
                    ? BigDecimal.ZERO
                    : amount(overview, "$.spending.others.amount");
            assertThat(sum(overview, "$.spending.categories[*].amount").add(rest))
                    .as(period)
                    .isEqualByComparingTo(total);
            // The profit report takes the same off income.
            String profit = body(send(get("/api/v1/report/profit").param("period", period), ""));
            assertThat(amount(profit, "$.currencies[?(@.currency == 'USD')].expenses"))
                    .as(period)
                    .isEqualByComparingTo(total);
            // The expenses list for the same days and currency adds up to it,
            // and narrowed to each category lists the expenses behind it.
            assertThat(amount(body(expensesIn(period, "currency", "USD")), "$.totals[0].spent"))
                    .as(period)
                    .isEqualByComparingTo(total);
            List<Map<String, Object>> rows = JsonPath.read(overview, "$.spending.categories");
            for (Map<String, Object> row : rows) {
                String listed = body(expensesIn(period, "currency", "USD", "category", (String) row.get("category")));
                assertThat(amount(listed, "$.totals[0].spent"))
                        .as(period + " " + row.get("category"))
                        .isEqualByComparingTo(row.get("amount").toString());
                assertThat((Integer) JsonPath.read(listed, "$.totalExpenses"))
                        .as(period + " " + row.get("category"))
                        .isEqualTo(row.get("expenses"));
            }
        }
    }
}
