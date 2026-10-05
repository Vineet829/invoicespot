package com.invoicespot.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.invoicespot.IntegrationTest;
import com.invoicespot.business.BusinessRole;
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

// An issued document voided instead of deleted: it keeps its number and says
// why, can't be sent, paid or changed any more, and counts for nothing.
class VoidIntegrationTest extends IntegrationTest {

    private static final ZoneId ZONE = ZoneId.of("UTC");

    @Autowired
    private OverdueAlerts overdueAlerts;

    private User owner;
    private String session;
    private String client;

    private ResultActions send(MockHttpServletRequestBuilder request, String json) throws Exception {
        return send(request, json, session);
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String json, String as) throws Exception {
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, as)
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
                 "taxLabel": "Tax", "timeZone": "UTC"}
                """).andExpect(status().isOk());
        client = JsonPath.read(
                body(send(post("/api/v1/customer/create"), """
                        {"name": "Beta LLC", "email": "%s@clients.test", "phoneNumber": "+1"}
                        """.formatted(UUID.randomUUID()))),
                "$.createdCustomer._id");
    }

    // A document for the given amount, overdue by nine days.
    private String document(String type, String price, boolean draft) throws Exception {
        String due = LocalDate.now(ZONE).minusDays(9).atTime(LocalTime.NOON).atZone(ZONE).toInstant().toString();
        return JsonPath.read(
                body(send(post("/api/v1/document/create"), """
                        {"customerId": "%s", "documentType": "%s", "currency": "USD", "draft": %s,
                         "issueDate": "%s", "dueDate": "%s",
                         "billingItems": [{"itemName": "Work", "unitPrice": %s, "quantity": 1}]}
                        """.formatted(client, type, draft, LocalDate.now(ZONE).minusDays(9), due, price))
                        .andExpect(status().isOk())),
                "$.newDocument._id");
    }

    private ResultActions voided(String id, String reason) throws Exception {
        return send(post("/api/v1/document/{id}/void", id), "{\"reason\": \"%s\"}".formatted(reason));
    }

    private String shareToken(String id) throws Exception {
        String url = JsonPath.read(body(send(post("/api/v1/document/{id}/share", id), "")), "$.url");
        return url.substring(url.lastIndexOf('/') + 1);
    }

    @Test
    @DisplayName("a void document keeps its number and says why, and its share link and PDF show it as void")
    void voiding() throws Exception {
        String id = document("Invoice", "100", false);
        String token = shareToken(id);

        voided(id, "  Sent to the wrong client  ")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Invoice INV-0001 is void"))
                .andExpect(jsonPath("$.updatedDocument.state").value("void"))
                .andExpect(jsonPath("$.updatedDocument.documentNumber").value("INV-0001"))
                .andExpect(jsonPath("$.updatedDocument.voidedAt", notNullValue()))
                .andExpect(jsonPath("$.updatedDocument.voidReason").value("Sent to the wrong client"))
                .andExpect(jsonPath("$.updatedDocument.overdue").value(false));
        send(get("/api/v1/document/{id}/activity", id), "")
                .andExpect(jsonPath("$.events[0].type").value("VOIDED"))
                .andExpect(jsonPath("$.events[0].detail").value("Sent to the wrong client"));
        mockMvc.perform(get("/api/v1/public/share/{token}", token))
                .andExpect(jsonPath("$.state").value("void"))
                .andExpect(jsonPath("$.voidReason").value("Sent to the wrong client"))
                .andExpect(jsonPath("$.balance").value(0))
                .andExpect(jsonPath("$.overdue").value(false));
        String text = PdfText.of(mockMvc.perform(get("/api/v1/document/{id}/pdf", id)
                        .header(HttpHeaders.AUTHORIZATION, session))
                .andReturn()
                .getResponse()
                .getContentAsByteArray());
        assertThat(text).contains("INV-0001", "VOID", "Sent to the wrong client").doesNotContain("Unpaid");
        voided(id, "Again")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("This document is already void"));
    }

    @Test
    @DisplayName("a void document can't be changed, sent, shared, paid or reminded about, but can be copied")
    void refusals() throws Exception {
        String id = document("Invoice", "100", false);
        voided(id, "Wrong amount").andExpect(status().isOk());

        send(patch("/api/v1/document/{id}", id), "{\"additionalInfo\": \"Hi\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("A void document can't be changed"));
        send(get("/api/v1/document/{id}/email", id), "")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("A void document isn't sent"));
        send(post("/api/v1/document/{id}/share", id), "")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("A void document can't be shared"));
        send(post("/api/v1/document/{id}/payment", id), "{\"amountPaid\": 10}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("A void document can't take payments"));
        send(get("/api/v1/document/{id}/remind", id), "")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("A void document needs no reminder"));
        send(post("/api/v1/document/{id}/duplicate", id), "")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.newDocument.state").value("draft"));
    }

    @Test
    @DisplayName("only an issued document without payments or a schedule is voided, for a reason, by an owner or admin")
    void blockers() throws Exception {
        voided(document("Invoice", "100", true), "Not needed")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("A draft isn't issued, so delete it instead"));
        String paid = document("Invoice", "100", false);
        send(post("/api/v1/document/{id}/payment", paid), "{\"amountPaid\": 10}").andExpect(status().isCreated());
        voided(paid, "Wrong amount")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Delete its payments before voiding it"));
        String repeating = document("Invoice", "100", false);
        send(post("/api/v1/recurring"), """
                {"documentId": "%s", "frequency": "monthly", "startDate": "%s"}
                """.formatted(repeating, LocalDate.now(ZONE).plusDays(1))).andExpect(status().isCreated());
        voided(repeating, "Wrong amount")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("This invoice repeats. Stop its schedule before voiding it"));

        String id = document("Invoice", "100", false);
        send(post("/api/v1/document/{id}/void", id), "{}").andExpect(status().isBadRequest());
        voided(id, "No").andExpect(status().isBadRequest());
        String member = bearer(createMember(businessOf(owner), BusinessRole.MEMBER), businessOf(owner));
        send(post("/api/v1/document/{id}/void", id), "{\"reason\": \"Wrong amount\"}", member)
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("a void quotation can't be accepted or become an invoice")
    void quotations() throws Exception {
        String quotation = document("Quotation", "900", false);
        String token = shareToken(quotation);
        voided(quotation, "Prices changed").andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/public/share/{token}", token))
                .andExpect(jsonPath("$.state").value("void"))
                .andExpect(jsonPath("$.canAccept").value(false))
                .andExpect(jsonPath("$.balance").value(900.0));
        mockMvc.perform(post("/api/v1/public/share/{token}/accept", token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("This quotation was withdrawn, so it can't be accepted"));
        send(post("/api/v1/document/{id}/convert", quotation), "")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("This quotation is void"));
    }

    @Test
    @DisplayName("a void document counts for nothing: not in balances, reports, the overview, statements or alerts")
    void leftOut() throws Exception {
        String kept = document("Invoice", "100", false);
        String voided = document("Invoice", "500", false);
        voided(voided, "Duplicate of INV-0001").andExpect(status().isOk());

        send(get("/api/v1/document/all").param("state", "void"), "")
                .andExpect(jsonPath("$.myDocuments[*]._id", contains(voided)));
        send(get("/api/v1/document/all").param("state", "issued"), "")
                .andExpect(jsonPath("$.myDocuments[*]._id", contains(kept)));
        send(get("/api/v1/document/all").param("overdue", "true"), "")
                .andExpect(jsonPath("$.myDocuments[*]._id", contains(kept)));
        send(get("/api/v1/document/all").param("status", "Not Paid"), "")
                .andExpect(jsonPath("$.myDocuments[*]._id", contains(kept)));
        assertThat(body(send(get("/api/v1/document/csv"), ""))).contains("INV-0002,Invoice,Void,Beta LLC,");

        send(get("/api/v1/report/overview"), "").andExpect(jsonPath("$.expectedIncome").value(100.0));
        send(get("/api/v1/report").param("period", "12m"), "")
                .andExpect(jsonPath("$.currencies[0].billed").value(100.0))
                .andExpect(jsonPath("$.currencies[0].outstanding").value(100.0));
        send(get("/api/v1/customer/{id}/summary", client), "")
                .andExpect(jsonPath("$.totals[0].billed").value(100.0))
                .andExpect(jsonPath("$.totals[0].overdue").value(100.0));
        String statement = PdfText.of(mockMvc.perform(get("/api/v1/customer/{id}/statement", client)
                        .header(HttpHeaders.AUTHORIZATION, session))
                .andReturn()
                .getResponse()
                .getContentAsByteArray());
        assertThat(statement).contains("INV-0001").doesNotContain("INV-0002");

        overdueAlerts.raise(Instant.now());
        send(get("/api/v1/notification"), "")
                .andExpect(jsonPath("$.notifications[*].message", contains(
                        "Invoice INV-0001 for Beta LLC is overdue, with USD 100.00 unpaid")));
        send(get("/api/v1/document/{id}", voided), "").andExpect(jsonPath("$.document.overdue").value(false));
        send(get("/api/v1/document/{id}", kept), "").andExpect(jsonPath("$.document.voidReason").doesNotExist());
    }
}
