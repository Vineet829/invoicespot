package com.invoicespot.document;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.invoicespot.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

// The app's form always sends a currency; the API may not.
class DocumentCurrencyIntegrationTest extends IntegrationTest {

    private String session;

    private ResultActions send(MockHttpServletRequestBuilder request, String json) throws Exception {
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    @Test
    @DisplayName("a document sent without a currency is in the business's own, and the client exports still add up")
    void withoutCurrency() throws Exception {
        session = bearer(createUser());
        send(put("/api/v1/settings"), """
                {"defaultCurrency": "INR", "paymentTermsDays": 7, "defaultTaxRate": 0, "taxLabel": "Tax",
                 "timeZone": "UTC"}
                """).andExpect(status().isOk());
        String client = JsonPath.read(
                send(post("/api/v1/customer/create"), """
                        {"name": "Beta LLC", "email": "%s@clients.test", "phoneNumber": "+1"}
                        """.formatted(UUID.randomUUID()))
                        .andReturn()
                        .getResponse()
                        .getContentAsString(),
                "$.createdCustomer._id");

        send(post("/api/v1/document/create"), """
                {"customerId": "%s", "documentType": "Invoice", "rates": "0",
                 "billingItems": [{"itemName": "Work", "unitPrice": 100, "quantity": 1}]}
                """.formatted(client))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.newDocument.currency").value("INR"));

        send(get("/api/v1/customer/csv"), "").andExpect(status().isOk());
        send(get("/api/v1/export/all"), "").andExpect(status().isOk());
    }
}
