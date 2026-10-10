package com.invoicespot.document;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.test.context.TestPropertySource;

// A server started without its encryption key still shares documents, but
// keeps only the link's hash, so each link is shown once, when it is made.
@TestPropertySource(properties = "app.encryption.key=")
class ShareWithoutKeyIntegrationTest extends IntegrationTest {

    @Test
    @DisplayName("a link is shown when it is made and works, but can't be shown again")
    void shownOnce() throws Exception {
        User owner = createUser();
        String customer = mockMvc.perform(post("/api/v1/customer/create")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Beta LLC\", \"email\": \"%s\", \"phoneNumber\": \"+1\"}"
                                .formatted("beta-" + UUID.randomUUID() + "@clients.test")))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String document = JsonPath.read(
                mockMvc.perform(post("/api/v1/document/create")
                                .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"customerId": "%s", "documentType": "Invoice", "currency": "USD",
                                         "billingItems": [{"itemName": "Design", "unitPrice": 100, "quantity": 2}]}
                                        """.formatted((String) JsonPath.read(customer, "$.createdCustomer._id"))))
                        .andReturn()
                        .getResponse()
                        .getContentAsString(),
                "$.newDocument._id");

        String url = JsonPath.read(
                mockMvc.perform(post("/api/v1/document/{id}/share", document)
                                .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.url", notNullValue()))
                        .andExpect(jsonPath("$.kept").value(false))
                        .andReturn()
                        .getResponse()
                        .getContentAsString(),
                "$.url");

        mockMvc.perform(get("/api/v1/public/share/{token}", url.substring(url.lastIndexOf('/') + 1)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/document/{id}/share", document).header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.url").doesNotExist());
    }
}
