package com.invoicespot.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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

class StatementIntegrationTest extends IntegrationTest {

    private String customer(User owner, String email) throws Exception {
        String body = mockMvc.perform(post("/api/v1/customer/create")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Beta LLC\", \"email\": \"%s\", \"phoneNumber\": \"+1\"}"
                                .formatted(email)))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String id = JsonPath.read(body, "$.createdCustomer._id");
        mockMvc.perform(post("/api/v1/document/create")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId": "%s", "documentType": "Invoice", "issueDate": "2026-01-15",
                                 "dueDate": "2026-01-15T12:00:00Z",
                                 "billingItems": [{"itemName": "Work", "unitPrice": 120, "quantity": 1}]}
                                """.formatted(id)))
                .andExpect(status().isOk());
        return id;
    }

    private String email() {
        return "beta-" + UUID.randomUUID() + "@clients.test";
    }

    @Test
    @DisplayName("a statement needs a sign-in, and only the client's owner can see it")
    void access() throws Exception {
        User owner = createUser();
        String client = customer(owner, email());

        mockMvc.perform(get("/api/v1/customer/{id}/statement", client)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/customer/{id}/statement", client)
                        .header(HttpHeaders.AUTHORIZATION, bearer(createUser())))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/customer/{id}/statement", UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("the owner gets the statement as an inline PDF named after the client's account")
    void pdf() throws Exception {
        User owner = createUser();
        String client = customer(owner, email());

        byte[] body = mockMvc.perform(get("/api/v1/customer/{id}/statement", client)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string(
                        HttpHeaders.CONTENT_DISPOSITION, startsWith("inline; filename=\"Statement-CUS-")))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        assertThat(new String(body, 0, 5)).isEqualTo("%PDF-");
    }

    @Test
    @DisplayName("emailing sends the statement to the client, with replies going to the owner")
    void emailStatement() throws Exception {
        User owner = createUser();
        String address = email();
        String client = customer(owner, address);
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage((Session) null));

        mockMvc.perform(get("/api/v1/customer/{id}/statement/email", client)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(jsonPath("$.to").value(address))
                .andExpect(jsonPath("$.subject").value("Statement of account from Test User"))
                .andExpect(jsonPath("$.message").value(startsWith("Hello Beta LLC,")));
        mockMvc.perform(post("/api/v1/customer/{id}/statement/email", client)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Statement sent successfully."));

        ArgumentCaptor<MimeMessage> sent = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(sent.capture());
        assertThat(sent.getValue().getAllRecipients()).extracting(Object::toString).containsExactly(address);
        assertThat(sent.getValue().getReplyTo()).extracting(Object::toString).containsExactly(owner.getEmail());
    }

    @Test
    @DisplayName("the demo account can't email a statement")
    void demoCannotEmail() throws Exception {
        User demo = demoUser();
        String client = customer(demo, email());

        mockMvc.perform(post("/api/v1/customer/{id}/statement/email", client)
                        .header(HttpHeaders.AUTHORIZATION, bearer(demo)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("This is a shared demo account, so emailing is turned off"));
        verify(mailSender, never()).send(any(MimeMessage.class));
    }
}
