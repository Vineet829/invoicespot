package com.invoicespot.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.invoicespot.IntegrationTest;
import com.invoicespot.user.User;
import com.jayway.jsonpath.JsonPath;
import jakarta.mail.Address;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

class AutomaticRemindersIntegrationTest extends IntegrationTest {

    private record Invoice(String id, String email) {}

    @Autowired
    private AutomaticReminders reminders;

    private final LocalDate today = LocalDate.now(ZoneOffset.UTC);

    @BeforeEach
    void mail() {
        when(mailSender.createMimeMessage()).thenAnswer(invocation -> new MimeMessage((Session) null));
    }

    private void turnOnReminders(User owner) throws Exception {
        mockMvc.perform(put("/api/v1/settings")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"defaultCurrency": "USD", "paymentTermsDays": 14, "defaultTaxRate": 0,
                                 "taxLabel": "Tax", "timeZone": "UTC", "remindersEnabled": true,
                                 "reminderDaysBefore": 3, "reminderOnDue": true, "reminderEveryDays": 7,
                                 "reminderMax": 3}
                                """))
                .andExpect(status().isOk());
    }

    private Invoice invoice(User owner, LocalDate due) throws Exception {
        String email = "client-" + UUID.randomUUID() + "@clients.test";
        String customer = mockMvc.perform(post("/api/v1/customer/create")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Client\", \"email\": \"%s\", \"phoneNumber\": \"+1\"}".formatted(email)))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String body = mockMvc.perform(post("/api/v1/document/create")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId": "%s", "documentType": "Invoice", "currency": "USD", "dueDate": "%s",
                                 "billingItems": [{"itemName": "Work", "unitPrice": 100, "quantity": 1}]}
                                """.formatted(
                                        (String) JsonPath.read(customer, "$.createdCustomer._id"),
                                        due.atTime(12, 0).toInstant(ZoneOffset.UTC))))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return new Invoice(JsonPath.read(body, "$.newDocument._id"), email);
    }

    private int remindersSent(User owner, Invoice invoice) throws Exception {
        String body = mockMvc.perform(get("/api/v1/document/{id}", invoice.id())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(body, "$.document.remindersSent");
    }

    private List<String> recipients() throws MessagingException {
        ArgumentCaptor<MimeMessage> sent = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender, atLeast(0)).send(sent.capture());
        List<String> recipients = new ArrayList<>();
        for (MimeMessage message : sent.getAllValues()) {
            for (Address address : message.getAllRecipients()) {
                recipients.add(address.toString());
            }
        }
        return recipients;
    }

    @Test
    @DisplayName("reminders go out on the planned days, once a day, for owners who turned them on")
    void plannedDays() throws Exception {
        User owner = createUser();
        turnOnReminders(owner);
        Invoice soon = invoice(owner, today.plusDays(3));
        Invoice dueToday = invoice(owner, today);
        Invoice weekLate = invoice(owner, today.minusDays(7));
        Invoice threeDaysLate = invoice(owner, today.minusDays(3));
        Invoice paid = invoice(owner, today);
        mockMvc.perform(post("/api/v1/document/{id}/payment", paid.id())
                .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amountPaid\": 100}"));
        User quiet = createUser();
        Invoice notTurnedOn = invoice(quiet, today);

        assertThat(reminders.send(Instant.now())).isGreaterThanOrEqualTo(3);
        reminders.send(Instant.now());

        assertThat(recipients())
                .contains(soon.email(), dueToday.email(), weekLate.email())
                .doesNotContain(threeDaysLate.email(), paid.email(), notTurnedOn.email());
        for (Invoice reminded : List.of(soon, dueToday, weekLate)) {
            assertThat(remindersSent(owner, reminded)).isEqualTo(1);
        }
        assertThat(remindersSent(owner, threeDaysLate)).isZero();
        mockMvc.perform(get("/api/v1/document/{id}/activity", dueToday.id())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(jsonPath("$.events[0].type").value("REMINDED"))
                .andExpect(jsonPath("$.events[0].detail").value("automatically to " + dueToday.email()))
                .andExpect(jsonPath("$.events[0].actor").doesNotExist())
                .andExpect(jsonPath("$.events[0].byYou").value(false));
        mockMvc.perform(get("/api/v1/notification").header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(jsonPath("$.unread").value(3))
                .andExpect(jsonPath("$.notifications[0].type").value("REMINDER_SENT"));
    }

    @Test
    @DisplayName("two runs at once send a due reminder once")
    void twoRunsAtOnce() throws Exception {
        User owner = createUser();
        turnOnReminders(owner);
        Invoice due = invoice(owner, today);
        // A send to this client waits, a few seconds at most, for a second
        // one, so two runs that both found the reminder due both send it.
        CountDownLatch bothSending = new CountDownLatch(2);
        doAnswer(invocation -> {
                    MimeMessage message = invocation.getArgument(0);
                    if (message.getAllRecipients()[0].toString().equals(due.email())) {
                        bothSending.countDown();
                        bothSending.await(4, TimeUnit.SECONDS);
                    }
                    return null;
                })
                .when(mailSender)
                .send(any(MimeMessage.class));
        Instant now = Instant.now();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> first = pool.submit(() -> reminders.send(now));
            Future<Integer> second = pool.submit(() -> reminders.send(now));
            first.get(60, TimeUnit.SECONDS);
            second.get(60, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }

        assertThat(recipients()).containsOnlyOnce(due.email());
        assertThat(remindersSent(owner, due)).isEqualTo(1);
    }

    @Test
    @DisplayName("the shared demo account never sends automatic reminders")
    void demoAccount() throws Exception {
        User demo = demoUser();
        turnOnReminders(demo);
        Invoice due = invoice(demo, today);

        reminders.send(Instant.now());

        assertThat(recipients()).doesNotContain(due.email());
        assertThat(remindersSent(demo, due)).isZero();
    }
}
