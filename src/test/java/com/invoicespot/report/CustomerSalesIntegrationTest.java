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

// Sales by customer: every client billed in a period, with what the period's
// documents billed it, what was paid on them and what it still owes on them,
// as the reports' top clients count them, a page at a time.
class CustomerSalesIntegrationTest extends IntegrationTest {

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

    private void pay(String document, String amount, String withheld, LocalDate date) throws Exception {
        send(post("/api/v1/document/{id}/payment", document), """
                {"amountPaid": %s, "tdsAmount": %s, "datePaid": "%s", "paymentMethod": "Bank Transfer"}
                """.formatted(amount, withheld, date))
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

    private void refund(String document, String amount, LocalDate date) throws Exception {
        send(post("/api/v1/document/{id}/refund", document), """
                {"amount": %s, "dateRefunded": "%s", "paymentMethod": "Bank Transfer"}
                """.formatted(amount, date))
                .andExpect(status().isCreated());
    }

    private void voided(String document) throws Exception {
        send(post("/api/v1/document/{id}/void", document), "{\"reason\": \"Issued twice\"}")
                .andExpect(status().isOk());
    }

    private ResultActions sales(String... params) throws Exception {
        MockHttpServletRequestBuilder request = get("/api/v1/customer/sales");
        for (int index = 0; index < params.length; index += 2) {
            request.param(params[index], params[index + 1]);
        }
        return send(request, "");
    }

    private static BigDecimal amount(Object value) {
        return new BigDecimal(value.toString());
    }

    @ParameterizedTest(name = "in {0}")
    @ArgumentsSource(AwayFromUtc.class)
    @DisplayName("each client billed in the period, with what it was billed, paid and still owes on the period's"
            + " documents, the most billed first")
    void figures(ZoneId zone) throws Exception {
        inZone(zone);
        String acme = client("Acme");
        String birch = client("Birch Studio");
        String cedar = client("Cedar Works");
        String oak = client("Oak Joinery");
        // Acme: an invoice partly paid with tax withheld, and a receipt.
        pay(issue("Invoice", acme, "1000", today.minusDays(1)), "400", "100", today);
        issue("Receipt", acme, "500", today.minusDays(10));
        // Birch Studio: an invoice on the period's first day, paid in full,
        // then credited and paid back.
        String credited = issue("Invoice", birch, "2000", today.minusDays(29));
        pay(credited, "2000", "0", today.minusDays(29));
        credit(credited, "300", today.minusDays(5));
        refund(credited, "300", today.minusDays(2));
        issue("Invoice", cedar, "900", today);
        // Oak Joinery, deleted since, is billed under the name its invoice
        // was saved with, with no client id.
        pay(issue("Invoice", oak, "600", today.minusDays(3)), "100", "0", today);
        send(delete("/api/v1/customer/{id}", oak), "").andExpect(status().isOk());
        // The day before the period; never counted: a quotation, a draft, a
        // void invoice, and euros.
        issue("Invoice", acme, "700", today.minusDays(30));
        issue("Quotation", acme, "5000", today);
        create("Invoice", acme, "USD", "6000", today, true);
        voided(issue("Invoice", cedar, "7000", today));
        create("Invoice", acme, "EUR", "8000", today, false);

        String sold = body(sales("period", "30d", "currency", "USD")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.currencies", contains("USD", "EUR")))
                .andExpect(jsonPath("$.period.key").value("30d"))
                .andExpect(jsonPath("$.period.from").value(today.minusDays(29).toString()))
                .andExpect(jsonPath("$.period.previousTo").value(today.minusDays(30).toString()))
                .andExpect(jsonPath("$.totalClients").value(4))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.numberOfPages").value(1))
                .andExpect(jsonPath(
                        "$.clients[*].client", contains("Birch Studio", "Acme", "Cedar Works", "Oak Joinery")))
                .andExpect(jsonPath("$.clients[*].clientId", contains(birch, acme, cedar, null)))
                .andExpect(jsonPath("$.clients[*].documents", contains(2, 2, 1, 1)))
                .andExpect(jsonPath("$.clients[*].billed", contains(1700.0, 1500.0, 900.0, 600.0)))
                .andExpect(jsonPath("$.clients[*].paid", contains(1700.0, 900.0, 0.0, 100.0)))
                .andExpect(jsonPath("$.clients[*].owed", contains(0.0, 500.0, 900.0, 500.0)))
                .andExpect(jsonPath("$.totals.documents").value(6))
                .andExpect(jsonPath("$.totals.billed").value(4700.0))
                .andExpect(jsonPath("$.totals.paid").value(2700.0))
                .andExpect(jsonPath("$.totals.owed").value(1900.0))
                .andExpect(content().string(containsString(
                        "{\"clientId\":null,\"client\":\"Oak Joinery\",\"documents\":1,\"billed\":600.00,"
                                + "\"paid\":100.00,\"owed\":500.00}"))));
        // What they were billed is what the overview billed.
        String overview = body(send(get("/api/v1/report/overview").param("period", "30d"), ""));
        assertThat(amount(JsonPath.read(sold, "$.totals.billed")))
                .isEqualByComparingTo(amount(JsonPath.read(overview, "$.totals.billed.current")));
        sales("period", "30d", "currency", "EUR")
                .andExpect(jsonPath("$.clients[*].client", contains("Acme")))
                .andExpect(jsonPath("$.clients[*].billed", contains(8000.0)))
                .andExpect(jsonPath("$.totals.owed").value(8000.0));
    }

    @ParameterizedTest(name = "in {0}")
    @ArgumentsSource(AwayFromUtc.class)
    @DisplayName("over each of the reports' periods the first five are the reports' top clients and the rest add"
            + " up to their other clients")
    void addsUpToTheReports(ZoneId zone) throws Exception {
        inZone(zone);
        LocalDate year = today.withDayOfMonth(1).minusMonths(11);
        LocalDate january = today.withDayOfYear(1);
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
        List<String> clients = List.of(
                client("Acme"), client("Birch Studio"), client("Cedar Works"), client("Delta Foods"),
                client("Elm Dental"), client("Fir Logistics"), client("Gum Tree"));
        // Seven clients billed on the first day of each period and the day
        // before it, some paid in part with tax withheld, some credited.
        for (int index = 0; index < days.size(); index++) {
            for (int number = 0; number < clients.size(); number++) {
                String amount = String.valueOf(1000 + 97 * number + 13 * index);
                String invoice = issue("Invoice", clients.get(number), amount, days.get(index));
                if ((index + number) % 3 == 0) {
                    pay(invoice, String.valueOf(100 + number), number % 2 == 0 ? "10" : "0", days.get(index));
                }
                if ((index + number) % 4 == 1) {
                    credit(invoice, String.valueOf(50 + index), days.get(index));
                }
            }
        }

        Map<String, String> periods = Map.of("30d", "30d", "90d", "90d", "12m", "12m", "this-year", "ytd");
        for (Map.Entry<String, String> period : periods.entrySet()) {
            String key = period.getKey();
            String sold = body(sales("period", key, "currency", "USD", "size", "100").andExpect(status().isOk()));
            String report = body(send(get("/api/v1/report").param("period", period.getValue()), "")
                    .andExpect(status().isOk()));
            String dollars = "$.currencies[?(@.currency == 'USD')]";
            List<Map<String, Object>> top = JsonPath.read(report, dollars + ".clients[*]");
            List<Map<String, Object>> others = JsonPath.read(report, dollars + ".otherClients");
            List<Object> billed = JsonPath.read(report, dollars + ".billed");
            List<Map<String, Object>> rows = JsonPath.read(sold, "$.clients");
            assertThat(top).as(key).hasSize(5);
            assertThat(rows).as(key).hasSize(5 + ((Integer) others.get(0).get("clients")));
            for (int index = 0; index < top.size(); index++) {
                Map<String, Object> row = rows.get(index);
                Map<String, Object> client = top.get(index);
                assertThat(row.get("clientId")).as(key).isEqualTo(client.get("customerId"));
                assertThat(row.get("client")).as(key).isEqualTo(client.get("name"));
                assertThat(row.get("documents")).as(key).isEqualTo(client.get("documents"));
                assertThat(amount(row.get("billed"))).as(key).isEqualByComparingTo(amount(client.get("billed")));
                assertThat(amount(row.get("paid"))).as(key).isEqualByComparingTo(amount(client.get("paid")));
                assertThat(amount(row.get("owed"))).as(key).isEqualByComparingTo(amount(client.get("outstanding")));
            }
            // The totals are the top five and the rest added up, and what
            // they were billed is the period's billed total.
            for (Map.Entry<String, String> field : Map.of(
                            "documents", "documents", "billed", "billed", "paid", "paid", "owed", "outstanding")
                    .entrySet()) {
                BigDecimal expected = top.stream()
                        .map(client -> amount(client.get(field.getValue())))
                        .reduce(amount(others.get(0).get(field.getValue())), BigDecimal::add);
                assertThat(amount(JsonPath.read(sold, "$.totals." + field.getKey())))
                        .as(key + " " + field.getKey())
                        .isEqualByComparingTo(expected);
            }
            assertThat(amount(JsonPath.read(sold, "$.totals.billed")))
                    .as(key)
                    .isEqualByComparingTo(amount(billed.get(0)));
        }
    }

    @Test
    @DisplayName("clients sort by billed, paid, owed or name either way, ties by name, and come a page at a time")
    void sortingAndPaging() throws Exception {
        pay(issue("Invoice", client("Delta"), "400", today), "400", "0", today);
        issue("Invoice", client("alpha"), "300", today);
        pay(issue("Invoice", client("Charlie"), "300", today), "100", "0", today);
        pay(issue("Invoice", client("bravo"), "100", today), "50", "0", today);
        pay(issue("Invoice", client("Echo"), "200", today), "200", "0", today);
        // Saved before a client was named or linked, as the oldest invoices were.
        String nameless = issue("Invoice", client("Fir"), "50", today);
        jdbcTemplate.update(
                "update documents set customer_pkid = null, customer_name = null where id = ?",
                UUID.fromString(nameless));

        String clients = "$.clients[*].client";
        sales()
                .andExpect(jsonPath(clients, contains("Delta", "alpha", "Charlie", "Echo", "bravo", null)))
                .andExpect(jsonPath("$.clients[5].clientId").value(nullValue()));
        sales("sort", "billed", "dir", "asc")
                .andExpect(jsonPath(clients, contains(null, "bravo", "Echo", "alpha", "Charlie", "Delta")));
        sales("sort", "paid", "dir", "desc")
                .andExpect(jsonPath(clients, contains("Delta", "Echo", "Charlie", "bravo", null, "alpha")))
                .andExpect(jsonPath("$.clients[*].paid", contains(400.0, 200.0, 100.0, 50.0, 0.0, 0.0)));
        sales("sort", "owed", "dir", "desc")
                .andExpect(jsonPath(clients, contains("alpha", "Charlie", null, "bravo", "Delta", "Echo")))
                .andExpect(jsonPath("$.clients[*].owed", contains(300.0, 200.0, 50.0, 50.0, 0.0, 0.0)));
        sales("sort", "name", "dir", "asc")
                .andExpect(jsonPath(clients, contains(null, "alpha", "bravo", "Charlie", "Delta", "Echo")));
        sales("sort", "name", "dir", "desc")
                .andExpect(jsonPath(clients, contains("Echo", "Delta", "Charlie", "bravo", "alpha", null)));
        // A page at a time, the totals counting every client.
        sales("size", "2", "page", "2")
                .andExpect(jsonPath("$.clients[*].client", contains("Charlie", "Echo")))
                .andExpect(jsonPath("$.totalClients").value(6))
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.numberOfPages").value(3))
                .andExpect(jsonPath("$.totals.documents").value(6))
                .andExpect(jsonPath("$.totals.billed").value(1350.0))
                .andExpect(jsonPath("$.totals.paid").value(750.0))
                .andExpect(jsonPath("$.totals.owed").value(600.0));
        sales("size", "2", "page", "4")
                .andExpect(jsonPath("$.clients").isEmpty())
                .andExpect(jsonPath("$.totalClients").value(6));
        sales("size", "1000").andExpect(jsonPath("$.size").value(100));
        sales("sort", "documents")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Clients can be sorted by billed, paid, owed or name"));
    }

    @Test
    @DisplayName("the currency is chosen as the overview chooses it, and a period no one was billed in lists no one")
    void currencyAndEmpty() throws Exception {
        sales("period", "30d")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.currencies").isEmpty())
                .andExpect(jsonPath("$.totalClients").value(0))
                .andExpect(jsonPath("$.numberOfPages").value(0))
                .andExpect(content().string(containsString(
                        "\"totals\":{\"documents\":0,\"billed\":0.00,\"paid\":0.00,\"owed\":0.00},\"clients\":[]")));

        // Only euros are billed, so euros are shown whatever is asked for;
        // an older sale is in no recent period, but in all dates.
        create("Invoice", client("Acme"), "EUR", "400", today.minusDays(100), false);
        sales("period", "30d", "currency", "USD")
                .andExpect(jsonPath("$.currency").value("EUR"))
                .andExpect(jsonPath("$.clients").isEmpty());
        sales()
                .andExpect(jsonPath("$.period.key").value(""))
                .andExpect(jsonPath("$.period.from").value(nullValue()))
                .andExpect(jsonPath("$.clients[*].billed", contains(400.0)));
    }

    @Test
    @DisplayName("the view isn't read as a client's id, a viewer may see it, it counts only its business, and a"
            + " period from no list is refused")
    void route() throws Exception {
        String acme = client("Acme");
        issue("Invoice", acme, "100", today);

        send(get("/api/v1/customer/{id}", acme), "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customer._id").value(acme));
        sales().andExpect(status().isOk()).andExpect(jsonPath("$.clients[*].clientId", contains(acme)));
        User viewer = createMember(businessOf(owner), BusinessRole.VIEWER);
        mockMvc.perform(get("/api/v1/customer/sales")
                        .param("period", "this-month")
                        .header(HttpHeaders.AUTHORIZATION, bearer(viewer, businessOf(owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clients[*].client", contains("Acme")));
        mockMvc.perform(get("/api/v1/customer/sales").header(HttpHeaders.AUTHORIZATION, bearer(createUser())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clients").isEmpty());
        sales("period", "6m")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Choose a period from the list"));
    }
}
