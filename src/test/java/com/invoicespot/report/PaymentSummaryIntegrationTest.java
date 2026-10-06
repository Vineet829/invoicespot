package com.invoicespot.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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

// What the payments list adds up to over a period: its received total beside
// the period before, by method, receipts being one, and by the clients who
// paid most.
class PaymentSummaryIntegrationTest extends IntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private LocalDate today;
    private User owner;

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

    private String client(String name) throws Exception {
        return JsonPath.read(
                body(send(post("/api/v1/customer/create"), """
                        {"name": "%s", "email": "%s@clients.test", "phoneNumber": "+1"}
                        """.formatted(name, UUID.randomUUID()))),
                "$.createdCustomer._id");
    }

    // A document for the client dated the given day, for one amount.
    private String issue(String client, String type, String currency, String amount, LocalDate issued)
            throws Exception {
        return JsonPath.read(
                body(send(post("/api/v1/document/create"), """
                                {"customerId": "%s", "documentType": "%s", "currency": "%s",
                                 "issueDate": "%s", "dueDate": %s,
                                 "billingItems": [{"itemName": "Work", "unitPrice": %s, "quantity": 1}]}
                                """.formatted(
                                        client,
                                        type,
                                        currency,
                                        issued,
                                        type.equals("Receipt") ? "null" : "\"" + issued.plusDays(30) + "\"",
                                        amount))
                        .andExpect(status().isOk())),
                "$.newDocument._id");
    }

    private void pay(String document, String amount, String withheld, String method, LocalDate date)
            throws Exception {
        send(post("/api/v1/document/{id}/payment", document), """
                {"amountPaid": %s, "tdsAmount": %s, "paymentMethod": "%s", "datePaid": "%s"}
                """.formatted(amount, withheld, method, date))
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

    private void refund(String document, String amount, String method, LocalDate date) throws Exception {
        send(post("/api/v1/document/{id}/refund", document), """
                {"amount": %s, "paymentMethod": "%s", "dateRefunded": "%s"}
                """.formatted(amount, method, date))
                .andExpect(status().isCreated());
    }

    private ResultActions summary(String... params) throws Exception {
        MockHttpServletRequestBuilder request = get("/api/v1/payments/summary");
        for (int index = 0; index < params.length; index += 2) {
            request.param(params[index], params[index + 1]);
        }
        return send(request, "");
    }

    // The payments list in dollars over the days, or every day, with any
    // other filters given.
    private String listed(LocalDate from, LocalDate to, String... params) throws Exception {
        MockHttpServletRequestBuilder request = get("/api/v1/payments").param("currency", "USD");
        if (from != null) {
            request.param("from", from.toString()).param("to", to.toString());
        }
        for (int index = 0; index < params.length; index += 2) {
            request.param(params[index], params[index + 1]);
        }
        return body(send(request, "").andExpect(status().isOk()));
    }

    // What the list received, or nothing when it has no rows.
    private static BigDecimal received(String list) {
        List<Object> totals = JsonPath.read(list, "$.totals[*].received");
        return totals.isEmpty() ? BigDecimal.ZERO : new BigDecimal(totals.get(0).toString());
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
        Object day = JsonPath.read(body, path);
        return day == null ? null : LocalDate.parse(day.toString());
    }

    @ParameterizedTest(name = "in {0}")
    @ArgumentsSource(AwayFromUtc.class)
    @DisplayName("a period's payments, receipts and refunds add up in all, by method and by who paid, beside the"
            + " period before")
    void figures(ZoneId zone) throws Exception {
        inZone(zone);
        String acme = client("Acme");
        String birch = client("Birch Studio");
        String cedar = client("Cedar Works");
        String oak = client("Oak Joinery");
        // In the last 30 days: two payments on an invoice, one on their first
        // day and one with tax withheld; an invoice paid in full, credited
        // and partly paid back; a receipt; and a payment from a client
        // deleted since.
        String invoice = issue(acme, "Invoice", "USD", "1000", today.minusDays(40));
        pay(invoice, "400", "0", "Bank Transfer", today.minusDays(29));
        pay(invoice, "100", "50", "Cash", today.minusDays(5));
        String credited = issue(birch, "Invoice", "USD", "600", today.minusDays(50));
        pay(credited, "600", "0", "Cash", today.minusDays(10));
        credit(credited, "200", today.minusDays(8));
        refund(credited, "150", "Bank Transfer", today.minusDays(2));
        issue(cedar, "Receipt", "USD", "300", today);
        pay(issue(oak, "Invoice", "USD", "500", today.minusDays(20)), "250", "0", "UPI", today.minusDays(1));
        send(delete("/api/v1/customer/{id}", oak), "").andExpect(status().isOk());
        // In the 30 days before: a receipt on their last day and a cheque.
        issue(acme, "Receipt", "USD", "80", today.minusDays(30));
        pay(issue(birch, "Invoice", "USD", "70", today.minusDays(60)), "70", "0", "Cheque", today.minusDays(45));
        // Euros are summed apart.
        pay(issue(acme, "Invoice", "EUR", "900", today.minusDays(3)), "900", "0", "Cash", today);

        summary("period", "30d", "currency", "USD")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.currencies", contains("USD", "EUR")))
                .andExpect(jsonPath("$.period.key").value("30d"))
                .andExpect(jsonPath("$.period.from").value(today.minusDays(29).toString()))
                .andExpect(jsonPath("$.period.to").value(today.toString()))
                .andExpect(jsonPath("$.period.previousFrom").value(today.minusDays(59).toString()))
                .andExpect(jsonPath("$.period.previousTo").value(today.minusDays(30).toString()))
                .andExpect(jsonPath("$.total").value(1500.0))
                .andExpect(jsonPath("$.previousTotal").value(150.0))
                .andExpect(jsonPath("$.payments").value(6))
                .andExpect(jsonPath("$.methods[*].method", contains("Cash", "On a receipt", "Bank Transfer", "UPI")))
                .andExpect(jsonPath("$.methods[*].payments", contains(2, 1, 2, 1)))
                .andExpect(jsonPath("$.methods[*].amount", contains(700.0, 300.0, 250.0, 250.0)))
                .andExpect(jsonPath(
                        "$.payers[*].client", contains("Acme", "Birch Studio", "Cedar Works", "Oak Joinery")))
                .andExpect(jsonPath("$.payers[*].clientId", contains(acme, birch, cedar, null)))
                .andExpect(jsonPath("$.payers[*].payments", contains(2, 2, 1, 1)))
                .andExpect(jsonPath("$.payers[*].amount", contains(500.0, 450.0, 300.0, 250.0)))
                .andExpect(content().string(containsString(
                        "\"methods\":[{\"method\":\"Cash\",\"payments\":2,\"amount\":700.00},")))
                .andExpect(content().string(containsString(
                        "{\"clientId\":null,\"client\":\"Oak Joinery\",\"payments\":1,\"amount\":250.00}")));
        summary("period", "30d", "currency", "EUR")
                .andExpect(jsonPath("$.currency").value("EUR"))
                .andExpect(jsonPath("$.total").value(900.0))
                .andExpect(jsonPath("$.previousTotal").value(0.0))
                .andExpect(jsonPath("$.methods[*].method", contains("Cash")))
                .andExpect(jsonPath("$.payers[*].client", contains("Acme")));
    }

    @ParameterizedTest(name = "in {0}")
    @ArgumentsSource(AwayFromUtc.class)
    @DisplayName("over every period the totals are the payments list's for the same days, and the overview's"
            + " Received over its own, and each method is the list's rows by that method")
    void addsUpToTheList(ZoneId zone) throws Exception {
        inZone(zone);
        String acme = client("Acme");
        String birch = client("Birch Studio");
        LocalDate year = today.withDayOfMonth(1).minusMonths(11);
        LocalDate month = today.withDayOfMonth(1);
        LocalDate january = today.withDayOfYear(1);
        // Money on the first day of each period and the day before it, and
        // on the first and last days of the periods before them, by several
        // methods, with receipts and refunds.
        List<LocalDate> days = List.of(
                today,
                today.minusDays(29),
                today.minusDays(30),
                today.minusDays(59),
                today.minusDays(89),
                today.minusDays(90),
                today.minusDays(179),
                year,
                year.minusDays(1),
                year.minusMonths(12),
                month,
                month.minusDays(1),
                month.minusMonths(1),
                month.minusMonths(2),
                january,
                january.minusDays(1),
                january.minusYears(1),
                january.minusYears(1).minusDays(1));
        List<String> methods = List.of("Cash", "Bank Transfer", "UPI", "Cheque");
        String invoice = issue(acme, "Invoice", "USD", "1000000", january.minusYears(2));
        String paidBack = issue(birch, "Invoice", "USD", "100000", january.minusYears(2));
        pay(paidBack, "100000", "0", "Cash", january.minusYears(2));
        credit(paidBack, "50000", january.minusYears(2));
        for (int index = 0; index < days.size(); index++) {
            LocalDate day = days.get(index);
            pay(invoice, String.valueOf(100 + 37 * index), index % 3 == 0 ? "5" : "0", methods.get(index % 4), day);
            issue(index % 2 == 0 ? acme : birch, "Receipt", "USD", String.valueOf(40 + 11 * index), day);
            if (index % 3 == 1) {
                refund(paidBack, String.valueOf(10 + index), methods.get((index + 1) % 4), day);
            }
        }
        pay(issue(acme, "Invoice", "EUR", "900", today), "900", "0", "Cash", today);

        List<String> keys = List.of(
                "30d", "90d", "12m", "ytd", "this-month", "last-month", "this-quarter", "this-year", "last-year", "");
        for (String key : keys) {
            String summary = body(summary("period", key, "currency", "USD").andExpect(status().isOk()));
            LocalDate from = date(summary, "$.period.from");
            LocalDate to = date(summary, "$.period.to");
            String list = listed(from, to, "size", "100");
            assertThat(amount(summary, "$.total"))
                    .as(key)
                    .isPositive()
                    .isEqualByComparingTo(received(list))
                    .isEqualByComparingTo(sum(summary, "$.methods[*].amount"))
                    .isEqualByComparingTo(sum(summary, "$.payers[*].amount"));
            assertThat((Integer) JsonPath.read(summary, "$.payments"))
                    .as(key)
                    .isEqualTo(JsonPath.read(list, "$.total"))
                    .isEqualTo(sum(summary, "$.methods[*].payments").intValue());
            if (from != null) {
                String before = listed(date(summary, "$.period.previousFrom"), date(summary, "$.period.previousTo"));
                assertThat(amount(summary, "$.previousTotal")).as(key).isEqualByComparingTo(received(before));
            }
            // Each method is the list's rows by it; the list's method filter
            // asks for receipts as receipt.
            List<Map<String, Object>> rows = JsonPath.read(summary, "$.methods");
            assertThat(rows).as(key).isNotEmpty();
            assertThat(rows).as(key).anyMatch(method -> method.get("method").equals("On a receipt"));
            for (Map<String, Object> method : rows) {
                String name = (String) method.get("method");
                String byMethod = listed(from, to, "method", name.equals("On a receipt") ? "receipt" : name);
                assertThat(received(byMethod))
                        .as(key + " " + method.get("method"))
                        .isEqualByComparingTo(method.get("amount").toString());
                assertThat((Integer) JsonPath.read(byMethod, "$.total"))
                        .as(key + " " + method.get("method"))
                        .isEqualTo(method.get("payments"));
            }
        }
        // The overview's periods are its Received, now and before.
        for (String period : List.of("30d", "90d", "12m", "ytd")) {
            String overview = body(send(
                    get("/api/v1/report/overview").param("currency", "USD").param("period", period), ""));
            String summary = body(summary("period", period));
            assertThat(amount(summary, "$.total"))
                    .as(period)
                    .isEqualByComparingTo(amount(overview, "$.totals.received.current"));
            assertThat(amount(summary, "$.previousTotal"))
                    .as(period)
                    .isEqualByComparingTo(amount(overview, "$.totals.received.previous"));
        }
        summary().andExpect(jsonPath("$.previousTotal").value(nullValue()));
    }

    @Test
    @DisplayName("five payers are listed, largest first and by name when two paid the same, and money on"
            + " documents linked to no client and saved without a name is one payer with neither")
    void topFive() throws Exception {
        for (String name : List.of("delta", "Acme", "echo", "birch", "Cedar", "Zed")) {
            issue(client(name), "Receipt", "USD", name.equals("Zed") ? "300" : "100", today);
        }
        // Saved before a client was named or linked, as the oldest receipts were.
        String nameless = issue(client("Fir"), "Receipt", "USD", "500", today);
        jdbcTemplate.update(
                "update documents set customer_pkid = null, customer_name = null where id = ?",
                UUID.fromString(nameless));

        summary()
                .andExpect(jsonPath("$.total").value(1300.0))
                .andExpect(jsonPath("$.payments").value(7))
                .andExpect(jsonPath("$.methods[*].method", contains("On a receipt")))
                .andExpect(jsonPath("$.payers[*].client", contains(null, "Zed", "Acme", "birch", "Cedar")))
                .andExpect(jsonPath("$.payers[0].clientId").value(nullValue()))
                .andExpect(jsonPath("$.payers[*].amount", contains(500.0, 300.0, 100.0, 100.0, 100.0)));
    }

    @Test
    @DisplayName("the currency is chosen as the overview chooses it, and a period with no money has nothing in it")
    void currencyAndEmpty() throws Exception {
        summary("period", "90d")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.currencies").isEmpty())
                .andExpect(jsonPath("$.total").value(0.0))
                .andExpect(jsonPath("$.previousTotal").value(0.0))
                .andExpect(jsonPath("$.payments").value(0))
                .andExpect(content().string(containsString("\"methods\":[],\"payers\":[]")));
        summary()
                .andExpect(jsonPath("$.period.key").value(""))
                .andExpect(jsonPath("$.previousTotal").value(nullValue()));

        // Only euros are billed, so euros are shown whatever is asked for.
        String acme = client("Acme");
        pay(issue(acme, "Invoice", "EUR", "400", today.minusDays(100)), "400", "0", "PayPal", today.minusDays(100));
        summary("period", "30d", "currency", "USD")
                .andExpect(jsonPath("$.currency").value("EUR"))
                .andExpect(jsonPath("$.total").value(0.0))
                .andExpect(jsonPath("$.methods").isEmpty());
        summary("period", "12m")
                .andExpect(jsonPath("$.total").value(400.0))
                .andExpect(jsonPath("$.methods[*].method", contains("PayPal")));
    }

    @Test
    @DisplayName("a viewer may see the summary, it counts only its business, and a period from no list is refused")
    void route() throws Exception {
        String acme = client("Acme");
        pay(issue(acme, "Invoice", "USD", "100", today), "60", "0", "Cash", today);

        User viewer = createMember(businessOf(owner), BusinessRole.VIEWER);
        mockMvc.perform(get("/api/v1/payments/summary")
                        .param("period", "30d")
                        .header(HttpHeaders.AUTHORIZATION, bearer(viewer, businessOf(owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(60.0))
                .andExpect(jsonPath("$.payers[0].client").value("Acme"));
        mockMvc.perform(get("/api/v1/payments/summary").header(HttpHeaders.AUTHORIZATION, bearer(createUser())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0.0))
                .andExpect(jsonPath("$.payers").isEmpty());
        for (String period : List.of("7d", "6m", "2025")) {
            summary("period", period)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Choose a period from the list"));
        }
    }
}
