package com.invoicespot.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.invoicespot.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

// Money paid back when a credit note leaves an invoice or receipt overpaid:
// never more than is due back, and counted wherever money received is.
class RefundIntegrationTest extends IntegrationTest {

    // UTC+14, the time zone furthest ahead: a day after its today is still to
    // come everywhere.
    private static final ZoneId FURTHEST_AHEAD = ZoneId.of("Pacific/Kiritimati");

    private final LocalDate today = LocalDate.now(ZoneOffset.UTC);
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

    private String document(String type, String amount) throws Exception {
        return JsonPath.read(
                body(send(post("/api/v1/document/create"), """
                                {"customerId": "%s", "documentType": "%s", "currency": "USD",
                                 "billingItems": [{"itemName": "Work", "unitPrice": %s, "quantity": 1}]}
                                """.formatted(client, type, amount))
                        .andExpect(status().isOk())),
                "$.newDocument._id");
    }

    private String paidInvoice(String amount) throws Exception {
        String invoice = document("Invoice", amount);
        send(post("/api/v1/document/{id}/payment", invoice), "{\"amountPaid\": %s}".formatted(amount))
                .andExpect(status().isCreated());
        return invoice;
    }

    private String credit(String document, String amount) throws Exception {
        String note = JsonPath.read(
                body(send(post("/api/v1/document/{id}/credit-note", document), "").andExpect(status().isCreated())),
                "$.newDocument._id");
        send(patch("/api/v1/document/{id}", note), """
                {"billingItems": [{"itemName": "Work", "unitPrice": %s, "quantity": 1}]}
                """.formatted(amount))
                .andExpect(status().isOk());
        send(post("/api/v1/document/{id}/issue", note), "{}").andExpect(status().isOk());
        return note;
    }

    private ResultActions refund(String document, String json) throws Exception {
        return send(post("/api/v1/document/{id}/refund", document), json);
    }

    private void timeZone(ZoneId zone) throws Exception {
        send(put("/api/v1/settings"), """
                {"defaultCurrency": "USD", "paymentTermsDays": 15, "defaultTaxRate": 0,
                 "taxLabel": "Tax", "timeZone": "%s"}
                """.formatted(zone.getId())).andExpect(status().isOk());
    }

    // A time zone whose today isn't UTC's right now: fourteen hours ahead of
    // UTC from 10:00 UTC, eleven hours behind it before then.
    private static ZoneId awayFromUtc() {
        return LocalDate.now(FURTHEST_AHEAD).equals(LocalDate.now(ZoneOffset.UTC))
                ? ZoneId.of("Pacific/Pago_Pago")
                : FURTHEST_AHEAD;
    }

    @Test
    @DisplayName("a refund pays back what a credit note left overpaid, and never more")
    void limits() throws Exception {
        String invoice = paidInvoice("1000");
        credit(invoice, "300");
        send(get("/api/v1/document/{id}", invoice), "")
                .andExpect(jsonPath("$.document.status").value("Paid"))
                .andExpect(jsonPath("$.document.ledger.balanceDue").value(0))
                .andExpect(jsonPath("$.document.ledger.refundDue").value(300.0));

        refund(invoice, "{\"amount\": 350}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("That is more than the 300.00 due back"));
        refund(invoice, "{\"amount\": 1E+16}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Amounts can have at most 15 whole digits"));
        String refundId = JsonPath.read(
                body(refund(invoice, """
                                {"amount": 200, "dateRefunded": "%s", "paymentMethod": "Bank Transfer",
                                 "reference": "NEFT 99"}
                                """.formatted(today))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.document.status").value("Paid"))
                        .andExpect(jsonPath("$.document.ledger.totalRefunded").value(200.0))
                        .andExpect(jsonPath("$.document.ledger.refundDue").value(100.0))
                        .andExpect(jsonPath("$.document.refunds[0].reference").value("NEFT 99"))),
                "$.document.refunds[0]._id");
        send(get("/api/v1/document/{id}/activity", invoice), "")
                .andExpect(jsonPath("$.events[0].type").value("REFUND_RECORDED"))
                .andExpect(jsonPath("$.events[0].detail").value("USD 200.00 · Bank Transfer"));

        send(patch("/api/v1/document/{id}/refund/{refund}", invoice, refundId), "{\"amount\": 301}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("That is more than the 100.00 due back"));
        send(patch("/api/v1/document/{id}/refund/{refund}", invoice, refundId), "{\"amount\": 300}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.document.ledger.refundDue").value(0));
        refund(invoice, "{\"amount\": 1}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Nothing is due back on INV-0001"));
        refund(invoice, "{\"amount\": 0}").andExpect(status().isBadRequest());

        // The money received can't drop below what was paid back.
        String payment = JsonPath.read(
                body(send(get("/api/v1/document/{id}", invoice), "")), "$.document.paymentRecords[0]._id");
        send(patch("/api/v1/document/{id}/payment/{payment}", invoice, payment), "{\"amountPaid\": 250}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Delete the refunds first: 300.00 was paid back"));
        send(delete("/api/v1/document/{id}/payment/{payment}", invoice, payment), "")
                .andExpect(status().isConflict());
        send(patch("/api/v1/document/{id}/payment/{payment}", invoice, payment), "{\"amountPaid\": 900}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.document.status").value("Not Fully Paid"))
                .andExpect(jsonPath("$.document.ledger.balanceDue").value(100.0));

        send(delete("/api/v1/document/{id}/refund/{refund}", invoice, refundId), "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.document.ledger.totalRefunded").value(0))
                .andExpect(jsonPath("$.document.ledger.refundDue").value(200.0));
    }

    @Test
    @DisplayName("a receipt pays back what its credit notes took off, which stay while it does")
    void receipts() throws Exception {
        String receipt = document("Receipt", "500");
        String note = credit(receipt, "100");
        refund(receipt, "{\"amount\": 100}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.document.ledger.refundDue").value(0));
        send(post("/api/v1/document/{id}/void", note), "{\"reason\": \"Wrong amount\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Delete the refunds on REC-0001 first"));
        refund(note, "{\"amount\": 1}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Only an invoice or a receipt pays money back"));
        refund(document("Quotation", "10"), "{\"amount\": 1}").andExpect(status().isConflict());
    }

    @Test
    @DisplayName("reports, the overview, statements, PDFs and the CSVs count money paid back")
    void everywhere() throws Exception {
        String invoice = paidInvoice("1000");
        credit(invoice, "300");
        send(get("/api/v1/document/{id}/pdf", invoice), "").andExpect(status().isOk());
        assertThat(pdfText(invoice)).contains("Refund due USD 300.00");
        refund(invoice, "{\"amount\": 300, \"paymentMethod\": \"UPI\", \"reference\": \"UTR 42\"}")
                .andExpect(status().isCreated());

        send(get("/api/v1/report/overview"), "")
                .andExpect(jsonPath("$.expectedIncome").value(700.0))
                .andExpect(jsonPath("$.cashReceived").value(700.0))
                .andExpect(jsonPath("$.cashPending").value(0));
        send(get("/api/v1/report").param("period", "12m"), "")
                .andExpect(jsonPath("$.currencies[0].billed").value(700.0))
                .andExpect(jsonPath("$.currencies[0].received").value(700.0))
                .andExpect(jsonPath("$.currencies[0].outstanding").value(0))
                .andExpect(jsonPath("$.currencies[0].clients[0].paid").value(700.0))
                .andExpect(jsonPath("$.currencies[0].clients[0].outstanding").value(0));
        send(get("/api/v1/customer/{id}/summary", client), "")
                .andExpect(jsonPath("$.totals[0].paid").value(700.0))
                .andExpect(jsonPath("$.totals[0].outstanding").value(0));

        String statement = PdfText.of(mockMvc.perform(get("/api/v1/customer/{id}/statement", client)
                        .header(HttpHeaders.AUTHORIZATION, session))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray())
                .replaceAll("\\s+", " ");
        assertThat(statement).contains(
                "Refund on INV-0001", "Refunded USD 300.00", "Credited USD 300.00", "Amount due USD 0.00");
        assertThat(pdfText(invoice)).contains("Refunded USD 300.00", "Balance due USD 0.00");

        assertThat(body(send(get("/api/v1/document/payments/csv"), "")))
                .contains("Tax withheld,Kind")
                .containsPattern("INV-0001,Invoice,Beta LLC,USD,-300.00,UPI,,UTR 42,,Refund\\r?\\n")
                .containsPattern(",1000.00,Cash,Beta LLC,,0.00,Payment\\r?\\n");
        assertThat(body(send(get("/api/v1/document/csv"), "")))
                .contains("Credits,Credited,Refunded")
                .containsPattern(",300.00,300.00\\r?\\n");
    }

    private String pdfText(String id) throws Exception {
        return PdfText.of(mockMvc.perform(get("/api/v1/document/{id}/pdf", id)
                        .header(HttpHeaders.AUTHORIZATION, session))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray())
                .replaceAll("\\s+", " ");
    }

    @Test
    @DisplayName("a refund is dated no later than today somewhere, and today where the business is if no day is given")
    void notAfterToday() throws Exception {
        ZoneId zone = awayFromUtc();
        timeZone(zone);
        LocalDate local = LocalDate.now(zone);
        LocalDate tomorrowFurthestAhead = LocalDate.now(FURTHEST_AHEAD).plusDays(1);
        String invoice = paidInvoice("1000");
        credit(invoice, "300");

        for (LocalDate future : List.of(local.plusDays(2), tomorrowFurthestAhead)) {
            refund(invoice, "{\"amount\": 100, \"dateRefunded\": \"%s\"}".formatted(future))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("A refund can't be dated after today"));
        }
        String refundId = JsonPath.read(
                body(refund(invoice, "{\"amount\": 100, \"dateRefunded\": \"%s\"}".formatted(local))
                        .andExpect(status().isCreated())),
                "$.document.refunds[0]._id");
        send(patch("/api/v1/document/{id}/refund/{refund}", invoice, refundId),
                        "{\"dateRefunded\": \"%s\"}".formatted(tomorrowFurthestAhead))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("A refund can't be dated after today"));
        // Without a day, it is dated today where the business is.
        refund(invoice, "{\"amount\": 50}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.document.refunds[1].dateRefunded").value(local.toString()));
    }

    @Test
    @DisplayName("a refund dated today in UTC+14 is taken while it is still the day before where the business is")
    void todayAheadOfTheBusiness() throws Exception {
        // Ten hours behind UTC, so always a day behind UTC+14.
        timeZone(ZoneId.of("Pacific/Honolulu"));
        LocalDate latest = LocalDate.now(FURTHEST_AHEAD);
        String invoice = paidInvoice("1000");
        credit(invoice, "300");

        String refundId = JsonPath.read(
                body(refund(invoice, "{\"amount\": 100, \"dateRefunded\": \"%s\"}".formatted(latest.minusDays(1)))
                        .andExpect(status().isCreated())),
                "$.document.refunds[0]._id");
        send(patch("/api/v1/document/{id}/refund/{refund}", invoice, refundId),
                        "{\"dateRefunded\": \"%s\"}".formatted(latest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.document.refunds[0].dateRefunded").value(latest.toString()));
        refund(invoice, "{\"amount\": 100, \"dateRefunded\": \"%s\"}".formatted(latest))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.document.refunds[1].dateRefunded").value(latest.toString()));
    }
}
