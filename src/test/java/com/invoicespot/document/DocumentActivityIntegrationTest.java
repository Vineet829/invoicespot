package com.invoicespot.document;

import static org.hamcrest.Matchers.contains;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mail.MailSendException;
import org.springframework.test.web.servlet.ResultActions;

class DocumentActivityIntegrationTest extends IntegrationTest {

    private String invoice(User owner) throws Exception {
        String customer = mockMvc.perform(post("/api/v1/customer/create")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Beta LLC\", \"email\": \"%s\", \"phoneNumber\": \"+1\"}"
                                .formatted("beta-" + UUID.randomUUID() + "@clients.test")))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String body = mockMvc.perform(post("/api/v1/document/create")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId": "%s", "documentType": "Invoice", "currency": "USD",
                                 "billingItems": [{"itemName": "Work", "unitPrice": 100, "quantity": 1}]}
                                """.formatted((String) JsonPath.read(customer, "$.createdCustomer._id"))))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(body, "$.newDocument._id");
    }

    private ResultActions activity(String id, User user) throws Exception {
        return mockMvc.perform(
                get("/api/v1/document/{id}/activity", id).header(HttpHeaders.AUTHORIZATION, bearer(user)));
    }

    @Test
    @DisplayName("a document's history lists what happened to it, newest first")
    void history() throws Exception {
        User owner = createUser();
        String id = invoice(owner);
        mockMvc.perform(patch("/api/v1/document/{id}", id)
                .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"additionalInfo\": \"Thanks\"}"));
        String paid = mockMvc.perform(post("/api/v1/document/{id}/payment", id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amountPaid\": 40, \"paymentMethod\": \"Bank Transfer\"}"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String paymentId = JsonPath.read(paid, "$.document.paymentRecords[0]._id");
        mockMvc.perform(patch("/api/v1/document/{id}/payment/{paymentId}", id, paymentId)
                .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amountPaid\": 50}"));
        mockMvc.perform(delete("/api/v1/document/{id}/payment/{paymentId}", id, paymentId)
                .header(HttpHeaders.AUTHORIZATION, bearer(owner)));
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage((Session) null));
        mockMvc.perform(post("/api/v1/document/{id}/email", id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"to\": \"accounts@beta.test\"}"))
                .andExpect(status().isOk());

        activity(id, owner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.events[*].type", contains(
                        "EMAILED", "PAYMENT_DELETED", "PAYMENT_EDITED", "PAYMENT_RECORDED", "EDITED", "CREATED")))
                .andExpect(jsonPath("$.events[*].detail", contains(
                        "to accounts@beta.test",
                        "USD 50.00 · Bank Transfer",
                        "USD 50.00 · Bank Transfer",
                        "USD 40.00 · Bank Transfer",
                        null,
                        null)));
    }

    @Test
    @DisplayName("an email that fails to send is not recorded as sent")
    void failedEmail() throws Exception {
        User owner = createUser();
        String id = invoice(owner);
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage((Session) null));
        doThrow(new MailSendException("down")).when(mailSender).send(any(MimeMessage.class));

        mockMvc.perform(post("/api/v1/document/{id}/email", id).header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isBadGateway());
        activity(id, owner).andExpect(jsonPath("$.events[*].type", contains("CREATED")));
    }

    @Test
    @DisplayName("only the document's owner sees its history")
    void ownership() throws Exception {
        User owner = createUser();
        String id = invoice(owner);

        activity(id, createUser()).andExpect(status().isForbidden());
        activity(UUID.randomUUID().toString(), owner).andExpect(status().isNotFound());
    }
}
