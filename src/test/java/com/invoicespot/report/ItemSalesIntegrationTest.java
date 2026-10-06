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
import java.util.ArrayList;
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

// Sales by item: every item a period's lines sold, as the overview's top items
// count them, with no limit, and each item's own page of who bought it and the
// lines that sold it.
class ItemSalesIntegrationTest extends IntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User owner;
    private LocalDate today;
    private String acme;

    @BeforeEach
    void owner() throws Exception {
        owner = createUser();
        today = LocalDate.now(ZoneOffset.UTC);
        acme = client("Acme");
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

    // A line: what it sells, at what unit price, how many, with a discount and
    // a tax rate, each a percentage.
    private static String line(String name, String price, String quantity, String discount, String rate) {
        return """
                {"itemName": "%s", "unitPrice": %s, "quantity": %s, "discount": "%s", "taxRate": "%s"}"""
                .formatted(name, price, quantity, discount, rate);
    }

    private static String line(String name, String price, String quantity) {
        return line(name, price, quantity, "0", "0");
    }

    // A document for the client with the lines, dated the day; a draft is
    // only planned for it.
    private String create(
            String type, String client, String currency, LocalDate issued, boolean draft, String... lines)
            throws Exception {
        return JsonPath.read(
                body(send(post("/api/v1/document/create"), """
                                {"customerId": "%s", "documentType": "%s", "currency": "%s", "draft": %s,
                                 "issueDate": "%s", "billingItems": [%s]}
                                """.formatted(client, type, currency, draft, issued, String.join(", ", lines)))
                        .andExpect(status().isOk())),
                "$.newDocument._id");
    }

    private String issue(String type, String client, LocalDate issued, String... lines) throws Exception {
        return create(type, client, "USD", issued, false, lines);
    }

    // A credit note dated the day, taking the lines off the document.
    private String credit(String document, LocalDate issued, String... lines) throws Exception {
        String note = JsonPath.read(
                body(send(post("/api/v1/document/{id}/credit-note", document), "").andExpect(status().isCreated())),
                "$.newDocument._id");
        send(patch("/api/v1/document/{id}", note), "{\"billingItems\": [%s]}".formatted(String.join(", ", lines)))
                .andExpect(status().isOk());
        send(post("/api/v1/document/{id}/issue", note), "{\"issueDate\": \"%s\"}".formatted(issued))
                .andExpect(status().isOk());
        return note;
    }

    private void voided(String document) throws Exception {
        send(post("/api/v1/document/{id}/void", document), "{\"reason\": \"Issued twice\"}")
                .andExpect(status().isOk());
    }

    // Names one of the document's lines as lines could be named before a name
    // was required.
    private void rename(String document, int position, String name) {
        jdbcTemplate.update(
                """
                update document_billing_items set item_name = ?
                where position = ? and document_pkid = (select pkid from documents where id = ?)
                """,
                name,
                position,
                UUID.fromString(document));
    }

    private ResultActions sales(String... params) throws Exception {
        MockHttpServletRequestBuilder request = get("/api/v1/item/sales");
        for (int index = 0; index < params.length; index += 2) {
            request.param(params[index], params[index + 1]);
        }
        return send(request, "");
    }

    private ResultActions detail(String... params) throws Exception {
        MockHttpServletRequestBuilder request = get("/api/v1/item/sales/detail");
        for (int index = 0; index < params.length; index += 2) {
            request.param(params[index], params[index + 1]);
        }
        return send(request, "");
    }

    private static BigDecimal amount(Object value) {
        return new BigDecimal(value.toString());
    }

    // The amounts at the path added up.
    private static BigDecimal sum(String body, String path) {
        List<Object> amounts = JsonPath.read(body, path);
        return amounts.stream().map(ItemSalesIntegrationTest::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @ParameterizedTest(name = "in {0}")
    @ArgumentsSource(AwayFromUtc.class)
    @DisplayName("every item the period sold, net of discounts and credit notes and before tax, with its share of"
            + " the income, and an item's page with who bought it and its lines")
    void figures(ZoneId zone) throws Exception {
        inZone(zone);
        String birch = client("Birch Studio");
        String cedar = client("Cedar Works");
        // An invoice with a line taxed 10% and a fraction of a line at a 10%
        // discount, a receipt with the same item in another spelling, and a
        // credit note taking one of them off.
        String invoice = issue(
                "Invoice",
                acme,
                today.minusDays(1),
                line("Website design", "450", "3", "0", "10"),
                line("Hosting", "120", "2"),
                line("Copywriting", "150", "4.5", "10", "0"));
        issue("Receipt", birch, today.minusDays(10), line("Logo", "3200", "1"), line("website design", "450", "1"));
        String credited = credit(invoice, today, line("Website design", "450", "1", "0", "10"));
        // Cedar Works, deleted since, bought under the name its invoice was
        // saved with, with no client id.
        issue("Invoice", cedar, today, line("Photography", "900", "1"), line("Social kit", "700", "1"));
        send(delete("/api/v1/customer/{id}", cedar), "").andExpect(status().isOk());
        // Lines saved without a name, before a name was required.
        String unnamed = issue("Invoice", acme, today.minusDays(2), line("Kept", "60", "1"), line("Gone", "15", "2"));
        rename(unnamed, 0, null);
        rename(unnamed, 1, " ");
        // The day before the period; never counted: a quotation, a draft, a
        // void invoice, and lines in euros.
        issue("Invoice", acme, today.minusDays(30), line("Logo", "3200", "1"));
        issue("Quotation", acme, today, line("Logo", "9999", "1"));
        create("Invoice", acme, "USD", today, true, line("Logo", "8888", "1"));
        voided(issue("Invoice", acme, today, line("Logo", "7777", "1")));
        create("Invoice", acme, "EUR", today, false, line("Logo", "6666", "1"));

        sales("period", "30d", "currency", "USD")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.currencies", contains("USD", "EUR")))
                .andExpect(jsonPath("$.period.key").value("30d"))
                .andExpect(jsonPath("$.period.from").value(today.minusDays(29).toString()))
                .andExpect(jsonPath("$.total").value(7087.5))
                .andExpect(jsonPath("$.totalItems").value(7))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.numberOfPages").value(1))
                .andExpect(jsonPath(
                        "$.items[*].item",
                        contains(
                                "Logo", "Website design", "Photography", "Social kit", "Copywriting", "Hosting",
                                "Other lines")))
                .andExpect(jsonPath("$.items[*].quantity", contains(1, 3, 1, 1, 4.5, 2, 3)))
                .andExpect(jsonPath("$.items[*].amount", contains(3200.0, 1350.0, 900.0, 700.0, 607.5, 240.0, 90.0)))
                .andExpect(jsonPath(
                        "$.items[*].share", contains(0.4515, 0.1905, 0.127, 0.0988, 0.0857, 0.0339, 0.0127)))
                .andExpect(jsonPath("$.items[*].documents", contains(1, 3, 1, 1, 1, 1, 1)))
                .andExpect(content().string(containsString(
                        "{\"item\":\"Logo\",\"quantity\":1,\"amount\":3200.00,\"share\":0.4515,\"documents\":1}")));
        // Asked for in any case and with spaces around it, an item is the
        // list's row, with who bought it and its lines, the newest first.
        detail("name", "  WEBSITE design ", "period", "30d", "currency", "USD")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.item").value("Website design"))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.period.key").value("30d"))
                .andExpect(jsonPath("$.quantity").value(3))
                .andExpect(jsonPath("$.amount").value(1350.0))
                .andExpect(jsonPath("$.share").value(0.1905))
                .andExpect(jsonPath("$.documents").value(3))
                .andExpect(jsonPath("$.clients[*].client", contains("Acme", "Birch Studio")))
                .andExpect(jsonPath("$.clients[*].clientId", contains(acme, birch)))
                .andExpect(jsonPath("$.clients[*].quantity", contains(2, 1)))
                .andExpect(jsonPath("$.clients[*].amount", contains(900.0, 450.0)))
                .andExpect(jsonPath("$.lines[*].documentType", contains("Credit Note", "Invoice", "Receipt")))
                .andExpect(jsonPath("$.lines[0].documentId").value(credited))
                .andExpect(jsonPath("$.lines[1].documentId").value(invoice))
                .andExpect(jsonPath("$.lines[*].documentNumber", contains("CN-0001", "INV-0001", "REC-0001")))
                .andExpect(jsonPath(
                        "$.lines[*].issueDate",
                        contains(today.toString(), today.minusDays(1).toString(), today.minusDays(10).toString())))
                .andExpect(jsonPath("$.lines[*].client", contains("Acme", "Acme", "Birch Studio")))
                .andExpect(jsonPath("$.lines[*].quantity", contains(-1, 3, 1)))
                .andExpect(jsonPath("$.lines[*].amount", contains(-450.0, 1350.0, 450.0)))
                .andExpect(jsonPath("$.linesTotal").value(3));
        detail("name", "photography", "period", "30d")
                .andExpect(jsonPath("$.item").value("Photography"))
                .andExpect(content().string(containsString(
                        "\"clients\":[{\"clientId\":null,\"client\":\"Cedar Works\",\"quantity\":1,"
                                + "\"amount\":900.00}]")));
        // Lines without a name are asked for as the list names them.
        detail("name", "other lines", "period", "30d")
                .andExpect(jsonPath("$.item").value("Other lines"))
                .andExpect(jsonPath("$.quantity").value(3))
                .andExpect(jsonPath("$.amount").value(90.0))
                .andExpect(jsonPath("$.share").value(0.0127))
                .andExpect(jsonPath("$.clients[*].client", contains("Acme")))
                .andExpect(jsonPath("$.lines[*].quantity", contains(1, 2)))
                .andExpect(jsonPath("$.lines[*].amount", contains(60.0, 30.0)))
                .andExpect(jsonPath("$.linesTotal").value(2));
        sales("period", "30d", "currency", "EUR")
                .andExpect(jsonPath("$.items[*].item", contains("Logo")))
                .andExpect(jsonPath("$.items[*].share", contains(1.0)))
                .andExpect(jsonPath("$.total").value(6666.0));
    }

    @ParameterizedTest(name = "in {0}")
    @ArgumentsSource(AwayFromUtc.class)
    @DisplayName("over each of the overview's periods the items add up to its itemsTotal, the first five are its top"
            + " items, and each item's page adds up to its row")
    void addsUpToTheOverview(ZoneId zone) throws Exception {
        inZone(zone);
        String birch = client("Birch Studio");
        LocalDate year = today.withDayOfMonth(1).minusMonths(11);
        LocalDate january = today.withDayOfYear(1);
        // Documents on the first day of each period and the day before it,
        // with fractions of a cent to round, discounts and tax.
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
            LocalDate day = days.get(index);
            String invoice = issue(
                    "Invoice",
                    index % 2 == 0 ? acme : birch,
                    day,
                    line("Design", "33.3333", "1.5", "12.5", "18"),
                    line("Hours", "19.99", "2.333", "7.25", "5"),
                    line("Support", "0.005" + index, "1"));
            issue(
                    "Receipt",
                    birch,
                    day,
                    line("Kit", "1234.8912", "3.3333", "33.3333", "0"),
                    line("Hours", "45", "0.5"));
            if (index % 2 == 0) {
                credit(invoice, day, line("Hours", "19.99", "1.111", "7.25", "5"));
            }
        }
        String unnamed = issue("Invoice", acme, today, line("Kept", "60.125", "1"), line("Gone", "15.0049", "3"));
        rename(unnamed, 0, null);
        rename(unnamed, 1, " ");
        issue("Invoice", acme, today, line("Seal", "7", "1"));

        Map<String, String> periods =
                Map.of("30d", "30d", "90d", "90d", "12m", "12m", "ytd", "ytd", "this-year", "ytd");
        for (Map.Entry<String, String> period : periods.entrySet()) {
            String key = period.getKey();
            String overview = body(send(
                    get("/api/v1/report/overview").param("currency", "USD").param("period", period.getValue()),
                    ""));
            String sold = body(sales("period", key, "size", "100").andExpect(status().isOk()));
            assertThat(amount(JsonPath.read(sold, "$.total")))
                    .as(key)
                    .isPositive()
                    .isEqualByComparingTo(amount(JsonPath.read(overview, "$.itemsTotal")))
                    .isEqualByComparingTo(sum(sold, "$.items[*].amount"))
                    .isEqualByComparingTo(sum(overview, "$.trend[*].income"));
            List<Map<String, Object>> top = JsonPath.read(overview, "$.topItems");
            List<Map<String, Object>> items = JsonPath.read(sold, "$.items");
            assertThat(top).as(key).hasSize(5);
            assertThat(items).as(key).hasSizeGreaterThan(5);
            for (int index = 0; index < top.size(); index++) {
                for (String field : List.of("item", "quantity", "amount", "documents")) {
                    assertThat(items.get(index).get(field))
                            .as(key + " " + field)
                            .isEqualTo(top.get(index).get(field));
                }
            }
            // Each item's page is its row, with who bought it and its lines
            // adding up to it.
            for (Map<String, Object> item : items) {
                String page = body(detail("name", (String) item.get("item"), "period", key).andExpect(status().isOk()));
                for (String field : List.of("item", "quantity", "amount", "share", "documents")) {
                    assertThat((Object) JsonPath.read(page, "$." + field))
                            .as(key + " " + item.get("item") + " " + field)
                            .isEqualTo(item.get(field));
                }
                assertThat(sum(page, "$.clients[*].amount"))
                        .as(key + " " + item.get("item"))
                        .isEqualByComparingTo(amount(item.get("amount")));
                assertThat(sum(page, "$.lines[*].amount"))
                        .as(key + " " + item.get("item"))
                        .isEqualByComparingTo(amount(item.get("amount")));
                assertThat(sum(page, "$.lines[*].quantity"))
                        .as(key + " " + item.get("item"))
                        .isEqualByComparingTo(amount(item.get("quantity")));
                assertThat(JsonPath.<List<Object>>read(page, "$.lines"))
                        .as(key + " " + item.get("item"))
                        .hasSize(JsonPath.<Integer>read(page, "$.linesTotal"));
            }
        }
    }

    @Test
    @DisplayName("an item's page lists its 50 newest lines and counts them all, and an item the period sold none of"
            + " has nothing in it")
    void manyLines() throws Exception {
        List<String> numbers = new ArrayList<>();
        for (int index = 0; index < 55; index++) {
            String bolt = line("Bolt", String.valueOf(index + 1), "1");
            String invoice = issue("Invoice", acme, today.minusDays(index % 5), bolt);
            numbers.add(JsonPath.read(
                    body(send(get("/api/v1/document/{id}", invoice), "")), "$.document.documentNumber"));
        }

        String page = body(detail("name", "Bolt")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(55))
                .andExpect(jsonPath("$.documents").value(55))
                .andExpect(jsonPath("$.amount").value(1540.0))
                .andExpect(jsonPath("$.share").value(1.0))
                .andExpect(jsonPath("$.lines.length()").value(50))
                .andExpect(jsonPath("$.linesTotal").value(55))
                // Today's are first, the last made first.
                .andExpect(jsonPath("$.lines[0].documentNumber").value(numbers.get(50)))
                .andExpect(jsonPath("$.lines[1].documentNumber").value(numbers.get(45))));
        List<String> dates = JsonPath.read(page, "$.lines[*].issueDate");
        for (int index = 1; index < dates.size(); index++) {
            assertThat(dates.get(index)).isLessThanOrEqualTo(dates.get(index - 1));
        }
        // An item the period sold none of, and one never sold.
        detail("name", "Bolt", "period", "last-year")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.item").value("Bolt"))
                .andExpect(jsonPath("$.quantity").value(0))
                .andExpect(jsonPath("$.amount").value(0.0))
                .andExpect(jsonPath("$.share").value(0.0))
                .andExpect(jsonPath("$.documents").value(0))
                .andExpect(jsonPath("$.clients").isEmpty())
                .andExpect(jsonPath("$.lines").isEmpty())
                .andExpect(jsonPath("$.linesTotal").value(0));
        detail("name", " Anvil ")
                .andExpect(jsonPath("$.item").value("Anvil"))
                .andExpect(jsonPath("$.amount").value(0.0))
                .andExpect(content().string(containsString("\"share\":0.0000,")));
        detail("name", " ")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Choose an item"));
        detail().andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Choose an item"));
    }

    @Test
    @DisplayName("items come a page at a time, the total counting every one of them")
    void paging() throws Exception {
        String[] lines = new String[12];
        for (int index = 0; index < lines.length; index++) {
            lines[index] = line("Item " + (char) ('A' + index), String.valueOf(100 * (index + 1)), "1");
        }
        issue("Invoice", acme, today, lines);

        sales("size", "5", "page", "3")
                .andExpect(jsonPath("$.items[*].item", contains("Item B", "Item A")))
                .andExpect(jsonPath("$.totalItems").value(12))
                .andExpect(jsonPath("$.page").value(3))
                .andExpect(jsonPath("$.size").value(5))
                .andExpect(jsonPath("$.numberOfPages").value(3))
                .andExpect(jsonPath("$.total").value(7800.0));
        sales("size", "5", "page", "4").andExpect(jsonPath("$.items").isEmpty());
        sales("size", "500").andExpect(jsonPath("$.size").value(100)).andExpect(jsonPath("$.items.length()").value(12));
    }

    @Test
    @DisplayName("the currency is chosen as the overview chooses it, and a period that sold nothing lists nothing")
    void currencyAndEmpty() throws Exception {
        sales("period", "30d")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.currencies").isEmpty())
                .andExpect(jsonPath("$.totalItems").value(0))
                .andExpect(jsonPath("$.numberOfPages").value(0))
                .andExpect(content().string(containsString("\"total\":0.00,\"totalItems\":0,")))
                .andExpect(jsonPath("$.items").isEmpty());

        create("Invoice", acme, "EUR", today.minusDays(100), false, line("Design", "400", "1"));
        sales("period", "30d", "currency", "USD")
                .andExpect(jsonPath("$.currency").value("EUR"))
                .andExpect(jsonPath("$.items").isEmpty());
        sales()
                .andExpect(jsonPath("$.period.key").value(""))
                .andExpect(jsonPath("$.period.from").value(nullValue()))
                .andExpect(jsonPath("$.items[*].item", contains("Design")));
        detail("name", "design")
                .andExpect(jsonPath("$.currency").value("EUR"))
                .andExpect(jsonPath("$.amount").value(400.0));
    }

    @Test
    @DisplayName("a viewer may see both, they count only their business, the catalog is unchanged, and a period"
            + " from no list is refused")
    void route() throws Exception {
        issue("Invoice", acme, today, line("Design", "500", "1"));

        send(get("/api/v1/item"), "").andExpect(status().isOk());
        User viewer = createMember(businessOf(owner), BusinessRole.VIEWER);
        mockMvc.perform(get("/api/v1/item/sales").header(HttpHeaders.AUTHORIZATION, bearer(viewer, businessOf(owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].item", contains("Design")));
        mockMvc.perform(get("/api/v1/item/sales/detail")
                        .param("name", "Design")
                        .header(HttpHeaders.AUTHORIZATION, bearer(viewer, businessOf(owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(500.0));
        String stranger = bearer(createUser());
        mockMvc.perform(get("/api/v1/item/sales").header(HttpHeaders.AUTHORIZATION, stranger))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty());
        mockMvc.perform(get("/api/v1/item/sales/detail")
                        .param("name", "Design")
                        .header(HttpHeaders.AUTHORIZATION, stranger))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(0.0))
                .andExpect(jsonPath("$.lines").isEmpty());
        sales("period", "6m")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Choose a period from the list"));
        detail("name", "Design", "period", "6m")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Choose a period from the list"));
    }
}
