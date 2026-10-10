package com.invoicespot.document;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.invoicespot.IntegrationTest;
import com.invoicespot.user.User;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class DocumentCustomerLinkIntegrationTest extends IntegrationTest {

    private String createCustomer(User owner, String name) throws Exception {
        String body = mockMvc.perform(post("/api/v1/customer/create")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "%s", "email": "%s@clients.test", "phoneNumber": "+1 555 0100"}
                                """.formatted(name, name.toLowerCase())))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(body, "$.createdCustomer._id");
    }

    private ResultActions createDocument(User owner, String customerJson) throws Exception {
        return mockMvc.perform(post("/api/v1/document/create")
                .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {%s, "documentType": "Invoice", "currency": "USD", "rates": "0",
                         "billingItems": [{"itemName": "Design", "unitPrice": 100, "quantity": 1, "discount": "0"}]}
                        """.formatted(customerJson)));
    }

    @Test
    @DisplayName("a document links to the chosen client and copies its details from the database")
    void linksChosenCustomer() throws Exception {
        User owner = createUser();
        String customerId = createCustomer(owner, "Northwind");

        createDocument(owner, """
                        "customerId": "%s", "customer": {"name": "Someone Else", "email": "other@x.test"}
                        """.formatted(customerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.newDocument.customerId").value(customerId))
                .andExpect(jsonPath("$.newDocument.customer.name").value("Northwind"))
                .andExpect(jsonPath("$.newDocument.customer.email").value("northwind@clients.test"));
    }

    @Test
    @DisplayName("another user's client can't be used, even with its id")
    void refusesForeignCustomer() throws Exception {
        String foreign = createCustomer(createUser(), "Contoso");

        createDocument(createUser(), "\"customerId\": \"%s\"".formatted(foreign))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("That customer does not exist for the currently logged in user"));
    }

    @Test
    @DisplayName("a request that names the client by account number alone still links it")
    void linksByAccountNumber() throws Exception {
        User owner = createUser();
        String customerId = createCustomer(owner, "Fabrikam");
        String accountNo = JsonPath.read(mockMvc.perform(get("/api/v1/customer/{id}", customerId)
                                .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                        .andReturn()
                        .getResponse()
                        .getContentAsString(),
                "$.customer.accountNo");

        createDocument(owner, "\"customer\": {\"name\": \"Fabrikam\", \"accountNo\": \"%s\"}".formatted(accountNo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.newDocument.customerId").value(customerId));
    }

    @Test
    @DisplayName("deleting a client keeps its documents and their copy of its details")
    void deletingCustomerKeepsDocuments() throws Exception {
        User owner = createUser();
        String customerId = createCustomer(owner, "Tailspin");
        String documentId = JsonPath.read(createDocument(owner, "\"customerId\": \"%s\"".formatted(customerId))
                        .andReturn()
                        .getResponse()
                        .getContentAsString(),
                "$.newDocument._id");

        mockMvc.perform(delete("/api/v1/customer/{id}", customerId).header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/document/{id}", documentId).header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.document.customerId").doesNotExist())
                .andExpect(jsonPath("$.document.customer.name").value("Tailspin"));
    }
}
