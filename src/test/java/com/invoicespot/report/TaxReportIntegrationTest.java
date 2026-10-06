package com.invoicespot.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

// The tax charged in a period at each rate, by the day each document is dated:
// invoices and receipts add theirs, credit notes take theirs off, and drafts,
// void documents and quotations don't count. Under GST it is split as it was
// charged, and supplies under reverse charge are kept apart.
class TaxReportIntegrationTest extends IntegrationTest {

    private final LocalDate today = LocalDate.now(ZoneOffset.UTC);
    private final LocalDate lastMonth = today.withDayOfMonth(1).minusMonths(1);
    private String session;

    private ResultActions send(MockHttpServletRequestBuilder request, String json) throws Exception {
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private static String body(ResultActions result) throws Exception {
        return result.andReturn().getResponse().getContentAsString();
    }

    private void business(String currency, String regime) throws Exception {
        send(put("/api/v1/settings"), """
                {"defaultCurrency": "%s", "paymentTermsDays": 15, "defaultTaxRate": 0, "taxLabel": "Tax",
                 "timeZone": "UTC", "taxRegime": "%s"}
                """.formatted(currency, regime))
                .andExpect(status().isOk());
    }

    private String client(String extra) throws Exception {
        return JsonPath.read(
                body(send(post("/api/v1/customer/create"), """
                        {"name": "Acme", "email": "%s@clients.test", "phoneNumber": "+1" %s}
                        """.formatted(UUID.randomUUID(), extra))),
                "$.createdCustomer._id");
    }

    // Each line is "price@rate".
    private String document(String client, String type, String currency, LocalDate issued, String extra,
            String... lines) throws Exception {
        List<String> items = new ArrayList<>();
        for (String line : lines) {
            String[] parts = line.split("@");
            items.add("{\"itemName\": \"Work\", \"unitPrice\": %s, \"quantity\": 1, \"taxRate\": \"%s\"}"
                    .formatted(parts[0], parts[1]));
        }
        return JsonPath.read(
                body(send(post("/api/v1/document/create"), """
                                {"customerId": "%s", "documentType": "%s", "currency": "%s", "issueDate": "%s",
                                 "billingItems": [%s] %s}
                                """.formatted(client, type, currency, issued, String.join(", ", items), extra))
                        .andExpect(status().isOk())),
                "$.newDocument._id");
    }

    // A credit note against an invoice for one amount at one rate, issued today.
    private void credit(String invoice, String line) throws Exception {
        String[] parts = line.split("@");
        String note = JsonPath.read(
                body(send(post("/api/v1/document/{id}/credit-note", invoice), "").andExpect(status().isCreated())),
                "$.newDocument._id");
        send(patch("/api/v1/document/{id}", note), """
                {"billingItems": [{"itemName": "Work", "unitPrice": %s, "quantity": 1, "taxRate": "%s"}]}
                """.formatted(parts[0], parts[1]))
                .andExpect(status().isOk());
        send(post("/api/v1/document/{id}/issue", note), "{}").andExpect(status().isOk());
    }

    private ResultActions report(String period, String view) throws Exception {
        MockHttpServletRequestBuilder request = get("/api/v1/report/tax").param("period", period);
        return mockMvc.perform((view == null ? request : request.param("view", view))
                .header(HttpHeaders.AUTHORIZATION, session));
    }

    // In dollars, euros at a typed-in rate, and pounds without one.
    private void standardBusiness() throws Exception {
        session = bearer(createUser());
        business("USD", "STANDARD");
        String acme = client("");
        String first = document(acme, "Invoice", "USD", lastMonth, "", "1000@18", "101@5"); // INV-0001
        document(acme, "Invoice", "USD", today, "", "500@18"); // INV-0002
        document(acme, "Receipt", "USD", today, "", "200@0"); // REC-0001
        credit(first, "100@18"); // CN-0001
        document(acme, "Invoice", "EUR", today, ", \"exchangeRate\": \"1.1\"", "400@20"); // INV-0003
        document(acme, "Invoice", "GBP", today, "", "300@10"); // INV-0004
        // Left out: a draft, a void invoice, a quotation, and an invoice from
        // before the period.
        document(acme, "Invoice", "USD", today, ", \"draft\": true", "1000@18");
        String voided = document(acme, "Invoice", "USD", today, "", "300@18"); // INV-0005
        send(post("/api/v1/document/{id}/void", voided), "{\"reason\": \"Sent twice\"}").andExpect(status().isOk());
        document(acme, "Quotation", "USD", today, "", "700@18");
        document(acme, "Invoice", "USD", today.withDayOfMonth(1).minusMonths(4), "", "900@18"); // INV-0006
    }

    @Test
    @DisplayName("each currency's tax per rate and per month, less credit notes, from issued documents only")
    void byCurrency() throws Exception {
        standardBusiness();
        report("3m", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period").value("3m"))
                .andExpect(jsonPath("$.from").value(today.withDayOfMonth(1).minusMonths(2).toString()))
                .andExpect(jsonPath("$.view").value("currency"))
                .andExpect(jsonPath("$.notConverted.length()").value(0))
                .andExpect(jsonPath("$.currencies.length()").value(3))
                .andExpect(jsonPath("$.currencies[0].currency").value("USD"))
                .andExpect(jsonPath("$.currencies[0].gst").value(false))
                .andExpect(jsonPath("$.currencies[0].documents").value(4))
                .andExpect(jsonPath("$.currencies[0].taxable").value(1701.0))
                .andExpect(jsonPath("$.currencies[0].tax").value(257.05))
                .andExpect(jsonPath("$.currencies[0].reverseChargeTaxable").value(0.0))
                .andExpect(jsonPath("$.currencies[0].rates.length()").value(3))
                .andExpect(jsonPath("$.currencies[0].rates[0].rate").value(0))
                .andExpect(jsonPath("$.currencies[0].rates[0].month").doesNotExist())
                .andExpect(jsonPath("$.currencies[0].rates[0].taxable").value(200.0))
                .andExpect(jsonPath("$.currencies[0].rates[1].rate").value(5))
                .andExpect(jsonPath("$.currencies[0].rates[1].tax").value(5.05))
                .andExpect(jsonPath("$.currencies[0].rates[2].rate").value(18))
                .andExpect(jsonPath("$.currencies[0].rates[2].documents").value(3))
                .andExpect(jsonPath("$.currencies[0].rates[2].taxable").value(1400.0))
                .andExpect(jsonPath("$.currencies[0].rates[2].tax").value(252.0))
                .andExpect(jsonPath("$.currencies[0].months.length()").value(4))
                .andExpect(jsonPath("$.currencies[0].months[0].month").value(YearMonth.from(lastMonth).toString()))
                .andExpect(jsonPath("$.currencies[0].months[0].rate").value(5))
                .andExpect(jsonPath("$.currencies[0].months[1].rate").value(18))
                .andExpect(jsonPath("$.currencies[0].months[1].tax").value(180.0))
                .andExpect(jsonPath("$.currencies[0].months[3].month").value(YearMonth.from(today).toString()))
                .andExpect(jsonPath("$.currencies[0].months[3].rate").value(18))
                .andExpect(jsonPath("$.currencies[0].months[3].documents").value(2))
                .andExpect(jsonPath("$.currencies[0].months[3].taxable").value(400.0))
                .andExpect(jsonPath("$.currencies[0].months[3].tax").value(72.0))
                .andExpect(jsonPath("$.currencies[1].currency").value("EUR"))
                .andExpect(jsonPath("$.currencies[1].tax").value(80.0))
                .andExpect(jsonPath("$.currencies[2].currency").value("GBP"))
                .andExpect(jsonPath("$.currencies[2].rates[0].rate").value(10));

        report("12m", null).andExpect(jsonPath("$.currencies[0].tax").value(419.05));
        report("next year", null).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("in the reporting currency, at each document's own rate, listing what has no rate to it")
    void inReportingCurrency() throws Exception {
        standardBusiness();
        report("3m", "base")
                .andExpect(jsonPath("$.view").value("base"))
                .andExpect(jsonPath("$.baseCurrency").value("USD"))
                .andExpect(jsonPath("$.currencies.length()").value(1))
                .andExpect(jsonPath("$.currencies[0].currency").value("USD"))
                .andExpect(jsonPath("$.currencies[0].rates.length()").value(4))
                .andExpect(jsonPath("$.currencies[0].rates[3].rate").value(20))
                .andExpect(jsonPath("$.currencies[0].rates[3].taxable").value(440.0))
                .andExpect(jsonPath("$.currencies[0].rates[3].tax").value(88.0))
                .andExpect(jsonPath("$.currencies[0].tax").value(345.05))
                .andExpect(jsonPath("$.notConverted.length()").value(1))
                .andExpect(jsonPath("$.notConverted[0].currency").value("GBP"))
                .andExpect(jsonPath("$.notConverted[0].documents").value(1))
                .andExpect(jsonPath("$.notConverted[0].amount").value(330.0));
        report("3m", "pounds").andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("under GST each rate is split as charged, and reverse-charge supplies are kept apart")
    void gst() throws Exception {
        session = bearer(createUser());
        send(patch("/api/v1/business"), "{\"taxId\": \"27AAPFU0939F1ZV\"}").andExpect(status().isOk());
        business("INR", "GST_INDIA");
        String mumbai = client(", \"gstin\": \"27AAACB1234C1ZF\"");
        String bengaluru = client(", \"gstin\": \"29AAGCB7383J1Z4\"");
        String within = document(mumbai, "Invoice", "INR", today, "", "1000@18");
        document(bengaluru, "Invoice", "INR", today, "", "2000@18");
        document(bengaluru, "Invoice", "INR", today, ", \"reverseCharge\": true", "500@18");
        credit(within, "100@18");

        report("3m", null)
                .andExpect(jsonPath("$.currencies.length()").value(1))
                .andExpect(jsonPath("$.currencies[0].currency").value("INR"))
                .andExpect(jsonPath("$.currencies[0].gst").value(true))
                .andExpect(jsonPath("$.currencies[0].rates[0].rate").value(18))
                .andExpect(jsonPath("$.currencies[0].rates[0].documents").value(4))
                .andExpect(jsonPath("$.currencies[0].rates[0].taxable").value(2900.0))
                .andExpect(jsonPath("$.currencies[0].rates[0].tax").value(522.0))
                .andExpect(jsonPath("$.currencies[0].rates[0].cgst").value(81.0))
                .andExpect(jsonPath("$.currencies[0].rates[0].sgst").value(81.0))
                .andExpect(jsonPath("$.currencies[0].rates[0].igst").value(360.0))
                .andExpect(jsonPath("$.currencies[0].rates[0].reverseChargeTaxable").value(500.0));
        // The overview's tax in what was billed leaves out the supply under
        // reverse charge too.
        report("30d", null).andExpect(jsonPath("$.currencies[0].tax").value(522.0));
        mockMvc.perform(get("/api/v1/report/overview")
                        .param("period", "30d")
                        .header(HttpHeaders.AUTHORIZATION, session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currency").value("INR"))
                .andExpect(jsonPath("$.totals.tax").value(522.0));

        String csv = new String(
                mockMvc.perform(get("/api/v1/report/csv/tax")
                                .param("period", "3m")
                                .header(HttpHeaders.AUTHORIZATION, session))
                        .andExpect(status().isOk())
                        .andExpect(header().string(
                                HttpHeaders.CONTENT_DISPOSITION,
                                "attachment; filename=\"invoice-spot-tax-by-rate-%s-to-%s.csv\"".formatted(
                                        today.withDayOfMonth(1).minusMonths(2), today)))
                        .andReturn()
                        .getResponse()
                        .getContentAsByteArray(),
                StandardCharsets.UTF_8);
        assertThat(csv.lines().toList()).containsExactly(
                "﻿Currency,Month,Rate %,Documents,Taxable,Tax,CGST,SGST,IGST,Reverse-charge taxable",
                "INR,%s,18,4,2900.00,522.00,81.00,81.00,360.00,500.00".formatted(YearMonth.from(today)));
    }
}
