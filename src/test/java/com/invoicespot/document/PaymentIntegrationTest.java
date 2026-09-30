package com.invoicespot.document;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.invoicespot.IntegrationTest;
import com.invoicespot.user.User;
import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class PaymentIntegrationTest extends IntegrationTest {

    private String createDocument(User owner, String type) throws Exception {
        String customer = mockMvc.perform(post("/api/v1/customer/create")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Payer\", \"email\": \"payer-%s@clients.test\", \"phoneNumber\": \"+1 555 0100\"}"
                                .formatted(UUID.randomUUID())))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String body = mockMvc.perform(post("/api/v1/document/create")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId": "%s", "documentType": "%s", "currency": "USD", "rates": "0",
                                 "billingItems": [{"itemName": "Design", "unitPrice": 100, "quantity": 1, "discount": "0"}]}
                                """.formatted(JsonPath.read(customer, "$.createdCustomer._id"), type)))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(body, "$.newDocument._id");
    }

    private ResultActions pay(User owner, String documentId, String json) throws Exception {
        return mockMvc.perform(post("/api/v1/document/{id}/payment", documentId)
                .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    @Test
    @DisplayName("recording, editing and deleting payments keeps the amount received and status right")
    void paymentLifecycle() throws Exception {
        User owner = createUser();
        String documentId = createDocument(owner, "Invoice");

        String body = pay(owner, documentId, "{\"datePaid\": \"2026-09-30\", \"amountPaid\": 40, \"paymentMethod\": \"Bank Transfer\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.document.status").value("Not Fully Paid"))
                .andExpect(jsonPath("$.document.totalAmountReceived").value(40))
                .andExpect(jsonPath("$.document.paymentRecords[0].datePaid").value("2026-09-30"))
                .andExpect(jsonPath("$.document.paymentRecords[0].paidBy").value("Payer"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String paymentId = JsonPath.read(body, "$.document.paymentRecords[0]._id");

        mockMvc.perform(patch("/api/v1/document/{id}/payment/{paymentId}", documentId, paymentId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amountPaid\": 100}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.document.status").value("Paid"))
                .andExpect(jsonPath("$.document.totalAmountReceived").value(100));

        mockMvc.perform(delete("/api/v1/document/{id}/payment/{paymentId}", documentId, paymentId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.document.status").value("Not Paid"))
                .andExpect(jsonPath("$.document.paymentRecords").isEmpty());
    }

    @Test
    @DisplayName("a payment can't exceed what is still owed, on entry or on edit")
    void refusesOverpayment() throws Exception {
        User owner = createUser();
        String documentId = createDocument(owner, "Invoice");
        String body = pay(owner, documentId, "{\"amountPaid\": 60}").andReturn().getResponse().getContentAsString();
        String paymentId = JsonPath.read(body, "$.document.paymentRecords[0]._id");

        pay(owner, documentId, "{\"amountPaid\": 40.01}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("That is more than the 40.00 still owed"));
        mockMvc.perform(patch("/api/v1/document/{id}/payment/{paymentId}", documentId, paymentId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amountPaid\": 100.5}"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("quotations and receipts take no payments")
    void refusesQuotationAndReceipt() throws Exception {
        User owner = createUser();

        pay(owner, createDocument(owner, "Quotation"), "{\"amountPaid\": 10}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("A quotation can't take payments"));
        pay(owner, createDocument(owner, "Receipt"), "{\"amountPaid\": 10}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("A receipt is already paid in full"));
    }

    @Test
    @DisplayName("bad amounts and dates are refused, and a timestamp counts as its UTC date")
    void validatesInput() throws Exception {
        User owner = createUser();
        String documentId = createDocument(owner, "Invoice");

        pay(owner, documentId, "{\"amountPaid\": 0}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Enter an amount greater than zero"));
        pay(owner, documentId, "{\"amountPaid\": 1.005}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("An amount can have at most 2 decimals"));
        pay(owner, documentId, "{\"amountPaid\": 5, \"datePaid\": \"30/09/2026\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Enter the payment date as YYYY-MM-DD"));
        pay(owner, documentId, "{\"amountPaid\": 5, \"datePaid\": \"2026-09-30T06:30:00.000Z\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.document.paymentRecords[0].datePaid").value("2026-09-30"));
    }

    @Test
    @DisplayName("another user can't touch the payments, and unknown payments are not found")
    void ownership() throws Exception {
        User owner = createUser();
        String documentId = createDocument(owner, "Invoice");
        String body = pay(owner, documentId, "{\"amountPaid\": 10}").andReturn().getResponse().getContentAsString();
        String paymentId = JsonPath.read(body, "$.document.paymentRecords[0]._id");

        mockMvc.perform(delete("/api/v1/document/{id}/payment/{paymentId}", documentId, paymentId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(createUser())))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/document/{id}/payment/{paymentId}", documentId, UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("That payment does not exist"));
    }
}
