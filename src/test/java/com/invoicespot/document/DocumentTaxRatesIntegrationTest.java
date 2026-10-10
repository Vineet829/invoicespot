package com.invoicespot.document;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

// Each line is taxed at its own rate, and a document charges the tax on each
// rate's lines, rounded once per rate.
class DocumentTaxRatesIntegrationTest extends IntegrationTest {

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

    private ResultActions create(String extra, String lines) throws Exception {
        return send(post("/api/v1/document/create"), """
                {"customerId": "%s", "documentType": "Invoice", "currency": "USD"%s, "billingItems": [%s]}
                """.formatted(client, extra, lines));
    }

    @Test
    @DisplayName("lines at different rates are taxed per rate, shown on the document and its share page")
    void ratePerLine() throws Exception {
        String id = JsonPath.read(
                body(create("", """
                        {"itemName": "Guides", "unitPrice": 100.05, "quantity": 1, "taxRate": "5"},
                        {"itemName": "Design", "unitPrice": 200.03, "quantity": 1, "taxRate": "18"},
                        {"itemName": "Review", "unitPrice": 50, "quantity": 1, "taxRate": "18.00"},
                        {"itemName": "Guides", "unitPrice": 99.99, "quantity": 1, "taxRate": "5"}
                        """)
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.newDocument.billingItems[*].taxRate", contains(5, 18, 18, 5)))
                        .andExpect(jsonPath("$.newDocument.rates").value(nullValue()))
                        .andExpect(jsonPath("$.newDocument.taxes[*].rate", contains(5, 18)))
                        .andExpect(jsonPath("$.newDocument.taxes[*].taxable", contains(200.04, 250.03)))
                        .andExpect(jsonPath("$.newDocument.taxes[*].tax", contains(10.0, 45.01)))
                        .andExpect(jsonPath("$.newDocument.salesTax").value(55.01))
                        .andExpect(jsonPath("$.newDocument.total").value(505.08))),
                "$.newDocument._id");

        String url = JsonPath.read(body(send(post("/api/v1/document/{id}/share", id), "")), "$.url");
        mockMvc.perform(get("/api/v1/public/share/{token}", url.substring(url.lastIndexOf('/') + 1)))
                .andExpect(jsonPath("$.taxes[*].rate", contains(5, 18)))
                .andExpect(jsonPath("$.taxes[*].tax", contains(10.0, 45.01)));
    }

    @Test
    @DisplayName("a rate sent with the document taxes lines that have none; on a draft it can retax every line")
    void documentRate() throws Exception {
        create(", \"rates\": \"12\"", "{\"itemName\": \"Work\", \"unitPrice\": 100, \"quantity\": 1}")
                .andExpect(jsonPath("$.newDocument.billingItems[0].taxRate").value(12))
                .andExpect(jsonPath("$.newDocument.rates").value("12"))
                .andExpect(jsonPath("$.newDocument.salesTax").value(12.0));
        String draft = JsonPath.read(
                body(create(", \"draft\": true, \"rates\": \"12\"", """
                        {"itemName": "Work", "unitPrice": 100, "quantity": 1},
                        {"itemName": "Books", "unitPrice": 50, "quantity": 1, "taxRate": "0"}
                        """)),
                "$.newDocument._id");

        send(patch("/api/v1/document/{id}", draft), "{\"rates\": \"5\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updatedDocument.billingItems[*].taxRate", contains(5, 5)))
                .andExpect(jsonPath("$.updatedDocument.salesTax").value(7.5));
        send(patch("/api/v1/document/{id}", draft), """
                {"billingItems": [{"itemName": "Work", "unitPrice": 300, "quantity": 1}]}
                """)
                .andExpect(jsonPath("$.updatedDocument.billingItems[0].taxRate").value(5))
                .andExpect(jsonPath("$.updatedDocument.salesTax").value(15.0));
        create("", "{\"itemName\": \"Work\", \"unitPrice\": 100, \"quantity\": 1, \"taxRate\": \"abc\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Tax rate must be a number from 0 to 100, with up to 4 decimals"));
    }

    @Test
    @DisplayName("the rates offered are the business's own, most used first, with its default rate among them")
    void suggestions() throws Exception {
        send(put("/api/v1/settings"), """
                {"defaultCurrency": "USD", "paymentTermsDays": 7, "defaultTaxRate": 18, "taxLabel": "Tax",
                 "timeZone": "UTC"}
                """).andExpect(status().isOk());
        send(post("/api/v1/item"), """
                {"name": "Consulting", "unitPrice": 80, "taxRate": "5", "unit": "hours"}
                """).andExpect(status().isCreated());
        create("", """
                {"itemName": "Guides", "unitPrice": 10, "quantity": 1, "taxRate": "5"},
                {"itemName": "Guides", "unitPrice": 10, "quantity": 1, "taxRate": "5.00"},
                {"itemName": "Layout", "unitPrice": 10, "quantity": 1, "taxRate": "12", "unit": "pages"}
                """).andExpect(status().isOk());

        send(get("/api/v1/document/tax-rates"), "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rates[*].rate", contains(5, 12, 18)))
                .andExpect(jsonPath("$.rates[*].uses", contains(3, 1, 0)))
                .andExpect(jsonPath("$.units", contains("hours", "pages")));
    }
}
