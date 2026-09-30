package com.invoicespot.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
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
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

// The day a document is dated: today in its business's time zone unless an
// earlier day is chosen, never a day still to come, and the day every view
// of the document shows.
class DocumentIssueDateIntegrationTest extends IntegrationTest {

    // Fourteen hours ahead of UTC, so its today is often not UTC's.
    private static final ZoneId ZONE = ZoneId.of("Pacific/Kiritimati");

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
                {"defaultCurrency": "USD", "paymentTermsDays": 7, "defaultTaxRate": 0,
                 "taxLabel": "Tax", "timeZone": "Pacific/Kiritimati"}
                """).andExpect(status().isOk());
        client = JsonPath.read(
                body(send(post("/api/v1/customer/create"), """
                        {"name": "Beta LLC", "email": "%s@clients.test", "phoneNumber": "+1"}
                        """.formatted(UUID.randomUUID()))),
                "$.createdCustomer._id");
    }

    private ResultActions create(LocalDate issueDate) throws Exception {
        return send(post("/api/v1/document/create"), """
                {"customerId": "%s", "documentType": "Invoice", "currency": "USD"%s,
                 "billingItems": [{"itemName": "Work", "unitPrice": 100, "quantity": 1}]}
                """.formatted(client, issueDate == null ? "" : ", \"issueDate\": \"" + issueDate + "\""));
    }

    private String created(LocalDate issueDate) throws Exception {
        return JsonPath.read(body(create(issueDate).andExpect(status().isOk())), "$.newDocument._id");
    }

    private String draft() throws Exception {
        return JsonPath.read(
                body(send(post("/api/v1/document/create"), """
                        {"customerId": "%s", "documentType": "Invoice", "currency": "USD", "draft": true,
                         "billingItems": [{"itemName": "Work", "unitPrice": 100, "quantity": 1}]}
                        """.formatted(client)).andExpect(status().isOk())),
                "$.newDocument._id");
    }

    private byte[] download(String url, Object... variables) throws Exception {
        return mockMvc.perform(get(url, variables).header(HttpHeaders.AUTHORIZATION, session))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray();
    }

    @Test
    @DisplayName("a document is dated today in its business's time zone, unless an earlier day is chosen")
    void defaults() throws Exception {
        LocalDate today = LocalDate.now(ZONE);

        create(null).andExpect(jsonPath("$.newDocument.issueDate").value(today.toString()));
        create(today.minusDays(40))
                .andExpect(jsonPath("$.newDocument.issueDate").value(today.minusDays(40).toString()));
    }

    @Test
    @DisplayName("a day still to come is refused, when a document is made and when a draft is changed")
    void future() throws Exception {
        LocalDate tomorrow = LocalDate.now(ZONE).plusDays(1);

        create(tomorrow)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("A document can't be dated after today"));
        String id = draft();
        send(patch("/api/v1/document/{id}", id), "{\"issueDate\": \"" + tomorrow + "\"}")
                .andExpect(status().isBadRequest());
        send(patch("/api/v1/document/{id}", id), "{\"issueDate\": \"2026-01-15\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updatedDocument.issueDate").value("2026-01-15"));
    }

    @Test
    @DisplayName("the PDF, the share page, the CSV, the list's date filter and the statement all use it")
    void everywhere() throws Exception {
        LocalDate day = LocalDate.now(ZONE).minusDays(40);
        String id = created(day);
        String label = PdfService.DATE.format(day);

        assertThat(PdfText.of(download("/api/v1/document/{id}/pdf", id))).contains(label);
        String url = JsonPath.read(body(send(post("/api/v1/document/{id}/share", id), "")), "$.url");
        mockMvc.perform(get("/api/v1/public/share/{token}", url.substring(url.lastIndexOf('/') + 1)))
                .andExpect(jsonPath("$.issueDate").value(day.toString()));
        assertThat(new String(download("/api/v1/document/csv"))).contains("," + day + ",");
        mockMvc.perform(get("/api/v1/document/all")
                        .param("from", day.toString())
                        .param("to", day.toString())
                        .header(HttpHeaders.AUTHORIZATION, session))
                .andExpect(jsonPath("$.myDocuments[*]._id", hasItem(id)));
        mockMvc.perform(get("/api/v1/document/all")
                        .param("from", day.plusDays(1).toString())
                        .header(HttpHeaders.AUTHORIZATION, session))
                .andExpect(jsonPath("$.myDocuments[*]._id", not(hasItem(id))));
        assertThat(PdfText.of(download("/api/v1/customer/{id}/statement", client))).contains(label);
    }

    @Test
    @DisplayName("a copy is a draft with no date, dated the day it is issued")
    void copies() throws Exception {
        String id = created(LocalDate.now(ZONE).minusDays(40));

        String copy = JsonPath.read(
                body(send(post("/api/v1/document/{id}/duplicate", id), "")
                        .andExpect(jsonPath("$.newDocument.issueDate").value(nullValue()))),
                "$.newDocument._id");
        send(post("/api/v1/document/{id}/issue", copy), "{}")
                .andExpect(jsonPath("$.updatedDocument.issueDate").value(LocalDate.now(ZONE).toString()));
    }
}
