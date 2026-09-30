package com.invoicespot.notification;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.invoicespot.IntegrationTest;
import com.invoicespot.user.User;
import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class NotificationIntegrationTest extends IntegrationTest {

    private User owner;
    private String quotation;

    // A quotation shared by link, viewed twice by the client and accepted.
    @BeforeEach
    void sharedQuotation() throws Exception {
        owner = createUser();
        String customer = mockMvc.perform(post("/api/v1/customer/create")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Beta LLC\", \"email\": \"%s\", \"phoneNumber\": \"+1\"}"
                                .formatted("beta-" + UUID.randomUUID() + "@clients.test")))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String created = mockMvc.perform(post("/api/v1/document/create")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId": "%s", "documentType": "Quotation",
                                 "billingItems": [{"itemName": "Work", "unitPrice": 100, "quantity": 1}]}
                                """.formatted((String) JsonPath.read(customer, "$.createdCustomer._id"))))
                .andReturn()
                .getResponse()
                .getContentAsString();
        quotation = JsonPath.read(created, "$.newDocument._id");
        String url = JsonPath.read(
                mockMvc.perform(post("/api/v1/document/{id}/share", quotation)
                                .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                        .andReturn()
                        .getResponse()
                        .getContentAsString(),
                "$.url");
        String token = url.substring(url.lastIndexOf('/') + 1);
        mockMvc.perform(get("/api/v1/public/share/{token}", token));
        mockMvc.perform(get("/api/v1/public/share/{token}", token));
        mockMvc.perform(post("/api/v1/public/share/{token}/accept", token));
    }

    private ResultActions latest(User user) throws Exception {
        return mockMvc.perform(get("/api/v1/notification").header(HttpHeaders.AUTHORIZATION, bearer(user)));
    }

    private ResultActions read(User user, String body) throws Exception {
        return mockMvc.perform(post("/api/v1/notification/read")
                .header(HttpHeaders.AUTHORIZATION, bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    @Test
    @DisplayName("a client viewing a shared document, once an hour, and accepting it tell the owner")
    void clientActivity() throws Exception {
        latest(owner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unread").value(2))
                .andExpect(jsonPath("$.notifications[*].type", contains("QUOTATION_ACCEPTED", "DOCUMENT_VIEWED")))
                .andExpect(jsonPath("$.notifications[*].message", contains(
                        "Beta LLC accepted Quotation QUO-0001", "Beta LLC viewed Quotation QUO-0001")))
                .andExpect(jsonPath("$.notifications[0].documentId").value(quotation))
                .andExpect(jsonPath("$.notifications[0].read").value(false));
        latest(createUser()).andExpect(jsonPath("$.notifications", hasSize(0)));
    }

    @Test
    @DisplayName("notifications are read one at a time or all at once, and only by their owner")
    void markRead() throws Exception {
        String first = JsonPath.read(
                latest(owner).andReturn().getResponse().getContentAsString(), "$.notifications[0]._id");

        read(createUser(), "{\"id\": \"%s\"}".formatted(first)).andExpect(status().isNotFound());
        read(owner, "{\"id\": \"%s\"}".formatted(first)).andExpect(status().isOk());
        latest(owner)
                .andExpect(jsonPath("$.unread").value(1))
                .andExpect(jsonPath("$.notifications[0].read").value(true));
        read(owner, "{}").andExpect(status().isOk());
        latest(owner).andExpect(jsonPath("$.unread").value(0));
        read(owner, "{\"id\": \"%s\"}".formatted(UUID.randomUUID())).andExpect(status().isNotFound());
    }
}
