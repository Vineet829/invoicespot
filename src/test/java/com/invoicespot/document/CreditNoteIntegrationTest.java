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
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

// A credit note takes an amount off an issued invoice or receipt: it is made
// as a draft from it, keeps its client and currency, can't take off more than
// is left, and counts in balances, reports, statements, PDFs and exports.
class CreditNoteIntegrationTest extends IntegrationTest {

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

    private String document(String type, String amount, LocalDate issued, boolean draft) throws Exception {
        return JsonPath.read(
                body(send(post("/api/v1/document/create"), """
                                {"customerId": "%s", "documentType": "%s", "currency": "USD", "draft": %s,
                                 "issueDate": "%s",
                                 "billingItems": [{"itemName": "Work", "unitPrice": %s, "quantity": 1}]}
                                """.formatted(client, type, draft, issued, amount))
                        .andExpect(status().isOk())),
                "$.newDocument._id");
    }

    private String invoice(String amount) throws Exception {
        return document("Invoice", amount, today, false);
    }

    private ResultActions creditNote(String id) throws Exception {
        return send(post("/api/v1/document/{id}/credit-note", id), "");
    }

    // A draft credit note for the given amount before tax.
    private String draftFor(String invoice, String amount) throws Exception {
        String note = JsonPath.read(
                body(creditNote(invoice).andExpect(status().isCreated())), "$.newDocument._id");
        edit(note, """
                {"billingItems": [{"itemName": "Work", "unitPrice": %s, "quantity": 1}]}
                """.formatted(amount))
                .andExpect(status().isOk());
        return note;
    }

    private ResultActions edit(String id, String json) throws Exception {
        return send(patch("/api/v1/document/{id}", id), json);
    }

    private ResultActions issue(String id, String json) throws Exception {
        return send(post("/api/v1/document/{id}/issue", id), json);
    }

    private ResultActions pay(String id, String amount) throws Exception {
        return send(post("/api/v1/document/{id}/payment", id), "{\"amountPaid\": %s}".formatted(amount));
    }

    private ResultActions open(String id) throws Exception {
        return send(get("/api/v1/document/{id}", id), "");
    }

    @Test
    @DisplayName("a credit note starts as a draft of the whole invoice, keeps its client and currency, and is numbered on issue")
    void creditsAnInvoice() throws Exception {
        String invoice = invoice("1000");
        String note = JsonPath.read(
                body(creditNote(invoice)
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.newDocument.documentType").value("Credit Note"))
                        .andExpect(jsonPath("$.newDocument.state").value("draft"))
                        .andExpect(jsonPath("$.newDocument.customerId").value(client))
                        .andExpect(jsonPath("$.newDocument.currency").value("USD"))
                        .andExpect(jsonPath("$.newDocument.billingItems[0].itemName").value("Work"))
                        .andExpect(jsonPath("$.newDocument.total").value(1000.0))
                        .andExpect(jsonPath("$.newDocument.creditedDocument.documentNumber").value("INV-0001"))
                        .andExpect(jsonPath("$.newDocument.creditedDocument.creditable").value(1000.0))),
                "$.newDocument._id");

        edit(note, """
                {"billingItems": [{"itemName": "Work", "unitPrice": 300, "quantity": 1}],
                 "creditReason": "  Scope cut  "}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updatedDocument.total").value(300.0))
                .andExpect(jsonPath("$.updatedDocument.creditReason").value("Scope cut"));
        edit(note, "{\"currency\": \"EUR\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("A credit note keeps the client, currency and exchange rate of the document it credits"));
        edit(note, "{\"dueDate\": \"2030-01-01T12:00:00Z\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("A credit note has no due date"));
        edit(invoice, "{\"creditReason\": \"No\"}").andExpect(status().isConflict());

        issue(note, "{}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updatedDocument.documentNumber").value("CN-0001"))
                .andExpect(jsonPath("$.updatedDocument.state").value("issued"));
        open(invoice)
                .andExpect(jsonPath("$.document.status").value("Not Paid"))
                .andExpect(jsonPath("$.document.ledger.totalCredited").value(300.0))
                .andExpect(jsonPath("$.document.ledger.balanceDue").value(700.0))
                .andExpect(jsonPath("$.document.ledger.creditable").value(700.0))
                .andExpect(jsonPath("$.document.creditNotes[0].documentNumber").value("CN-0001"))
                .andExpect(jsonPath("$.document.creditNotes[0].total").value(300.0));
        send(get("/api/v1/document/{id}/activity", invoice), "")
                .andExpect(jsonPath("$.events[0].type").value("CREDITED"))
                .andExpect(jsonPath("$.events[0].detail").value("CN-0001 · USD 300.00"));

        pay(invoice, "700.01")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("That is more than the 700.00 still owed"));
        pay(invoice, "700").andExpect(jsonPath("$.document.status").value("Paid"));
        edit(note, "{\"creditReason\": \"Other\"}").andExpect(status().isConflict());
        edit(note, "{\"dueDate\": \"2030-01-01T12:00:00Z\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("A credit note has no due date"));
        edit(note, "{\"additionalInfo\": \"Thanks\"}").andExpect(status().isOk());
        pay(note, "1")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("A credit note can't take payments"));
        send(post("/api/v1/document/{id}/duplicate", note), "").andExpect(status().isConflict());
        send(post("/api/v1/document/create"), """
                {"customerId": "%s", "documentType": "Credit Note",
                 "billingItems": [{"itemName": "Work", "unitPrice": 1, "quantity": 1}]}
                """.formatted(client))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Make a credit note from the invoice or receipt it credits"));
    }

    @Test
    @DisplayName("credit notes take off no more than is left, settle the invoice when they take it all, and void back")
    void cap() throws Exception {
        String invoice = invoice("1000");
        String first = draftFor(invoice, "600");
        issue(first, "{}").andExpect(status().isOk());
        String second = draftFor(invoice, "500");
        issue(second, "{}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("That is more than the 400.00 left to credit on INV-0001"));
        edit(second, "{\"billingItems\": [{\"itemName\": \"Work\", \"unitPrice\": 400, \"quantity\": 1}]}")
                .andExpect(status().isOk());
        issue(second, "{}").andExpect(jsonPath("$.updatedDocument.documentNumber").value("CN-0002"));
        open(invoice)
                .andExpect(jsonPath("$.document.status").value("Paid"))
                .andExpect(jsonPath("$.document.totalAmountReceived").value(0.0))
                .andExpect(jsonPath("$.document.ledger.balanceDue").value(0))
                .andExpect(jsonPath("$.document.ledger.creditable").value(0));
        creditNote(invoice)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("INV-0001 is already credited in full"));

        send(post("/api/v1/document/{id}/void", first), "{\"reason\": \"Issued twice\"}").andExpect(status().isOk());
        open(invoice)
                .andExpect(jsonPath("$.document.status").value("Not Paid"))
                .andExpect(jsonPath("$.document.ledger.totalCredited").value(400.0))
                .andExpect(jsonPath("$.document.ledger.balanceDue").value(600.0));
        send(get("/api/v1/document/{id}/activity", invoice), "")
                .andExpect(jsonPath("$.events[0].type").value("CREDIT_VOIDED"))
                .andExpect(jsonPath("$.events[0].detail").value("CN-0001"));
        send(post("/api/v1/document/{id}/void", invoice), "{\"reason\": \"Wrong client\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Void or delete its credit notes before voiding it"));

        String earlier = document("Invoice", "100", today.minusDays(10), false);
        issue(draftFor(earlier, "50"), "{\"issueDate\": \"%s\"}".formatted(today.minusDays(11)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("A credit note can't be dated before INV-0002 was issued"));
        creditNote(document("Invoice", "100", today, true))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Issue this draft before crediting it"));
        creditNote(document("Quotation", "100", today, false))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Only an invoice or a receipt can be credited"));
    }

    @Test
    @DisplayName("two credit notes issued at once for one invoice take turns, so only one fits")
    void issuedAtOnce() throws Exception {
        String invoice = invoice("1000");
        List<String> notes = List.of(draftFor(invoice, "600"), draftFor(invoice, "600"));
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (String note : notes) {
                results.add(pool.submit(() -> {
                    start.await();
                    return issue(note, "{}").andReturn().getResponse().getStatus();
                }));
            }
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> result : results) {
                statuses.add(result.get());
            }
            assertThat(statuses).containsExactlyInAnyOrder(200, 409);
        } finally {
            pool.shutdownNow();
        }
        open(invoice).andExpect(jsonPath("$.document.ledger.totalCredited").value(600.0));
    }

    @Test
    @DisplayName("balances, reports, the overview, statements, PDFs, the share page and the CSV count what was credited")
    void everywhere() throws Exception {
        String invoice = invoice("1000");
        pay(invoice, "200").andExpect(status().isCreated());
        String note = draftFor(invoice, "300");
        edit(note, "{\"creditReason\": \"Returned goods\"}").andExpect(status().isOk());
        issue(note, "{}").andExpect(status().isOk());

        send(get("/api/v1/report/overview"), "")
                .andExpect(jsonPath("$.expectedIncome").value(700.0))
                .andExpect(jsonPath("$.cashReceived").value(200.0))
                .andExpect(jsonPath("$.cashPending").value(500.0))
                .andExpect(jsonPath("$.documents").value(1));
        send(get("/api/v1/report").param("period", "12m"), "")
                .andExpect(jsonPath("$.currencies[0].billed").value(700.0))
                .andExpect(jsonPath("$.currencies[0].outstanding").value(500.0))
                .andExpect(jsonPath("$.currencies[0].clients[0].billed").value(700.0))
                .andExpect(jsonPath("$.currencies[0].clients[0].outstanding").value(500.0));
        send(get("/api/v1/customer/{id}/summary", client), "")
                .andExpect(jsonPath("$.totals[0].billed").value(700.0))
                .andExpect(jsonPath("$.totals[0].outstanding").value(500.0));
        send(get("/api/v1/customer/all"), "")
                .andExpect(jsonPath("$.myCustomers[0].balances[0].outstanding").value(500.0));
        send(get("/api/v1/document/all").param("status", "Not Fully Paid"), "")
                .andExpect(jsonPath("$.myDocuments.length()").value(1));
        send(get("/api/v1/document/all").param("status", "Not Paid"), "")
                .andExpect(jsonPath("$.myDocuments.length()").value(0));
        send(get("/api/v1/document/all").param("type", "Credit Note"), "")
                .andExpect(jsonPath("$.myDocuments.length()").value(1))
                .andExpect(jsonPath("$.myDocuments[0].creditedDocument.documentNumber").value("INV-0001"));

        String statement = pdfText(get("/api/v1/customer/{id}/statement", client));
        assertThat(statement).contains(
                "PAYMENTS & CREDITS", "Credit note CN-0001 for INV-0001", "Credited USD 300.00",
                "Amount due USD 500.00");
        String invoicePdf = pdfText(get("/api/v1/document/{id}/pdf", invoice));
        assertThat(invoicePdf).contains("Credited (CN-0001) USD −300.00", "Balance due USD 500.00");
        String notePdf = pdfText(get("/api/v1/document/{id}/pdf", note));
        assertThat(notePdf).contains(
                "Credit Note", "CN-0001", "Against invoice INV-0001 dated", "REASON FOR THIS CREDIT",
                "Returned goods", "Total credit USD 300.00");

        String link = JsonPath.read(body(send(post("/api/v1/document/{id}/share", note), "")), "$.url");
        mockMvc.perform(get("/api/v1/public/share/{token}", link.substring(link.lastIndexOf('/') + 1)))
                .andExpect(jsonPath("$.documentType").value("Credit Note"))
                .andExpect(jsonPath("$.credits.documentNumber").value("INV-0001"))
                .andExpect(jsonPath("$.creditReason").value("Returned goods"))
                .andExpect(jsonPath("$.balance").value(300.0))
                .andExpect(jsonPath("$.canAccept").value(false));
        String invoiceLink = JsonPath.read(body(send(post("/api/v1/document/{id}/share", invoice), "")), "$.url");
        mockMvc.perform(get("/api/v1/public/share/{token}", invoiceLink.substring(invoiceLink.lastIndexOf('/') + 1)))
                .andExpect(jsonPath("$.credited").value(300.0))
                .andExpect(jsonPath("$.balance").value(500.0));

        String csv = body(send(get("/api/v1/document/csv"), ""));
        assertThat(csv)
                .contains("Exchange rate to,Credits,Credited")
                .containsPattern("CN-0001,Credit Note,Issued,Beta LLC,[^\\r\\n]*,INV-0001,,\\r?\\n")
                .containsPattern("INV-0001,Invoice,Partially paid,[^\\r\\n]*,300.00,0.00\\r?\\n");
    }

    private String pdfText(MockHttpServletRequestBuilder request) throws Exception {
        return PdfText.of(mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, session))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsByteArray())
                .replaceAll("\\s+", " ");
    }
}
