package com.invoicespot.customer;

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

class CustomerListIntegrationTest extends IntegrationTest {

    private User owner;

    @BeforeEach
    void owner() {
        owner = createUser();
    }

    private String customer(String name, String email) throws Exception {
        String body = mockMvc.perform(post("/api/v1/customer/create")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"%s\", \"email\": \"%s\", \"phoneNumber\": \"+1\"}"
                                .formatted(name, email)))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(body, "$.createdCustomer._id");
    }

    private String document(String customerId, String type, int price, String currency) throws Exception {
        String body = mockMvc.perform(post("/api/v1/document/create")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId": "%s", "documentType": "%s", "currency": "%s",
                                 "dueDate": "2099-01-01T12:00:00Z",
                                 "billingItems": [{"itemName": "Work", "unitPrice": %d, "quantity": 1}]}
                                """.formatted(customerId, type, currency, price)))
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

    private ResultActions list(String query) throws Exception {
        return mockMvc.perform(get("/api/v1/customer/all?" + query).header(HttpHeaders.AUTHORIZATION, bearer(owner)));
    }

    private String email() {
        return "client-" + UUID.randomUUID() + "@clients.test";
    }

    @Test
    @DisplayName("each client shows its number of documents and what its invoices still owe, per currency")
    void documentsAndBalances() throws Exception {
        String northwind = customer("Northwind", email());
        pay(document(northwind, "Invoice", 100, "USD"), 40);
        document(northwind, "Invoice", 50, "EUR");
        pay(document(northwind, "Invoice", 70, "USD"), 70);
        document(northwind, "Quotation", 900, "USD");
        document(northwind, "Receipt", 30, "USD");
        customer("Contoso", email());

        list("q=northwind")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.myCustomers[0].documentCount").value(5))
                .andExpect(jsonPath("$.myCustomers[0].balances[*].currency", contains("USD", "EUR")))
                .andExpect(jsonPath("$.myCustomers[0].balances[*].outstanding", contains(60.0, 50.0)));
        list("q=contoso")
                .andExpect(jsonPath("$.myCustomers[0].documentCount").value(0))
                .andExpect(jsonPath("$.myCustomers[0].balances", hasSize(0)));
        mockMvc.perform(get("/api/v1/customer/{id}", northwind).header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(jsonPath("$.customer.documentCount").doesNotHaveJsonPath());
    }

    @Test
    @DisplayName("a client's number of documents counts issued ones only, as the client's own page does")
    void issuedDocumentsOnly() throws Exception {
        String fabrikam = customer("Fabrikam", email());
        document(fabrikam, "Invoice", 100, "USD");
        String voided = document(fabrikam, "Invoice", 200, "USD");
        mockMvc.perform(post("/api/v1/document/{id}/void", voided)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\": \"Sent twice\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/document/create")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId": "%s", "documentType": "Invoice", "currency": "USD", "draft": true,
                                 "billingItems": [{"itemName": "Work", "unitPrice": 300, "quantity": 1}]}
                                """.formatted(fabrikam)))
                .andExpect(status().isOk());

        list("q=fabrikam").andExpect(jsonPath("$.myCustomers[0].documentCount").value(1));
    }

    @Test
    @DisplayName("search matches the name, email or account number, and % and _ match themselves")
    void search() throws Exception {
        customer("Acme_Co", email());
        customer("AcmeXCo", email());
        customer("Globex", "billing@globex.test");

        list("q=GLOBEX.TEST").andExpect(jsonPath("$.myCustomers[*].name", contains("Globex")));
        list("q=acme_").andExpect(jsonPath("$.myCustomers[*].name", contains("Acme_Co")));
        list("q=CUS-").andExpect(jsonPath("$.totalCustomers").value(3));
        mockMvc.perform(get("/api/v1/customer/all").param("q", "%").header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(jsonPath("$.totalCustomers").value(0));
    }

    @Test
    @DisplayName("clients sort by name, documents or the balance in the business currency, and come in pages")
    void sortingAndPaging() throws Exception {
        String busy = customer("Busy", email());
        document(busy, "Quotation", 10, "USD");
        document(busy, "Quotation", 10, "USD");
        document(busy, "Invoice", 20, "USD");
        String owing = customer("Owing", email());
        document(owing, "Invoice", 500, "USD");
        String foreign = customer("Foreign", email());
        document(foreign, "Invoice", 9000, "EUR");

        list("sort=name&dir=asc").andExpect(jsonPath("$.myCustomers[*].name", contains("Busy", "Foreign", "Owing")));
        list("sort=documents&dir=desc")
                .andExpect(jsonPath("$.myCustomers[*].name", contains("Busy", "Foreign", "Owing")));
        list("sort=balance&dir=desc")
                .andExpect(jsonPath("$.myCustomers[*].name", contains("Owing", "Busy", "Foreign")));
        list("sort=name&dir=desc&size=2&page=2")
                .andExpect(jsonPath("$.totalCustomers").value(3))
                .andExpect(jsonPath("$.numberOfPages").value(2))
                .andExpect(jsonPath("$.myCustomers[*].name", contains("Busy")));
    }

    @Test
    @DisplayName("another user's clients are never listed, and unknown sort fields are refused")
    void ownershipAndBadInput() throws Exception {
        customer("Private", email());

        mockMvc.perform(get("/api/v1/customer/all").header(HttpHeaders.AUTHORIZATION, bearer(createUser())))
                .andExpect(jsonPath("$.totalCustomers").value(0));
        list("sort=email")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("Clients can be sorted by name, createdAt, documents or balance"));
    }
}
