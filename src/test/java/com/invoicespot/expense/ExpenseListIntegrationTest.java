package com.invoicespot.expense;

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
import com.invoicespot.report.AwayFromUtc;
import com.invoicespot.user.User;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

// The expenses list narrowed to the overview's periods, the last 30 or 90 days
// or 12 months by the business's calendar, in place of dates, and to one
// currency, with what the listed expenses cost in each currency, as profit
// counts it.
class ExpenseListIntegrationTest extends IntegrationTest {

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

    // An expense paid on the day to the vendor, in the business's currency.
    private void expense(LocalDate date, String vendor, String category, String amount) throws Exception {
        send(post("/api/v1/expense"), """
                {"date": "%s", "vendor": "%s", "category": "%s", "amount": %s}
                """.formatted(date, vendor, category, amount))
                .andExpect(status().isCreated());
    }

    // An expense in the currency: what it cost before tax, and the tax paid
    // on it, which can be claimed back or not.
    private void expense(
            LocalDate date, String vendor, String category, String currency, String amount, String tax,
            boolean reclaimable) throws Exception {
        send(post("/api/v1/expense"), """
                {"date": "%s", "vendor": "%s", "category": "%s", "currency": "%s", "amount": %s,
                 "taxAmount": %s, "taxReclaimable": %s}
                """.formatted(date, vendor, category, currency, amount, tax, reclaimable))
                .andExpect(status().isCreated());
    }

    // The first amount the path finds.
    private static BigDecimal amount(String body, String path) {
        Object found = JsonPath.read(body, path);
        return new BigDecimal((found instanceof List<?> list ? list.get(0) : found).toString());
    }

    // The expenses list, every match on one page, with the given filters.
    private ResultActions list(String... params) throws Exception {
        return send(with(get("/api/v1/expense").param("size", "100"), params), "");
    }

    private ResultActions csvFile(String... params) throws Exception {
        return send(with(get("/api/v1/expense/csv"), params), "");
    }

    // The vendors in the CSV file, in its order.
    private List<String> csv(String... params) throws Exception {
        return body(csvFile(params).andExpect(status().isOk()))
                .lines()
                .skip(1)
                .map(line -> line.split(",")[1])
                .toList();
    }

    private static MockHttpServletRequestBuilder with(MockHttpServletRequestBuilder request, String... params) {
        for (int index = 0; index < params.length; index += 2) {
            request.param(params[index], params[index + 1]);
        }
        return request;
    }

    @ParameterizedTest(name = "in {0}")
    @ArgumentsSource(AwayFromUtc.class)
    @DisplayName("a period is the overview's last 30 or 90 days or 12 months, by the business's calendar")
    void periods(ZoneId zone) throws Exception {
        inZone(zone);
        LocalDate year = today.withDayOfMonth(1).minusMonths(11);
        // Paid today, and on the first day of each period and the day before it.
        expense(today, "Today", "Office", "10");
        expense(today.minusDays(29), "Day 29", "Office", "20");
        expense(today.minusDays(30), "Day 30", "Travel", "30");
        expense(today.minusDays(89), "Day 89", "Travel", "40");
        expense(today.minusDays(90), "Day 90", "Office", "50");
        expense(year, "First of the year", "Office", "60");
        expense(year.minusDays(1), "Before the year", "Office", "70");

        // The list says which days it covered, by the business's calendar.
        list("period", "30d")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from").value(today.minusDays(29).toString()))
                .andExpect(jsonPath("$.to").value(today.toString()))
                .andExpect(jsonPath("$.totalExpenses").value(2))
                .andExpect(jsonPath("$.expenses[*].vendor", contains("Today", "Day 29")));
        list("period", "90D")
                .andExpect(jsonPath("$.from").value(today.minusDays(89).toString()))
                .andExpect(jsonPath("$.to").value(today.toString()))
                .andExpect(jsonPath("$.expenses[*].vendor", contains("Today", "Day 29", "Day 30", "Day 89")));
        list("period", "12m")
                .andExpect(jsonPath("$.from").value(year.toString()))
                .andExpect(jsonPath("$.to").value(today.toString()))
                .andExpect(jsonPath(
                        "$.expenses[*].vendor",
                        contains("Today", "Day 29", "Day 30", "Day 89", "Day 90", "First of the year")));
        // With the other filters.
        list("period", "90d", "category", "travel")
                .andExpect(jsonPath("$.expenses[*].vendor", contains("Day 30", "Day 89")));
        list("period", "12m", "q", "day 9")
                .andExpect(jsonPath("$.totalExpenses").value(1))
                .andExpect(jsonPath("$.expenses[*].vendor", contains("Day 90")));
        // Dates are sent back as they were asked for, and none when none were.
        list("from", today.minusDays(30).toString())
                .andExpect(jsonPath("$.from").value(today.minusDays(30).toString()))
                .andExpect(jsonPath("$.to").value(nullValue()))
                .andExpect(jsonPath("$.expenses[*].vendor", contains("Today", "Day 29", "Day 30")));
        list("to", today.minusDays(90).toString(), "period", " ")
                .andExpect(jsonPath("$.from").value(nullValue()))
                .andExpect(jsonPath("$.to").value(today.minusDays(90).toString()))
                .andExpect(jsonPath("$.expenses[*].vendor", contains("Day 90", "First of the year", "Before the year")));
        list()
                .andExpect(jsonPath("$.from").value(nullValue()))
                .andExpect(jsonPath("$.to").value(nullValue()))
                .andExpect(jsonPath("$.totalExpenses").value(7));
        // The CSV file covers the same days.
        assertThat(csv("period", "30d")).containsExactly("Today", "Day 29");
        assertThat(csv("period", "12m", "category", "OFFICE"))
                .containsExactly("Today", "Day 29", "Day 90", "First of the year");
        // Each begins on the day the overview's own period does.
        for (String period : List.of("30d", "90d", "12m")) {
            String from = JsonPath.read(
                    body(send(get("/api/v1/report/overview").param("period", period), "")), "$.period.from");
            list("period", period).andExpect(jsonPath("$.from").value(from));
        }
    }

    @Test
    @DisplayName("currency keeps the expenses in one currency, written as it is stored, and a blank one keeps them all")
    void currency() throws Exception {
        expense(today, "Uber", "Travel", "USD", "8", "0", false);
        expense(today.minusDays(1), "Lufthansa", "Travel", "EUR", "200", "0", false);
        expense(today.minusDays(40), "Fnac", "Office", "EUR", "30", "0", false);
        expense(today, "Tesco", "Office", "GBP", "12", "0", false);

        list("currency", "EUR")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expenses[*].vendor", contains("Lufthansa", "Fnac")))
                .andExpect(jsonPath("$.totals[*].currency", contains("EUR")));
        list("currency", "USD").andExpect(jsonPath("$.expenses[*].vendor", contains("Uber")));
        list("currency", "eur").andExpect(jsonPath("$.totalExpenses").value(0));
        list("currency", " ").andExpect(jsonPath("$.totalExpenses").value(4));
        // With the other filters.
        list("currency", "EUR", "period", "30d")
                .andExpect(jsonPath("$.from").value(today.minusDays(29).toString()))
                .andExpect(jsonPath("$.expenses[*].vendor", contains("Lufthansa")));
        list("currency", "EUR", "category", "OFFICE").andExpect(jsonPath("$.expenses[*].vendor", contains("Fnac")));
        list("currency", "EUR", "q", "luft", "from", today.minusDays(1).toString(), "to", today.toString())
                .andExpect(jsonPath("$.expenses[*].vendor", contains("Lufthansa")));
        // The CSV file takes it too.
        assertThat(csv("currency", "EUR")).containsExactly("Lufthansa", "Fnac");
        assertThat(csv("currency", "EUR", "period", "30d")).containsExactly("Lufthansa");
    }

    @Test
    @DisplayName("the totals add up every listed expense in each currency, on every page, as profit counts its cost")
    void totals() throws Exception {
        // Tax that can't be claimed back is part of what an expense cost, and tax that can isn't.
        expense(today, "Staples", "Office", "USD", "100", "18", false);
        expense(today.minusDays(1), "Hostinger", "Software", "USD", "50", "9", true);
        expense(today.minusDays(2), "Uber", "Travel", "USD", "20.50", "0", false);
        expense(today.minusDays(40), "Lufthansa", "Travel", "EUR", "200", "38", false);
        expense(today.minusDays(3), "Tesco", "Office", "GBP", "12", "2.40", true);

        String all = body(list()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalExpenses").value(5))
                .andExpect(jsonPath("$.numberOfPages").value(1))
                .andExpect(jsonPath("$.totals[*].currency", contains("EUR", "GBP", "USD")))
                .andExpect(jsonPath("$.totals[*].expenses", contains(1, 1, 3)))
                .andExpect(jsonPath("$.totals[*].spent", contains(238.0, 12.0, 188.5)))
                // Written to the cent.
                .andExpect(content().string(containsString(
                        "\"totals\":[{\"currency\":\"EUR\",\"expenses\":1,\"spent\":238.00},"))));
        // Every page carries the totals of the whole list.
        String paged = body(send(get("/api/v1/expense").param("size", "2").param("page", "3"), "")
                .andExpect(jsonPath("$.expenses[*].vendor", contains("Lufthansa")))
                .andExpect(jsonPath("$.totalExpenses").value(5))
                .andExpect(jsonPath("$.numberOfPages").value(3)));
        assertThat((Object) JsonPath.read(paged, "$.totals")).isEqualTo(JsonPath.read(all, "$.totals"));
        // They add up only what the filters list.
        list("category", "travel")
                .andExpect(jsonPath("$.totals[*].currency", contains("EUR", "USD")))
                .andExpect(jsonPath("$.totals[*].expenses", contains(1, 1)))
                .andExpect(jsonPath("$.totals[*].spent", contains(238.0, 20.5)));
        list("period", "30d")
                .andExpect(jsonPath("$.totals[*].currency", contains("GBP", "USD")))
                .andExpect(jsonPath("$.totals[*].spent", contains(12.0, 188.5)));
        list("currency", "USD", "q", "o")
                .andExpect(jsonPath("$.expenses[*].vendor", contains("Staples", "Hostinger")))
                .andExpect(jsonPath("$.totals[*].spent", contains(168.0)));
        list("currency", "CHF")
                .andExpect(jsonPath("$.totals").isEmpty())
                .andExpect(jsonPath("$.totalExpenses").value(0))
                .andExpect(jsonPath("$.numberOfPages").value(0));
        // What each currency's expenses cost is what profit takes off its income for the same days.
        String profit = body(send(get("/api/v1/report/profit").param("period", "90d"), "")
                .andExpect(status().isOk()));
        for (String currency : List.of("USD", "EUR", "GBP")) {
            String listed = body(list("period", "90d", "currency", currency));
            assertThat(amount(listed, "$.totals[0].spent"))
                    .as(currency)
                    .isEqualByComparingTo(amount(
                            profit, "$.currencies[?(@.currency == '%s')].expenses".formatted(currency)));
        }
    }

    @Test
    @DisplayName("another business's expenses are never listed, added up or exported")
    void otherBusinesses() throws Exception {
        expense(today, "Uber", "Travel", "USD", "8", "0", false);
        User mine = owner;
        owner = createUser();
        expense(today, "Lyft", "Travel", "USD", "999", "0", false);
        expense(today, "Lufthansa", "Travel", "EUR", "50", "0", false);
        owner = mine;

        list("period", "30d", "currency", "USD", "category", "travel")
                .andExpect(jsonPath("$.totalExpenses").value(1))
                .andExpect(jsonPath("$.expenses[*].vendor", contains("Uber")))
                .andExpect(jsonPath("$.totals.length()").value(1))
                .andExpect(jsonPath("$.totals[0].expenses").value(1))
                .andExpect(jsonPath("$.totals[0].spent").value(8.0));
        list().andExpect(jsonPath("$.totals[*].currency", contains("USD")));
        assertThat(csv("period", "30d")).containsExactly("Uber");
        list("currency", "EUR").andExpect(jsonPath("$.totalExpenses").value(0));
    }

    @Test
    @DisplayName("a period with dates is refused, and so is one the overview doesn't have, by the CSV file too")
    void badInput() throws Exception {
        list("period", "30d", "from", today.minusDays(5).toString())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Choose a period or dates, not both"));
        list("period", "12m", "to", today.toString())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Choose a period or dates, not both"));
        for (String period : List.of("7d", "ytd", "this-year", "3m", "2025")) {
            list("period", period)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Choose 30 days, 90 days or 12 months"));
        }
        csvFile("period", "ytd")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Choose 30 days, 90 days or 12 months"));
        csvFile("period", "90d", "from", today.minusDays(5).toString())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Choose a period or dates, not both"));
    }
}
