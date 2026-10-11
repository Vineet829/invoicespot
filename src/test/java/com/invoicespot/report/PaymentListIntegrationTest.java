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
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
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

// The payments list: every payment on an issued invoice, every issued receipt
// and every refund, as the overview counts what was received.
class PaymentListIntegrationTest extends IntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private LocalDate today;
    private User owner;
    private String acme;
    private String birch;

    @BeforeEach
    void owner() throws Exception {
        owner = createUser();
        acme = client("Acme");
        birch = client("Birch Studio");
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
        return client(name, UUID.randomUUID() + "@clients.test");
    }

    private String client(String name, String email) throws Exception {
        return JsonPath.read(
                body(send(post("/api/v1/customer/create"), """
                        {"name": "%s", "email": "%s", "phoneNumber": "+1"}
                        """.formatted(name, email))),
                "$.createdCustomer._id");
    }

    // A document for the client dated the given day, for one amount; a draft
    // is only planned for that day.
    private String create(
            String client, String type, String currency, String amount, LocalDate issued, boolean draft)
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
                                        type.equals("Receipt") ? "null" : "\"" + today.plusDays(30) + "\"",
                                        amount))
                        .andExpect(status().isOk())),
                "$.newDocument._id");
    }

    private String issue(String client, String type, String currency, String amount, LocalDate issued)
            throws Exception {
        return create(client, type, currency, amount, issued, false);
    }

    // Records a payment and returns its id.
    private String pay(String document, String amount, String withheld, String method, String note, LocalDate date)
            throws Exception {
        send(post("/api/v1/document/{id}/payment", document), """
                {"amountPaid": %s, "tdsAmount": %s, "paymentMethod": "%s", "additionalInfo": %s, "datePaid": "%s"}
                """.formatted(amount, withheld, method, note == null ? "null" : "\"" + note + "\"", date))
                .andExpect(status().isCreated());
        return latest("payments", document);
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

    // Records a refund and returns its id.
    private String refund(String document, String amount, String method, String reference, LocalDate date)
            throws Exception {
        send(post("/api/v1/document/{id}/refund", document), """
                {"amount": %s, "paymentMethod": "%s", "reference": %s, "dateRefunded": "%s"}
                """.formatted(amount, method, reference == null ? "null" : "\"" + reference + "\"", date))
                .andExpect(status().isCreated());
        return latest("refunds", document);
    }

    // The id of the payment or refund last recorded on the document.
    private String latest(String table, String document) {
        return jdbcTemplate.queryForObject(
                "select id from " + table + " where document_pkid = (select pkid from documents where id = ?)"
                        + " order by pkid desc limit 1",
                UUID.class,
                UUID.fromString(document)).toString();
    }

    private ResultActions payments(String... params) throws Exception {
        MockHttpServletRequestBuilder request = get("/api/v1/payments");
        for (int index = 0; index < params.length; index += 2) {
            request.param(params[index], params[index + 1]);
        }
        return send(request, "");
    }

    private static BigDecimal amount(String body, String path) {
        return new BigDecimal(JsonPath.read(body, path).toString());
    }

    // The ids of the rows, in the order listed.
    private List<String> ids(String... params) throws Exception {
        return JsonPath.read(body(payments(params).andExpect(status().isOk())), "$.payments[*].id");
    }

    private record Books(
            String invoice,
            String firstPart,
            String secondPart,
            String birchInvoice,
            String birchPayment,
            String refund,
            String receipt,
            String oldReceipt,
            String euros) {

        List<String> all() {
            return List.of(refund, receipt, euros, secondPart, firstPart, birchPayment, oldReceipt);
        }
    }

    // Payments on two invoices, one partly with tax withheld and one paid in
    // full, credited and paid back; two receipts; a payment in euros. A void
    // receipt, a draft one and a quotation hold no money.
    private Books books() throws Exception {
        String invoice = issue(acme, "Invoice", "USD", "1000", today.minusDays(40));
        String firstPart = pay(invoice, "400", "0", "Bank Transfer", "First part", today.minusDays(29));
        String secondPart = pay(invoice, "100", "50", "Cash", null, today.minusDays(5));
        String birchInvoice = issue(birch, "Invoice", "USD", "600", today.minusDays(50));
        String birchPayment = pay(birchInvoice, "600", "0", "Credit Card", null, today.minusDays(30));
        credit(birchInvoice, "150", today.minusDays(1));
        String refund = refund(birchInvoice, "150", "Cheque", "Card reversal", today);
        String receipt = issue(acme, "Receipt", "USD", "90", today);
        String oldReceipt = issue(birch, "Receipt", "USD", "40", today.minusDays(100));
        String euros = pay(issue(acme, "Invoice", "EUR", "900", today.minusDays(3)), "100", "0", "UPI", null, today);
        send(post("/api/v1/document/{id}/void", issue(acme, "Receipt", "USD", "70", today)),
                        "{\"reason\": \"Issued twice\"}")
                .andExpect(status().isOk());
        create(acme, "Receipt", "USD", "80", today, true);
        issue(acme, "Quotation", "USD", "5000", today);
        return new Books(
                invoice, firstPart, secondPart, birchInvoice, birchPayment, refund, receipt, oldReceipt, euros);
    }

    @Test
    @DisplayName("each payment, receipt and refund is a row, newest first; a refund is money going back")
    void rows() throws Exception {
        Books books = books();

        payments()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.total").value(7))
                .andExpect(jsonPath("$.numberOfPages").value(1))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.from").value(nullValue()))
                .andExpect(jsonPath("$.to").value(nullValue()))
                .andExpect(jsonPath("$.payments[*].id", contains(books.all().toArray())))
                .andExpect(jsonPath("$.payments[*].kind", contains(
                        "refund", "receipt", "payment", "payment", "payment", "payment", "receipt")))
                .andExpect(jsonPath("$.payments[*].amount", contains(-150.0, 90.0, 100.0, 100.0, 400.0, 600.0, 40.0)))
                .andExpect(jsonPath("$.payments[0].date").value(today.toString()))
                .andExpect(jsonPath("$.payments[0].client").value("Birch Studio"))
                .andExpect(jsonPath("$.payments[0].document.id").value(books.birchInvoice()))
                .andExpect(jsonPath("$.payments[0].document.number").value("INV-0002"))
                .andExpect(jsonPath("$.payments[0].document.type").value("Invoice"))
                .andExpect(jsonPath("$.payments[0].method").value("Cheque"))
                .andExpect(jsonPath("$.payments[0].note").value("Card reversal"))
                .andExpect(jsonPath("$.payments[0].currency").value("USD"))
                // A receipt is named by its document, and has no method.
                .andExpect(jsonPath("$.payments[1].id").value(books.receipt()))
                .andExpect(jsonPath("$.payments[1].document.id").value(books.receipt()))
                .andExpect(jsonPath("$.payments[1].document.number").value("REC-0001"))
                .andExpect(jsonPath("$.payments[1].document.type").value("Receipt"))
                .andExpect(jsonPath("$.payments[1].method").value(nullValue()))
                .andExpect(jsonPath("$.payments[1].note").value(nullValue()))
                .andExpect(jsonPath("$.payments[2].currency").value("EUR"))
                .andExpect(jsonPath("$.payments[2].method").value("UPI"))
                // Tax withheld isn't money received.
                .andExpect(jsonPath("$.payments[3].amount").value(100.0))
                .andExpect(jsonPath("$.payments[3].method").value("Cash"))
                .andExpect(jsonPath("$.payments[4].date").value(today.minusDays(29).toString()))
                .andExpect(jsonPath("$.payments[4].client").value("Acme"))
                .andExpect(jsonPath("$.payments[4].document.id").value(books.invoice()))
                .andExpect(jsonPath("$.payments[4].method").value("Bank Transfer"))
                .andExpect(jsonPath("$.payments[4].note").value("First part"))
                .andExpect(jsonPath("$.totals.length()").value(2))
                .andExpect(jsonPath("$.totals[0].currency").value("EUR"))
                .andExpect(jsonPath("$.totals[0].payments").value(1))
                .andExpect(jsonPath("$.totals[0].receipts").value(0))
                .andExpect(jsonPath("$.totals[0].received").value(100.0))
                .andExpect(jsonPath("$.totals[1].currency").value("USD"))
                .andExpect(jsonPath("$.totals[1].payments").value(6))
                .andExpect(jsonPath("$.totals[1].receipts").value(2))
                .andExpect(jsonPath("$.totals[1].received").value(1080.0))
                .andExpect(jsonPath("$.methods", contains("Bank Transfer", "Cash", "Cheque", "Credit Card", "UPI")))
                .andExpect(content().string(containsString("\"amount\":-150.00,\"currency\":\"USD\"")))
                .andExpect(content().string(containsString(
                        "{\"currency\":\"USD\",\"payments\":6,\"receipts\":2,\"received\":1080.00}")));
    }

    @Test
    @DisplayName("the payments file holds the list's rows, newest first, receipts among them, and adds up to what"
            + " the list received")
    void file() throws Exception {
        Books books = books();
        // A payment saved on a receipt, as some older records have it. A
        // receipt counts at its total, so the list leaves that payment out.
        String columns = String.join(", ", jdbcTemplate.queryForList("""
                select column_name from information_schema.columns
                where table_schema = current_schema() and table_name = 'payments'
                  and column_name not in ('pkid', 'id', 'document_pkid')
                order by ordinal_position
                """, String.class));
        jdbcTemplate.update(
                """
                insert into payments (id, document_pkid, %1$s)
                select gen_random_uuid(), (select pkid from documents where id = ?), %1$s
                from payments where id = ?
                """.formatted(columns),
                UUID.fromString(books.receipt()),
                UUID.fromString(books.firstPart()));

        String list = body(payments("size", "100").andExpect(jsonPath("$.total").value(7)));
        List<String> file = body(send(get("/api/v1/document/payments/csv"), "").andExpect(status().isOk()))
                .lines()
                .toList();
        assertThat(file).containsExactly(
                "\uFEFFDate,Document,Type,Client,Currency,Amount,Method,Paid by,Notes,Tax withheld,Kind",
                today + ",INV-0002,Invoice,Birch Studio,USD,-150.00,Cheque,,Card reversal,,Refund",
                today + ",REC-0001,Receipt,Acme,USD,90.00,,,,,Receipt",
                today + ",INV-0003,Invoice,Acme,EUR,100.00,UPI,Acme,,0.00,Payment",
                today.minusDays(5) + ",INV-0001,Invoice,Acme,USD,100.00,Cash,Acme,,50.00,Payment",
                today.minusDays(29) + ",INV-0001,Invoice,Acme,USD,400.00,Bank Transfer,Acme,First part,0.00,Payment",
                today.minusDays(30)
                        + ",INV-0002,Invoice,Birch Studio,USD,600.00,Credit Card,Birch Studio,,0.00,Payment",
                today.minusDays(100) + ",REC-0002,Receipt,Birch Studio,USD,40.00,,,,,Receipt");
        // Each currency's amounts add up to what the list received in it.
        for (String currency : List.of("USD", "EUR")) {
            BigDecimal total = file.stream()
                    .skip(1)
                    .map(row -> row.split(","))
                    .filter(cells -> cells[4].equals(currency))
                    .map(cells -> new BigDecimal(cells[5]))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            List<Object> received = JsonPath.read(list, "$.totals[?(@.currency == '%s')].received".formatted(currency));
            assertThat(total).as(currency).isEqualByComparingTo(received.get(0).toString());
        }
    }

    @Test
    @DisplayName("search, method, period, dates and currency narrow the rows and their totals")
    void filters() throws Exception {
        Books books = books();

        assertThat(ids("q", "birch")).containsExactly(books.refund(), books.birchPayment(), books.oldReceipt());
        assertThat(ids("q", "INV-0001")).containsExactly(books.secondPart(), books.firstPart());
        assertThat(ids("q", "rec-")).containsExactly(books.receipt(), books.oldReceipt());
        assertThat(ids("q", "%")).isEmpty();
        assertThat(ids("method", "Cash")).containsExactly(books.secondPart());
        assertThat(ids("method", "CASH")).containsExactly(books.secondPart());
        assertThat(ids("method", "bank transfer")).containsExactly(books.firstPart());
        assertThat(ids("method", "Cheque")).containsExactly(books.refund());
        assertThat(ids("method", "receipt")).containsExactly(books.receipt(), books.oldReceipt());
        assertThat(ids("method", "Receipt")).containsExactly(books.receipt(), books.oldReceipt());
        assertThat(ids("period", "30d"))
                .containsExactly(books.refund(), books.receipt(), books.euros(), books.secondPart(), books.firstPart());
        assertThat(ids("period", "90d")).hasSize(6).doesNotContain(books.oldReceipt());
        assertThat(ids("period", "12m")).hasSize(7);
        assertThat(ids("from", today.minusDays(30).toString(), "to", today.minusDays(29).toString()))
                .containsExactly(books.firstPart(), books.birchPayment());
        assertThat(ids("currency", "EUR")).containsExactly(books.euros());
        assertThat(ids("period", "30d", "currency", "USD", "method", "receipt")).containsExactly(books.receipt());

        payments("period", "30d")
                .andExpect(jsonPath("$.from").value(today.minusDays(29).toString()))
                .andExpect(jsonPath("$.to").value(today.toString()));
        payments("from", today.minusDays(30).toString())
                .andExpect(jsonPath("$.from").value(today.minusDays(30).toString()))
                .andExpect(jsonPath("$.to").value(nullValue()));
        payments("q", "birch")
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.totals.length()").value(1))
                .andExpect(jsonPath("$.totals[0].received").value(490.0));
        payments("period", "30d")
                .andExpect(jsonPath("$.totals[0].currency").value("EUR"))
                .andExpect(jsonPath("$.totals[0].received").value(100.0))
                .andExpect(jsonPath("$.totals[1].currency").value("USD"))
                .andExpect(jsonPath("$.totals[1].payments").value(4))
                .andExpect(jsonPath("$.totals[1].received").value(440.0))
                // The method filter still offers every method.
                .andExpect(jsonPath("$.methods.length()").value(5));
        payments("currency", "GBP")
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.numberOfPages").value(0))
                .andExpect(jsonPath("$.payments").isEmpty())
                .andExpect(jsonPath("$.totals").isEmpty());
        // A client's email matches too.
        String cobalt = client("Cobalt", "ledger-desk-%s@clients.test".formatted(UUID.randomUUID()));
        String byEmail = pay(issue(cobalt, "Invoice", "USD", "75", today), "75", "0", "Cash", null, today);
        assertThat(ids("q", "ledger-desk")).containsExactly(byEmail);
    }

    @Test
    @DisplayName("rows sort by date, amount or client either way, ties in a fixed order, and come a page at a time")
    void sortAndPages() throws Exception {
        Books books = books();

        assertThat(ids("sort", "date", "dir", "asc")).containsExactly(
                books.oldReceipt(), books.birchPayment(), books.firstPart(), books.secondPart(), books.euros(),
                books.receipt(), books.refund());
        assertThat(ids("sort", "amount", "dir", "asc")).containsExactly(
                books.refund(), books.oldReceipt(), books.receipt(), books.secondPart(), books.euros(),
                books.firstPart(), books.birchPayment());
        assertThat(ids("sort", "amount")).containsExactly(
                books.birchPayment(), books.firstPart(), books.euros(), books.secondPart(), books.receipt(),
                books.oldReceipt(), books.refund());
        assertThat(ids("sort", "client", "dir", "asc")).containsExactly(
                books.firstPart(), books.secondPart(), books.euros(), books.receipt(), books.birchPayment(),
                books.oldReceipt(), books.refund());

        String all = body(payments());
        payments("size", "2", "page", "2")
                .andExpect(jsonPath("$.total").value(7))
                .andExpect(jsonPath("$.numberOfPages").value(4))
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.payments[*].id", contains(books.euros(), books.secondPart())))
                .andExpect(jsonPath("$.totals[1].received").value(amount(all, "$.totals[1].received").doubleValue()));
        payments("size", "500").andExpect(jsonPath("$.size").value(100));
        payments("page", "9").andExpect(jsonPath("$.payments").isEmpty()).andExpect(jsonPath("$.total").value(7));
    }

    @Test
    @DisplayName("money on documents made without a currency is totalled in the business's default currency")
    void noCurrency() throws Exception {
        pay(issue(acme, "Invoice", null, "50", today), "50", "0", "Cash", null, today);
        issue(acme, "Receipt", null, "20", today);
        pay(issue(acme, "Invoice", "USD", "30", today), "30", "0", "Cash", null, today);

        payments()
                .andExpect(jsonPath("$.totals.length()").value(1))
                .andExpect(content().string(containsString(
                        "{\"currency\":\"USD\",\"payments\":3,\"receipts\":1,\"received\":100.00}")))
                .andExpect(jsonPath("$.payments[?(@.document.number == 'REC-0001')].currency", contains("USD")));
        payments("currency", "USD").andExpect(jsonPath("$.total").value(3));
    }

    @Test
    @DisplayName("filters and sorts the list can't read are refused, and so is a period with dates")
    void badInput() throws Exception {
        payments("period", "30d", "from", today.minusDays(3).toString())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Choose a period or dates, not both"));
        for (String period : List.of("ytd", "7d", "6m")) {
            payments("period", period)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Choose 30 days, 90 days or 12 months"));
        }
        payments("method", "Barter")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Unknown payment method filter"));
        payments("sort", "method")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Payments can be sorted by date, amount or client"));
        payments("from", "yesterday")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid value for from"));
    }

    @Test
    @DisplayName("a viewer may read the list, and another business's money never appears in it")
    void isolation() throws Exception {
        books();
        User first = owner;
        owner = createUser();
        String theirs = client("Someone");
        pay(issue(theirs, "Invoice", "USD", "999", today), "999", "0", "PayPal", null, today);

        payments()
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.payments[0].amount").value(999.0))
                .andExpect(jsonPath("$.totals[0].received").value(999.0))
                .andExpect(jsonPath("$.methods", contains("PayPal")));
        User viewer = createMember(businessOf(first), BusinessRole.VIEWER);
        mockMvc.perform(get("/api/v1/payments").header(HttpHeaders.AUTHORIZATION, bearer(viewer, businessOf(first))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(7))
                .andExpect(jsonPath("$.totals[1].received").value(1080.0))
                .andExpect(jsonPath("$.methods", contains("Bank Transfer", "Cash", "Cheque", "Credit Card", "UPI")));
    }

    @ParameterizedTest(name = "in {0}")
    @ArgumentsSource(AwayFromUtc.class)
    @DisplayName("over each of the overview's periods, the rows add up to what the overview received")
    void addUpToTheOverview(ZoneId zone) throws Exception {
        inZone(zone);
        LocalDate year = today.minusMonths(12).plusDays(1);
        // Paid on the first day of each period and the day before it.
        String invoice = issue(acme, "Invoice", "USD", "10000", year.minusDays(5));
        pay(invoice, "100", "0", "Cash", null, today);
        pay(invoice, "200", "10", "Cash", null, today.minusDays(29));
        pay(invoice, "300", "0", "Cash", null, today.minusDays(30));
        pay(invoice, "400", "0", "Cash", null, today.minusDays(89));
        pay(invoice, "500", "0", "Cash", null, today.minusDays(90));
        pay(invoice, "600", "0", "Cash", null, year);
        pay(invoice, "700", "0", "Cash", null, year.minusDays(1));
        issue(acme, "Receipt", "USD", "50", today);
        issue(acme, "Receipt", "USD", "60", today.minusDays(30));
        // Paid in full a year ago, then credited and paid back in two parts.
        String credited = issue(birch, "Invoice", "USD", "1000", year.minusDays(5));
        pay(credited, "1000", "0", "Cash", null, year.minusDays(1));
        credit(credited, "300", year.minusDays(1));
        refund(credited, "100", "Cash", null, today.minusDays(29));
        refund(credited, "200", "Cash", null, today.minusDays(90));
        // Money in euros, never counted with dollars.
        pay(issue(acme, "Invoice", "EUR", "900", today), "900", "0", "Cash", null, today);

        List<String> periods = List.of("30d", "90d", "12m");
        List<String> received = List.of("250", "1010", "1910");
        List<Integer> rows = List.of(4, 7, 10);
        for (int index = 0; index < periods.size(); index++) {
            String period = periods.get(index);
            String listed = body(payments("period", period, "currency", "USD")
                    .andExpect(jsonPath("$.total").value(rows.get(index))));
            String overview = body(send(
                    get("/api/v1/report/overview").param("currency", "USD").param("period", period), ""));
            assertThat(amount(listed, "$.totals[0].received"))
                    .as(period)
                    .isEqualByComparingTo(amount(overview, "$.totals.received.current"))
                    .isEqualByComparingTo(received.get(index));
        }
        // The money bar's last 30 days are this list's.
        send(get("/api/v1/document/summary").param("currency", "USD"), "")
                .andExpect(jsonPath("$.receivedLast30Days.total").value(250.0))
                .andExpect(jsonPath("$.receivedLast30Days.payments").value(4));
    }
}
