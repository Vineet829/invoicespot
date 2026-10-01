package com.invoicespot.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.invoicespot.IntegrationTest;
import com.invoicespot.user.User;
import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

// A draft has no number and counts for nothing until it is issued: then it
// takes the next number, is dated, and keeps the client as they are.
class DraftIntegrationTest extends IntegrationTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");

    @Autowired
    private OverdueAlerts overdueAlerts;

    @Autowired
    private DocumentRepository documentRepository;

    private User owner;
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
        owner = createUser();
        session = bearer(owner);
        send(put("/api/v1/settings"), """
                {"defaultCurrency": "USD", "paymentTermsDays": 15, "defaultTaxRate": 0,
                 "taxLabel": "Tax", "timeZone": "Asia/Kolkata"}
                """).andExpect(status().isOk());
        client = JsonPath.read(
                body(send(post("/api/v1/customer/create"), """
                        {"name": "Beta LLC", "email": "%s@clients.test", "phoneNumber": "+1"}
                        """.formatted(UUID.randomUUID()))),
                "$.createdCustomer._id");
    }

    // An invoice for 100 or 500, issued or saved as a draft, due on a day
    // when one is given.
    private String invoice(boolean draft, String price, String extra) throws Exception {
        return JsonPath.read(
                body(send(post("/api/v1/document/create"), """
                        {"customerId": "%s", "documentType": "Invoice", "currency": "USD", "draft": %s%s,
                         "billingItems": [{"itemName": "Work", "unitPrice": %s, "quantity": 1}]}
                        """.formatted(client, draft, extra, price)).andExpect(status().isOk())),
                "$.newDocument._id");
    }

    private String draft() throws Exception {
        return invoice(true, "500", "");
    }

    private ResultActions issue(String id, String json) throws Exception {
        return send(post("/api/v1/document/{id}/issue", id), json);
    }

    private ResultActions read(String id) throws Exception {
        return send(get("/api/v1/document/{id}", id), "");
    }

    private static String dueNoon(LocalDate day) {
        return day.atTime(LocalTime.NOON).atZone(ZONE).toInstant().toString();
    }

    @Test
    @DisplayName("a draft has no number until it is issued, then takes the next one; a deleted draft takes none")
    void numbering() throws Exception {
        invoice(false, "100", "");
        String draft = draft();
        read(draft)
                .andExpect(jsonPath("$.document.state").value("draft"))
                .andExpect(jsonPath("$.document.documentNumber").value(nullValue()))
                .andExpect(jsonPath("$.document.issuedAt").doesNotExist());
        invoice(false, "100", "");
        String deleted = draft();
        send(delete("/api/v1/document/{id}", deleted), "").andExpect(status().isOk());

        issue(draft, "{}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Invoice INV-0003 was issued"))
                .andExpect(jsonPath("$.updatedDocument.state").value("issued"))
                .andExpect(jsonPath("$.updatedDocument.documentNumber").value("INV-0003"))
                .andExpect(jsonPath("$.updatedDocument.issuedAt", notNullValue()));
        send(get("/api/v1/document/{id}/activity", draft), "")
                .andExpect(jsonPath("$.events[*].type", contains("ISSUED", "CREATED")))
                .andExpect(jsonPath("$.events[0].detail").value("INV-0003"));
        issue(draft, "{}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("This document is already issued"));
        assertThat(JsonPath.<String>read(body(read(invoice(false, "100", ""))), "$.document.documentNumber"))
                .isEqualTo("INV-0004");
    }

    @Test
    @DisplayName("a draft is dated the day it is issued, unless a day was planned or is asked for")
    void dates() throws Exception {
        LocalDate today = LocalDate.now(ZONE);
        String unplanned = draft();
        read(unplanned).andExpect(jsonPath("$.document.issueDate").value(nullValue()));
        issue(unplanned, "").andExpect(jsonPath("$.updatedDocument.issueDate").value(today.toString()));

        String planned = invoice(true, "500", ", \"issueDate\": \"" + today.minusDays(10) + "\"");
        issue(planned, "{}").andExpect(jsonPath("$.updatedDocument.issueDate").value(today.minusDays(10).toString()));

        String asked = invoice(true, "500", ", \"issueDate\": \"" + today.minusDays(10) + "\"");
        issue(asked, "{\"issueDate\": \"" + today.plusDays(1) + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("A document can't be dated after today"));
        issue(asked, "{\"issueDate\": \"" + today.minusDays(3) + "\"}")
                .andExpect(jsonPath("$.updatedDocument.issueDate").value(today.minusDays(3).toString()));
    }

    @Test
    @DisplayName("an invoice issued without a due date is due as the payment terms say, from its issue date")
    void dueDates() throws Exception {
        LocalDate day = LocalDate.now(ZONE).minusDays(4);

        issue(draft(), "{\"issueDate\": \"" + day + "\"}")
                .andExpect(jsonPath("$.updatedDocument.dueDate").value(day.plusDays(15).toString()));
        String due = dueNoon(day.plusDays(30));
        issue(invoice(true, "500", ", \"dueDate\": \"" + dueNoon(day) + "\""), "{\"dueDate\": \"" + due + "\"}")
                .andExpect(jsonPath("$.updatedDocument.dueDate").value(day.plusDays(30).toString()));
    }

    @Test
    @DisplayName("issuing a draft takes the client's details as they are then")
    void clientAtIssue() throws Exception {
        String draft = draft();
        send(patch("/api/v1/customer/{id}", client), "{\"name\": \"Beta Limited\"}").andExpect(status().isOk());

        read(draft).andExpect(jsonPath("$.document.customer.name").value("Beta LLC"));
        issue(draft, "{}").andExpect(jsonPath("$.updatedDocument.customer.name").value("Beta Limited"));
    }

    @Test
    @DisplayName("a draft can't be emailed, shared, paid or reminded about, but its PDF says DRAFT")
    void refusals() throws Exception {
        String draft = invoice(true, "500", ", \"dueDate\": \"" + dueNoon(LocalDate.now(ZONE).minusDays(9)) + "\"");

        send(get("/api/v1/document/{id}/email", draft), "")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Issue this draft before sending it"));
        send(post("/api/v1/document/{id}/email", draft), "{\"to\": \"beta@clients.test\"}")
                .andExpect(status().isConflict());
        send(post("/api/v1/document/{id}/share", draft), "")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Issue this draft before sharing it"));
        send(post("/api/v1/document/{id}/payment", draft), "{\"amountPaid\": 10}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Issue this draft before recording a payment"));
        send(get("/api/v1/document/{id}/remind", draft), "")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Issue this draft before sending a reminder"));
        byte[] pdf = mockMvc.perform(get("/api/v1/document/{id}/pdf", draft)
                        .header(HttpHeaders.AUTHORIZATION, session))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"Draft-invoice.pdf\""))
                .andReturn()
                .getResponse()
                .getContentAsByteArray();
        assertThat(PdfText.of(pdf)).contains("DRAFT", "On issue").doesNotContain("INV-");
    }

    @Test
    @DisplayName("drafts count for nothing: not in balances, reports, the overview, statements or alerts")
    void leftOut() throws Exception {
        String overdue = dueNoon(LocalDate.now(ZONE).minusDays(9));
        String issued = invoice(false, "100", ", \"dueDate\": \"" + overdue + "\"");
        String draft = invoice(true, "500", ", \"dueDate\": \"" + overdue + "\"");

        send(get("/api/v1/document/all"), "")
                .andExpect(jsonPath("$.myDocuments[*]._id", containsInAnyOrder(issued, draft)));
        send(get("/api/v1/document/all").param("state", "draft"), "")
                .andExpect(jsonPath("$.myDocuments[*]._id", contains(draft)));
        send(get("/api/v1/document/all").param("state", "issued"), "")
                .andExpect(jsonPath("$.myDocuments[*]._id", contains(issued)));
        send(get("/api/v1/document/all").param("status", "Not Paid"), "")
                .andExpect(jsonPath("$.myDocuments[*]._id", contains(issued)));
        send(get("/api/v1/document/all").param("overdue", "true"), "")
                .andExpect(jsonPath("$.myDocuments[*]._id", contains(issued)));
        send(get("/api/v1/document/all").param("state", "archived"), "")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Unknown state filter"));
        read(draft).andExpect(jsonPath("$.document.overdue").value(false));
        assertThat(body(send(get("/api/v1/document/csv"), ""))).contains(",Invoice,Draft,Beta LLC,");

        send(get("/api/v1/report/overview"), "")
                .andExpect(jsonPath("$.documents").value(1))
                .andExpect(jsonPath("$.expectedIncome").value(100.0))
                .andExpect(jsonPath("$.unpaid").value(1))
                .andExpect(jsonPath("$.overdue").value(1));
        send(get("/api/v1/report").param("period", "12m"), "")
                .andExpect(jsonPath("$.currencies[0].billed").value(100.0))
                .andExpect(jsonPath("$.currencies[0].outstanding").value(100.0));
        send(get("/api/v1/customer/{id}/summary", client), "")
                .andExpect(jsonPath("$.totals[0].billed").value(100.0))
                .andExpect(jsonPath("$.totals[0].outstanding").value(100.0))
                .andExpect(jsonPath("$.totals[0].overdue").value(100.0));
        send(get("/api/v1/customer/all"), "")
                .andExpect(jsonPath("$.myCustomers[0].balances[0].outstanding").value(100.0));
        String statement = PdfText.of(mockMvc.perform(get("/api/v1/customer/{id}/statement", client)
                        .header(HttpHeaders.AUTHORIZATION, session))
                .andReturn()
                .getResponse()
                .getContentAsByteArray());
        assertThat(statement).contains("INV-0001").doesNotContain("500.00");

        overdueAlerts.raise(Instant.now());
        send(get("/api/v1/notification"), "")
                .andExpect(jsonPath("$.notifications[*].message", contains(
                        "Invoice INV-0001 for Beta LLC is overdue, with USD 100.00 unpaid")));
        assertThat(documentRepository.findRemindable(
                        businessOf(owner).getPkid(), DocumentType.INVOICE, DocumentStatus.PAID, 10))
                .extracting(document -> document.getId().toString())
                .containsExactly(issued);
    }
}
