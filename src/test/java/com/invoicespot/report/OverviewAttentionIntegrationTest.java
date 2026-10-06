package com.invoicespot.report;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.invoicespot.IntegrationTest;
import com.invoicespot.user.User;
import com.jayway.jsonpath.JsonPath;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
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

// What needs attention on the overview, most urgent first: payments clients
// reported, overdue invoices, invoices due within the week, and drafts.
// Quotations about to expire and invoices a schedule is about to make are
// what is coming up instead. Clients answer and report from a network of
// their own each time, so no limit is shared.
class OverviewAttentionIntegrationTest extends IntegrationTest {

    private static final AtomicInteger NETWORKS = new AtomicInteger();

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User owner;
    private LocalDate today;
    private String acme;
    private String birch;

    private record Made(String id, String number) {}

    // A business with two clients.
    @BeforeEach
    void business() throws Exception {
        owner = createUser();
        acme = client("Acme");
        birch = client("Birch Studio");
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

    // A document dated well before its due date, or valid-until date for a
    // quotation; a draft is only planned for that day.
    private Made create(String type, String client, String currency, String amount, LocalDate due, boolean draft)
            throws Exception {
        String made = body(send(post("/api/v1/document/create"), """
                        {"customerId": "%s", "documentType": "%s", "currency": "%s", "draft": %s,
                         "issueDate": "%s", "dueDate": "%s",
                         "billingItems": [{"itemName": "Work", "unitPrice": %s, "quantity": 1}]}
                        """.formatted(client, type, currency, draft, today.minusDays(40), due, amount))
                .andExpect(status().isOk()));
        return new Made(JsonPath.read(made, "$.newDocument._id"), JsonPath.read(made, "$.newDocument.documentNumber"));
    }

    private Made invoice(String client, String amount, LocalDate due) throws Exception {
        return create("Invoice", client, "USD", amount, due, false);
    }

    private Made quotation(String amount, LocalDate validUntil) throws Exception {
        return create("Quotation", acme, "USD", amount, validUntil, false);
    }

    private void pay(Made document, String amount) throws Exception {
        send(post("/api/v1/document/{id}/payment", document.id()), "{\"amountPaid\": %s}".formatted(amount))
                .andExpect(status().isCreated());
    }

    private void voided(Made document) throws Exception {
        send(post("/api/v1/document/{id}/void", document.id()), "{\"reason\": \"Issued twice\"}")
                .andExpect(status().isOk());
    }

    // The client opens the document's link and does something there.
    private ResultActions fromLink(Made document, String action, String json) throws Exception {
        String url = JsonPath.read(body(send(post("/api/v1/document/{id}/share", document.id()), "")), "$.url");
        String network = "10.70." + NETWORKS.incrementAndGet() + ".1";
        return mockMvc.perform(post("/api/v1/public/share/{token}/" + action, url.substring(url.lastIndexOf('/') + 1))
                .with(request -> {
                    request.setRemoteAddr(network);
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private void report(Made invoice, String amount, LocalDate paidOn) throws Exception {
        fromLink(invoice, "payment-claims", """
                {"amount": %s, "paidOn": "%s", "method": "UPI"}
                """.formatted(amount, paidOn))
                .andExpect(status().isOk());
    }

    private void dismissNewestReport(Made invoice) throws Exception {
        String claim = JsonPath.read(
                body(send(get("/api/v1/document/{id}/payment-claims", invoice.id()), "")), "$.claims[0].id");
        send(post("/api/v1/document/{id}/payment-claims/{claim}/dismiss", invoice.id(), claim), "{}")
                .andExpect(status().isOk());
    }

    // A schedule making the invoice again each month from the given day.
    private String repeat(Made invoice, LocalDate start) throws Exception {
        return JsonPath.read(
                body(send(post("/api/v1/recurring"), """
                                {"documentId": "%s", "frequency": "MONTHLY", "startDate": "%s"}
                                """.formatted(invoice.id(), start))
                        .andExpect(status().isCreated())),
                "$.schedule._id");
    }

    private ResultActions overview() throws Exception {
        return send(get("/api/v1/report/overview"), "");
    }

    @ParameterizedTest(name = "in {0}")
    @ArgumentsSource(AwayFromUtc.class)
    @DisplayName("each kind is listed in turn, with what the client reported or owes, and the drafts to issue;"
            + " quotations and schedules are left to what is coming up")
    void kinds(ZoneId zone) throws Exception {
        inZone(zone);
        Made reported = invoice(acme, "500", today.plusDays(30));
        report(reported, "120", today.minusDays(1));
        Made overdue = invoice(birch, "300", today.minusDays(4));
        pay(overdue, "100");
        Made dueSoon = invoice(acme, "150", today.plusDays(3));
        Made draft = create("Invoice", birch, "USD", "250", today.minusDays(10), true);

        // Coming up instead: a quotation whose last day is this week, and a
        // schedule making an invoice this week.
        Made quotation = quotation("900", today.plusDays(6));
        Made repeating = invoice(acme, "400", today.plusDays(40));
        repeat(repeating, today.plusDays(5));
        // None of these: a report dismissed, and one on an invoice in euros;
        // an invoice paid, a void one, one in euros, and one due in a week;
        // quotations valid for longer, accepted, declined, expired, void or
        // in euros; schedules paused, running in a week, finished or making
        // an invoice in euros; a draft in euros.
        report(reported, "50", today);
        dismissNewestReport(reported);
        Made euros = create("Invoice", acme, "EUR", "700", today.minusDays(5), false);
        report(euros, "70", today);
        pay(invoice(acme, "60", today.minusDays(10)), "60");
        voided(invoice(acme, "70", today.minusDays(10)));
        invoice(acme, "80", today.plusDays(7));
        quotation("910", today.plusDays(7));
        fromLink(quotation("920", today.plusDays(2)), "accept", "").andExpect(status().isOk());
        fromLink(quotation("930", today.plusDays(1)), "decline", "").andExpect(status().isOk());
        quotation("940", today.minusDays(1));
        voided(quotation("950", today.plusDays(3)));
        create("Quotation", acme, "EUR", "960", today.plusDays(3), false);
        String paused = repeat(invoice(acme, "410", today.plusDays(40)), today.plusDays(2));
        send(post("/api/v1/recurring/{id}/pause", paused), "").andExpect(status().isOk());
        repeat(invoice(acme, "420", today.plusDays(40)), today.plusDays(7));
        String finished = repeat(invoice(acme, "430", today.plusDays(40)), today.plusDays(1));
        jdbcTemplate.update("update recurring_schedules set next_run = null where id = ?", UUID.fromString(finished));
        repeat(create("Invoice", acme, "EUR", "440", today.plusDays(40), false), today.plusDays(3));
        create("Invoice", acme, "EUR", "260", today.plusDays(5), true);

        overview()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attention.items.length()").value(4))
                .andExpect(jsonPath("$.attention.items[0].kind").value("paymentReport"))
                .andExpect(jsonPath("$.attention.items[0].documentId").value(reported.id()))
                .andExpect(jsonPath("$.attention.items[0].documentNumber").value(reported.number()))
                .andExpect(jsonPath("$.attention.items[0].client").value("Acme"))
                .andExpect(jsonPath("$.attention.items[0].clientId").value(acme))
                .andExpect(jsonPath("$.attention.items[0].amount").value(120.0))
                .andExpect(jsonPath("$.attention.items[0].date").value(today.minusDays(1).toString()))
                .andExpect(jsonPath("$.attention.items[0].days").value(nullValue()))
                .andExpect(jsonPath("$.attention.items[1].kind").value("overdue"))
                .andExpect(jsonPath("$.attention.items[1].documentId").value(overdue.id()))
                .andExpect(jsonPath("$.attention.items[1].documentNumber").value(overdue.number()))
                .andExpect(jsonPath("$.attention.items[1].client").value("Birch Studio"))
                .andExpect(jsonPath("$.attention.items[1].clientId").value(birch))
                .andExpect(jsonPath("$.attention.items[1].amount").value(200.0))
                .andExpect(jsonPath("$.attention.items[1].date").value(today.minusDays(4).toString()))
                .andExpect(jsonPath("$.attention.items[1].days").value(4))
                .andExpect(jsonPath("$.attention.items[2].kind").value("dueSoon"))
                .andExpect(jsonPath("$.attention.items[2].documentId").value(dueSoon.id()))
                .andExpect(jsonPath("$.attention.items[2].clientId").value(acme))
                .andExpect(jsonPath("$.attention.items[2].amount").value(150.0))
                .andExpect(jsonPath("$.attention.items[2].date").value(today.plusDays(3).toString()))
                .andExpect(jsonPath("$.attention.items[2].days").value(3))
                .andExpect(jsonPath("$.attention.items[3].kind").value("draft"))
                .andExpect(jsonPath("$.attention.items[3].documentId").value(draft.id()))
                .andExpect(jsonPath("$.attention.items[3].documentNumber").value(nullValue()))
                .andExpect(jsonPath("$.attention.items[3].client").value("Birch Studio"))
                .andExpect(jsonPath("$.attention.items[3].clientId").value(birch))
                .andExpect(jsonPath("$.attention.items[3].amount").value(250.0))
                .andExpect(jsonPath("$.attention.items[3].date").value(today.toString()))
                .andExpect(jsonPath("$.attention.items[3].days").value(nullValue()))
                .andExpect(jsonPath("$.attention.counts.paymentReports").value(1))
                .andExpect(jsonPath("$.attention.counts.overdue").value(1))
                .andExpect(jsonPath("$.attention.counts.dueSoon").value(1))
                .andExpect(jsonPath("$.attention.counts.drafts").value(1))
                .andExpect(jsonPath("$.attention.counts.quotations").doesNotExist())
                .andExpect(jsonPath("$.attention.counts.recurring").doesNotExist())
                .andExpect(jsonPath("$.comingUp.quotations.awaiting.items[0].documentId").value(quotation.id()))
                .andExpect(jsonPath("$.comingUp.recurring.nextRuns[0].templateId").value(repeating.id()));
    }

    @ParameterizedTest(name = "in {0}")
    @ArgumentsSource(AwayFromUtc.class)
    @DisplayName("the newest report and the latest invoice come first, and six are listed while all are counted")
    void mostUrgentFirst(ZoneId zone) throws Exception {
        inZone(zone);
        Made first = invoice(acme, "500", today.plusDays(30));
        report(first, "100", today.minusDays(2));
        Made second = invoice(birch, "600", today.plusDays(30));
        report(second, "200", today.minusDays(3));
        Made tenDays = invoice(acme, "110", today.minusDays(10));
        Made threeDays = invoice(acme, "120", today.minusDays(3));
        Made thirtyDays = invoice(acme, "130", today.minusDays(30));
        invoice(acme, "140", today.plusDays(5));
        Made dueToday = invoice(acme, "150", today);
        // Seven drafts: more of one kind than are ever listed.
        for (int draft = 0; draft < 7; draft++) {
            create("Invoice", acme, "USD", "160", today.plusDays(5), true);
        }

        overview()
                .andExpect(jsonPath("$.attention.items.length()").value(6))
                .andExpect(jsonPath("$.attention.items[0].documentId").value(second.id()))
                .andExpect(jsonPath("$.attention.items[0].client").value("Birch Studio"))
                .andExpect(jsonPath("$.attention.items[0].amount").value(200.0))
                .andExpect(jsonPath("$.attention.items[0].date").value(today.minusDays(3).toString()))
                .andExpect(jsonPath("$.attention.items[1].documentId").value(first.id()))
                .andExpect(jsonPath("$.attention.items[2].documentId").value(thirtyDays.id()))
                .andExpect(jsonPath("$.attention.items[2].days").value(30))
                .andExpect(jsonPath("$.attention.items[3].documentId").value(tenDays.id()))
                .andExpect(jsonPath("$.attention.items[3].days").value(10))
                .andExpect(jsonPath("$.attention.items[4].documentId").value(threeDays.id()))
                .andExpect(jsonPath("$.attention.items[4].days").value(3))
                .andExpect(jsonPath("$.attention.items[5].kind").value("dueSoon"))
                .andExpect(jsonPath("$.attention.items[5].documentId").value(dueToday.id()))
                .andExpect(jsonPath("$.attention.items[5].days").value(0))
                .andExpect(jsonPath("$.attention.counts.paymentReports").value(2))
                .andExpect(jsonPath("$.attention.counts.overdue").value(3))
                .andExpect(jsonPath("$.attention.counts.dueSoon").value(2))
                .andExpect(jsonPath("$.attention.counts.drafts").value(7))
                .andExpect(jsonPath("$.owedNow.overdueInvoices").value(3));
    }

    @ParameterizedTest(name = "in {0}")
    @ArgumentsSource(AwayFromUtc.class)
    @DisplayName("quotations about to expire and schedules about to run are left to what is coming up, and the"
            + " newest draft comes first")
    void comingUpInstead(ZoneId zone) throws Exception {
        inZone(zone);
        Made fourDays = quotation("900", today.plusDays(4));
        Made oneDay = quotation("800", today.plusDays(1));
        Made sixDays = invoice(acme, "700", today.plusDays(40));
        repeat(sixDays, today.plusDays(6));
        Made runsToday = invoice(birch, "600", today.plusDays(40));
        repeat(runsToday, today);
        // A quotation that is still a draft isn't offered yet, though its last
        // day is this week.
        Made older = create("Quotation", acme, "USD", "500", today.plusDays(2), true);
        Made newer = create("Receipt", birch, "USD", "400", today, true);

        overview()
                .andExpect(jsonPath("$.attention.items[*].kind", contains("draft", "draft")))
                .andExpect(jsonPath("$.attention.items[0].documentId").value(newer.id()))
                .andExpect(jsonPath("$.attention.items[0].amount").value(400.0))
                .andExpect(jsonPath("$.attention.items[1].documentId").value(older.id()))
                .andExpect(jsonPath("$.attention.counts.paymentReports").value(0))
                .andExpect(jsonPath("$.attention.counts.drafts").value(2))
                .andExpect(jsonPath(
                        "$.comingUp.quotations.awaiting.items[*].documentId", contains(oneDay.id(), fourDays.id())))
                .andExpect(jsonPath("$.comingUp.recurring.nextRuns[0].templateId").value(runsToday.id()))
                .andExpect(jsonPath("$.comingUp.recurring.nextRuns[1].templateId").value(sixDays.id()));
    }

    @ParameterizedTest(name = "in {0}")
    @ArgumentsSource(AwayFromUtc.class)
    @DisplayName("once a client is deleted, each kind still names it as its document does, with no client id")
    void deletedClient(ZoneId zone) throws Exception {
        inZone(zone);
        String cedar = client("Cedar Works");
        report(invoice(cedar, "500", today.plusDays(30)), "120", today.minusDays(1));
        invoice(cedar, "300", today.minusDays(4));
        invoice(cedar, "150", today.plusDays(3));
        create("Quotation", cedar, "USD", "900", today.plusDays(6), false);
        repeat(invoice(cedar, "400", today.plusDays(40)), today.plusDays(5));
        create("Invoice", cedar, "USD", "250", today.minusDays(10), true);
        send(delete("/api/v1/customer/{id}", cedar), "").andExpect(status().isOk());

        overview()
                .andExpect(jsonPath(
                        "$.attention.items[*].kind", contains("paymentReport", "overdue", "dueSoon", "draft")))
                .andExpect(jsonPath("$.attention.items[*].client", everyItem(is("Cedar Works"))))
                .andExpect(jsonPath("$.attention.items[*].clientId", everyItem(nullValue())))
                .andExpect(content().string(containsString("\"client\":\"Cedar Works\",\"clientId\":null,")));
    }

    @Test
    @DisplayName("a business with nothing to do has an empty list")
    void nothing() throws Exception {
        overview()
                .andExpect(jsonPath("$.attention.items").isEmpty())
                .andExpect(jsonPath("$.attention.counts.paymentReports").value(0))
                .andExpect(jsonPath("$.attention.counts.drafts").value(0));
    }
}
