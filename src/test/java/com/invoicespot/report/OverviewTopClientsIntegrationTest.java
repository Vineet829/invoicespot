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
import com.invoicespot.user.User;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
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

// The overview's top clients: the five its period billed most in its currency,
// as its billed total counts them, each with what it owes now on invoices of
// any date and how many documents billed it.
class OverviewTopClientsIntegrationTest extends IntegrationTest {

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

    // A document for the client dated the day, for one amount; a draft is
    // only planned for that day.
    private String create(String type, String client, String currency, String amount, LocalDate issued, boolean draft)
            throws Exception {
        return JsonPath.read(
                body(send(post("/api/v1/document/create"), """
                                {"customerId": "%s", "documentType": "%s", "currency": "%s", "draft": %s,
                                 "issueDate": "%s",
                                 "billingItems": [{"itemName": "Work", "unitPrice": %s, "quantity": 1}]}
                                """.formatted(client, type, currency, draft, issued, amount))
                        .andExpect(status().isOk())),
                "$.newDocument._id");
    }

    private String issue(String type, String client, String amount, LocalDate issued) throws Exception {
        return create(type, client, "USD", amount, issued, false);
    }

    private void pay(String document, String amount, LocalDate date) throws Exception {
        send(post("/api/v1/document/{id}/payment", document), """
                {"amountPaid": %s, "datePaid": "%s", "paymentMethod": "Bank Transfer"}
                """.formatted(amount, date))
                .andExpect(status().isCreated());
    }

    // A credit note taking the amount off the document, dated the given day.
    private void credit(String document, String amount, LocalDate issued) throws Exception {
        String note = JsonPath.read(
                body(send(post("/api/v1/document/{id}/credit-note", document), "").andExpect(status().isCreated())),
                "$.newDocument._id");
        send(patch("/api/v1/document/{id}", note), """
                {"billingItems": [{"itemName": "Work", "unitPrice": %s, "quantity": 1}]}
                """.formatted(amount))
                .andExpect(status().isOk());
        send(post("/api/v1/document/{id}/issue", note), "{\"issueDate\": \"%s\"}".formatted(issued))
                .andExpect(status().isOk());
    }

    private void voided(String document) throws Exception {
        send(post("/api/v1/document/{id}/void", document), "{\"reason\": \"Issued twice\"}")
                .andExpect(status().isOk());
    }

    private ResultActions overview(String... params) throws Exception {
        MockHttpServletRequestBuilder request = get("/api/v1/report/overview");
        for (int index = 0; index < params.length; index += 2) {
            request.param(params[index], params[index + 1]);
        }
        return send(request, "");
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
    @DisplayName("the five clients the period billed most come first, each with what it owes now and how many"
            + " documents billed it")
    void topFive(ZoneId zone) throws Exception {
        inZone(zone);
        String acme = client("Acme");
        String birch = client("Birch Studio");
        String cedar = client("Cedar Works");
        String delta = client("Delta Foods");
        String elm = client("Elm Dental");
        String fir = client("Fir Logistics");
        // Acme: an invoice and a receipt, and an older invoice it still owes.
        issue("Invoice", acme, "1000", today.minusDays(1));
        issue("Receipt", acme, "500", today.minusDays(10));
        issue("Invoice", acme, "300", today.minusDays(200));
        // Birch Studio: an invoice on the period's first day, mostly paid, and
        // a credit note on it.
        String birchInvoice = issue("Invoice", birch, "2000", today.minusDays(89));
        pay(birchInvoice, "1500", today.minusDays(89));
        credit(birchInvoice, "200", today.minusDays(5));
        issue("Invoice", cedar, "900", today);
        // Delta Foods: a receipt, and an invoice it owes from the day before the period.
        issue("Receipt", delta, "700", today.minusDays(30));
        issue("Invoice", delta, "3000", today.minusDays(90));
        pay(issue("Invoice", elm, "400", today.minusDays(60)), "400", today.minusDays(60));
        issue("Invoice", fir, "100", today.minusDays(2));
        // Never counted: a quotation, a draft, a void invoice, and money in euros.
        issue("Quotation", fir, "5000", today);
        create("Invoice", elm, "USD", "6000", today, true);
        voided(issue("Invoice", cedar, "7000", today));
        create("Invoice", acme, "EUR", "8000", today, false);

        overview("period", "90d")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.topClients.length()").value(5))
                .andExpect(jsonPath(
                        "$.topClients[*].client",
                        contains("Birch Studio", "Acme", "Cedar Works", "Delta Foods", "Elm Dental")))
                .andExpect(jsonPath("$.topClients[*].clientId", contains(birch, acme, cedar, delta, elm)))
                .andExpect(jsonPath("$.topClients[*].billed", contains(1800.0, 1500.0, 900.0, 700.0, 400.0)))
                .andExpect(jsonPath("$.topClients[*].owed", contains(300.0, 1300.0, 900.0, 3000.0, 0.0)))
                .andExpect(jsonPath("$.topClients[*].documents", contains(2, 2, 1, 1, 1)))
                .andExpect(content().string(containsString("\"client\":\"Elm Dental\",\"billed\":400.00,"
                        + "\"owed\":0.00,\"documents\":1}")));
        // The last 30 days start the day after Delta Foods' receipt. Of Birch
        // Studio's documents they hold only the credit note, which takes its
        // amount off what Birch Studio was billed.
        overview("period", "30d")
                .andExpect(jsonPath(
                        "$.topClients[*].client", contains("Acme", "Cedar Works", "Fir Logistics", "Birch Studio")))
                .andExpect(jsonPath("$.topClients[*].billed", contains(1500.0, 900.0, 100.0, -200.0)))
                .andExpect(jsonPath("$.topClients[*].owed", contains(1300.0, 900.0, 100.0, 300.0)))
                .andExpect(jsonPath("$.topClients[*].documents", contains(2, 1, 1, 1)));
        // The last 12 months hold Acme's older invoice and Delta Foods' too;
        // Acme and Birch Studio were billed the same, and go by name.
        overview("period", "12m")
                .andExpect(jsonPath(
                        "$.topClients[*].client",
                        contains("Delta Foods", "Acme", "Birch Studio", "Cedar Works", "Elm Dental")))
                .andExpect(jsonPath("$.topClients[*].billed", contains(3700.0, 1800.0, 1800.0, 900.0, 400.0)))
                .andExpect(jsonPath("$.topClients[*].documents", contains(2, 3, 2, 1, 1)));
    }

    @ParameterizedTest(name = "in {0}")
    @ArgumentsSource(AwayFromUtc.class)
    @DisplayName("in every period the clients add up to what the period billed, and each to the documents the"
            + " documents list bills it for the same days")
    void addsUp(ZoneId zone) throws Exception {
        inZone(zone);
        String acme = client("Acme");
        String birch = client("Birch Studio");
        String cedar = client("Cedar Works");
        LocalDate year = today.minusMonths(12).plusDays(1);
        LocalDate january = today.withDayOfYear(1);
        // Documents on the first day of each period and the day before it.
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
        List<String> clients = List.of(acme, birch, cedar);
        for (int index = 0; index < days.size(); index++) {
            String invoice =
                    issue("Invoice", clients.get(index % 3), String.valueOf(100 + 37 * index), days.get(index));
            if (index % 2 == 0) {
                credit(invoice, String.valueOf(10 + index), days.get(index));
            }
            if (index % 3 == 0) {
                pay(invoice, "30", days.get(index));
            }
            issue("Receipt", clients.get((index + 1) % 3), String.valueOf(40 + 11 * index), days.get(index));
        }

        for (String period : List.of("30d", "90d", "12m", "ytd")) {
            String overview = body(overview("period", period).andExpect(status().isOk()));
            assertThat(sum(overview, "$.topClients[*].billed"))
                    .as(period)
                    .isEqualByComparingTo(JsonPath.read(overview, "$.totals.billed.current").toString());
            List<Map<String, Object>> rows = JsonPath.read(overview, "$.topClients");
            assertThat(rows).as(period).hasSize(3);
            for (Map<String, Object> row : rows) {
                String client = (String) row.get("clientId");
                String listed = body(billedIn(period, client));
                assertThat((Integer) JsonPath.read(listed, "$.totalDocuments"))
                        .as(period + " " + row.get("client"))
                        .isEqualTo(row.get("documents"));
                assertThat(sum(listed, "$.totals[*].billed"))
                        .as(period + " " + row.get("client"))
                        .isEqualByComparingTo(row.get("billed").toString());
                assertThat(sum(body(outstanding(client)), "$.totals[*].balanceDue"))
                        .as(period + " " + row.get("client"))
                        .isEqualByComparingTo(row.get("owed").toString());
            }
        }
    }

    // The documents list's invoices, receipts and credit notes in dollars for
    // the client, over the days one of the overview's periods covers: this
    // year to date is a pair of dates there.
    private ResultActions billedIn(String period, String client) throws Exception {
        MockHttpServletRequestBuilder request = get("/api/v1/document/all")
                .param("size", "100")
                .param("billed", "true")
                .param("currency", "USD")
                .param("customerId", client);
        if (period.equals("ytd")) {
            request.param("from", today.withDayOfYear(1).toString()).param("to", today.toString());
        } else {
            request.param("period", period);
        }
        return send(request, "").andExpect(status().isOk());
    }

    // The documents list's invoices the client still owes dollars on, of any date.
    private ResultActions outstanding(String client) throws Exception {
        return send(
                        get("/api/v1/document/all")
                                .param("outstanding", "true")
                                .param("currency", "USD")
                                .param("customerId", client),
                        "")
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("clients billed the same go by name, whatever its case, and the sixth is left out")
    void ties() throws Exception {
        for (String name : List.of("delta", "Acme", "echo", "birch", "Cedar")) {
            issue("Invoice", client(name), "100", today);
        }
        issue("Invoice", client("Zed"), "300", today);

        overview()
                .andExpect(jsonPath("$.topClients[*].client", contains("Zed", "Acme", "birch", "Cedar", "delta")))
                .andExpect(jsonPath("$.topClients[*].billed", contains(300.0, 100.0, 100.0, 100.0, 100.0)));
    }

    @ParameterizedTest(name = "in {0}")
    @ArgumentsSource(AwayFromUtc.class)
    @DisplayName("documents of a client deleted since are one client by the name they were saved with, with no id,"
            + " owing what they still do")
    void deletedClient(ZoneId zone) throws Exception {
        inZone(zone);
        String cedar = client("Cedar Works");
        issue("Invoice", cedar, "500", today.minusDays(3));
        issue("Receipt", cedar, "300", today.minusDays(1));
        issue("Invoice", cedar, "200", today.minusDays(100));
        String oak = client("Oak Joinery");
        issue("Invoice", oak, "80", today);
        send(delete("/api/v1/customer/{id}", cedar), "").andExpect(status().isOk());
        send(delete("/api/v1/customer/{id}", oak), "").andExpect(status().isOk());
        // A client added since under the same name is a client of its own.
        String again = client("Cedar Works");
        issue("Invoice", again, "50", today);

        overview()
                .andExpect(jsonPath("$.topClients[*].client", contains("Cedar Works", "Oak Joinery", "Cedar Works")))
                .andExpect(jsonPath("$.topClients[0].clientId").value(nullValue()))
                .andExpect(jsonPath("$.topClients[1].clientId").value(nullValue()))
                .andExpect(jsonPath("$.topClients[2].clientId").value(again))
                .andExpect(jsonPath("$.topClients[*].billed", contains(800.0, 80.0, 50.0)))
                .andExpect(jsonPath("$.topClients[*].owed", contains(700.0, 80.0, 50.0)))
                .andExpect(jsonPath("$.topClients[*].documents", contains(2, 1, 1)))
                .andExpect(content().string(containsString("{\"clientId\":null,\"client\":\"Cedar Works\",")));
    }

    @Test
    @DisplayName("documents linked to no client and saved without a name are one client, with no name or id, owing"
            + " what they still do")
    void noName() throws Exception {
        String cedar = client("Cedar Works");
        String invoice = issue("Invoice", cedar, "500", today.minusDays(3));
        String receipt = issue("Receipt", cedar, "300", today.minusDays(1));
        issue("Invoice", client("Oak Joinery"), "80", today);
        // Saved before a client was named or linked, as the oldest documents were.
        for (String document : List.of(invoice, receipt)) {
            jdbcTemplate.update(
                    "update documents set customer_pkid = null, customer_name = null where id = ?",
                    UUID.fromString(document));
        }

        overview()
                .andExpect(jsonPath("$.topClients.length()").value(2))
                .andExpect(jsonPath("$.topClients[0].clientId").value(nullValue()))
                .andExpect(jsonPath("$.topClients[0].client").value(nullValue()))
                .andExpect(jsonPath("$.topClients[0].billed").value(800.0))
                .andExpect(jsonPath("$.topClients[0].owed").value(500.0))
                .andExpect(jsonPath("$.topClients[0].documents").value(2))
                .andExpect(jsonPath("$.topClients[1].client").value("Oak Joinery"))
                .andExpect(content().string(containsString("{\"clientId\":null,\"client\":null,\"billed\":800.00,")));
    }

    @Test
    @DisplayName("clients are billed and owe in the overview's currency: the one asked for when the business bills"
            + " in it")
    void currency() throws Exception {
        String acme = client("Acme");
        String birch = client("Birch Studio");
        issue("Invoice", acme, "1000", today);
        create("Invoice", acme, "EUR", "400", today, false);
        create("Invoice", acme, "EUR", "250", today.minusDays(120), false);
        create("Invoice", birch, "EUR", "600", today, false);

        overview()
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.topClients[*].client", contains("Acme")))
                .andExpect(jsonPath("$.topClients[*].billed", contains(1000.0)))
                .andExpect(jsonPath("$.topClients[*].owed", contains(1000.0)));
        overview("currency", "EUR")
                .andExpect(jsonPath("$.currency").value("EUR"))
                .andExpect(jsonPath("$.topClients[*].client", contains("Birch Studio", "Acme")))
                .andExpect(jsonPath("$.topClients[*].billed", contains(600.0, 400.0)))
                .andExpect(jsonPath("$.topClients[*].owed", contains(600.0, 650.0)));
    }

    @Test
    @DisplayName("a period no one was billed in lists no one")
    void empty() throws Exception {
        overview().andExpect(content().string(containsString("\"topClients\":[]")));

        String acme = client("Acme");
        issue("Quotation", acme, "900", today);
        create("Invoice", acme, "USD", "800", today, true);
        voided(issue("Invoice", acme, "700", today));
        issue("Invoice", acme, "600", today.minusDays(40));
        overview("period", "30d").andExpect(content().string(containsString("\"topClients\":[]")));
        overview("period", "90d")
                .andExpect(jsonPath("$.topClients[*].client", contains("Acme")))
                .andExpect(jsonPath("$.topClients[*].billed", contains(600.0)))
                .andExpect(jsonPath("$.topClients[*].documents", contains(1)));
    }
}
