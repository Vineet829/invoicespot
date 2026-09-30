package com.invoicespot.customer;

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

class CustomerAccountIntegrationTest extends IntegrationTest {

    private static final String PAST = "2026-01-15T12:00:00Z";
    private static final String FUTURE = "2099-01-01T12:00:00Z";

    private User owner;

    @BeforeEach
    void owner() {
        owner = createUser();
    }

    private String customer(String name) throws Exception {
        String body = mockMvc.perform(post("/api/v1/customer/create")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"%s\", \"email\": \"%s@clients.test\", \"phoneNumber\": \"+1\"}"
                                .formatted(name, UUID.randomUUID())))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(body, "$.createdCustomer._id");
    }

    private String document(String customerId, String type, int price, String currency, String dueDate)
            throws Exception {
        String body = mockMvc.perform(post("/api/v1/document/create")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId": "%s", "documentType": "%s", "currency": "%s", "dueDate": "%s",
                                 "billingItems": [{"itemName": "Work", "unitPrice": %d, "quantity": 1}]}
                                """.formatted(customerId, type, currency, dueDate, price)))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(body, "$.newDocument._id");
    }

    private void pay(String documentId, int amount) throws Exception {
        mockMvc.perform(post("/api/v1/document/{id}/payment", documentId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amountPaid\": %d}".formatted(amount)))
                .andExpect(status().isCreated());
    }

    private ResultActions summary(String customerId, User user) throws Exception {
        return mockMvc.perform(
                get("/api/v1/customer/{id}/summary", customerId).header(HttpHeaders.AUTHORIZATION, bearer(user)));
    }

    @Test
    @DisplayName("a client's summary totals what was billed, paid, still owed and overdue, per currency")
    void totals() throws Exception {
        String client = customer("Northwind");
        pay(document(client, "Invoice", 100, "USD", FUTURE), 40);
        document(client, "Invoice", 30, "USD", PAST);
        pay(document(client, "Invoice", 20, "USD", PAST), 20);
        document(client, "Receipt", 50, "USD", FUTURE);
        document(client, "Quotation", 900, "USD", PAST);
        document(client, "Invoice", 70, "EUR", PAST);

        summary(client, owner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totals", hasSize(2)))
                .andExpect(jsonPath("$.totals[0].currency").value("USD"))
                .andExpect(jsonPath("$.totals[0].documents").value(5))
                .andExpect(jsonPath("$.totals[0].billed").value(200.0))
                .andExpect(jsonPath("$.totals[0].paid").value(110.0))
                .andExpect(jsonPath("$.totals[0].outstanding").value(90.0))
                .andExpect(jsonPath("$.totals[0].overdue").value(30.0))
                .andExpect(jsonPath("$.totals[1].currency").value("EUR"))
                .andExpect(jsonPath("$.totals[1].outstanding").value(70.0))
                .andExpect(jsonPath("$.totals[1].overdue").value(70.0));
    }

    @Test
    @DisplayName("a client without documents has no totals, and only the client's owner sees them")
    void emptyAndOwnership() throws Exception {
        String client = customer("Quiet");

        summary(client, owner).andExpect(jsonPath("$.totals", hasSize(0)));
        summary(client, createUser()).andExpect(status().isForbidden());
        summary(UUID.randomUUID().toString(), owner).andExpect(status().isNotFound());
    }
}
