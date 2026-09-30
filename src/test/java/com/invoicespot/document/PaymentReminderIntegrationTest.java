package com.invoicespot.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.invoicespot.IntegrationTest;
import com.invoicespot.user.User;
import com.jayway.jsonpath.JsonPath;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class PaymentReminderIntegrationTest extends IntegrationTest {

    private record Sent(String id, String email) {}

    private Sent document(User owner, String type, String dueDate) throws Exception {
        String email = "beta-" + UUID.randomUUID() + "@clients.test";
        String customer = mockMvc.perform(post("/api/v1/customer/create")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Beta LLC\", \"email\": \"%s\", \"phoneNumber\": \"+1\"}"
                                .formatted(email)))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String body = mockMvc.perform(post("/api/v1/document/create")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId": "%s", "documentType": "%s", "currency": "USD", "dueDate": "%s",
                                 "billingItems": [{"itemName": "Work", "unitPrice": 100, "quantity": 1}]}
                                """.formatted(
                                        (String) JsonPath.read(customer, "$.createdCustomer._id"), type, dueDate)))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return new Sent(JsonPath.read(body, "$.newDocument._id"), email);
    }

    private void pay(User owner, String id, int amount) throws Exception {
        mockMvc.perform(post("/api/v1/document/{id}/payment", id)
                .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amountPaid\": %d}".formatted(amount)));
    }

    private ResultActions draft(User owner, String id) throws Exception {
        return mockMvc.perform(
                get("/api/v1/document/{id}/remind", id).header(HttpHeaders.AUTHORIZATION, bearer(owner)));
    }

    private ResultActions remind(User owner, String id) throws Exception {
        return mockMvc.perform(
                post("/api/v1/document/{id}/remind", id).header(HttpHeaders.AUTHORIZATION, bearer(owner)));
    }

    @Test
    @DisplayName("a reminder for an overdue invoice says what is owed since when, and is counted once sent")
    void overdue() throws Exception {
        User owner = createUser();
        Sent invoice = document(owner, "Invoice", "2026-01-15T12:00:00Z");
        pay(owner, invoice.id(), 40);
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage((Session) null));

        draft(owner, invoice.id())
                .andExpect(jsonPath("$.to").value(invoice.email()))
                .andExpect(jsonPath("$.subject").value("Reminder: Invoice INV-0001 from Test User"))
                .andExpect(jsonPath("$.message", containsString(
                        "Invoice INV-0001 for USD 60.00 was due on Jan 15, 2026 and is still open")));
        remind(owner, invoice.id())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Reminder sent successfully."));

        ArgumentCaptor<MimeMessage> sent = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(sent.capture());
        assertThat(sent.getValue().getAllRecipients()).extracting(Object::toString).containsExactly(invoice.email());
        mockMvc.perform(get("/api/v1/document/{id}", invoice.id()).header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(jsonPath("$.document.remindersSent").value(1))
                .andExpect(jsonPath("$.document.lastRemindedAt", notNullValue()));
        mockMvc.perform(get("/api/v1/document/{id}/activity", invoice.id())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(jsonPath("$.events[0].type").value("REMINDED"))
                .andExpect(jsonPath("$.events[0].detail").value("to " + invoice.email()));
    }

    @Test
    @DisplayName("a reminder for an invoice that isn't due yet says when it is due")
    void notYetDue() throws Exception {
        User owner = createUser();
        Sent invoice = document(owner, "Invoice", "2099-01-01T12:00:00Z");

        draft(owner, invoice.id())
                .andExpect(jsonPath(
                        "$.message", containsString("Invoice INV-0001 for USD 100.00 is due on Jan 1, 2099")));
    }

    @Test
    @DisplayName("paid invoices and quotations need no reminder, and the demo account sends none")
    void rules() throws Exception {
        User owner = createUser();
        Sent paid = document(owner, "Invoice", "2026-01-15T12:00:00Z");
        pay(owner, paid.id(), 100);
        Sent quotation = document(owner, "Quotation", "2026-01-15T12:00:00Z");

        for (String id : new String[] {paid.id(), quotation.id()}) {
            remind(owner, id)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value("Only an unpaid invoice needs a reminder"));
        }
        User demo = demoUser();
        remind(demo, document(demo, "Invoice", "2026-01-15T12:00:00Z").id()).andExpect(status().isForbidden());
        verify(mailSender, never()).send(any(MimeMessage.class));
    }
}
