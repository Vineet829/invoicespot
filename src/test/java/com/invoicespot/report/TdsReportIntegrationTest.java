package com.invoicespot.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.invoicespot.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

// The tax clients withheld from their payments, by the date paid: each
// payment, and the totals per client and per month, in each currency.
class TdsReportIntegrationTest extends IntegrationTest {

    private final LocalDate today = LocalDate.now(ZoneOffset.UTC);
    private final LocalDate lastMonth = today.withDayOfMonth(1).minusMonths(1);
    private String session;

    private ResultActions send(String session, MockHttpServletRequestBuilder request, String json) throws Exception {
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private String business() throws Exception {
        String owner = bearer(createUser());
        send(owner, put("/api/v1/settings"), """
                {"defaultCurrency": "USD", "paymentTermsDays": 15, "defaultTaxRate": 0,
                 "taxLabel": "Tax", "timeZone": "UTC"}
                """).andExpect(status().isOk());
        return owner;
    }

    private String client(String session, String name) throws Exception {
        return JsonPath.read(
                send(session, post("/api/v1/customer/create"), """
                                {"name": "%s", "email": "%s@clients.test", "phoneNumber": "+1"}
                                """.formatted(name, UUID.randomUUID()))
                        .andReturn()
                        .getResponse()
                        .getContentAsString(),
                "$.createdCustomer._id");
    }

    private String invoice(String session, String client, String currency, String amount) throws Exception {
        return JsonPath.read(
                send(session, post("/api/v1/document/create"), """
                                {"customerId": "%s", "documentType": "Invoice", "currency": "%s",
                                 "billingItems": [{"itemName": "Work", "unitPrice": %s, "quantity": 1}]}
                                """.formatted(client, currency, amount))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString(),
                "$.newDocument._id");
    }

    private void pay(String session, String invoice, String cash, String withheld, LocalDate date) throws Exception {
        send(session, post("/api/v1/document/{id}/payment", invoice), """
                        {"amountPaid": %s, "tdsAmount": %s, "datePaid": "%s", "paymentMethod": "Bank Transfer"}
                        """.formatted(cash, withheld, date))
                .andExpect(status().isCreated());
    }

    private ResultActions report(String period) throws Exception {
        return mockMvc.perform(get("/api/v1/report/tds")
                .param("period", period)
                .header(HttpHeaders.AUTHORIZATION, session));
    }

    @BeforeEach
    void setUp() throws Exception {
        session = business();
        String acme = client(session, "Acme");
        String beta = client(session, "Beta LLC");
        pay(session, invoice(session, acme, "USD", "1000"), "900", "100", lastMonth); // INV-0001
        pay(session, invoice(session, beta, "USD", "2000"), "1800", "200", today); // INV-0002
        pay(session, invoice(session, beta, "USD", "400"), "400", "0", today); // INV-0003, nothing withheld
        pay(session, invoice(session, acme, "USD", "500"), "0", "50", today); // INV-0004, only tax withheld
        pay(session, invoice(session, acme, "EUR", "300"), "270", "30", today); // INV-0005
        pay(session, invoice(session, acme, "USD", "700"), "690", "10", lastMonth.minusMonths(3)); // INV-0006

        String other = business();
        pay(other, invoice(other, client(other, "Acme"), "USD", "100"), "90", "10", today);
    }

    @Test
    @DisplayName("each payment tax was withheld from, with totals per client and per month, in each currency")
    void totals() throws Exception {
        report("3m")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period").value("3m"))
                .andExpect(jsonPath("$.from").value(today.withDayOfMonth(1).minusMonths(2).toString()))
                .andExpect(jsonPath("$.to").value(today.toString()))
                .andExpect(jsonPath("$.currencies.length()").value(2))
                .andExpect(jsonPath("$.currencies[0].currency").value("USD"))
                .andExpect(jsonPath("$.currencies[0].received").value(2700.0))
                .andExpect(jsonPath("$.currencies[0].withheld").value(350.0))
                .andExpect(jsonPath("$.currencies[0].clients.length()").value(2))
                .andExpect(jsonPath("$.currencies[0].clients[0].name").value("Beta LLC"))
                .andExpect(jsonPath("$.currencies[0].clients[0].payments").value(1))
                .andExpect(jsonPath("$.currencies[0].clients[0].withheld").value(200.0))
                .andExpect(jsonPath("$.currencies[0].clients[1].name").value("Acme"))
                .andExpect(jsonPath("$.currencies[0].clients[1].payments").value(2))
                .andExpect(jsonPath("$.currencies[0].clients[1].received").value(900.0))
                .andExpect(jsonPath("$.currencies[0].clients[1].withheld").value(150.0))
                .andExpect(jsonPath("$.currencies[0].months.length()").value(2))
                .andExpect(jsonPath("$.currencies[0].months[0].month").value(YearMonth.from(lastMonth).toString()))
                .andExpect(jsonPath("$.currencies[0].months[0].withheld").value(100.0))
                .andExpect(jsonPath("$.currencies[0].months[1].month").value(YearMonth.from(today).toString()))
                .andExpect(jsonPath("$.currencies[0].months[1].payments").value(2))
                .andExpect(jsonPath("$.currencies[0].months[1].withheld").value(250.0))
                .andExpect(jsonPath("$.currencies[0].payments.length()").value(3))
                .andExpect(jsonPath("$.currencies[0].payments[0].datePaid").value(lastMonth.toString()))
                .andExpect(jsonPath("$.currencies[0].payments[0].documentNumber").value("INV-0001"))
                .andExpect(jsonPath("$.currencies[0].payments[0].client").value("Acme"))
                .andExpect(jsonPath("$.currencies[0].payments[1].documentNumber").value("INV-0002"))
                .andExpect(jsonPath("$.currencies[0].payments[2].documentNumber").value("INV-0004"))
                .andExpect(jsonPath("$.currencies[0].payments[2].received").value(0.0))
                .andExpect(jsonPath("$.currencies[1].currency").value("EUR"))
                .andExpect(jsonPath("$.currencies[1].withheld").value(30.0));

        report("12m")
                .andExpect(jsonPath("$.currencies[0].withheld").value(360.0))
                .andExpect(jsonPath("$.currencies[0].months.length()").value(3));
        report("next year").andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("the CSV lists every payment tax was withheld from, oldest first, in each currency")
    void csv() throws Exception {
        String from = today.withDayOfMonth(1).minusMonths(2).toString();
        String csv = new String(
                mockMvc.perform(get("/api/v1/report/csv/tds")
                                .param("period", "3m")
                                .header(HttpHeaders.AUTHORIZATION, session))
                        .andExpect(status().isOk())
                        .andExpect(header().string(
                                HttpHeaders.CONTENT_DISPOSITION,
                                "attachment; filename=\"invoice-spot-tax-withheld-%s-to-%s.csv\"".formatted(from, today)))
                        .andReturn()
                        .getResponse()
                        .getContentAsByteArray(),
                StandardCharsets.UTF_8);
        assertThat(csv.lines().toList()).containsExactly(
                "﻿Currency,Date paid,Client,Client PAN,Document,Received,Tax withheld",
                "USD,%s,Acme,,INV-0001,900.00,100.00".formatted(lastMonth),
                "USD,%s,Beta LLC,,INV-0002,1800.00,200.00".formatted(today),
                "USD,%s,Acme,,INV-0004,0.00,50.00".formatted(today),
                "EUR,%s,Acme,,INV-0005,270.00,30.00".formatted(today));
    }
}
