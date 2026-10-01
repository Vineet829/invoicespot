package com.invoicespot.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.invoicespot.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

// A line counts in whole or fractional quantities, and can say what it
// counts, such as hours.
class DocumentLinesIntegrationTest extends IntegrationTest {

    private String session;
    private String client;

    private ResultActions send(MockHttpServletRequestBuilder request, String json) throws Exception {
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private static String body(ResultActions result) throws Exception {
        return result.andReturn().getResponse().getContentAsString();
    }

    @BeforeEach
    void setUp() throws Exception {
        session = bearer(createUser());
        client = JsonPath.read(
                body(send(post("/api/v1/customer/create"), """
                        {"name": "Beta LLC", "email": "%s@clients.test", "phoneNumber": "+1"}
                        """.formatted(UUID.randomUUID()))),
                "$.createdCustomer._id");
    }

    private ResultActions create(String lines) throws Exception {
        return send(post("/api/v1/document/create"), """
                {"customerId": "%s", "documentType": "Invoice", "currency": "USD", "billingItems": [%s]}
                """.formatted(client, lines));
    }

    @Test
    @DisplayName("a quantity can be a fraction and a line can name its unit, both shown as typed")
    void fractionsAndUnits() throws Exception {
        String id = JsonPath.read(
                body(create("""
                        {"itemName": "Consulting", "unitPrice": 1000, "quantity": 1.5, "unit": " hours "},
                        {"itemName": "Licences", "unitPrice": 33.33, "quantity": 3}
                        """)
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.newDocument.billingItems[0].quantity").value(1.5))
                        .andExpect(jsonPath("$.newDocument.billingItems[0].unit").value("hours"))
                        .andExpect(jsonPath("$.newDocument.billingItems[0].lineTotal").value(1500.0))
                        .andExpect(jsonPath("$.newDocument.billingItems[1].quantity").value(3))
                        .andExpect(jsonPath("$.newDocument.billingItems[1].unit").doesNotExist())
                        .andExpect(jsonPath("$.newDocument.subTotal").value(1599.99))),
                "$.newDocument._id");

        String text = PdfText.of(mockMvc.perform(get("/api/v1/document/{id}/pdf", id)
                        .header(HttpHeaders.AUTHORIZATION, session))
                .andReturn()
                .getResponse()
                .getContentAsByteArray());
        assertThat(text).contains("Consulting 1.5 hours", "Licences 3 ");
        String url = JsonPath.read(body(send(post("/api/v1/document/{id}/share", id), "")), "$.url");
        mockMvc.perform(get("/api/v1/public/share/{token}", url.substring(url.lastIndexOf('/') + 1)))
                .andExpect(jsonPath("$.items[0].quantity").value(1.5))
                .andExpect(jsonPath("$.items[0].unit").value("hours"));
    }

    @Test
    @DisplayName("a quantity below 0, above 1,000,000 or with more than 4 decimals is refused, as is a long unit")
    void limits() throws Exception {
        create("{\"itemName\": \"Work\", \"unitPrice\": 1, \"quantity\": -1}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Quantities can't be negative"));
        create("{\"itemName\": \"Work\", \"unitPrice\": 1, \"quantity\": 1000000.5}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Quantities can be at most 1,000,000"));
        create("{\"itemName\": \"Work\", \"unitPrice\": 1, \"quantity\": 1.23456}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Quantities can have at most 4 decimals"));
        create("{\"itemName\": \"Work\", \"unitPrice\": 1, \"quantity\": 1, \"unit\": \"%s\"}".formatted("u".repeat(21)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Units can be at most 20 characters"));
    }
}
