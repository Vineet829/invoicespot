package com.invoicespot.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
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
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
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

// The money bar over the documents list: what is owed now, and what came in
// over the last 30 days, as the overview counts them.
class DocumentSummaryIntegrationTest extends IntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private LocalDate today;
    private User owner;
    private String client;

    @BeforeEach
    void owner() throws Exception {
        owner = createUser();
        client = JsonPath.read(
                body(send(post("/api/v1/customer/create"), """
                        {"name": "Acme", "email": "acme-%s@clients.test", "phoneNumber": "+1"}
                        """.formatted(UUID.randomUUID()))),
                "$.createdCustomer._id");
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

    // A document dated the given day and due on the other, for one amount; a
    // draft is only planned for that day.
    private String create(
            String type, String currency, String amount, LocalDate issued, LocalDate due, boolean draft)
            throws Exception {
        return JsonPath.read(
                body(send(post("/api/v1/document/create"), """
                                {"customerId": "%s", "documentType": "%s", "currency": %s, "draft": %s,
                                 "issueDate": "%s", "dueDate": %s,
                                 "billingItems": [{"itemName": "Work", "unitPrice": %s, "quantity": 1}]}
                                """.formatted(
                                        client,
                                        type,
                                        currency == null ? "null" : "\"" + currency + "\"",
                                        draft,
                                        issued,
                                        due == null ? "null" : "\"" + due + "\"",
                                        amount))
                        .andExpect(status().isOk())),
                "$.newDocument._id");
    }

    private String issue(String type, String amount, LocalDate issued, LocalDate due) throws Exception {
        return create(type, "USD", amount, issued, due, false);
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
                body(send(post("/api/v1/document/{id}/credit-note", document), "")
                        .andExpect(status().isCreated())),
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
                {"amount": %s, "dateRefunded": "%s", "paymentMethod": "Cash"}
                """.formatted(amount, date))
                .andExpect(status().isCreated());
    }

    // Moves a document's due date to a moment on the business's clock.
    private void dueAt(String document, LocalDateTime moment, ZoneId zone) {
        jdbcTemplate.update(
                "update documents set due_date = ? where id = ?",
                Timestamp.from(moment.atZone(zone).toInstant()),
                UUID.fromString(document));
    }

    private ResultActions summary(String currency) throws Exception {
        MockHttpServletRequestBuilder request = get("/api/v1/document/summary");
        if (currency != null) {
            request.param("currency", currency);
        }
        return send(request, "");
    }

    private ResultActions overview(String currency, String period) throws Exception {
        MockHttpServletRequestBuilder request = get("/api/v1/report/overview").param("period", period);
        if (currency != null) {
            request.param("currency", currency);
        }
        return send(request, "");
    }

    private static BigDecimal amount(String body, String path) {
        return new BigDecimal(JsonPath.read(body, path).toString());
    }

    @ParameterizedTest(name = "in {0}")
    @ArgumentsSource(AwayFromUtc.class)
    @DisplayName("what is owed now and what came in over the last 30 days are the overview's figures")
    void overviewFigures(ZoneId zone) throws Exception {
        inZone(zone);
        // Owed: overdue and partly paid with tax withheld, due today, credited,
        // partly paid today, and due in the last minutes of yesterday and the
        // first of today by the business's clock.
        pay(issue("Invoice", "1000", today.minusDays(40), today.minusDays(1)), "400", "50", today.minusDays(29));
        issue("Invoice", "300", today.minusDays(5), today);
        credit(issue("Invoice", "800", today.minusDays(60), today.plusDays(10)), "100", today.minusDays(2));
        pay(issue("Invoice", "200", today.minusDays(10), today.plusDays(5)), "50", "0", today);
        String late = issue("Invoice", "40", today.minusDays(3), today);
        dueAt(late, today.minusDays(1).atTime(23, 30), zone);
        String early = issue("Invoice", "60", today.minusDays(3), today);
        dueAt(early, today.atTime(0, 30), zone);
        // Paid the day before the last 30 days, then credited and paid back today.
        String refunded = issue("Invoice", "600", today.minusDays(50), today.minusDays(20));
        pay(refunded, "600", "0", today.minusDays(30));
        credit(refunded, "150", today.minusDays(1));
        refund(refunded, "150", today);
        // Receipts today and the day before the last 30 days.
        issue("Receipt", "90", today, null);
        issue("Receipt", "40", today.minusDays(30), null);
        // Never counted: a quotation, a draft, a void invoice, and money in euros.
        issue("Quotation", "5000", today, today.plusDays(5));
        create("Invoice", "USD", "6000", today, today.plusDays(5), true);
        send(post("/api/v1/document/{id}/void", issue("Invoice", "7000", today, today.plusDays(5))),
                        "{\"reason\": \"Issued twice\"}")
                .andExpect(status().isOk());
        pay(create("Invoice", "EUR", "900", today, today.plusDays(5), false), "100", "0", today);

        String summary = body(summary("USD")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.outstanding.total").value(1800.0))
                .andExpect(jsonPath("$.outstanding.overdue").value(590.0))
                .andExpect(jsonPath("$.outstanding.notDue").value(1210.0))
                .andExpect(jsonPath("$.outstanding.invoices").value(6))
                .andExpect(jsonPath("$.outstanding.overdueInvoices").value(2))
                // 400 and 50 paid, the 90 receipt, less the 150 paid back.
                .andExpect(jsonPath("$.receivedLast30Days.total").value(390.0))
                .andExpect(jsonPath("$.receivedLast30Days.payments").value(4)));
        String overview = body(overview("USD", "30d"));
        assertThat((Object) JsonPath.read(summary, "$.outstanding")).isEqualTo(JsonPath.read(overview, "$.owedNow"));
        assertThat((Object) JsonPath.read(summary, "$.currencies")).isEqualTo(JsonPath.read(overview, "$.currencies"));
        assertThat(amount(summary, "$.receivedLast30Days.total"))
                .isEqualByComparingTo(amount(overview, "$.totals.received.current"));
    }

    @Test
    @DisplayName("the currency is chosen as the overview chooses it: the one asked for if there is money in it, else the default")
    void currencies() throws Exception {
        pay(issue("Invoice", "100", today.minusDays(2), today.plusDays(5)), "40", "0", today);
        pay(create("Invoice", "EUR", "50", today.minusDays(2), today.minusDays(1), false), "10", "0", today);
        create("Quotation", "GBP", "70", today, today.plusDays(5), false);

        summary(null)
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.currencies.length()").value(2))
                .andExpect(jsonPath("$.currencies[0]").value("USD"))
                .andExpect(jsonPath("$.currencies[1]").value("EUR"))
                .andExpect(jsonPath("$.outstanding.total").value(60.0))
                .andExpect(jsonPath("$.receivedLast30Days.total").value(40.0));
        summary("EUR")
                .andExpect(jsonPath("$.currency").value("EUR"))
                .andExpect(jsonPath("$.outstanding.total").value(40.0))
                .andExpect(jsonPath("$.outstanding.overdue").value(40.0))
                .andExpect(jsonPath("$.receivedLast30Days.total").value(10.0))
                .andExpect(jsonPath("$.receivedLast30Days.payments").value(1));
        // An offer isn't money, and a currency without any falls back to the default.
        for (String currency : new String[] {"GBP", "JPY", ""}) {
            summary(currency)
                    .andExpect(jsonPath("$.currency").value("USD"))
                    .andExpect(jsonPath("$.outstanding.total").value(60.0));
        }
    }

    @Test
    @DisplayName("documents without a currency aren't offered, so no currency looks like all of them")
    void noCurrency() throws Exception {
        create("Invoice", null, "70", today.minusDays(2), today.plusDays(5), false);
        pay(issue("Invoice", "100", today.minusDays(2), today.plusDays(5)), "40", "0", today);

        send(get("/api/v1/report/overview"), "").andExpect(jsonPath("$.currencies[1]").value(""));
        for (String currency : new String[] {null, "", " "}) {
            summary(currency)
                    .andExpect(jsonPath("$.currency").value("USD"))
                    .andExpect(jsonPath("$.currencies.length()").value(1))
                    .andExpect(jsonPath("$.currencies[0]").value("USD"))
                    .andExpect(jsonPath("$.outstanding.total").value(60.0));
        }
        // A business whose only documents have no currency sees its default one.
        owner = createUser();
        client = JsonPath.read(
                body(send(post("/api/v1/customer/create"), """
                        {"name": "Birch", "email": "birch-%s@clients.test", "phoneNumber": "+1"}
                        """.formatted(UUID.randomUUID()))),
                "$.createdCustomer._id");
        create("Invoice", null, "70", today.minusDays(2), today.plusDays(5), false);
        summary(null)
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.currencies").isEmpty())
                .andExpect(jsonPath("$.outstanding.total").value(0.0));
    }

    @Test
    @DisplayName("a business without documents sees nothing owed or received, in its default currency")
    void empty() throws Exception {
        summary(null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.currencies").isEmpty())
                .andExpect(content().string(containsString(
                        "\"outstanding\":{\"total\":0.00,\"notDue\":0.00,\"overdue\":0.00,"
                                + "\"invoices\":0,\"overdueInvoices\":0}")))
                .andExpect(content().string(containsString("\"receivedLast30Days\":{\"total\":0.00,\"payments\":0}")));
    }

    @Test
    @DisplayName("the summary isn't read as a document's id, a viewer may see it, and it counts only its business")
    void route() throws Exception {
        String invoice = issue("Invoice", "100", today.minusDays(2), today.plusDays(5));
        pay(invoice, "30", "0", today);

        send(get("/api/v1/document/{id}", invoice), "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.document._id").value(invoice));
        send(get("/api/v1/document/summary"), "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outstanding.total").value(70.0))
                .andExpect(jsonPath("$.receivedLast30Days.total").value(30.0));
        User viewer = createMember(businessOf(owner), BusinessRole.VIEWER);
        mockMvc.perform(get("/api/v1/document/summary")
                        .header(HttpHeaders.AUTHORIZATION, bearer(viewer, businessOf(owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outstanding.total").value(70.0))
                .andExpect(jsonPath("$.receivedLast30Days.payments").value(1));
        // Another business has nothing in it.
        mockMvc.perform(get("/api/v1/document/summary").header(HttpHeaders.AUTHORIZATION, bearer(createUser())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outstanding.total").value(0.0))
                .andExpect(jsonPath("$.receivedLast30Days.payments").value(0));
    }
}
