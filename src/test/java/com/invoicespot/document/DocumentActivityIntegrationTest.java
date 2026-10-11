package com.invoicespot.document;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
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
import com.invoicespot.business.Business;
import com.invoicespot.business.BusinessRole;
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
                        "EMAILED",
                        "SHARED",
                        "PAYMENT_DELETED",
                        "PAYMENT_EDITED",
                        "PAYMENT_RECORDED",
                        "EDITED",
                        "ISSUED",
                        "CREATED")))
                .andExpect(jsonPath("$.events[*].detail", contains(
                        "to accounts@beta.test",
                        "for an email",
                        "USD 50.00 · Bank Transfer",
                        "USD 50.00 · Bank Transfer",
                        "USD 40.00 · Bank Transfer",
                        null,
                        "INV-0001",
                        null)))
                .andExpect(jsonPath("$.events[*].actor", everyItem(is("Test User"))))
                .andExpect(jsonPath("$.events[*].byYou", everyItem(is(true))));
    }

    @Test
    @DisplayName("each event names the member who did it, the client's views name no one, and a deleted account goes")
    void actors() throws Exception {
        User owner = createUser();
        Business business = businessOf(owner);
        User member = createMember(business, BusinessRole.MEMBER);
        member.setFirstName("Priya");
        member.setLastName("Shah");
        userRepository.save(member);
        String id = invoice(owner);
        String memberSession = bearer(member, business);
        mockMvc.perform(post("/api/v1/document/{id}/payment", id)
                        .header(HttpHeaders.AUTHORIZATION, memberSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amountPaid\": 40, \"paymentMethod\": \"Bank Transfer\"}"))
                .andExpect(status().isCreated());
        String link = JsonPath.read(
                mockMvc.perform(post("/api/v1/document/{id}/share", id)
                                .header(HttpHeaders.AUTHORIZATION, memberSession))
                        .andReturn()
                        .getResponse()
                        .getContentAsString(),
                "$.url");
        mockMvc.perform(get("/api/v1/public/share/{token}", link.substring(link.lastIndexOf('/') + 1)))
                .andExpect(status().isOk());

        activity(id, owner)
                .andExpect(jsonPath(
                        "$.events[*].type", contains("VIEWED", "SHARED", "PAYMENT_RECORDED", "ISSUED", "CREATED")))
                .andExpect(jsonPath(
                        "$.events[*].actor", contains(null, "Priya Shah", "Priya Shah", "Test User", "Test User")))
                .andExpect(jsonPath("$.events[*].byYou", contains(false, false, false, true, true)));
        mockMvc.perform(get("/api/v1/document/{id}/activity", id).header(HttpHeaders.AUTHORIZATION, memberSession))
                .andExpect(jsonPath("$.events[*].byYou", contains(false, true, true, false, false)));

        withAccountPassword(member);
        mockMvc.perform(delete("/api/v1/user/profile")
                        .header(HttpHeaders.AUTHORIZATION, memberSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CONFIRMATION))
                .andExpect(status().isOk());
        activity(id, owner)
                .andExpect(jsonPath("$.events[*].actor", contains(null, null, null, "Test User", "Test User")));
    }

    @Test
    @DisplayName("an email that fails to send is not recorded as sent, though the link made for it stays")
    void failedEmail() throws Exception {
        User owner = createUser();
        String id = invoice(owner);
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage((Session) null));
        doThrow(new MailSendException("down")).when(mailSender).send(any(MimeMessage.class));

        mockMvc.perform(post("/api/v1/document/{id}/email", id).header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isBadGateway());
        activity(id, owner).andExpect(jsonPath("$.events[*].type", contains("SHARED", "ISSUED", "CREATED")));
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
