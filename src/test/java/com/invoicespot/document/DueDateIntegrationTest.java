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
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

// A due date is a day in the business's calendar. It is sent and read as
// that day, whatever time zone the person typing or reading it is in, and
// kept as noon that day in the business's time zone.
class DueDateIntegrationTest extends IntegrationTest {

    private static final String MALFORMED = "Enter the due date as YYYY-MM-DD";

    @Autowired
    private JdbcTemplate jdbc;

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

    // A new business in the given time zone, with one client.
    private void business(String timeZone) throws Exception {
        session = bearer(createUser());
        send(put("/api/v1/settings"), """
                {"defaultCurrency": "USD", "paymentTermsDays": 15, "defaultTaxRate": 0,
                 "taxLabel": "Tax", "timeZone": "%s"}
                """.formatted(timeZone)).andExpect(status().isOk());
        client = JsonPath.read(
                body(send(post("/api/v1/customer/create"), """
                        {"name": "Beta LLC", "email": "%s@clients.test", "phoneNumber": "+1"}
                        """.formatted(UUID.randomUUID()))),
                "$.createdCustomer._id");
    }

    // Dated in January, so the only day in October its PDF names is the due date.
    private ResultActions invoice(boolean draft, String dueDate) throws Exception {
        return send(post("/api/v1/document/create"), """
                {"customerId": "%s", "documentType": "Invoice", "currency": "USD", "issueDate": "2026-01-15",
                 "draft": %s, "dueDate": "%s",
                 "billingItems": [{"itemName": "Work", "unitPrice": 100, "quantity": 1}]}
                """.formatted(client, draft, dueDate));
    }

    private String made(boolean draft, String dueDate) throws Exception {
        return JsonPath.read(body(invoice(draft, dueDate).andExpect(status().isOk())), "$.newDocument._id");
    }

    private Instant stored(String id) {
        return jdbc.queryForObject(
                        "select due_date from documents where id = ?", Timestamp.class, UUID.fromString(id))
                .toInstant();
    }

    private String pdf(String id) throws Exception {
        return PdfText.of(mockMvc.perform(get("/api/v1/document/{id}/pdf", id)
                        .header(HttpHeaders.AUTHORIZATION, session))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray());
    }

    // A share link or portal as its client opens it, from a network of its
    // own, so the visit counts against no other test's.
    private ResultActions open(String kind, String url) throws Exception {
        return mockMvc.perform(get("/api/v1/public/" + kind + "/{token}", url.substring(url.lastIndexOf('/') + 1))
                .with(request -> {
                    request.setRemoteAddr("10.60.0.1");
                    return request;
                }));
    }

    @Test
    @DisplayName("a due date sent as a day is that day in the business's calendar, kept as noon there")
    void day() throws Exception {
        business("Asia/Kolkata");

        String id = JsonPath.read(
                body(invoice(false, "2026-10-15")
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.newDocument.dueDate").value("2026-10-15"))),
                "$.newDocument._id");
        assertThat(stored(id)).isEqualTo(Instant.parse("2026-10-15T06:30:00Z"));
        assertThat(pdf(id)).contains("Oct 15, 2026");
        send(patch("/api/v1/document/{id}", id), "{\"dueDate\": \"2026-11-01\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updatedDocument.dueDate").value("2026-11-01"));

        String draft = made(true, "2026-10-15");
        send(post("/api/v1/document/{id}/issue", draft), "{\"dueDate\": \"2026-10-20\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updatedDocument.dueDate").value("2026-10-20"));
        assertThat(stored(draft)).isEqualTo(Instant.parse("2026-10-20T06:30:00Z"));
    }

    @Test
    @DisplayName("every view of a document sends its due date as its day in the business's calendar")
    void views() throws Exception {
        // Noon in Los Angeles, as the issuer, schedules and the demo keep a
        // due date: already the 16th in India.
        seenAs("America/Los_Angeles", "2026-10-15T19:00:00Z");
        // Noon in Auckland is still the 14th in UTC.
        seenAs("Pacific/Auckland", "2026-10-15");
    }

    // A business in the given zone with an invoice due on 15 October, sent as
    // given, shared and paid in part: each view sends that day.
    private void seenAs(String timeZone, String dueDate) throws Exception {
        business(timeZone);
        String id = made(false, dueDate);
        send(post("/api/v1/document/{id}/payment", id), "{\"amountPaid\": 50}").andExpect(status().isCreated());
        String share = JsonPath.read(body(send(post("/api/v1/document/{id}/share", id), "")), "$.url");
        String portal = JsonPath.read(
                body(send(post("/api/v1/customer/{id}/portal", client), "").andExpect(status().isCreated())),
                "$.url");

        send(get("/api/v1/document/{id}", id), "").andExpect(jsonPath("$.document.dueDate").value("2026-10-15"));
        send(get("/api/v1/document/all"), "").andExpect(jsonPath("$.myDocuments[0].dueDate").value("2026-10-15"));
        send(get("/api/v1/report/overview"), "")
                .andExpect(jsonPath("$.payments[0].document.dueDate").value("2026-10-15"));
        open("share", share).andExpect(jsonPath("$.dueDate").value("2026-10-15"));
        open("portal", portal).andExpect(jsonPath("$.documents[0].dueDate").value("2026-10-15"));
    }

    @Test
    @DisplayName("a moment sent by an older client keeps the day it falls on in the business's calendar")
    void moment() throws Exception {
        business("Asia/Kolkata");
        String id = made(false, "2026-10-10T06:30:00Z");

        // 19:00 UTC is half past midnight on the 16th in India.
        send(patch("/api/v1/document/{id}", id), "{\"dueDate\": \"2026-10-15T19:00:00Z\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updatedDocument.dueDate").value("2026-10-16"));
        assertThat(stored(id)).isEqualTo(Instant.parse("2026-10-16T06:30:00Z"));
        assertThat(pdf(id)).contains("Oct 16, 2026").doesNotContain("Oct 15, 2026");
    }

    @Test
    @DisplayName("a blank due date is no due date: a receipt takes it, and an invoice keeps the one it has")
    void blank() throws Exception {
        business("Asia/Kolkata");
        String receipt = JsonPath.read(
                body(send(post("/api/v1/document/create"), """
                                {"customerId": "%s", "documentType": "Receipt", "currency": "USD",
                                 "billingItems": [{"itemName": "Work", "unitPrice": 100, "quantity": 1}]}
                                """.formatted(client))
                        .andExpect(status().isOk())),
                "$.newDocument._id");
        String invoice = made(false, "2026-10-15");

        send(patch("/api/v1/document/{id}", receipt), "{\"dueDate\": \"\", \"additionalInfo\": \"Paid in full\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updatedDocument.additionalInfo").value("Paid in full"));
        send(patch("/api/v1/document/{id}", invoice), "{\"dueDate\": \" \"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updatedDocument.dueDate").value("2026-10-15"));
    }

    @Test
    @DisplayName("a due date that is neither a day nor a moment is refused, however it is sent")
    void malformed() throws Exception {
        business("Asia/Kolkata");
        String draft = made(true, "2026-10-10T06:30:00Z");

        invoice(false, "15/10/2026")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(MALFORMED));
        send(patch("/api/v1/document/{id}", draft), "{\"dueDate\": \"15/10/2026\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(MALFORMED));
        send(post("/api/v1/document/{id}/issue", draft), "{\"dueDate\": \"2026-10-32\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(MALFORMED));
    }
}
