package com.invoicespot.report;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.invoicespot.IntegrationTest;
import com.invoicespot.user.User;
import com.jayway.jsonpath.JsonPath;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class OverviewIntegrationTest extends IntegrationTest {

    private final LocalDate today = LocalDate.now(ZoneOffset.UTC);
    private User owner;
    private String client;

    @BeforeEach
    void owner() throws Exception {
        owner = createUser();
        String body = mockMvc.perform(post("/api/v1/customer/create")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Acme\", \"email\": \"acme-%s@clients.test\", \"phoneNumber\": \"+1\"}"
                                .formatted(UUID.randomUUID())))
                .andReturn()
                .getResponse()
                .getContentAsString();
        client = JsonPath.read(body, "$.createdCustomer._id");
    }

    private String document(String type, String currency, String amount, int dueInDays) throws Exception {
        String body = mockMvc.perform(post("/api/v1/document/create")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId": "%s", "documentType": "%s", "currency": "%s",
                                 "issueDate": "%s", "dueDate": "%s",
                                 "billingItems": [{"itemName": "Work", "unitPrice": %s, "quantity": 1}]}
                                """.formatted(
                                        client,
                                        type,
                                        currency,
                                        today.plusDays(Math.min(dueInDays, 0)),
                                        today.plusDays(dueInDays).atTime(12, 0).toInstant(ZoneOffset.UTC),
                                        amount)))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(body, "$.newDocument._id");
    }

    private void pay(String document, String amount, String date) throws Exception {
        mockMvc.perform(post("/api/v1/document/{id}/payment", document)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amountPaid\": %s, \"datePaid\": \"%s\", \"paymentMethod\": \"Cash\"}"
                                .formatted(amount, date)))
                .andExpect(status().isCreated());
    }

    private ResultActions overview(String currency) throws Exception {
        var request = get("/api/v1/report/overview").header(HttpHeaders.AUTHORIZATION, bearer(owner));
        return mockMvc.perform(currency == null ? request : request.param("currency", currency));
    }

    @Test
    @DisplayName("income is invoices and receipts, pending what they still await; statuses count every document")
    void figures() throws Exception {
        String partly = document("Invoice", "USD", "100", -1);
        pay(partly, "20", "2025-01-10");
        pay(document("Invoice", "USD", "200", 5), "200", "2025-02-01");
        document("Quotation", "USD", "900", -3);
        document("Receipt", "USD", "60", 0);
        document("Invoice", "USD", "30", -10);

        overview(null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currencies[0]").value("USD"))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.documents").value(5))
                .andExpect(jsonPath("$.clients").value(1))
                .andExpect(jsonPath("$.expectedIncome").value(390.0))
                .andExpect(jsonPath("$.cashReceived").value(280.0))
                .andExpect(jsonPath("$.cashPending").value(110.0))
                .andExpect(jsonPath("$.paid").value(2))
                .andExpect(jsonPath("$.partial").value(1))
                .andExpect(jsonPath("$.unpaid").value(2))
                .andExpect(jsonPath("$.overdue").value(2))
                .andExpect(jsonPath("$.payments[0].datePaid").value("2025-02-01"))
                .andExpect(jsonPath("$.payments[0].amountPaid").value(200.0))
                .andExpect(jsonPath("$.payments[0].paidBy").value("Acme"))
                .andExpect(jsonPath("$.payments[0].document.status").value("Paid"))
                .andExpect(jsonPath("$.payments[1].datePaid").value("2025-01-10"))
                .andExpect(jsonPath("$.payments[1].document._id").value(partly))
                .andExpect(jsonPath("$.payments[1].document.documentNumber").value("INV-0001"))
                .andExpect(jsonPath("$.payments[1].document.status").value("Not Fully Paid"))
                .andExpect(jsonPath("$.payments.length()").value(2));
    }

    @Test
    @DisplayName("money is shown one currency at a time, the default first; counts still cover everything")
    void currencies() throws Exception {
        pay(document("Invoice", "USD", "100", 5), "40", "2025-01-10");
        pay(document("Invoice", "EUR", "50", 5), "10", "2025-03-01");
        document("Quotation", "GBP", "70", 5);

        overview("EUR")
                .andExpect(jsonPath("$.currencies.length()").value(2))
                .andExpect(jsonPath("$.currencies[0]").value("USD"))
                .andExpect(jsonPath("$.currencies[1]").value("EUR"))
                .andExpect(jsonPath("$.currency").value("EUR"))
                .andExpect(jsonPath("$.expectedIncome").value(50.0))
                .andExpect(jsonPath("$.cashReceived").value(10.0))
                .andExpect(jsonPath("$.payments.length()").value(1))
                .andExpect(jsonPath("$.payments[0].amountPaid").value(10.0))
                .andExpect(jsonPath("$.documents").value(3))
                .andExpect(jsonPath("$.partial").value(2));
        overview("JPY")
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.expectedIncome").value(100.0));
    }

    @Test
    @DisplayName("a new account sees zeros in its default currency")
    void empty() throws Exception {
        owner = createUser();
        overview(null)
                .andExpect(jsonPath("$.currencies").isEmpty())
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.documents").value(0))
                .andExpect(jsonPath("$.expectedIncome").value(0.0))
                .andExpect(jsonPath("$.payments").isEmpty());
    }
}
