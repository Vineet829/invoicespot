package com.invoicespot.report;

import static org.assertj.core.api.Assertions.assertThat;
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
import com.invoicespot.user.User;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
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

class OverviewIntegrationTest extends IntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final LocalDate today = LocalDate.now(ZoneOffset.UTC);
    private User owner;
    private String client;

    @BeforeEach
    void owner() throws Exception {
        owner = createUser();
        String body = mockMvc.perform(post("/api/v1/customer/create")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Acme\", \"email\": \"acme-%s@clients.test\", \"phoneNumber\": \"+1\"}"
                                .formatted(UUID.randomUUID())))
                .andReturn()
                .getResponse()
                .getContentAsString();
        client = JsonPath.read(body, "$.createdCustomer._id");
    }

    private String document(String type, String currency, String amount, int dueInDays) throws Exception {
        String body = mockMvc.perform(post("/api/v1/document/create")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId": "%s", "documentType": "%s", "currency": "%s",
                                 "issueDate": "%s", "dueDate": "%s",
                                 "billingItems": [{"itemName": "Work", "unitPrice": %s, "quantity": 1}]}
                                """.formatted(
                                        client,
                                        type,
                                        currency,
                                        today.plusDays(Math.min(dueInDays, 0)),
                                        today.plusDays(dueInDays).atTime(12, 0).toInstant(ZoneOffset.UTC),
                                        amount)))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(body, "$.newDocument._id");
    }

    private void pay(String document, String amount, String date) throws Exception {
        mockMvc.perform(post("/api/v1/document/{id}/payment", document)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amountPaid\": %s, \"datePaid\": \"%s\", \"paymentMethod\": \"Cash\"}"
                                .formatted(amount, date)))
                .andExpect(status().isCreated());
    }

    private ResultActions overview(String currency) throws Exception {
        return overview(currency, null);
    }

    private ResultActions overview(String currency, String period) throws Exception {
        MockHttpServletRequestBuilder request =
                get("/api/v1/report/overview").header(HttpHeaders.AUTHORIZATION, bearer(owner));
        if (currency != null) {
            request.param("currency", currency);
        }
        if (period != null) {
            request.param("period", period);
        }
        return mockMvc.perform(request);
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String json) throws Exception {
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, bearer(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    // Puts the business in the time zone and returns its today.
    private LocalDate inZone(ZoneId zone) throws Exception {
        send(put("/api/v1/settings"), """
                {"defaultCurrency": "USD", "paymentTermsDays": 15, "defaultTaxRate": 0,
                 "taxLabel": "Tax", "timeZone": "%s"}
                """.formatted(zone.getId()))
                .andExpect(status().isOk());
        return LocalDate.now(zone);
    }

    // A document dated the given day and due on the other, at one price taxed
    // at a rate; a draft is only planned for that day.
    private String create(
            String type, String currency, String price, String rate, LocalDate issued, LocalDate due, boolean draft)
            throws Exception {
        return JsonPath.read(
                send(post("/api/v1/document/create"), """
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
                                        due == null ? "null" : "\"" + due + "\"",
                                        price,
                                        rate))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString(),
                "$.newDocument._id");
    }

    private String issue(String type, String currency, String amount, LocalDate issued, LocalDate due)
            throws Exception {
        return create(type, currency, amount, "0", issued, due, false);
    }

    private void pay(String document, String amount, String withheld, LocalDate date) throws Exception {
        send(post("/api/v1/document/{id}/payment", document), """
                {"amountPaid": %s, "tdsAmount": %s, "datePaid": "%s", "paymentMethod": "Bank Transfer"}
                """.formatted(amount, withheld, date))
                .andExpect(status().isCreated());
    }

    // A credit note taking the amount off the document, dated the given day.
    private void credit(String document, String amount, LocalDate issued) throws Exception {
        String note = JsonPath.read(
                send(post("/api/v1/document/{id}/credit-note", document), "")
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString(),
                "$.newDocument._id");
        send(patch("/api/v1/document/{id}", note), """
                {"billingItems": [{"itemName": "Work", "unitPrice": %s, "quantity": 1}]}
                """.formatted(amount))
                .andExpect(status().isOk());
        send(post("/api/v1/document/{id}/issue", note), "{\"issueDate\": \"%s\"}".formatted(issued))
                .andExpect(status().isOk());
    }

    private void refund(String document, String amount, LocalDate date) throws Exception {
        send(post("/api/v1/document/{id}/refund", document), """
                {"amount": %s, "dateRefunded": "%s", "paymentMethod": "Bank Transfer"}
                """.formatted(amount, date))
                .andExpect(status().isCreated());
    }

    private void expense(String currency, String amount, String tax, boolean reclaimable, LocalDate date)
            throws Exception {
        send(post("/api/v1/expense"), """
                {"date": "%s", "category": "Office", "currency": "%s", "amount": %s, "taxAmount": %s,
                 "taxReclaimable": %s}
                """.formatted(date, currency, amount, tax, reclaimable))
                .andExpect(status().isCreated());
    }

    private void voided(String document) throws Exception {
        send(post("/api/v1/document/{id}/void", document), "{\"reason\": \"Issued twice\"}")
                .andExpect(status().isOk());
    }

    private static BigDecimal amount(String body, String path) {
        return new BigDecimal(JsonPath.read(body, path).toString());
    }

    // One of the trend's figures, summed over its buckets.
    private static BigDecimal trendSum(String body, String figure) {
        List<Object> amounts = JsonPath.read(body, "$.trend[*]." + figure);
        return amounts.stream()
                .map(amount -> new BigDecimal(amount.toString()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Test
    @DisplayName("income is invoices and receipts, pending what they still await; statuses count every document")
    void figures() throws Exception {
        String partly = document("Invoice", "USD", "100", -1);
        pay(partly, "20", "2025-01-10");
        pay(document("Invoice", "USD", "200", 5), "200", "2025-02-01");
        document("Quotation", "USD", "900", -3);
        document("Receipt", "USD", "60", 0);
        document("Invoice", "USD", "30", -10);

        overview(null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currencies[0]").value("USD"))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.documents").value(5))
                .andExpect(jsonPath("$.clients").value(1))
                .andExpect(jsonPath("$.expectedIncome").value(390.0))
                .andExpect(jsonPath("$.cashReceived").value(280.0))
                .andExpect(jsonPath("$.cashPending").value(110.0))
                .andExpect(jsonPath("$.paid").value(2))
                .andExpect(jsonPath("$.partial").value(1))
                .andExpect(jsonPath("$.unpaid").value(2))
                .andExpect(jsonPath("$.overdue").value(2))
                .andExpect(jsonPath("$.payments[0].datePaid").value("2025-02-01"))
                .andExpect(jsonPath("$.payments[0].amountPaid").value(200.0))
                .andExpect(jsonPath("$.payments[0].paidBy").value("Acme"))
                .andExpect(jsonPath("$.payments[0].document.status").value("Paid"))
                .andExpect(jsonPath("$.payments[1].datePaid").value("2025-01-10"))
                .andExpect(jsonPath("$.payments[1].document._id").value(partly))
                .andExpect(jsonPath("$.payments[1].document.documentNumber").value("INV-0001"))
                .andExpect(jsonPath("$.payments[1].document.status").value("Not Fully Paid"))
                .andExpect(jsonPath("$.payments.length()").value(2));
    }

    @Test
    @DisplayName("money is shown one currency at a time, the default first; counts still cover everything")
    void currencies() throws Exception {
        pay(document("Invoice", "USD", "100", 5), "40", "2025-01-10");
        pay(document("Invoice", "EUR", "50", 5), "10", "2025-03-01");
        document("Quotation", "GBP", "70", 5);

        overview("EUR")
                .andExpect(jsonPath("$.currencies.length()").value(2))
                .andExpect(jsonPath("$.currencies[0]").value("USD"))
                .andExpect(jsonPath("$.currencies[1]").value("EUR"))
                .andExpect(jsonPath("$.currency").value("EUR"))
                .andExpect(jsonPath("$.expectedIncome").value(50.0))
                .andExpect(jsonPath("$.cashReceived").value(10.0))
                .andExpect(jsonPath("$.payments.length()").value(1))
                .andExpect(jsonPath("$.payments[0].amountPaid").value(10.0))
                .andExpect(jsonPath("$.documents").value(3))
                .andExpect(jsonPath("$.partial").value(2));
        overview("JPY")
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.expectedIncome").value(100.0));
    }

    @Test
    @DisplayName("a new account sees zeros in its default currency")
    void empty() throws Exception {
        owner = createUser();
        overview(null)
                .andExpect(jsonPath("$.currencies").isEmpty())
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.documents").value(0))
                .andExpect(jsonPath("$.expectedIncome").value(0.0))
                .andExpect(jsonPath("$.payments").isEmpty())
                .andExpect(content().string(containsString(
                        "\"totals\":{\"billed\":{\"current\":0.00,\"previous\":0.00}")))
                .andExpect(jsonPath("$.totals.hasPrevious").value(false))
                .andExpect(content().string(containsString(
                        "\"owedNow\":{\"total\":0.00,\"notDue\":0.00,\"overdue\":0.00,"
                                + "\"invoices\":0,\"overdueInvoices\":0}")));
    }

    @ParameterizedTest(name = "in {0}")
    @ArgumentsSource(AwayFromUtc.class)
    @DisplayName("the period is the last 90 days unless another is chosen, beside as long a stretch just before it")
    void periods(ZoneId zone) throws Exception {
        LocalDate day = inZone(zone);
        overview(null, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period.key").value("90d"))
                .andExpect(jsonPath("$.period.from").value(day.minusDays(89).toString()))
                .andExpect(jsonPath("$.period.to").value(day.toString()))
                .andExpect(jsonPath("$.period.previousFrom").value(day.minusDays(179).toString()))
                .andExpect(jsonPath("$.period.previousTo").value(day.minusDays(90).toString()))
                .andExpect(jsonPath("$.period.step").value("week"));
        overview("USD", "30d")
                .andExpect(jsonPath("$.period.key").value("30d"))
                .andExpect(jsonPath("$.period.from").value(day.minusDays(29).toString()))
                .andExpect(jsonPath("$.period.to").value(day.toString()))
                .andExpect(jsonPath("$.period.previousFrom").value(day.minusDays(59).toString()))
                .andExpect(jsonPath("$.period.previousTo").value(day.minusDays(30).toString()))
                .andExpect(jsonPath("$.period.step").value("day"));
        LocalDate first = day.withDayOfMonth(1).minusMonths(11);
        overview(null, "12m")
                .andExpect(jsonPath("$.period.key").value("12m"))
                .andExpect(jsonPath("$.period.from").value(first.toString()))
                .andExpect(jsonPath("$.period.to").value(day.toString()))
                .andExpect(jsonPath("$.period.previousFrom").value(first.minusMonths(12).toString()))
                .andExpect(jsonPath("$.period.previousTo").value(first.minusDays(1).toString()))
                .andExpect(jsonPath("$.period.step").value("month"));
        overview(null, "ytd")
                .andExpect(jsonPath("$.period.key").value("ytd"))
                .andExpect(jsonPath("$.period.from").value(day.withDayOfYear(1).toString()))
                .andExpect(jsonPath("$.period.to").value(day.toString()))
                .andExpect(jsonPath("$.period.previousFrom").value(day.withDayOfYear(1).minusYears(1).toString()))
                .andExpect(jsonPath("$.period.previousTo").value(day.minusYears(1).toString()))
                .andExpect(jsonPath("$.period.step").value("month"));
        overview(null, "7d")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Choose 30 days, 90 days, 12 months or this year"));
    }

    @ParameterizedTest(name = "in {0}")
    @ArgumentsSource(AwayFromUtc.class)
    @DisplayName("billed, received and profit in the period and the one before, counted as the reports count them")
    void totals(ZoneId zone) throws Exception {
        LocalDate day = inZone(zone);
        // In the last 90 days: an invoice taxed 10% and partly paid, a receipt,
        // an invoice partly paid and credited, and one paid, credited and
        // refunded; an expense with tax that can't be claimed back, and one
        // with tax that can.
        String taxed = create("Invoice", "USD", "1000", "10", day.minusDays(10), day.plusDays(20), false);
        pay(taxed, "400", "0", day.minusDays(5));
        create("Receipt", "USD", "300", "0", day.minusDays(3), null, false);
        String credited = issue("Invoice", "USD", "500", day.minusDays(20), day.minusDays(1));
        pay(credited, "100", "0", day.minusDays(2));
        credit(credited, "50", day.minusDays(1));
        String refunded = issue("Invoice", "USD", "200", day.minusDays(30), day.minusDays(15));
        pay(refunded, "200", "0", day.minusDays(30));
        credit(refunded, "80", day.minusDays(4));
        refund(refunded, "80", day.minusDays(2));
        expense("USD", "100", "18", false, day.minusDays(6));
        expense("USD", "50", "9", true, day.minusDays(7));
        // In the 90 days before: an invoice paid partly then and partly now,
        // a payment on an older invoice, and an expense.
        String older = issue("Invoice", "USD", "2000", day.minusDays(100), day.minusDays(70));
        pay(older, "1500", "0", day.minusDays(95));
        pay(older, "500", "0", day.minusDays(10));
        pay(issue("Invoice", "USD", "250", day.minusDays(200), day.minusDays(170)), "250", "0", day.minusDays(150));
        expense("USD", "300", "0", false, day.minusDays(120));
        // Never counted: a draft, a void invoice, a quotation, another currency.
        create("Invoice", "USD", "700", "0", day.minusDays(1), null, true);
        voided(issue("Invoice", "USD", "300", day.minusDays(1), day.plusDays(10)));
        issue("Quotation", "USD", "900", day.minusDays(2), day.plusDays(10));
        pay(issue("Invoice", "EUR", "400", day.minusDays(1), day.plusDays(10)), "100", "0", day);
        expense("EUR", "30", "0", false, day.minusDays(1));

        overview(null, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.totals.billed.current").value(1970.0))
                .andExpect(jsonPath("$.totals.billed.previous").value(2000.0))
                .andExpect(jsonPath("$.totals.received.current").value(1420.0))
                .andExpect(jsonPath("$.totals.received.previous").value(1750.0))
                .andExpect(jsonPath("$.totals.profit.current").value(1702.0))
                .andExpect(jsonPath("$.totals.profit.previous").value(1700.0))
                .andExpect(jsonPath("$.totals.hasPrevious").value(true));
        // The 30 days before the last 30 end on the day the refunded invoice
        // was dated and paid.
        overview(null, "30d")
                .andExpect(jsonPath("$.totals.billed.current").value(1770.0))
                .andExpect(jsonPath("$.totals.billed.previous").value(200.0))
                .andExpect(jsonPath("$.totals.received.current").value(1220.0))
                .andExpect(jsonPath("$.totals.received.previous").value(200.0))
                .andExpect(jsonPath("$.totals.profit.current").value(1502.0))
                .andExpect(jsonPath("$.totals.profit.previous").value(200.0));
        overview("EUR", null)
                .andExpect(jsonPath("$.totals.billed.current").value(400.0))
                .andExpect(jsonPath("$.totals.received.current").value(100.0))
                .andExpect(jsonPath("$.totals.profit.current").value(370.0))
                .andExpect(jsonPath("$.totals.hasPrevious").value(false));
    }

    @ParameterizedTest(name = "in {0}")
    @ArgumentsSource(AwayFromUtc.class)
    @DisplayName("there is a period before to compare with once money was billed, paid or spent before this one")
    void previous(ZoneId zone) throws Exception {
        LocalDate day = inZone(zone);
        String invoice = issue("Invoice", "USD", "100", day.minusDays(89), day.plusDays(10));
        expense("USD", "20", "0", false, day.minusDays(5));
        // Earlier, but none of it counts: a quotation, a draft, a void
        // invoice, and money in another currency.
        issue("Quotation", "USD", "50", day.minusDays(120), day.minusDays(100));
        create("Invoice", "USD", "60", "0", day.minusDays(120), null, true);
        voided(issue("Invoice", "USD", "70", day.minusDays(120), day.minusDays(100)));
        issue("Invoice", "EUR", "80", day.minusDays(120), day.minusDays(100));
        expense("EUR", "90", "0", false, day.minusDays(120));

        overview("USD", null)
                .andExpect(jsonPath("$.totals.hasPrevious").value(false))
                .andExpect(jsonPath("$.totals.billed.current").value(100.0))
                .andExpect(jsonPath("$.totals.billed.previous").value(0.0))
                .andExpect(jsonPath("$.totals.profit.current").value(80.0));

        // A deposit paid the day before the period, on the invoice dated in it.
        pay(invoice, "40", "0", day.minusDays(90));
        overview("USD", null)
                .andExpect(jsonPath("$.totals.hasPrevious").value(true))
                .andExpect(jsonPath("$.totals.received.current").value(0.0))
                .andExpect(jsonPath("$.totals.received.previous").value(40.0));

        // A business whose only money is an old expense.
        owner = createUser();
        LocalDate later = inZone(zone);
        expense("USD", "10", "0", false, later.minusDays(400));
        overview(null, null)
                .andExpect(jsonPath("$.totals.hasPrevious").value(true))
                .andExpect(jsonPath("$.totals.profit.previous").value(0.0));
    }

    @ParameterizedTest(name = "in {0}")
    @ArgumentsSource(AwayFromUtc.class)
    @DisplayName("the trend splits the period's money by day, week or month, and adds up to its totals")
    void trend(ZoneId zone) throws Exception {
        LocalDate day = inZone(zone);
        // Today: a receipt, and a credit note on yesterday's invoice taxed 10%,
        // which was partly paid. Two days ago: a credit note on the day
        // before's receipt, paid back, and an expense with tax that can't be
        // claimed back. On the first day of the last 30: an expense.
        create("Receipt", "USD", "300", "0", day, null, false);
        String invoice = create("Invoice", "USD", "1000", "10", day.minusDays(1), day.plusDays(30), false);
        pay(invoice, "400", "0", day.minusDays(1));
        credit(invoice, "100", day);
        String receipt = create("Receipt", "USD", "200", "0", day.minusDays(3), null, false);
        credit(receipt, "50", day.minusDays(2));
        refund(receipt, "50", day.minusDays(2));
        expense("USD", "100", "18", false, day.minusDays(2));
        expense("USD", "10", "0", false, day.minusDays(29));
        // The day before the last 30, and money in another currency.
        pay(issue("Invoice", "USD", "500", day.minusDays(35), day.minusDays(5)), "500", "0", day.minusDays(30));
        expense("USD", "70", "0", false, day.minusDays(30));
        pay(issue("Invoice", "EUR", "900", day, day.plusDays(5)), "900", "0", day);

        overview(null, "30d")
                .andExpect(jsonPath("$.trend.length()").value(30))
                .andExpect(jsonPath("$.trend[0].from").value(day.minusDays(29).toString()))
                .andExpect(jsonPath("$.trend[0].to").value(day.minusDays(29).toString()))
                .andExpect(jsonPath("$.trend[0].expenses").value(10.0))
                .andExpect(jsonPath("$.trend[0].moneyOut").value(10.0))
                .andExpect(jsonPath("$.trend[1].billed").value(0.0))
                .andExpect(jsonPath("$.trend[1].moneyIn").value(0.0))
                .andExpect(jsonPath("$.trend[26].from").value(day.minusDays(3).toString()))
                .andExpect(jsonPath("$.trend[26].billed").value(200.0))
                .andExpect(jsonPath("$.trend[26].received").value(200.0))
                .andExpect(jsonPath("$.trend[26].income").value(200.0))
                .andExpect(jsonPath("$.trend[26].moneyIn").value(200.0))
                // A refund is money out, and comes off what was received.
                .andExpect(jsonPath("$.trend[27].billed").value(-50.0))
                .andExpect(jsonPath("$.trend[27].received").value(-50.0))
                .andExpect(jsonPath("$.trend[27].income").value(-50.0))
                .andExpect(jsonPath("$.trend[27].expenses").value(118.0))
                .andExpect(jsonPath("$.trend[27].moneyIn").value(0.0))
                .andExpect(jsonPath("$.trend[27].moneyOut").value(168.0))
                .andExpect(jsonPath("$.trend[28].billed").value(1100.0))
                .andExpect(jsonPath("$.trend[28].received").value(400.0))
                .andExpect(jsonPath("$.trend[28].income").value(1000.0))
                .andExpect(jsonPath("$.trend[28].moneyIn").value(400.0))
                .andExpect(jsonPath("$.trend[28].moneyOut").value(0.0))
                .andExpect(jsonPath("$.trend[29].from").value(day.toString()))
                .andExpect(jsonPath("$.trend[29].to").value(day.toString()))
                .andExpect(jsonPath("$.trend[29].billed").value(190.0))
                .andExpect(jsonPath("$.trend[29].received").value(300.0))
                .andExpect(jsonPath("$.trend[29].income").value(200.0))
                .andExpect(jsonPath("$.trend[29].expenses").value(0.0))
                .andExpect(jsonPath("$.trend[29].moneyIn").value(300.0))
                .andExpect(jsonPath("$.totals.billed.current").value(1440.0))
                .andExpect(jsonPath("$.totals.received.current").value(850.0))
                .andExpect(jsonPath("$.totals.profit.current").value(1222.0));
        overview(null, null)
                .andExpect(jsonPath("$.trend.length()").value(13))
                .andExpect(jsonPath("$.trend[0].from").value(day.minusDays(89).toString()))
                .andExpect(jsonPath("$.trend[0].to").value(day.minusDays(83).toString()))
                .andExpect(jsonPath("$.trend[12].from").value(day.minusDays(5).toString()))
                .andExpect(jsonPath("$.trend[12].to").value(day.toString()))
                .andExpect(jsonPath("$.trend[12].billed").value(1440.0))
                .andExpect(jsonPath("$.trend[12].moneyIn").value(900.0))
                .andExpect(jsonPath("$.trend[12].moneyOut").value(168.0));
        overview(null, "12m")
                .andExpect(jsonPath("$.trend.length()").value(12))
                .andExpect(jsonPath("$.trend[0].from").value(day.withDayOfMonth(1).minusMonths(11).toString()))
                .andExpect(jsonPath("$.trend[11].from").value(day.withDayOfMonth(1).toString()))
                .andExpect(jsonPath("$.trend[11].to").value(day.toString()));
        overview(null, "ytd")
                .andExpect(jsonPath("$.trend.length()").value(day.getMonthValue()))
                .andExpect(jsonPath("$.trend[0].from").value(day.withDayOfYear(1).toString()));

        for (String period : List.of("30d", "90d", "12m", "ytd")) {
            String body = overview(null, period).andReturn().getResponse().getContentAsString();
            assertThat(trendSum(body, "billed")).isEqualByComparingTo(amount(body, "$.totals.billed.current"));
            assertThat(trendSum(body, "received")).isEqualByComparingTo(amount(body, "$.totals.received.current"));
            assertThat(trendSum(body, "income").subtract(trendSum(body, "expenses")))
                    .isEqualByComparingTo(amount(body, "$.totals.profit.current"));
            // Received is the money in less what was paid back, and money out
            // is the expenses and what was paid back.
            assertThat(trendSum(body, "moneyIn").subtract(trendSum(body, "received")))
                    .isEqualByComparingTo(trendSum(body, "moneyOut").subtract(trendSum(body, "expenses")));
        }
    }

    @ParameterizedTest(name = "in {0}")
    @ArgumentsSource(AwayFromUtc.class)
    @DisplayName("what is owed now is the pending money, split by due date as the overdue count splits invoices")
    void owedNow(ZoneId zone) throws Exception {
        LocalDate day = inZone(zone);
        issue("Invoice", "USD", "100", day.minusDays(10), day.minusDays(1));
        String partly = issue("Invoice", "USD", "300", day.minusDays(40), day.minusDays(30));
        pay(partly, "100", "20", day.minusDays(5));
        issue("Invoice", "USD", "50", day.minusDays(2), day);
        String credited = issue("Invoice", "USD", "400", day.minusDays(1), day.plusDays(30));
        credit(credited, "150", day);
        // Nothing owed: a paid invoice, a receipt, a draft, a void invoice
        // and a quotation; one in euros is owed in euros.
        pay(issue("Invoice", "USD", "70", day.minusDays(20), day.minusDays(10)), "70", "0", day);
        create("Receipt", "USD", "60", "0", day, null, false);
        create("Invoice", "USD", "500", "0", day.minusDays(9), day.minusDays(5), true);
        voided(issue("Invoice", "USD", "600", day.minusDays(9), day.minusDays(5)));
        issue("Quotation", "USD", "700", day.minusDays(9), day.minusDays(5));
        issue("Invoice", "EUR", "800", day.minusDays(9), day.minusDays(5));

        overview("USD", null)
                .andExpect(jsonPath("$.cashPending").value(580.0))
                .andExpect(jsonPath("$.owedNow.total").value(580.0))
                .andExpect(jsonPath("$.owedNow.overdue").value(280.0))
                .andExpect(jsonPath("$.owedNow.notDue").value(300.0))
                .andExpect(jsonPath("$.owedNow.invoices").value(4))
                .andExpect(jsonPath("$.owedNow.overdueInvoices").value(2))
                .andExpect(jsonPath("$.overdue").value(3));
        overview("EUR", "30d")
                .andExpect(jsonPath("$.cashPending").value(800.0))
                .andExpect(jsonPath("$.owedNow.total").value(800.0))
                .andExpect(jsonPath("$.owedNow.overdue").value(800.0))
                .andExpect(jsonPath("$.owedNow.notDue").value(0.0))
                .andExpect(jsonPath("$.owedNow.overdueInvoices").value(1));
    }

    @ParameterizedTest(name = "in {0}")
    @ArgumentsSource(AwayFromUtc.class)
    @DisplayName("what is owed is grouped by the week it falls due from today: overdue first, then later or undated")
    void owedByWeek(ZoneId zone) throws Exception {
        LocalDate day = inZone(zone);
        // Due on each side of where one week ends and the next begins.
        issue("Invoice", "USD", "100", day.minusDays(3), day.minusDays(1));
        issue("Invoice", "USD", "200", day.minusDays(3), day);
        issue("Invoice", "USD", "50", day.minusDays(3), day.plusDays(6));
        issue("Invoice", "USD", "300", day.minusDays(3), day.plusDays(7));
        pay(issue("Invoice", "USD", "250", day.minusDays(3), day.plusDays(13)), "100", "0", day);
        issue("Invoice", "USD", "30", day.minusDays(3), day.plusDays(14));
        issue("Invoice", "USD", "400", day.minusDays(3), day.plusDays(20));
        issue("Invoice", "USD", "500", day.minusDays(3), day.plusDays(21));
        // One made before every invoice took a due date.
        String undated = issue("Invoice", "USD", "600", day.minusDays(3), day.plusDays(40));
        jdbcTemplate.update("update documents set due_date = null where id = ?", UUID.fromString(undated));
        // Nothing owed in dollars: a paid invoice, and one in euros.
        pay(issue("Invoice", "USD", "70", day.minusDays(3), day.plusDays(2)), "70", "0", day);
        issue("Invoice", "EUR", "80", day.minusDays(3), day.plusDays(2));

        overview("USD", null)
                .andExpect(jsonPath("$.owedByWeek.length()").value(5))
                .andExpect(jsonPath("$.owedByWeek[0].key").value("overdue"))
                .andExpect(jsonPath("$.owedByWeek[0].from").value(nullValue()))
                .andExpect(jsonPath("$.owedByWeek[0].to").value(day.minusDays(1).toString()))
                .andExpect(jsonPath("$.owedByWeek[0].amount").value(100.0))
                .andExpect(jsonPath("$.owedByWeek[0].invoices").value(1))
                .andExpect(jsonPath("$.owedByWeek[1].key").value("thisWeek"))
                .andExpect(jsonPath("$.owedByWeek[1].from").value(day.toString()))
                .andExpect(jsonPath("$.owedByWeek[1].to").value(day.plusDays(6).toString()))
                .andExpect(jsonPath("$.owedByWeek[1].amount").value(250.0))
                .andExpect(jsonPath("$.owedByWeek[1].invoices").value(2))
                .andExpect(jsonPath("$.owedByWeek[2].key").value("nextWeek"))
                .andExpect(jsonPath("$.owedByWeek[2].from").value(day.plusDays(7).toString()))
                .andExpect(jsonPath("$.owedByWeek[2].to").value(day.plusDays(13).toString()))
                .andExpect(jsonPath("$.owedByWeek[2].amount").value(450.0))
                .andExpect(jsonPath("$.owedByWeek[2].invoices").value(2))
                .andExpect(jsonPath("$.owedByWeek[3].key").value("weekAfter"))
                .andExpect(jsonPath("$.owedByWeek[3].from").value(day.plusDays(14).toString()))
                .andExpect(jsonPath("$.owedByWeek[3].to").value(day.plusDays(20).toString()))
                .andExpect(jsonPath("$.owedByWeek[3].amount").value(430.0))
                .andExpect(jsonPath("$.owedByWeek[3].invoices").value(2))
                .andExpect(jsonPath("$.owedByWeek[4].key").value("later"))
                .andExpect(jsonPath("$.owedByWeek[4].from").value(day.plusDays(21).toString()))
                .andExpect(jsonPath("$.owedByWeek[4].to").value(nullValue()))
                .andExpect(jsonPath("$.owedByWeek[4].amount").value(1100.0))
                .andExpect(jsonPath("$.owedByWeek[4].invoices").value(2))
                .andExpect(jsonPath("$.owedNow.total").value(2330.0))
                .andExpect(jsonPath("$.owedNow.overdue").value(100.0))
                .andExpect(jsonPath("$.cashPending").value(2330.0));
        overview("EUR", null)
                .andExpect(jsonPath("$.owedByWeek[1].amount").value(80.0))
                .andExpect(jsonPath("$.owedByWeek[1].invoices").value(1))
                .andExpect(jsonPath("$.owedByWeek[4].amount").value(0.0))
                .andExpect(jsonPath("$.owedByWeek[4].invoices").value(0));
    }

    @ParameterizedTest(name = "in {0}")
    @ArgumentsSource(AwayFromUtc.class)
    @DisplayName("the reports' last 30 and 90 days are the overview's, and bill, receive and earn as it does")
    void reportPeriods(ZoneId zone) throws Exception {
        LocalDate day = inZone(zone);
        // On the first day of each period and the day before it: invoices
        // paid, one with tax withheld and one credited and paid back, a
        // receipt, and expenses.
        pay(issue("Invoice", "USD", "1000", day.minusDays(29), day.plusDays(10)), "300", "20", day.minusDays(29));
        pay(issue("Invoice", "USD", "500", day.minusDays(30), day.plusDays(10)), "500", "0", day.minusDays(30));
        String credited = issue("Invoice", "USD", "800", day.minusDays(89), day.plusDays(10));
        pay(credited, "800", "0", day.minusDays(89));
        credit(credited, "100", day.minusDays(1));
        refund(credited, "100", day);
        issue("Invoice", "USD", "700", day.minusDays(90), day.plusDays(10));
        create("Receipt", "USD", "60", "0", day, null, false);
        expense("USD", "40", "0", false, day.minusDays(29));
        expense("USD", "30", "0", false, day.minusDays(90));
        // Never counted with dollars: euros, a quotation and a draft.
        pay(issue("Invoice", "EUR", "900", day, day.plusDays(5)), "900", "0", day);
        issue("Quotation", "USD", "5000", day, day.plusDays(5));
        create("Invoice", "USD", "6000", "0", day, null, true);

        List<String> periods = List.of("30d", "90d");
        List<List<String>> figures = List.of(List.of("960", "260", "920"), List.of("2260", "1560", "2220"));
        for (int index = 0; index < periods.size(); index++) {
            String period = periods.get(index);
            String overview = overview("USD", period).andReturn().getResponse().getContentAsString();
            String report = send(get("/api/v1/report").param("period", period), "")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.period").value(period))
                    .andExpect(jsonPath("$.from").value(JsonPath.<String>read(overview, "$.period.from")))
                    .andExpect(jsonPath("$.to").value(JsonPath.<String>read(overview, "$.period.to")))
                    .andExpect(jsonPath("$.currencies[0].currency").value("USD"))
                    .andReturn()
                    .getResponse()
                    .getContentAsString();
            String profit = send(get("/api/v1/report/profit").param("period", period), "")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.currencies[0].currency").value("USD"))
                    .andReturn()
                    .getResponse()
                    .getContentAsString();
            assertThat(amount(report, "$.currencies[0].billed"))
                    .as(period)
                    .isEqualByComparingTo(amount(overview, "$.totals.billed.current"))
                    .isEqualByComparingTo(figures.get(index).get(0));
            assertThat(amount(report, "$.currencies[0].received"))
                    .as(period)
                    .isEqualByComparingTo(amount(overview, "$.totals.received.current"))
                    .isEqualByComparingTo(figures.get(index).get(1));
            assertThat(amount(profit, "$.currencies[0].profit"))
                    .as(period)
                    .isEqualByComparingTo(amount(overview, "$.totals.profit.current"))
                    .isEqualByComparingTo(figures.get(index).get(2));
        }
        // The other reports read the same days.
        for (String report : List.of("/api/v1/report/tax", "/api/v1/report/tds")) {
            send(get(report).param("period", "30d"), "")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.from").value(day.minusDays(29).toString()))
                    .andExpect(jsonPath("$.to").value(day.toString()));
        }
        send(get("/api/v1/report/csv/income").param("period", "90d"), "")
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("USD," + YearMonth.from(day) + ",")));
    }
}
