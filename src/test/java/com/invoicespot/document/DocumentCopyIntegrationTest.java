package com.invoicespot.document;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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

class DocumentCopyIntegrationTest extends IntegrationTest {

    private User owner;
    private String client;

    @BeforeEach
    void client() throws Exception {
        owner = createUser();
        String body = mockMvc.perform(post("/api/v1/customer/create")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Beta LLC\", \"email\": \"%s\", \"phoneNumber\": \"+1\"}"
                                .formatted("beta-" + UUID.randomUUID() + "@clients.test")))
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
                                {"customerId": "%s", "documentType": "%s", "currency": "EUR", "rates": "10",
                                 "additionalInfo": "Thanks", "dueDate": "2026-01-15T12:00:00Z",
                                 "billingItems": [{"itemName": "Design", "unitPrice": 200, "quantity": 2,
                                                   "discount": "5"}]}
                                """.formatted(client, type)))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(body, "$.newDocument._id");
    }

    private ResultActions action(String id, String action, User user) throws Exception {
        return mockMvc.perform(
                post("/api/v1/document/{id}/" + action, id).header(HttpHeaders.AUTHORIZATION, bearer(user)));
    }

    private ResultActions read(String id) throws Exception {
        return mockMvc.perform(get("/api/v1/document/{id}", id).header(HttpHeaders.AUTHORIZATION, bearer(owner)));
    }

    private ResultActions activity(String id) throws Exception {
        return mockMvc.perform(
                get("/api/v1/document/{id}/activity", id).header(HttpHeaders.AUTHORIZATION, bearer(owner)));
    }

    @Test
    @DisplayName("a copy keeps the client and lines, gets a new number and no payments, and links back")
    void duplicate() throws Exception {
        String original = document("Invoice");
        mockMvc.perform(post("/api/v1/document/{id}/payment", original)
                .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amountPaid\": 100}"));

        String body = action(original, "duplicate", owner)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.newDocument.documentNumber").value("INV-0002"))
                .andExpect(jsonPath("$.newDocument.customerId").value(client))
                .andExpect(jsonPath("$.newDocument.billingItems[0].itemName").value("Design"))
                .andExpect(jsonPath("$.newDocument.total").value(418.0))
                .andExpect(jsonPath("$.newDocument.currency").value("EUR"))
                .andExpect(jsonPath("$.newDocument.additionalInfo").value("Thanks"))
                .andExpect(jsonPath("$.newDocument.totalAmountReceived").value(0.0))
                .andExpect(jsonPath("$.newDocument.status").value("Not Paid"))
                .andExpect(jsonPath("$.newDocument.overdue").value(false))
                .andExpect(jsonPath("$.newDocument.paymentRecords", hasSize(0)))
                .andExpect(jsonPath("$.newDocument.source.documentNumber").value("INV-0001"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String copy = JsonPath.read(body, "$.newDocument._id");

        read(original).andExpect(jsonPath("$.document.copies[*].documentNumber", contains("INV-0002")));
        activity(copy).andExpect(jsonPath("$.events[0].type").value("COPIED_FROM"))
                .andExpect(jsonPath("$.events[0].detail").value("INV-0001"));
        activity(original).andExpect(jsonPath("$.events[0].type").value("COPIED_TO"))
                .andExpect(jsonPath("$.events[0].detail").value("INV-0002"));
    }

    @Test
    @DisplayName("a quotation becomes an invoice once, and each names the other")
    void convert() throws Exception {
        String quotation = document("Quotation");

        String body = action(quotation, "convert", owner)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.newDocument.documentType").value("Invoice"))
                .andExpect(jsonPath("$.newDocument.documentNumber").value("INV-0001"))
                .andExpect(jsonPath("$.newDocument.source.documentNumber").value("QUO-0001"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String invoice = JsonPath.read(body, "$.newDocument._id");

        action(quotation, "convert", owner)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("This quotation is already invoice INV-0001"));
        read(quotation).andExpect(jsonPath("$.document.copies[0].documentType").value("Invoice"));
        activity(invoice).andExpect(jsonPath("$.events[0].type").value("CONVERTED_FROM"));
        activity(quotation).andExpect(jsonPath("$.events[0].type").value("CONVERTED_TO"))
                .andExpect(jsonPath("$.events[0].detail").value("INV-0001"));
    }

    @Test
    @DisplayName("only quotations convert, only the owner can copy, and a deleted original leaves its copy")
    void rules() throws Exception {
        String invoice = document("Invoice");

        action(invoice, "convert", owner)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Only a quotation can become an invoice"));
        action(invoice, "duplicate", createUser()).andExpect(status().isForbidden());
        action(UUID.randomUUID().toString(), "duplicate", owner).andExpect(status().isNotFound());

        String copy = JsonPath.read(
                action(invoice, "duplicate", owner).andReturn().getResponse().getContentAsString(),
                "$.newDocument._id");
        mockMvc.perform(delete("/api/v1/document/{id}", invoice).header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk());
        read(copy).andExpect(status().isOk()).andExpect(jsonPath("$.document.source").doesNotExist());
    }
}
