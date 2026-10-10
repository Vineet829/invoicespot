package com.invoicespot.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.invoicespot.IntegrationTest;
import com.invoicespot.user.User;
import com.jayway.jsonpath.JsonPath;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

class RecurringIntegrationTest extends IntegrationTest {

    @Autowired
    private RecurringService recurring;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private DataSource dataSource;

    private final LocalDate today = LocalDate.now(ZoneOffset.UTC);
    private User owner;
    private String client;
    private String clientEmail;

    @BeforeEach
    void client() throws Exception {
        owner = createUser();
        clientEmail = "client-" + UUID.randomUUID() + "@clients.test";
        String body = mockMvc.perform(post("/api/v1/customer/create")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Beta LLC\", \"email\": \"%s\", \"phoneNumber\": \"+1\"}"
                                .formatted(clientEmail)))
                .andReturn()
                .getResponse()
                .getContentAsString();
        client = JsonPath.read(body, "$.createdCustomer._id");
    }

    private String document(String type) throws Exception {
        String body = mockMvc.perform(post("/api/v1/document/create")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId": "%s", "documentType": "%s", "currency": "USD",
                                 "billingItems": [{"itemName": "Hosting", "unitPrice": 120, "quantity": 1}]}
                                """.formatted(client, type)))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(body, "$.newDocument._id");
    }

    private ResultActions schedule(String document, String fields) throws Exception {
        return mockMvc.perform(post("/api/v1/recurring")
                .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"documentId\": \"%s\", %s}".formatted(document, fields)));
    }

    private String scheduleId(String document, String fields) throws Exception {
        String body = schedule(document, fields)
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(body, "$.schedule._id");
    }

    private ResultActions list() throws Exception {
        return mockMvc.perform(get("/api/v1/recurring").header(HttpHeaders.AUTHORIZATION, bearer(owner)));
    }

    private int documents() throws Exception {
        String body = mockMvc.perform(get("/api/v1/document/all").param("customerId", client)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.<Integer>read(body, "$.totalDocuments");
    }

    private Instant daysFromNow(int days) {
        return Instant.now().plus(Duration.ofDays(days));
    }

    // Makes the invoices due while the business's settings are held, as by
    // another document taking its number: the run locks its schedule, then
    // waits to number the invoice. The request arrives then, and the settings
    // are let go once it waits too. Gives the request's status.
    private int whileMaking(Instant now, Callable<ResultActions> request) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try (Connection holder = dataSource.getConnection()) {
            holder.setAutoCommit(false);
            try (PreparedStatement lock =
                    holder.prepareStatement("select pkid from business_settings where business_pkid = ? for update")) {
                lock.setLong(1, businessOf(owner).getPkid());
                lock.executeQuery().close();
            }
            Future<Integer> making = pool.submit(() -> recurring.makeDue(now));
            awaitWaitingForLocks(1);
            Future<Integer> status = pool.submit(() -> request.call().andReturn().getResponse().getStatus());
            awaitWaitingForLocks(2);
            holder.rollback();
            making.get(30, TimeUnit.SECONDS);
            return status.get(30, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
    }

    // Waits until that many connections to the test's database wait for a
    // lock, or gives up after half a minute.
    private void awaitWaitingForLocks(int count) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (waitingForLocks() < count && System.nanoTime() < deadline) {
            Thread.sleep(20);
        }
    }

    private int waitingForLocks() {
        return jdbc.queryForObject(
                "select count(*) from pg_stat_activity"
                        + " where datname = current_database() and wait_event_type = 'Lock'",
                Integer.class);
    }

    @Test
    @DisplayName("an invoice repeats from its first date, each period once, telling the owner")
    void monthly() throws Exception {
        String invoice = document("Invoice");
        scheduleId(invoice, "\"frequency\": \"monthly\", \"startDate\": \"%s\", \"maxRuns\": 3".formatted(today));

        assertThat(recurring.makeDue(Instant.now())).isGreaterThanOrEqualTo(1);
        recurring.makeDue(Instant.now());

        assertThat(documents()).isEqualTo(2);
        list()
                .andExpect(jsonPath("$.schedules[0].documentNumber").value("INV-0001"))
                .andExpect(jsonPath("$.schedules[0].runsDone").value(1))
                .andExpect(jsonPath("$.schedules[0].nextRun").value(today.plusMonths(1).toString()))
                .andExpect(jsonPath("$.schedules[0].lastInvoice.documentNumber").value("INV-0002"))
                .andExpect(jsonPath("$.schedules[0].finished").value(false));
        String made = JsonPath.read(
                list().andReturn().getResponse().getContentAsString(), "$.schedules[0].lastInvoice._id");
        mockMvc.perform(get("/api/v1/document/{id}/activity", made).header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(jsonPath("$.events[0].type").value("ISSUED"))
                .andExpect(jsonPath("$.events[0].detail").value("INV-0002"))
                .andExpect(jsonPath("$.events[1].type").value("RECURRING"))
                .andExpect(jsonPath("$.events[1].detail").value("INV-0001 · monthly"));
        mockMvc.perform(get("/api/v1/document/{id}", made).header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(jsonPath("$.document.source").doesNotExist())
                .andExpect(jsonPath("$.document.paymentRecords").isEmpty());
        mockMvc.perform(get("/api/v1/notification").header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(jsonPath("$.notifications[0].type").value("RECURRING_CREATED"))
                .andExpect(jsonPath("$.notifications[0].message")
                        .value("Invoice INV-0002 for Beta LLC was made by its monthly schedule"));
    }

    @Test
    @DisplayName("each invoice is due as the payment terms say from its day, at noon in the business's time zone")
    void dueDate() throws Exception {
        // Twelve or thirteen hours ahead of UTC, so its days are often not UTC's.
        ZoneId zone = ZoneId.of("Pacific/Auckland");
        mockMvc.perform(put("/api/v1/settings")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"defaultCurrency": "USD", "paymentTermsDays": 7, "defaultTaxRate": 0,
                                 "taxLabel": "Tax", "timeZone": "Pacific/Auckland"}
                                """))
                .andExpect(status().isOk());
        LocalDate day = LocalDate.now(zone);
        scheduleId(document("Invoice"), "\"frequency\": \"monthly\", \"startDate\": \"%s\"".formatted(day));

        recurring.makeDue(Instant.now());

        String made = JsonPath.read(
                list().andReturn().getResponse().getContentAsString(), "$.schedules[0].lastInvoice._id");
        mockMvc.perform(get("/api/v1/document/{id}", made).header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(jsonPath("$.document.dueDate").value(day.plusDays(7).toString()));
        assertThat(jdbc.queryForObject(
                                "select due_date from documents where id = ?", Timestamp.class, UUID.fromString(made))
                        .toInstant())
                .isEqualTo(day.plusDays(7).atTime(LocalTime.NOON).atZone(zone).toInstant());
    }

    @Test
    @DisplayName("missed periods are caught up to the most allowed, then the schedule is finished")
    void catchUpAndCount() throws Exception {
        String weekly = scheduleId(document("Invoice"),
                "\"frequency\": \"WEEKLY\", \"startDate\": \"%s\", \"maxRuns\": 2".formatted(today));

        recurring.makeDue(daysFromNow(20));

        assertThat(documents()).isEqualTo(3);
        list()
                .andExpect(jsonPath("$.schedules[0]._id").value(weekly))
                .andExpect(jsonPath("$.schedules[0].runsDone").value(2))
                .andExpect(jsonPath("$.schedules[0].finished").value(true));
    }

    @Test
    @DisplayName("an end date stops a schedule, and a paused one makes nothing until resumed")
    void endDateAndPause() throws Exception {
        String id = scheduleId(
                document("Invoice"),
                "\"frequency\": \"monthly\", \"startDate\": \"%s\", \"endDate\": \"%s\""
                        .formatted(today, today.plusDays(40)));
        mockMvc.perform(post("/api/v1/recurring/{id}/pause", id).header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(jsonPath("$.schedule.paused").value(true));

        recurring.makeDue(daysFromNow(100));
        assertThat(documents()).isEqualTo(1);

        mockMvc.perform(post("/api/v1/recurring/{id}/resume", id).header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(jsonPath("$.schedule.paused").value(false));
        recurring.makeDue(daysFromNow(100));
        assertThat(documents()).isEqualTo(3);
        list().andExpect(jsonPath("$.schedules[0].finished").value(true));
    }

    @Test
    @DisplayName("a schedule can email each new invoice to the client")
    void emailsEachInvoice() throws Exception {
        when(mailSender.createMimeMessage()).thenAnswer(invocation -> new MimeMessage((Session) null));
        scheduleId(document("Invoice"),
                "\"frequency\": \"monthly\", \"startDate\": \"%s\", \"emailAutomatically\": true".formatted(today));

        recurring.makeDue(Instant.now());

        ArgumentCaptor<MimeMessage> sent = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(sent.capture());
        assertThat(sent.getValue().getAllRecipients()).extracting(Object::toString).containsExactly(clientEmail);
        String made = JsonPath.read(
                list().andReturn().getResponse().getContentAsString(), "$.schedules[0].lastInvoice._id");
        mockMvc.perform(get("/api/v1/document/{id}/activity", made).header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(jsonPath("$.events[0].type").value("EMAILED"))
                .andExpect(jsonPath("$.events[0].detail").value("automatically to " + clientEmail));
    }

    @Test
    @DisplayName("a schedule can leave each invoice as a draft to review, which is never emailed on its own")
    void drafts() throws Exception {
        schedule(document("Invoice"), """
                "frequency": "monthly", "startDate": "%s", "issueAutomatically": false, "emailAutomatically": true
                """.formatted(today))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("Only an invoice that is issued as it is made can be emailed on its own"));
        String template = document("Invoice");
        scheduleId(template, "\"frequency\": \"monthly\", \"startDate\": \"%s\", \"issueAutomatically\": false"
                .formatted(today));

        recurring.makeDue(Instant.now());

        verify(mailSender, never()).send(any(MimeMessage.class));
        list()
                .andExpect(jsonPath("$.schedules[0].issueAutomatically").value(false))
                .andExpect(jsonPath("$.schedules[0].runsDone").value(1))
                .andExpect(jsonPath("$.schedules[0].lastInvoice.state").value("draft"))
                .andExpect(jsonPath("$.schedules[0].lastInvoice.documentNumber").value(nullValue()));
        String made = JsonPath.read(
                list().andReturn().getResponse().getContentAsString(), "$.schedules[0].lastInvoice._id");
        mockMvc.perform(get("/api/v1/document/{id}/activity", made).header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(jsonPath("$.events[*].type", contains("RECURRING")));
        mockMvc.perform(get("/api/v1/notification").header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(jsonPath("$.notifications[0].type").value("RECURRING_DRAFT"))
                .andExpect(jsonPath("$.notifications[0].message")
                        .value("A draft invoice for Beta LLC from its monthly schedule is ready to review"));
        mockMvc.perform(post("/api/v1/document/{id}/issue", made).header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(jsonPath("$.updatedDocument.documentNumber").value("INV-0003"));
    }

    @Test
    @DisplayName("changing a schedule counts its dates from the new first one, keeping what was made")
    void edit() throws Exception {
        String invoice = document("Invoice");
        String id = scheduleId(
                invoice, "\"frequency\": \"monthly\", \"startDate\": \"%s\", \"maxRuns\": 3".formatted(today));
        recurring.makeDue(Instant.now());

        mockMvc.perform(patch("/api/v1/recurring/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"frequency\": \"weekly\", \"startDate\": \"%s\", \"maxRuns\": 3}"
                                .formatted(today.plusDays(10))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schedule.frequency").value("WEEKLY"))
                .andExpect(jsonPath("$.schedule.nextRun").value(today.plusDays(10).toString()))
                .andExpect(jsonPath("$.schedule.runsDone").value(1));
        mockMvc.perform(get("/api/v1/document/{id}/recurring", invoice)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(jsonPath("$.schedule._id").value(id));
    }

    @Test
    @DisplayName("only an owner's invoices repeat, once each, from today on, and schedules can be removed")
    void rules() throws Exception {
        String invoice = document("Invoice");
        String start = "\"frequency\": \"monthly\", \"startDate\": \"%s\"".formatted(today);

        schedule(document("Quotation"), start)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Only an invoice can repeat"));
        schedule(invoice, "\"frequency\": \"daily\", \"startDate\": \"%s\"".formatted(today))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Repeat weekly, monthly, quarterly or yearly"));
        schedule(invoice, "\"frequency\": \"monthly\", \"startDate\": \"%s\"".formatted(today.minusDays(1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("The first invoice can't be in the past"));
        schedule(invoice, start + ", \"endDate\": \"%s\"".formatted(today.minusDays(1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("The end date must come after the first invoice"));
        String id = scheduleId(invoice, start);
        schedule(invoice, start)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("This invoice already repeats"));
        mockMvc.perform(post("/api/v1/recurring/{id}/pause", id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(createUser())))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/v1/recurring/{id}", id).header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk());
        list().andExpect(jsonPath("$.schedules").isEmpty());
        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("a schedule paused while it makes an invoice counts that invoice, and makes the next once resumed")
    void pauseWhileMaking() throws Exception {
        String id = scheduleId(
                document("Invoice"), "\"frequency\": \"monthly\", \"startDate\": \"%s\"".formatted(today));
        String session = bearer(owner);

        int status = whileMaking(Instant.now(), () -> mockMvc.perform(
                post("/api/v1/recurring/{id}/pause", id).header(HttpHeaders.AUTHORIZATION, session)));

        assertThat(status).isEqualTo(200);
        list()
                .andExpect(jsonPath("$.schedules[0].paused").value(true))
                .andExpect(jsonPath("$.schedules[0].runsDone").value(1))
                .andExpect(jsonPath("$.schedules[0].nextRun").value(today.plusMonths(1).toString()));
        mockMvc.perform(post("/api/v1/recurring/{id}/resume", id).header(HttpHeaders.AUTHORIZATION, session))
                .andExpect(status().isOk());
        recurring.makeDue(daysFromNow(40));
        assertThat(documents()).isEqualTo(3);
    }

    @Test
    @DisplayName("a schedule changed while it makes an invoice counts that invoice toward the most allowed")
    void editWhileMaking() throws Exception {
        String id = scheduleId(
                document("Invoice"), "\"frequency\": \"monthly\", \"startDate\": \"%s\"".formatted(today));
        String session = bearer(owner);

        int status = whileMaking(Instant.now(), () -> mockMvc.perform(patch("/api/v1/recurring/{id}", id)
                .header(HttpHeaders.AUTHORIZATION, session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"frequency\": \"monthly\", \"startDate\": \"%s\", \"maxRuns\": 2}"
                        .formatted(today.plusDays(10)))));

        assertThat(status).isEqualTo(200);
        recurring.makeDue(daysFromNow(80));
        assertThat(documents()).isEqualTo(3);
        list()
                .andExpect(jsonPath("$.schedules[0].runsDone").value(2))
                .andExpect(jsonPath("$.schedules[0].finished").value(true));
    }

    @Test
    @DisplayName("changing a schedule but not its next date keeps it on the day it started, past a short month")
    void editKeepsMonthEnds() throws Exception {
        // The next 31st of a month followed by a shorter one: Jan, Mar, May, Aug or Oct.
        LocalDate month = today.withDayOfMonth(1);
        while (month.lengthOfMonth() < 31
                || month.plusMonths(1).lengthOfMonth() == 31
                || month.withDayOfMonth(31).isBefore(today)) {
            month = month.plusMonths(1);
        }
        LocalDate start = month.withDayOfMonth(31);
        String id = scheduleId(
                document("Invoice"), "\"frequency\": \"monthly\", \"startDate\": \"%s\"".formatted(start));
        recurring.makeDue(start.atTime(LocalTime.NOON).toInstant(ZoneOffset.UTC));
        LocalDate next = start.plusMonths(1);
        list().andExpect(jsonPath("$.schedules[0].nextRun").value(next.toString()));

        // Its dialog sends the next date as it shows it, here with at most 12 invoices.
        mockMvc.perform(patch("/api/v1/recurring/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"frequency\": \"MONTHLY\", \"startDate\": \"%s\", \"maxRuns\": 12}"
                                .formatted(next)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schedule.nextRun").value(next.toString()));
        recurring.makeDue(next.atTime(LocalTime.NOON).toInstant(ZoneOffset.UTC));

        list()
                .andExpect(jsonPath("$.schedules[0].runsDone").value(2))
                .andExpect(jsonPath("$.schedules[0].nextRun").value(start.plusMonths(2).toString()));
    }
}
