package com.invoicespot.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.invoicespot.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

// Tax a client withholds from a payment (TDS) settles the invoice like money
// received, though it isn't cash: it counts toward the balance everywhere and
// is shown apart from the money.
class TaxWithheldIntegrationTest extends IntegrationTest {

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
        send(put("/api/v1/settings"), """
                {"defaultCurrency": "USD", "paymentTermsDays": 15, "defaultTaxRate": 0,
                 "taxLabel": "Tax", "timeZone": "UTC"}
                """).andExpect(status().isOk());
        client = JsonPath.read(
                body(send(post("/api/v1/customer/create"), """
                        {"name": "Beta LLC", "email": "%s@clients.test", "phoneNumber": "+1"}
                        """.formatted(UUID.randomUUID()))),
                "$.createdCustomer._id");
    }

    // An invoice for 1,000, due nine days ago.
    private String invoice() throws Exception {
        String due = LocalDate.now(ZoneOffset.UTC).minusDays(9).atTime(LocalTime.NOON).toInstant(ZoneOffset.UTC)
                .toString();
        return JsonPath.read(
                body(send(post("/api/v1/document/create"), """
                        {"customerId": "%s", "documentType": "Invoice", "currency": "USD", "issueDate": "%s",
                         "dueDate": "%s", "billingItems": [{"itemName": "Work", "unitPrice": 1000, "quantity": 1}]}
                        """.formatted(client, LocalDate.now(ZoneOffset.UTC).minusDays(9), due))
                        .andExpect(status().isOk())),
                "$.newDocument._id");
    }

    private ResultActions pay(String id, String json) throws Exception {
        return send(post("/api/v1/document/{id}/payment", id), json);
    }

    @Test
    @DisplayName("tax withheld settles an invoice with the money received, and is kept apart from it")
    void settles() throws Exception {
        String id = invoice();

        String payment = JsonPath.read(
                body(pay(id, "{\"amountPaid\": 900, \"tdsAmount\": 100, \"paymentMethod\": \"Bank Transfer\"}")
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.document.status").value("Paid"))
                        .andExpect(jsonPath("$.document.totalAmountReceived").value(900.0))
                        .andExpect(jsonPath("$.document.ledger.totalTds").value(100.0))
                        .andExpect(jsonPath("$.document.ledger.balanceDue").value(0))
                        .andExpect(jsonPath("$.document.paymentRecords[0].tdsAmount").value(100.0))),
                "$.document.paymentRecords[0]._id");
        send(get("/api/v1/document/{id}/activity", id), "")
                .andExpect(jsonPath("$.events[0].detail").value("USD 900.00 · Bank Transfer · tax withheld USD 100.00"));

        String receipt = PdfText.of(mockMvc.perform(get("/api/v1/document/{id}/payment/{payment}/receipt", id, payment)
                        .header(HttpHeaders.AUTHORIZATION, session))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray())
                .replaceAll("\\s+", " ");
        assertThat(receipt).contains(
                "USD 900.00",
                "Tax withheld by you (TDS) USD 100.00",
                "Settles USD 1,000.00",
                "Paid to date USD 1,000.00",
                "Balance after this payment USD 0.00");
    }

    @Test
    @DisplayName("money and tax withheld together can't settle more than is owed, and each is checked")
    void limits() throws Exception {
        String id = invoice();

        pay(id, "{\"amountPaid\": 950, \"tdsAmount\": 100}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("That is more than the 1000.00 still owed"));
        String payment = JsonPath.read(
                body(pay(id, "{\"tdsAmount\": 50}")
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.document.status").value("Not Fully Paid"))
                        .andExpect(jsonPath("$.document.paymentRecords[0].amountPaid").value(0.0))),
                "$.document.paymentRecords[0]._id");
        send(patch("/api/v1/document/{id}/payment/{payment}", id, payment), "{\"tdsAmount\": 1000.01}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("That is more than the 950.00 still owed"));
        pay(id, "{\"tdsAmount\": -1, \"amountPaid\": 10}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Enter an amount greater than zero"));
        pay(id, "{\"tdsAmount\": 1.005}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Tax withheld can have at most 2 decimals"));
        pay(id, "{\"tdsAmount\": 0, \"amountPaid\": 0}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Enter an amount greater than zero"));
    }

    @Test
    @DisplayName("balances, reports, the overview, the statement and the CSVs count tax withheld as settled")
    void everywhere() throws Exception {
        String id = invoice();
        pay(id, "{\"amountPaid\": 600, \"tdsAmount\": 100}").andExpect(status().isCreated());

        send(get("/api/v1/report/overview"), "")
                .andExpect(jsonPath("$.cashReceived").value(600.0))
                .andExpect(jsonPath("$.taxWithheld").value(100.0))
                .andExpect(jsonPath("$.cashPending").value(300.0));
        send(get("/api/v1/report").param("period", "12m"), "")
                .andExpect(jsonPath("$.currencies[0].outstanding").value(300.0))
                .andExpect(jsonPath("$.currencies[0].clients[0].outstanding").value(300.0));
        send(get("/api/v1/customer/{id}/summary", client), "")
                .andExpect(jsonPath("$.totals[0].outstanding").value(300.0))
                .andExpect(jsonPath("$.totals[0].overdue").value(300.0));
        send(get("/api/v1/customer/all"), "")
                .andExpect(jsonPath("$.myCustomers[0].balances[0].outstanding").value(300.0));
        String statement = PdfText.of(mockMvc.perform(get("/api/v1/customer/{id}/statement", client)
                        .header(HttpHeaders.AUTHORIZATION, session))
                .andReturn()
                .getResponse()
                .getContentAsByteArray())
                .replaceAll("\\s+", " ");
        assertThat(statement).contains("Tax withheld by you on INV-0001", "USD 300.00");
        String pdf = PdfText.of(mockMvc.perform(get("/api/v1/document/{id}/pdf", id)
                        .header(HttpHeaders.AUTHORIZATION, session))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray())
                .replaceAll("\\s+", " ");
        assertThat(pdf).contains("Amount paid USD 600.00", "Tax withheld USD 100.00", "Balance due USD 300.00");
        String link = JsonPath.read(body(send(post("/api/v1/document/{id}/share", id), "")), "$.url");
        mockMvc.perform(get("/api/v1/public/share/{token}", link.substring(link.lastIndexOf('/') + 1)))
                .andExpect(jsonPath("$.received").value(600.0))
                .andExpect(jsonPath("$.taxWithheld").value(100.0))
                .andExpect(jsonPath("$.balance").value(300.0));
        assertThat(body(send(get("/api/v1/document/csv"), ""))).contains(",600.00,300.00,");
        assertThat(body(send(get("/api/v1/document/csv"), ""))).containsPattern(",Yes,100.00,,,,0.00,0.00\\r?\\n");
        assertThat(body(send(get("/api/v1/document/payments/csv"), ""))).containsPattern(",100.00,Payment\\r?\\n");
    }
}
