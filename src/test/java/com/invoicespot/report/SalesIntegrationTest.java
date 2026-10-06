package com.invoicespot.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.invoicespot.IntegrationTest;
import com.invoicespot.business.BusinessRole;
import com.invoicespot.user.User;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.IsoFields;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

// The documents' view of what was sold in a period: what the overview's Billed
// counts, by kind of document, beside the period before it, and the tax in it
// at each rate, as the tax report counts it.
class SalesIntegrationTest extends IntegrationTest {

    private User owner;
    private LocalDate today;
    private String client;

    @BeforeEach
    void owner() throws Exception {
        owner = createUser();
        today = LocalDate.now(ZoneOffset.UTC);
        client = client();
    }

    private String client() throws Exception {
        return JsonPath.read(
                body(send(post("/api/v1/customer/create"), """
                        {"name": "Acme", "email": "%s@clients.test", "phoneNumber": "+1"}
                        """.formatted(UUID.randomUUID()))),
                "$.createdCustomer._id");
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
        return result.andReturn().getResponse().getContentAsString();
    }

    // A document dated the day, at one price taxed at a rate; a draft is only
    // planned for that day.
    private String create(String type, String currency, String price, String rate, LocalDate issued, boolean draft)
            throws Exception {
        return JsonPath.read(
                body(send(post("/api/v1/document/create"), """
                                {"customerId": "%s", "documentType": "%s", "currency": "%s", "draft": %s,
                                 "issueDate": "%s", "dueDate": %s,
                                 "billingItems": [{"itemName": "Work", "unitPrice": %s, "quantity": 1,
                                                   "taxRate": "%s"}]}
                                """.formatted(
                                        client,
                                        type,
                                        currency,
                                        draft,
                                        issued,
                                        type.equals("Receipt") ? "null" : "\"" + issued.plusDays(30) + "\"",
                                        price,
                                        rate))
                        .andExpect(status().isOk())),
                "$.newDocument._id");
    }

    private String issue(String type, String price, String rate, LocalDate issued) throws Exception {
        return create(type, "USD", price, rate, issued, false);
    }

    // A credit note on the document, dated the day, taking one line at the
    // price off, taxed at the rate.
    private void credit(String document, String price, String rate, LocalDate issued) throws Exception {
        String note = JsonPath.read(
                body(send(post("/api/v1/document/{id}/credit-note", document), "").andExpect(status().isCreated())),
                "$.newDocument._id");
        send(patch("/api/v1/document/{id}", note), """
                {"billingItems": [{"itemName": "Work", "quantity": 1, "unitPrice": %s, "taxRate": "%s"}]}
                """.formatted(price, rate))
                .andExpect(status().isOk());
        send(post("/api/v1/document/{id}/issue", note), "{\"issueDate\": \"%s\"}".formatted(issued))
                .andExpect(status().isOk());
    }

    private void voided(String document) throws Exception {
        send(post("/api/v1/document/{id}/void", document), "{\"reason\": \"Issued twice\"}")
                .andExpect(status().isOk());
    }

    private ResultActions sales(String... params) throws Exception {
        MockHttpServletRequestBuilder request = get("/api/v1/document/sales");
        for (int index = 0; index < params.length; index += 2) {
            request.param(params[index], params[index + 1]);
        }
        return send(request, "");
    }

    // The documents list's billed documents in dollars over the days.
    private String billedList(LocalDate from, LocalDate to) throws Exception {
        MockHttpServletRequestBuilder request = get("/api/v1/document/all")
                .param("billed", "true")
                .param("currency", "USD")
                .param("size", "100");
        if (from != null) {
            request.param("from", from.toString()).param("to", to.toString());
        }
        return body(send(request, "").andExpect(status().isOk()));
    }

    private static BigDecimal amount(String body, String path) {
        return new BigDecimal(JsonPath.read(body, path).toString());
    }

    // The amounts at the path added up.
    private static BigDecimal sum(String body, String path) {
        List<Object> amounts = JsonPath.read(body, path);
        return amounts.stream()
                .map(amount -> new BigDecimal(amount.toString()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static LocalDate date(String body, String path) {
        return LocalDate.parse(JsonPath.read(body, path));
    }

    @ParameterizedTest(name = "in {0}")
    @ArgumentsSource(AwayFromUtc.class)
    @DisplayName("a period's invoices, receipts and credit notes, by kind and by tax rate, beside the period before")
    void figures(ZoneId zone) throws Exception {
        inZone(zone);
        // In the last 30 days: an invoice taxed 10% and an untaxed one today,
        // a receipt taxed 18%, an invoice taxed 5% on their first day, and a
        // credit note taking 18% off an invoice from before them.
        issue("Invoice", "1000", "10", today);
        issue("Invoice", "50", "0", today);
        issue("Receipt", "200", "18", today.minusDays(3));
        issue("Invoice", "300", "5", today.minusDays(29));
        credit(issue("Invoice", "500", "18", today.minusDays(60)), "100", "18", today.minusDays(2));
        // The day before the last 30 days.
        issue("Invoice", "400", "10", today.minusDays(30));
        // Never counted: a draft, a void invoice, a quotation and euros.
        create("Invoice", "USD", "900", "18", today, true);
        voided(issue("Invoice", "800", "18", today));
        issue("Quotation", "600", "18", today);
        create("Invoice", "EUR", "250", "20", today, false);

        sales("period", "30d", "currency", "USD")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.currencies", contains("USD", "EUR")))
                .andExpect(jsonPath("$.period.key").value("30d"))
                .andExpect(jsonPath("$.period.from").value(today.minusDays(29).toString()))
                .andExpect(jsonPath("$.period.to").value(today.toString()))
                .andExpect(jsonPath("$.period.previousFrom").value(today.minusDays(59).toString()))
                .andExpect(jsonPath("$.period.previousTo").value(today.minusDays(30).toString()))
                // 1100 + 50 + 315 + 236 - 118, beside the 440 the day before.
                .andExpect(jsonPath("$.total").value(1583.0))
                .andExpect(jsonPath("$.previousTotal").value(440.0))
                .andExpect(jsonPath("$.documents").value(5))
                .andExpect(jsonPath("$.byType[*].type", contains("Invoice", "Receipt", "Credit Note")))
                .andExpect(jsonPath("$.byType[*].documents", contains(3, 1, 1)))
                .andExpect(jsonPath("$.byType[*].amount", contains(1465.0, 236.0, -118.0)))
                .andExpect(jsonPath("$.tax.total").value(133.0))
                .andExpect(jsonPath("$.tax.rates[*].rate", contains(0, 5, 10, 18)))
                .andExpect(jsonPath("$.tax.rates[*].taxable", contains(50.0, 300.0, 1000.0, 100.0)))
                .andExpect(jsonPath("$.tax.rates[*].tax", contains(0.0, 15.0, 100.0, 18.0)))
                .andExpect(jsonPath("$.tax.rates[*].documents", contains(1, 1, 1, 2)))
                .andExpect(content().string(containsString(
                        "\"byType\":[{\"type\":\"Invoice\",\"documents\":3,\"amount\":1465.00},")))
                .andExpect(content().string(containsString(
                        "\"tax\":{\"total\":133.00,\"rates\":[{\"rate\":0,\"taxable\":50.00,\"tax\":0.00,"
                                + "\"documents\":1},")));
        sales("period", "30d", "currency", "EUR")
                .andExpect(jsonPath("$.currency").value("EUR"))
                .andExpect(jsonPath("$.total").value(300.0))
                .andExpect(jsonPath("$.previousTotal").value(0.0))
                .andExpect(jsonPath("$.byType[*].amount", contains(300.0, 0.0, 0.0)))
                .andExpect(jsonPath("$.tax.rates[*].rate", contains(20)));
    }

    @ParameterizedTest(name = "in {0}")
    @ArgumentsSource(AwayFromUtc.class)
    @DisplayName("over each of the overview's periods the total, the one before and the tax are the overview's, the"
            + " tax is the tax report's, and the documents are the documents list's billed ones")
    void addsUpToTheOverview(ZoneId zone) throws Exception {
        inZone(zone);
        LocalDate year = today.withDayOfMonth(1).minusMonths(11);
        LocalDate january = today.withDayOfYear(1);
        // Documents on the first day of each period and the day before it,
        // and on the first and last days of the periods before them, at
        // several rates, with credit notes and receipts.
        List<LocalDate> days = List.of(
                today,
                today.minusDays(29),
                today.minusDays(30),
                today.minusDays(59),
                today.minusDays(60),
                today.minusDays(89),
                today.minusDays(90),
                today.minusDays(179),
                today.minusDays(180),
                year,
                year.minusDays(1),
                year.minusMonths(12),
                year.minusMonths(12).minusDays(1),
                january,
                january.minusDays(1),
                january.minusYears(1),
                today.minusYears(1),
                today.minusYears(1).plusDays(1));
        List<String> rates = List.of("0", "5", "12.5", "18");
        for (int index = 0; index < days.size(); index++) {
            LocalDate day = days.get(index);
            String invoice = issue("Invoice", String.valueOf(100 + 37 * index), rates.get(index % 4), day);
            if (index % 2 == 0) {
                credit(invoice, String.valueOf(10 + index), rates.get(index % 4), day);
            }
            issue("Receipt", String.valueOf(40 + 11 * index), rates.get((index + 1) % 4), day);
        }
        // Never counted with dollars: euros, a quotation and a draft.
        create("Invoice", "EUR", "900", "18", today, false);
        issue("Quotation", "5000", "18", today);
        create("Invoice", "USD", "6000", "18", today, true);

        Map<String, String> overviewPeriods = Map.of(
                "30d", "30d", "90d", "90d", "12m", "12m", "ytd", "ytd", "this-year", "ytd");
        for (Map.Entry<String, String> periods : overviewPeriods.entrySet()) {
            String key = periods.getKey();
            String overview = body(send(
                    get("/api/v1/report/overview").param("currency", "USD").param("period", periods.getValue()),
                    ""));
            String sold = body(sales("period", key, "currency", "USD").andExpect(status().isOk()));
            assertThat(amount(sold, "$.total"))
                    .as(key)
                    .isPositive()
                    .isEqualByComparingTo(amount(overview, "$.totals.billed.current"))
                    .isEqualByComparingTo(sum(sold, "$.byType[*].amount"));
            assertThat(amount(sold, "$.previousTotal"))
                    .as(key)
                    .isPositive()
                    .isEqualByComparingTo(amount(overview, "$.totals.billed.previous"));
            assertThat(amount(sold, "$.tax.total"))
                    .as(key)
                    .isEqualByComparingTo(amount(overview, "$.totals.tax"))
                    .isEqualByComparingTo(sum(sold, "$.tax.rates[*].tax"));
            assertThat(date(sold, "$.period.from")).as(key).isEqualTo(date(overview, "$.period.from"));
            assertThat(date(sold, "$.period.previousFrom")).as(key).isEqualTo(date(overview, "$.period.previousFrom"));
            assertThat(date(sold, "$.period.previousTo")).as(key).isEqualTo(date(overview, "$.period.previousTo"));
            // The tax report's dollars over the same days, rate for rate.
            String report = body(send(get("/api/v1/report/tax").param("period", periods.getValue()), "")
                    .andExpect(status().isOk()));
            List<Object> tax = JsonPath.read(report, "$.currencies[?(@.currency == 'USD')].tax");
            assertThat(amount(sold, "$.tax.total")).as(key).isEqualByComparingTo(tax.get(0).toString());
            List<Map<String, Object>> rows = JsonPath.read(report, "$.currencies[?(@.currency == 'USD')].rates[*]");
            List<Map<String, Object>> listed = JsonPath.read(sold, "$.tax.rates");
            assertThat(listed).as(key).hasSameSizeAs(rows).hasSizeGreaterThan(2);
            for (int index = 0; index < rows.size(); index++) {
                for (String field : List.of("rate", "taxable", "tax", "documents")) {
                    assertThat(new BigDecimal(listed.get(index).get(field).toString()))
                            .as(key + " " + field)
                            .isEqualByComparingTo(rows.get(index).get(field).toString());
                }
            }
            // The documents list's billed documents over the same days.
            String billed = billedList(date(sold, "$.period.from"), date(sold, "$.period.to"));
            assertThat((Integer) JsonPath.read(billed, "$.totalDocuments"))
                    .as(key)
                    .isEqualTo(JsonPath.read(sold, "$.documents"));
            assertThat(amount(billed, "$.totals[0].billed")).as(key).isEqualByComparingTo(amount(sold, "$.total"));
        }
    }

    @Test
    @DisplayName("a calendar month, quarter or year runs to its last day, compared with the one before; all dates"
            + " have nothing to compare with")
    void calendarPeriods() throws Exception {
        LocalDate month = today.withDayOfMonth(1);
        LocalDate quarter = today.with(IsoFields.DAY_OF_QUARTER, 1);
        LocalDate year = today.withDayOfYear(1);
        // On the first day of this month, quarter and year and the day before
        // each, on the first day of last month, quarter and year, and the
        // day before those.
        List<LocalDate> days = List.of(
                month,
                month.minusDays(1),
                month.minusMonths(1),
                month.minusMonths(1).minusDays(1),
                quarter,
                quarter.minusDays(1),
                quarter.minusMonths(3),
                quarter.minusMonths(3).minusDays(1),
                year,
                year.minusDays(1),
                year.minusYears(1),
                year.minusYears(1).minusDays(1),
                year.minusYears(2));
        for (int index = 0; index < days.size(); index++) {
            issue("Invoice", String.valueOf(100 * (index + 1)), "0", days.get(index));
        }

        List<List<Object>> periods = List.of(
                List.of("this-month", month, month.plusMonths(1).minusDays(1), month.minusMonths(1),
                        today.minusMonths(1)),
                List.of("last-month", month.minusMonths(1), month.minusDays(1), month.minusMonths(2),
                        month.minusMonths(1).minusDays(1)),
                List.of("this-quarter", quarter, quarter.plusMonths(3).minusDays(1), quarter.minusMonths(3),
                        today.minusMonths(3)),
                List.of("this-year", year, year.plusYears(1).minusDays(1), year.minusYears(1),
                        today.minusYears(1)),
                List.of("last-year", year.minusYears(1), year.minusDays(1), year.minusYears(2),
                        year.minusYears(1).minusDays(1)));
        for (List<Object> period : periods) {
            String key = (String) period.get(0);
            LocalDate from = (LocalDate) period.get(1);
            LocalDate to = (LocalDate) period.get(2);
            String sold = body(sales("period", key)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.period.key").value(key))
                    .andExpect(jsonPath("$.period.from").value(from.toString()))
                    .andExpect(jsonPath("$.period.to").value(to.toString()))
                    .andExpect(jsonPath("$.period.previousFrom").value(period.get(3).toString()))
                    .andExpect(jsonPath("$.period.previousTo").value(period.get(4).toString())));
            String listed = billedList(from, to);
            String before = billedList((LocalDate) period.get(3), (LocalDate) period.get(4));
            assertThat(amount(sold, "$.total"))
                    .as(key)
                    .isPositive()
                    .isEqualByComparingTo(sum(listed, "$.totals[*].billed"));
            assertThat(amount(sold, "$.previousTotal")).as(key).isEqualByComparingTo(sum(before, "$.totals[*].billed"));
            assertThat((Integer) JsonPath.read(sold, "$.documents"))
                    .as(key)
                    .isEqualTo(JsonPath.read(listed, "$.totalDocuments"));
        }
        // All dates: every document, and nothing before them.
        String all = body(sales()
                .andExpect(jsonPath("$.period.key").value(""))
                .andExpect(jsonPath("$.period.from").value(nullValue()))
                .andExpect(jsonPath("$.period.to").value(nullValue()))
                .andExpect(jsonPath("$.period.previousFrom").value(nullValue()))
                .andExpect(jsonPath("$.period.previousTo").value(nullValue()))
                .andExpect(jsonPath("$.previousTotal").value(nullValue()))
                .andExpect(jsonPath("$.documents").value(13))
                .andExpect(jsonPath("$.total").value(9100.0))
                .andExpect(content().string(containsString("\"previousTotal\":null,"))));
        assertThat(amount(all, "$.total")).isEqualByComparingTo(amount(billedList(null, null), "$.totals[0].billed"));
        sales("period", " ").andExpect(jsonPath("$.total").value(9100.0));
    }

    @Test
    @DisplayName("the currency is chosen as the overview chooses it, and a period that sold nothing has zeros of"
            + " every kind and no tax rates")
    void currencyAndEmpty() throws Exception {
        sales("period", "30d")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.currencies").isEmpty())
                .andExpect(jsonPath("$.total").value(0.0))
                .andExpect(jsonPath("$.previousTotal").value(0.0))
                .andExpect(jsonPath("$.documents").value(0))
                .andExpect(jsonPath("$.byType[*].type", contains("Invoice", "Receipt", "Credit Note")))
                .andExpect(jsonPath("$.byType[*].documents", contains(0, 0, 0)))
                .andExpect(jsonPath("$.byType[*].amount", contains(0.0, 0.0, 0.0)))
                .andExpect(content().string(containsString("\"tax\":{\"total\":0.00,\"rates\":[]}")));
        sales().andExpect(jsonPath("$.total").value(0.0)).andExpect(jsonPath("$.previousTotal").value(nullValue()));

        // Only euros are billed, so euros are shown whatever is asked for,
        // until there are dollars too; an older sale is in no recent period.
        create("Invoice", "EUR", "400", "0", today.minusDays(100), false);
        sales("period", "30d", "currency", "USD")
                .andExpect(jsonPath("$.currency").value("EUR"))
                .andExpect(jsonPath("$.currencies", contains("EUR")))
                .andExpect(jsonPath("$.total").value(0.0));
        sales("period", "12m").andExpect(jsonPath("$.total").value(400.0));
        issue("Receipt", "60", "0", today);
        sales("period", "30d")
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.currencies", contains("USD", "EUR")))
                .andExpect(jsonPath("$.total").value(60.0));
    }

    @Test
    @DisplayName("the view isn't read as a document's id, a viewer may see it, it counts only its business, and"
            + " a period from no list is refused")
    void route() throws Exception {
        String invoice = issue("Invoice", "100", "0", today);

        send(get("/api/v1/document/{id}", invoice), "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.document._id").value(invoice));
        sales().andExpect(status().isOk()).andExpect(jsonPath("$.total").value(100.0));
        User viewer = createMember(businessOf(owner), BusinessRole.VIEWER);
        mockMvc.perform(get("/api/v1/document/sales")
                        .param("period", "this-month")
                        .header(HttpHeaders.AUTHORIZATION, bearer(viewer, businessOf(owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(100.0))
                .andExpect(jsonPath("$.documents").value(1));
        mockMvc.perform(get("/api/v1/document/sales").header(HttpHeaders.AUTHORIZATION, bearer(createUser())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0.0))
                .andExpect(jsonPath("$.documents").value(0));
        for (String period : List.of("7d", "3m", "2025", "all")) {
            sales("period", period)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Choose a period from the list"));
        }
    }
}
