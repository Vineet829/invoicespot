package com.invoicespot.document;

import static org.hamcrest.Matchers.contains;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

class DocumentSearchIntegrationTest extends IntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User owner;
    private String northwind;

    private String customer(String name) throws Exception {
        return customer(name, "%s-%s@clients.test".formatted(name.toLowerCase(), UUID.randomUUID()));
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

    private String document(String customerId, String type, int price, String dueDate) throws Exception {
        String body = mockMvc.perform(post("/api/v1/document/create")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId": "%s", "documentType": "%s", "issueDate": "2026-01-15", "dueDate": "%s",
                                 "billingItems": [{"itemName": "Work", "unitPrice": %d, "quantity": 1}]}
                                """.formatted(customerId, type, dueDate, price)))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(body, "$.newDocument._id");
    }

    @BeforeEach
    void documents() throws Exception {
        owner = createUser();
        northwind = customer("Northwind");
        String paid = document(northwind, "Invoice", 100, "2099-01-01T12:00:00Z");
        mockMvc.perform(post("/api/v1/document/{id}/payment", paid)
                .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amountPaid\": 100}"));
        document(customer("Contoso"), "Invoice", 200, "2026-01-15T12:00:00Z");
        document(customer("Fabrikam"), "Quotation", 300, "2099-01-01T12:00:00Z");
        document(northwind, "Receipt", 50, "2099-01-01T12:00:00Z");
    }

    private ResultActions search(String query) throws Exception {
        return mockMvc.perform(get("/api/v1/document/all?" + query).header(HttpHeaders.AUTHORIZATION, bearer(owner)));
    }

    @Test
    @DisplayName("search matches the number or the client, filters narrow by status, type, client and overdue")
    void filters() throws Exception {
        search("q=north").andExpect(jsonPath("$.totalDocuments").value(2));
        search("q=QUO-0001").andExpect(jsonPath("$.myDocuments[*].customer.name", contains("Fabrikam")));
        search("status=Paid").andExpect(jsonPath("$.totalDocuments").value(2));
        search("type=Quotation").andExpect(jsonPath("$.myDocuments[*].total", contains(300.0)));
        search("customerId=" + northwind).andExpect(jsonPath("$.totalDocuments").value(2));
        search("overdue=true")
                .andExpect(jsonPath("$.myDocuments[*].customer.name", contains("Contoso")))
                .andExpect(jsonPath("$.myDocuments[0].overdue").value(true));
        search("from=2099-01-01").andExpect(jsonPath("$.totalDocuments").value(0));
    }

    @Test
    @DisplayName("% and _ in a search match themselves, not any text")
    void searchWildcards() throws Exception {
        document(customer("Acme_Co"), "Invoice", 10, "2099-01-01T12:00:00Z");
        document(customer("AcmeXCo"), "Invoice", 10, "2099-01-01T12:00:00Z");

        mockMvc.perform(get("/api/v1/document/all")
                        .param("q", "acme_")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(jsonPath("$.myDocuments[*].customer.name", contains("Acme_Co")));
        mockMvc.perform(get("/api/v1/document/all")
                        .param("q", "%")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(jsonPath("$.totalDocuments").value(0));
    }

    @Test
    @DisplayName("results sort by a chosen field in either direction and come in pages")
    void sortingAndPaging() throws Exception {
        search("sort=total&dir=asc").andExpect(jsonPath("$.myDocuments[*].total", contains(50.0, 100.0, 200.0, 300.0)));
        search("sort=client&dir=asc")
                .andExpect(jsonPath(
                        "$.myDocuments[*].customer.name", contains("Contoso", "Fabrikam", "Northwind", "Northwind")));
        search("sort=total&dir=desc&size=2&page=2")
                .andExpect(jsonPath("$.numberOfPages").value(2))
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.myDocuments[*].total", contains(100.0, 50.0)));
    }

    @Test
    @DisplayName("search matches the client's email too")
    void email() throws Exception {
        document(customer("Gamma", "ledger-desk-%s@clients.test".formatted(UUID.randomUUID())), "Invoice", 10,
                "2099-01-01T12:00:00Z");

        search("q=ledger-desk").andExpect(jsonPath("$.myDocuments[*].documentNumber", contains("INV-0003")));
    }

    @Test
    @DisplayName("numbers and clients sort whatever their case; alike ones come in the order made, or its reverse")
    void caseAndTies() throws Exception {
        // Clients whose names differ only in case, made in this order, and a
        // number that differs from another only in case.
        document(customer("Delta"), "Invoice", 10, "2099-01-01T12:00:00Z");
        String lower = document(customer("delta"), "Invoice", 10, "2099-01-01T12:00:00Z");
        jdbcTemplate.update("update documents set document_number = 'inv-0003' where id = ?", UUID.fromString(lower));

        search("sort=client&dir=asc").andExpect(jsonPath("$.myDocuments[*].documentNumber", contains(
                "INV-0002", "INV-0003", "inv-0003", "QUO-0001", "INV-0001", "REC-0001")));
        search("sort=client&dir=desc").andExpect(jsonPath("$.myDocuments[*].documentNumber", contains(
                "REC-0001", "INV-0001", "QUO-0001", "inv-0003", "INV-0003", "INV-0002")));
        search("sort=number&dir=asc").andExpect(jsonPath("$.myDocuments[*].documentNumber", contains(
                "INV-0001", "INV-0002", "INV-0003", "inv-0003", "QUO-0001", "REC-0001")));
        search("sort=number&dir=desc").andExpect(jsonPath("$.myDocuments[*].documentNumber", contains(
                "REC-0001", "QUO-0001", "inv-0003", "INV-0003", "INV-0002", "INV-0001")));
    }

    @Test
    @DisplayName("documents of the same amount come in the order made, or its reverse when sorting down")
    void ties() throws Exception {
        for (int copy = 0; copy < 3; copy++) {
            document(northwind, "Invoice", 777, "2099-01-01T12:00:00Z");
        }

        search("sort=total&dir=asc").andExpect(jsonPath("$.myDocuments[*].documentNumber", contains(
                "REC-0001", "INV-0001", "INV-0002", "QUO-0001", "INV-0003", "INV-0004", "INV-0005")));
        search("sort=total&dir=desc").andExpect(jsonPath("$.myDocuments[*].documentNumber", contains(
                "INV-0005", "INV-0004", "INV-0003", "QUO-0001", "INV-0002", "INV-0001", "REC-0001")));
    }

    @Test
    @DisplayName("a document without a due date comes last when sorting by due date, and first the other way")
    void undated() throws Exception {
        search("sort=dueDate&dir=asc").andExpect(jsonPath(
                "$.myDocuments[*].documentNumber", contains("INV-0002", "INV-0001", "QUO-0001", "REC-0001")));
        search("sort=dueDate&dir=desc").andExpect(jsonPath(
                "$.myDocuments[*].documentNumber", contains("REC-0001", "QUO-0001", "INV-0001", "INV-0002")));
    }

    @Test
    @DisplayName("unknown sort fields, filters and malformed dates are refused")
    void badInput() throws Exception {
        search("sort=password").andExpect(status().isBadRequest());
        search("status=Maybe")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Unknown status filter"));
        search("from=yesterday")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid value for from"));
    }
}
