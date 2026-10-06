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
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
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

// The documents' view of what clients owe now: the overview's total
// receivables, split into what is not due yet and what is overdue, each in
// stretches of 15 days and with the five clients who owe most of it.
class ReceivablesIntegrationTest extends IntegrationTest {

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
        return result.andReturn().getResponse().getContentAsString();
    }

    private String client(String name) throws Exception {
        return JsonPath.read(
                body(send(post("/api/v1/customer/create"), """
                        {"name": "%s", "email": "%s@clients.test", "phoneNumber": "+1"}
                        """.formatted(name, UUID.randomUUID()))),
                "$.createdCustomer._id");
    }

    // A document for the client, dated well before it falls due, for one
    // amount; a draft is only planned.
    private String create(String type, String client, String currency, String amount, LocalDate due, boolean draft)
            throws Exception {
        return JsonPath.read(
                body(send(post("/api/v1/document/create"), """
                                {"customerId": "%s", "documentType": "%s", "currency": "%s", "draft": %s,
                                 "issueDate": "%s", "dueDate": %s,
                                 "billingItems": [{"itemName": "Work", "unitPrice": %s, "quantity": 1}]}
                                """.formatted(
                                        client,
                                        type,
                                        currency,
                                        draft,
                                        today.minusDays(500),
                                        due == null ? "null" : "\"" + due + "\"",
                                        amount))
                        .andExpect(status().isOk())),
                "$.newDocument._id");
    }

    private String invoice(String client, String amount, LocalDate due) throws Exception {
        return create("Invoice", client, "USD", amount, due, false);
    }

    // An invoice falling due at a moment on the business's clock.
    private String invoice(String client, String amount, LocalDateTime due, ZoneId zone) throws Exception {
        String invoice = invoice(client, amount, due.toLocalDate());
        jdbcTemplate.update(
                "update documents set due_date = ? where id = ?",
                Timestamp.from(due.atZone(zone).toInstant()),
                UUID.fromString(invoice));
        return invoice;
    }

    private void pay(String document, String amount, String withheld) throws Exception {
        send(post("/api/v1/document/{id}/payment", document), """
                {"amountPaid": %s, "tdsAmount": %s, "datePaid": "%s", "paymentMethod": "Bank Transfer"}
                """.formatted(amount, withheld, today))
                .andExpect(status().isCreated());
    }

    // A credit note taking the amount off the document, dated today.
    private void credit(String document, String amount) throws Exception {
        String note = JsonPath.read(
                body(send(post("/api/v1/document/{id}/credit-note", document), "").andExpect(status().isCreated())),
                "$.newDocument._id");
        send(patch("/api/v1/document/{id}", note), """
                {"billingItems": [{"itemName": "Work", "unitPrice": %s, "quantity": 1}]}
                """.formatted(amount))
                .andExpect(status().isOk());
        send(post("/api/v1/document/{id}/issue", note), "{\"issueDate\": \"%s\"}".formatted(today))
                .andExpect(status().isOk());
    }

    private void voided(String document) throws Exception {
        send(post("/api/v1/document/{id}/void", document), "{\"reason\": \"Issued twice\"}")
                .andExpect(status().isOk());
    }

    private ResultActions receivables(String... params) throws Exception {
        MockHttpServletRequestBuilder request = get("/api/v1/document/receivables");
        for (int index = 0; index < params.length; index += 2) {
            request.param(params[index], params[index + 1]);
        }
        return send(request, "");
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

    @ParameterizedTest(name = "in {0}")
    @ArgumentsSource(AwayFromUtc.class)
    @DisplayName("what is not due yet is split by days left and what is overdue by days late, each side of 15, 30"
            + " and 45 days on the business's clock, with the clients who owe most")
    void bands(ZoneId zone) throws Exception {
        inZone(zone);
        String acme = client("Acme");
        String birch = client("Birch Studio");
        String cedar = client("Cedar Works");
        String oak = client("Oak Joinery");
        // Not due yet: due in the first minutes of today, then each side of
        // 15, 30 and 45 days ahead; one partly paid with tax withheld, one
        // partly credited, and one saved without a due date, as the oldest
        // invoices were.
        invoice(acme, "100", today.atTime(0, 30), zone);
        invoice(birch, "200", today.plusDays(15).atTime(23, 30), zone);
        invoice(acme, "300", today.plusDays(16).atTime(0, 30), zone);
        pay(invoice(cedar, "400", today.plusDays(30)), "150", "50");
        credit(invoice(birch, "500", today.plusDays(31)), "100");
        invoice(oak, "600", today.plusDays(45).atTime(23, 30), zone);
        invoice(acme, "700", today.plusDays(46).atTime(0, 30), zone);
        String undated = invoice(cedar, "800", today.plusDays(5));
        jdbcTemplate.update("update documents set due_date = null where id = ?", UUID.fromString(undated));
        // Overdue: due in the last minutes of yesterday, then each side of 15,
        // 30 and 45 days late; one partly paid, and one partly credited.
        invoice(acme, "10", today.minusDays(1).atTime(23, 30), zone);
        invoice(birch, "20", today.minusDays(15).atTime(0, 30), zone);
        pay(invoice(birch, "30", today.minusDays(16).atTime(23, 30), zone), "10", "0");
        invoice(oak, "40", today.minusDays(30).atTime(0, 30), zone);
        invoice(cedar, "50", today.minusDays(31).atTime(23, 30), zone);
        credit(invoice(acme, "60", today.minusDays(45).atTime(0, 30), zone), "15");
        invoice(oak, "70", today.minusDays(46).atTime(23, 30), zone);
        invoice(cedar, "80", today.minusDays(400));
        // Never owed: an invoice paid in full, a receipt, a draft, a void
        // invoice and a quotation. Euros are owed in euros.
        pay(invoice(acme, "90", today.minusDays(5)), "90", "0");
        create("Receipt", acme, "USD", "95", null, false);
        create("Invoice", acme, "USD", "96", today.minusDays(5), true);
        voided(invoice(acme, "97", today.minusDays(5)));
        create("Quotation", acme, "USD", "98", today.plusDays(5), false);
        create("Invoice", acme, "EUR", "900", today.plusDays(5), false);
        pay(create("Invoice", birch, "EUR", "90", today.minusDays(20), false), "40", "0");
        // Oak Joinery is deleted since: its invoices are owed under the name
        // they were saved with, with no client id.
        send(delete("/api/v1/customer/{id}", oak), "").andExpect(status().isOk());

        String dollars = body(receivables("currency", "USD")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.currencies", contains("USD", "EUR")))
                .andExpect(jsonPath("$.total").value(3635.0))
                .andExpect(jsonPath("$.invoices").value(16))
                .andExpect(jsonPath("$.current.amount").value(3300.0))
                .andExpect(jsonPath("$.current.invoices").value(8))
                .andExpect(jsonPath(
                        "$.current.bands[*].key", contains("dueIn0to15", "dueIn16to30", "dueIn31to45", "dueAfter45")))
                .andExpect(jsonPath("$.current.bands[*].amount", contains(1100.0, 500.0, 1000.0, 700.0)))
                .andExpect(jsonPath("$.current.bands[*].invoices", contains(3, 2, 2, 1)))
                .andExpect(jsonPath(
                        "$.current.clients[*].client",
                        contains("Acme", "Cedar Works", "Birch Studio", "Oak Joinery")))
                .andExpect(jsonPath("$.current.clients[*].clientId", contains(acme, cedar, birch, null)))
                .andExpect(jsonPath("$.current.clients[*].amount", contains(1100.0, 1000.0, 600.0, 600.0)))
                .andExpect(jsonPath("$.current.clients[*].invoices", contains(3, 2, 2, 1)))
                .andExpect(jsonPath("$.overdue.amount").value(335.0))
                .andExpect(jsonPath("$.overdue.invoices").value(8))
                .andExpect(jsonPath(
                        "$.overdue.bands[*].key", contains("days1to15", "days16to30", "days31to45", "over45")))
                .andExpect(jsonPath("$.overdue.bands[*].amount", contains(30.0, 60.0, 95.0, 150.0)))
                .andExpect(jsonPath("$.overdue.bands[*].invoices", contains(2, 2, 2, 2)))
                .andExpect(jsonPath(
                        "$.overdue.clients[*].client", contains("Cedar Works", "Oak Joinery", "Acme", "Birch Studio")))
                .andExpect(jsonPath("$.overdue.clients[*].clientId", contains(cedar, null, acme, birch)))
                .andExpect(jsonPath("$.overdue.clients[*].amount", contains(130.0, 110.0, 55.0, 40.0)))
                .andExpect(jsonPath("$.overdue.clients[*].invoices", contains(2, 2, 2, 2)))
                .andExpect(content().string(containsString(
                        "{\"clientId\":null,\"client\":\"Oak Joinery\",\"amount\":110.00,\"invoices\":2}"))));
        String euros = body(receivables("currency", "EUR")
                .andExpect(jsonPath("$.currency").value("EUR"))
                .andExpect(jsonPath("$.total").value(950.0))
                .andExpect(jsonPath("$.current.bands[*].amount", contains(900.0, 0.0, 0.0, 0.0)))
                .andExpect(jsonPath("$.overdue.bands[*].amount", contains(0.0, 50.0, 0.0, 0.0)))
                .andExpect(jsonPath("$.overdue.clients[*].client", contains("Birch Studio")))
                .andExpect(jsonPath("$.overdue.clients[*].amount", contains(50.0))));
        // Each currency's figures are the overview's owedNow, band for band.
        for (String currency : List.of("USD", "EUR")) {
            String received = currency.equals("USD") ? dollars : euros;
            String overview = body(send(get("/api/v1/report/overview").param("currency", currency), ""));
            assertThat(amount(received, "$.total")).isEqualByComparingTo(amount(overview, "$.owedNow.total"));
            assertThat((Integer) JsonPath.read(received, "$.invoices"))
                    .isEqualTo(JsonPath.read(overview, "$.owedNow.invoices"));
            assertThat(amount(received, "$.current.amount"))
                    .isEqualByComparingTo(amount(overview, "$.owedNow.notDue"))
                    .isEqualByComparingTo(amount(overview, "$.owedNow.bands[0].amount"))
                    .isEqualByComparingTo(sum(received, "$.current.bands[*].amount"));
            assertThat((Integer) JsonPath.read(received, "$.current.invoices"))
                    .isEqualTo(JsonPath.read(overview, "$.owedNow.bands[0].invoices"));
            assertThat(amount(received, "$.overdue.amount"))
                    .isEqualByComparingTo(amount(overview, "$.owedNow.overdue"))
                    .isEqualByComparingTo(sum(received, "$.overdue.bands[*].amount"));
            assertThat((Integer) JsonPath.read(received, "$.overdue.invoices"))
                    .isEqualTo(JsonPath.read(overview, "$.owedNow.overdueInvoices"));
            List<Map<String, Object>> late = JsonPath.read(overview, "$.owedNow.bands[1:]");
            assertThat(JsonPath.<List<Map<String, Object>>>read(received, "$.overdue.bands")).isEqualTo(late);
            // The documents list's invoices not yet due are the same.
            String listed = body(send(
                    get("/api/v1/document/all").param("due", "current").param("currency", currency), ""));
            assertThat(amount(listed, "$.totals[0].balanceDue"))
                    .isEqualByComparingTo(amount(received, "$.current.amount"));
            assertThat((Integer) JsonPath.read(listed, "$.totalDocuments"))
                    .isEqualTo(JsonPath.read(received, "$.current.invoices"));
        }
    }

    @Test
    @DisplayName("five clients are listed in each part, largest first and by name when two owe the same, and"
            + " invoices linked to no client and saved without a name are one client with neither")
    void topFive() throws Exception {
        List<String> names = List.of("delta", "Acme", "echo", "birch", "Cedar", "Zed");
        for (String name : names) {
            invoice(client(name), name.equals("Zed") ? "300" : "100", today.minusDays(3));
        }
        // Saved before a client was named or linked, as the oldest invoices were.
        String nameless = invoice(client("Fir"), "500", today.minusDays(3));
        jdbcTemplate.update(
                "update documents set customer_pkid = null, customer_name = null where id = ?",
                UUID.fromString(nameless));
        invoice(client("Gum"), "50", today.plusDays(3));

        receivables()
                .andExpect(jsonPath("$.overdue.amount").value(1300.0))
                .andExpect(jsonPath("$.overdue.invoices").value(7))
                .andExpect(jsonPath("$.overdue.clients[*].client", contains(null, "Zed", "Acme", "birch", "Cedar")))
                .andExpect(jsonPath("$.overdue.clients[0].clientId").value(nullValue()))
                .andExpect(jsonPath("$.overdue.clients[*].amount", contains(500.0, 300.0, 100.0, 100.0, 100.0)))
                .andExpect(jsonPath("$.current.clients[*].client", contains("Gum")))
                .andExpect(jsonPath("$.current.clients[*].amount", contains(50.0)));
    }

    @Test
    @DisplayName("the currency is chosen as the overview chooses it, and a business owed nothing sees zeros in"
            + " every band and no clients")
    void currencyAndEmpty() throws Exception {
        receivables()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.currencies").isEmpty())
                .andExpect(jsonPath("$.total").value(0.0))
                .andExpect(jsonPath("$.invoices").value(0))
                .andExpect(jsonPath("$.current.amount").value(0.0))
                .andExpect(jsonPath("$.current.invoices").value(0))
                .andExpect(jsonPath("$.current.bands[*].amount", contains(0.0, 0.0, 0.0, 0.0)))
                .andExpect(jsonPath("$.current.clients").isEmpty())
                .andExpect(jsonPath(
                        "$.overdue.bands[*].key", contains("days1to15", "days16to30", "days31to45", "over45")))
                .andExpect(jsonPath("$.overdue.bands[*].invoices", contains(0, 0, 0, 0)))
                .andExpect(jsonPath("$.overdue.clients").isEmpty())
                .andExpect(content().string(containsString(
                        "\"current\":{\"amount\":0.00,\"invoices\":0,\"bands\":[{\"key\":\"dueIn0to15\","
                                + "\"amount\":0.00,\"invoices\":0},")));

        // Only euros are billed, so euros are shown whatever is asked for,
        // until the business bills in dollars too.
        String acme = client("Acme");
        create("Invoice", acme, "EUR", "400", today.plusDays(3), false);
        receivables().andExpect(jsonPath("$.currency").value("EUR")).andExpect(jsonPath("$.total").value(400.0));
        receivables("currency", "USD")
                .andExpect(jsonPath("$.currency").value("EUR"))
                .andExpect(jsonPath("$.currencies", contains("EUR")));
        // A receipt in dollars is billed, though nothing is owed on it.
        create("Receipt", acme, "USD", "60", null, false);
        receivables()
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.currencies", contains("USD", "EUR")))
                .andExpect(jsonPath("$.total").value(0.0));
        receivables("currency", "EUR").andExpect(jsonPath("$.total").value(400.0));
    }

    @Test
    @DisplayName("the view isn't read as a document's id, a viewer may see it, and it counts only its business")
    void route() throws Exception {
        String invoice = invoice(client("Acme"), "100", today.minusDays(2));

        send(get("/api/v1/document/{id}", invoice), "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.document._id").value(invoice));
        receivables().andExpect(status().isOk()).andExpect(jsonPath("$.overdue.amount").value(100.0));
        User viewer = createMember(businessOf(owner), BusinessRole.VIEWER);
        mockMvc.perform(get("/api/v1/document/receivables")
                        .header(HttpHeaders.AUTHORIZATION, bearer(viewer, businessOf(owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(100.0))
                .andExpect(jsonPath("$.overdue.clients[0].client").value("Acme"));
        // Another business owes nothing of it.
        mockMvc.perform(get("/api/v1/document/receivables").header(HttpHeaders.AUTHORIZATION, bearer(createUser())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0.0))
                .andExpect(jsonPath("$.overdue.clients").isEmpty());
    }
}
