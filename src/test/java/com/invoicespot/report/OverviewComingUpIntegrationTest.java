package com.invoicespot.report;

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
import com.invoicespot.document.RecurringService;
import com.invoicespot.user.User;
import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
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

// What is coming up on the overview: the invoices running schedules will make
// over the next 90 days, dated as each schedule counts its next run, and the
// quotations waiting for an answer or that expired without one. Clients answer
// from a network of their own each time, so no limit is shared.
class OverviewComingUpIntegrationTest extends IntegrationTest {

    private static final AtomicInteger NETWORKS = new AtomicInteger();

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RecurringService recurring;

    private User owner;
    private LocalDate today;
    private String acme;
    private String birch;

    private record Made(String id, String number) {}

    // A business with two clients.
    @BeforeEach
    void business() throws Exception {
        owner = createUser();
        today = LocalDate.now(ZoneOffset.UTC);
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

    // A document dated 40 days ago, due or valid until the given day, or with
    // no such day; a draft is only planned for it.
    private Made create(String type, String client, String currency, String amount, LocalDate due, boolean draft)
            throws Exception {
        String made = body(send(post("/api/v1/document/create"), """
                        {"customerId": "%s", "documentType": "%s", "currency": "%s", "draft": %s,
                         "issueDate": "%s", "dueDate": %s,
                         "billingItems": [{"itemName": "Work", "unitPrice": %s, "quantity": 1}]}
                        """.formatted(
                                client,
                                type,
                                currency,
                                draft,
                                today.minusDays(40),
                                due == null ? "null" : "\"" + due + "\"",
                                amount))
                .andExpect(status().isOk()));
        return new Made(JsonPath.read(made, "$.newDocument._id"), JsonPath.read(made, "$.newDocument.documentNumber"));
    }

    private Made invoice(String client, String currency, String amount) throws Exception {
        return create("Invoice", client, currency, amount, today.plusDays(40), false);
    }

    private Made quotation(String client, String currency, String amount, LocalDate validUntil) throws Exception {
        return create("Quotation", client, currency, amount, validUntil, false);
    }

    private void voided(Made document) throws Exception {
        send(post("/api/v1/document/{id}/void", document.id()), "{\"reason\": \"Issued twice\"}")
                .andExpect(status().isOk());
    }

    // The client opens the document's link and does something there.
    private ResultActions fromLink(Made document, String action) throws Exception {
        String url = JsonPath.read(body(send(post("/api/v1/document/{id}/share", document.id()), "")), "$.url");
        String network = "10.71." + NETWORKS.incrementAndGet() + ".1";
        return mockMvc.perform(post("/api/v1/public/share/{token}/" + action, url.substring(url.lastIndexOf('/') + 1))
                .with(request -> {
                    request.setRemoteAddr(network);
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content(""));
    }

    // A schedule making the invoice again from the given day, with any more
    // fields given.
    private String repeat(Made invoice, String frequency, LocalDate start, String more) throws Exception {
        return JsonPath.read(
                body(send(post("/api/v1/recurring"), """
                                {"documentId": "%s", "frequency": "%s", "startDate": "%s"%s}
                                """.formatted(invoice.id(), frequency, start, more))
                        .andExpect(status().isCreated())),
                "$.schedule._id");
    }

    private ResultActions overview(String... params) throws Exception {
        MockHttpServletRequestBuilder request = get("/api/v1/report/overview");
        for (int index = 0; index < params.length; index += 2) {
            request.param(params[index], params[index + 1]);
        }
        return send(request, "");
    }

    @ParameterizedTest(name = "in {0}")
    @ArgumentsSource(AwayFromUtc.class)
    @DisplayName("running schedules make invoices over the next 90 days on the days they count their next runs;"
            + " the five soonest are listed, and all are counted")
    void recurring(ZoneId zone) throws Exception {
        inZone(zone);
        // Weekly from today, three times at most: today's invoice is made, then
        // the schedule moves to start in three days, with two left to make.
        Made moved = invoice(acme, "USD", "1000");
        String movedId = repeat(moved, "WEEKLY", today, ", \"maxRuns\": 3");
        recurring.makeDue(Instant.now());
        send(patch("/api/v1/recurring/{id}", movedId), """
                {"frequency": "WEEKLY", "startDate": "%s", "maxRuns": 3}
                """.formatted(today.plusDays(3)))
                .andExpect(status().isOk());
        Made weekly = invoice(birch, "USD", "100");
        String weeklyId = repeat(weekly, "WEEKLY", today.plusDays(1), "");
        // Twice, from the day the moved schedule makes its last invoice.
        repeat(invoice(acme, "USD", "200"), "MONTHLY", today.plusDays(10), ", \"maxRuns\": 2");
        repeat(invoice(acme, "USD", "300"), "QUARTERLY", today.plusDays(20), "");
        Made ending = invoice(birch, "USD", "400");
        String endingId = repeat(
                ending, "MONTHLY", today.plusDays(5), ", \"endDate\": \"%s\"".formatted(today.plusDays(40)));
        // On the last of the 90 days, and the day after them.
        repeat(invoice(acme, "USD", "500"), "MONTHLY", today.plusDays(89), "");
        repeat(invoice(acme, "USD", "600"), "MONTHLY", today.plusDays(90), "");
        // Never counted: a paused schedule, a finished one, and one making
        // invoices in euros.
        String paused = repeat(invoice(acme, "USD", "700"), "MONTHLY", today.plusDays(2), "");
        send(post("/api/v1/recurring/{id}/pause", paused), "").andExpect(status().isOk());
        String finished = repeat(invoice(acme, "USD", "800"), "MONTHLY", today.plusDays(3), "");
        jdbcTemplate.update("update recurring_schedules set next_run = null where id = ?", UUID.fromString(finished));
        repeat(invoice(birch, "EUR", "900"), "MONTHLY", today.plusDays(4), "");

        // Weekly: 13 invoices; moved: 2; twice: 2; quarterly: 1; ending: 2;
        // the last of the 90 days: 1; the day after them: none.
        overview()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.comingUp.recurring.active").value(7))
                .andExpect(jsonPath("$.comingUp.recurring.next90Days.invoices").value(21))
                .andExpect(jsonPath("$.comingUp.recurring.next90Days.amount").value(5300.0))
                .andExpect(jsonPath(
                        "$.comingUp.recurring.nextRuns[*].date",
                        contains(
                                today.plusDays(1).toString(),
                                today.plusDays(3).toString(),
                                today.plusDays(5).toString(),
                                today.plusDays(8).toString(),
                                today.plusDays(10).toString())))
                .andExpect(jsonPath(
                        "$.comingUp.recurring.nextRuns[*].scheduleId",
                        contains(weeklyId, movedId, endingId, weeklyId, movedId)))
                .andExpect(jsonPath(
                        "$.comingUp.recurring.nextRuns[*].templateId",
                        contains(weekly.id(), moved.id(), ending.id(), weekly.id(), moved.id())))
                .andExpect(jsonPath(
                        "$.comingUp.recurring.nextRuns[*].documentNumber",
                        contains(weekly.number(), moved.number(), ending.number(), weekly.number(), moved.number())))
                .andExpect(jsonPath(
                        "$.comingUp.recurring.nextRuns[*].client",
                        contains("Birch Studio", "Acme", "Birch Studio", "Birch Studio", "Acme")))
                .andExpect(jsonPath(
                        "$.comingUp.recurring.nextRuns[*].amount", contains(100.0, 1000.0, 400.0, 100.0, 1000.0)));
        overview("currency", "EUR")
                .andExpect(jsonPath("$.currency").value("EUR"))
                .andExpect(jsonPath("$.comingUp.recurring.active").value(1))
                .andExpect(jsonPath("$.comingUp.recurring.next90Days.invoices").value(3))
                .andExpect(jsonPath("$.comingUp.recurring.next90Days.amount").value(2700.0))
                .andExpect(jsonPath(
                        "$.comingUp.recurring.nextRuns[*].date",
                        contains(
                                today.plusDays(4).toString(),
                                today.plusDays(4).plusMonths(1).toString(),
                                today.plusDays(4).plusMonths(2).toString())));
    }

    @ParameterizedTest(name = "in {0}")
    @ArgumentsSource(AwayFromUtc.class)
    @DisplayName("runs whose day went by while a schedule was paused are made at the next daily run, so they count"
            + " as today's until then")
    void resumedLate(ZoneId zone) throws Exception {
        inZone(zone);
        Made late = invoice(acme, "USD", "100");
        String lateId = repeat(late, "MONTHLY", today, "");
        send(post("/api/v1/recurring/{id}/pause", lateId), "").andExpect(status().isOk());
        // Forty days go by while it is paused, then it is resumed.
        jdbcTemplate.update(
                "update recurring_schedules set start_date = ?, next_run = ? where id = ?",
                today.minusDays(40),
                today.minusDays(40),
                UUID.fromString(lateId));
        send(post("/api/v1/recurring/{id}/resume", lateId), "").andExpect(status().isOk());
        Made other = invoice(birch, "USD", "200");
        repeat(other, "MONTHLY", today.plusDays(1), "");

        // Two of the late schedule's runs went by; three more are within the
        // 90 days, as are three of the other's.
        LocalDate caughtUp = today.minusDays(40).plusMonths(2);
        overview()
                .andExpect(jsonPath("$.comingUp.recurring.active").value(2))
                .andExpect(jsonPath("$.comingUp.recurring.next90Days.invoices").value(8))
                .andExpect(jsonPath("$.comingUp.recurring.next90Days.amount").value(1100.0))
                .andExpect(jsonPath(
                        "$.comingUp.recurring.nextRuns[*].date",
                        contains(
                                today.toString(),
                                today.toString(),
                                today.plusDays(1).toString(),
                                caughtUp.toString(),
                                today.plusDays(1).plusMonths(1).toString())))
                .andExpect(jsonPath(
                        "$.comingUp.recurring.nextRuns[*].documentNumber",
                        contains(late.number(), late.number(), other.number(), late.number(), other.number())));

        // The daily run makes the two, and the schedule carries on from its dates.
        recurring.makeDue(Instant.now());
        overview()
                .andExpect(jsonPath("$.comingUp.recurring.next90Days.invoices").value(6))
                .andExpect(jsonPath("$.comingUp.recurring.next90Days.amount").value(900.0))
                .andExpect(jsonPath("$.comingUp.recurring.nextRuns[0].date").value(today.plusDays(1).toString()))
                .andExpect(jsonPath("$.comingUp.recurring.nextRuns[1].date").value(caughtUp.toString()))
                .andExpect(jsonPath("$.comingUp.recurring.nextRuns[1].documentNumber").value(late.number()));
    }

    @ParameterizedTest(name = "in {0}")
    @ArgumentsSource(AwayFromUtc.class)
    @DisplayName("a schedule moved to a later date after an invoice counts its runs from that date, and none before")
    void movedAfterRun(ZoneId zone) throws Exception {
        inZone(zone);
        Made moved = invoice(acme, "USD", "300");
        String id = repeat(moved, "MONTHLY", today, "");
        recurring.makeDue(Instant.now());
        send(patch("/api/v1/recurring/{id}", id), """
                {"frequency": "MONTHLY", "startDate": "%s"}
                """.formatted(today.plusDays(40)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schedule.runsDone").value(1))
                .andExpect(jsonPath("$.schedule.nextRun").value(today.plusDays(40).toString()));

        overview()
                .andExpect(jsonPath("$.comingUp.recurring.active").value(1))
                .andExpect(jsonPath("$.comingUp.recurring.next90Days.invoices").value(2))
                .andExpect(jsonPath("$.comingUp.recurring.next90Days.amount").value(600.0))
                .andExpect(jsonPath(
                        "$.comingUp.recurring.nextRuns[*].date",
                        contains(today.plusDays(40).toString(), today.plusDays(40).plusMonths(1).toString())));
    }

    @ParameterizedTest(name = "in {0}")
    @ArgumentsSource(AwayFromUtc.class)
    @DisplayName("a schedule moved to the end of a month after some invoices runs on each month's end, counting from"
            + " that date, without drifting")
    void monthEnds(ZoneId zone) throws Exception {
        inZone(zone);
        Made ends = invoice(acme, "USD", "100");
        String id = repeat(ends, "MONTHLY", today, "");
        // The latest 31st on or before today, which the schedule was moved to
        // after two invoices, and the invoices made on its month ends since.
        LocalDate month = today.withDayOfMonth(1);
        while (month.lengthOfMonth() < 31 || month.withDayOfMonth(31).isAfter(today)) {
            month = month.minusMonths(1);
        }
        LocalDate start = month.withDayOfMonth(31);
        int made = 0;
        while (start.plusMonths(made).isBefore(today)) {
            made++;
        }
        jdbcTemplate.update(
                "update recurring_schedules set start_date = ?, start_offset = 2, runs_done = ?, next_run = ?"
                        + " where id = ?",
                start,
                2 + made,
                start.plusMonths(made),
                UUID.fromString(id));
        List<String> expected = new ArrayList<>();
        for (int run = made; !start.plusMonths(run).isAfter(today.plusDays(89)); run++) {
            expected.add(start.plusMonths(run).toString());
        }

        overview()
                .andExpect(jsonPath("$.comingUp.recurring.next90Days.invoices").value(expected.size()))
                .andExpect(jsonPath("$.comingUp.recurring.nextRuns[*].date", contains(expected.toArray())));
    }

    @Test
    @DisplayName("a schedule copying a draft lists no number, and one copying an invoice with no client no client")
    void draftAndNoClient() throws Exception {
        Made draft = create("Invoice", acme, "USD", "150", today.plusDays(40), true);
        String draftId = repeat(draft, "WEEKLY", today.plusDays(2), "");
        Made unnamed = invoice(birch, "USD", "250");
        // Saved before a client was named or linked, as the oldest documents were.
        jdbcTemplate.update(
                "update documents set customer_pkid = null, customer_name = null where id = ?",
                UUID.fromString(unnamed.id()));
        String unnamedId = repeat(unnamed, "WEEKLY", today.plusDays(3), "");

        overview()
                .andExpect(jsonPath("$.comingUp.recurring.nextRuns[0].scheduleId").value(draftId))
                .andExpect(jsonPath("$.comingUp.recurring.nextRuns[0].templateId").value(draft.id()))
                .andExpect(jsonPath("$.comingUp.recurring.nextRuns[0].documentNumber").value(nullValue()))
                .andExpect(jsonPath("$.comingUp.recurring.nextRuns[0].client").value("Acme"))
                .andExpect(jsonPath("$.comingUp.recurring.nextRuns[0].amount").value(150.0))
                .andExpect(jsonPath("$.comingUp.recurring.nextRuns[1].scheduleId").value(unnamedId))
                .andExpect(jsonPath("$.comingUp.recurring.nextRuns[1].documentNumber").value(unnamed.number()))
                .andExpect(jsonPath("$.comingUp.recurring.nextRuns[1].client").value(nullValue()))
                .andExpect(content().string(containsString("\"documentNumber\":null,\"client\":\"Acme\",")))
                .andExpect(content().string(containsString(
                        "\"documentNumber\":\"" + unnamed.number() + "\",\"client\":null,")));
    }

    @ParameterizedTest(name = "in {0}")
    @ArgumentsSource(AwayFromUtc.class)
    @DisplayName("unanswered quotations await an answer until their last day has gone by, then have expired; the"
            + " three that expire soonest are listed")
    void quotations(ZoneId zone) throws Exception {
        inZone(zone);
        Made lastDay = quotation(acme, "USD", "100", today);
        Made tomorrow = quotation(birch, "USD", "200", today.plusDays(1));
        Made week = quotation(acme, "USD", "300", today.plusDays(6));
        Made month = quotation(acme, "USD", "400", today.plusDays(30));
        Made open = quotation(birch, "USD", "500", null);
        quotation(acme, "USD", "600", today.minusDays(1));
        quotation(birch, "USD", "700", today.minusDays(40));
        // Never counted: quotations accepted, declined, void, still a draft or
        // in euros, and an invoice.
        fromLink(quotation(acme, "USD", "800", today.plusDays(2)), "accept").andExpect(status().isOk());
        fromLink(quotation(acme, "USD", "900", today.plusDays(3)), "decline").andExpect(status().isOk());
        voided(quotation(acme, "USD", "1000", today.plusDays(4)));
        create("Quotation", acme, "USD", "1100", today.plusDays(5), true);
        quotation(acme, "EUR", "1200", today.plusDays(1));
        invoice(birch, "EUR", "1250");
        invoice(acme, "USD", "1350");

        overview()
                .andExpect(jsonPath("$.comingUp.quotations.awaiting.count").value(5))
                .andExpect(jsonPath("$.comingUp.quotations.awaiting.amount").value(1500.0))
                .andExpect(jsonPath(
                        "$.comingUp.quotations.awaiting.items[*].documentId",
                        contains(lastDay.id(), tomorrow.id(), week.id())))
                .andExpect(jsonPath(
                        "$.comingUp.quotations.awaiting.items[*].documentNumber",
                        contains(lastDay.number(), tomorrow.number(), week.number())))
                .andExpect(jsonPath(
                        "$.comingUp.quotations.awaiting.items[*].client", contains("Acme", "Birch Studio", "Acme")))
                .andExpect(jsonPath("$.comingUp.quotations.awaiting.items[*].amount", contains(100.0, 200.0, 300.0)))
                .andExpect(jsonPath(
                        "$.comingUp.quotations.awaiting.items[*].validUntil",
                        contains(today.toString(), today.plusDays(1).toString(), today.plusDays(6).toString())))
                .andExpect(jsonPath("$.comingUp.quotations.awaiting.items[*].days", contains(0, 1, 6)))
                .andExpect(jsonPath("$.comingUp.quotations.expired.count").value(2))
                .andExpect(jsonPath("$.comingUp.quotations.expired.amount").value(1300.0));
        overview("currency", "EUR")
                .andExpect(jsonPath("$.comingUp.quotations.awaiting.count").value(1))
                .andExpect(jsonPath("$.comingUp.quotations.awaiting.amount").value(1200.0))
                .andExpect(jsonPath("$.comingUp.quotations.expired.count").value(0));

        // Once the two soonest are answered, the one valid for as long as it
        // takes is listed, last.
        fromLink(lastDay, "accept").andExpect(status().isOk());
        fromLink(tomorrow, "decline").andExpect(status().isOk());
        overview()
                .andExpect(jsonPath("$.comingUp.quotations.awaiting.count").value(3))
                .andExpect(jsonPath("$.comingUp.quotations.awaiting.amount").value(1200.0))
                .andExpect(jsonPath(
                        "$.comingUp.quotations.awaiting.items[*].documentId",
                        contains(week.id(), month.id(), open.id())))
                .andExpect(jsonPath("$.comingUp.quotations.awaiting.items[2].validUntil").value(nullValue()))
                .andExpect(jsonPath("$.comingUp.quotations.awaiting.items[2].days").value(nullValue()))
                .andExpect(jsonPath("$.comingUp.quotations.expired.count").value(2));
    }

    @Test
    @DisplayName("a business with nothing coming up shows nothing")
    void nothing() throws Exception {
        overview()
                .andExpect(content().string(containsString("\"comingUp\":{\"recurring\":{\"active\":0,"
                        + "\"next90Days\":{\"invoices\":0,\"amount\":0.00},\"nextRuns\":[]},"
                        + "\"quotations\":{\"awaiting\":{\"count\":0,\"amount\":0.00,\"items\":[]},"
                        + "\"expired\":{\"count\":0,\"amount\":0.00}}}")));
    }
}
