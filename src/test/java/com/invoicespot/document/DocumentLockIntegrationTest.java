package com.invoicespot.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.invoicespot.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

// An issued document keeps what it went out with: its client, lines,
// currency, tax, issue date and amounts. Its due date, notes and terms still
// change, and only a draft is deleted.
class DocumentLockIntegrationTest extends IntegrationTest {

    private static final String LOCKED =
            "An issued document keeps its client, lines, tax and issue date. Void it and duplicate it to change them";

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

    @BeforeEach
    void setUp() throws Exception {
        session = bearer(createUser());
        client = JsonPath.read(
                body(send(post("/api/v1/customer/create"), """
                        {"name": "Beta LLC", "email": "%s@clients.test", "phoneNumber": "+1"}
                        """.formatted(UUID.randomUUID()))),
                "$.createdCustomer._id");
    }

    private String document(String type, boolean draft) throws Exception {
        return JsonPath.read(
                body(send(post("/api/v1/document/create"), """
                        {"customerId": "%s", "documentType": "%s", "currency": "USD", "rates": "10", "draft": %s,
                         "billingItems": [{"itemName": "Work", "unitPrice": 100, "quantity": 1}]}
                        """.formatted(client, type, draft)).andExpect(status().isOk())),
                "$.newDocument._id");
    }

    private ResultActions change(String id, String json) throws Exception {
        return send(patch("/api/v1/document/{id}", id), json);
    }

    @Test
    @DisplayName("an issued document keeps its client, type, lines, currency, tax and issue date")
    void locked() throws Exception {
        String id = document("Invoice", false);

        for (String json : List.of(
                "{\"customerId\": \"" + client + "\"}",
                "{\"customer\": {\"name\": \"Someone else\"}}",
                "{\"documentType\": \"Quotation\"}",
                "{\"issueDate\": \"2026-01-15\"}",
                "{\"rates\": \"10\"}",
                "{\"currency\": \"EUR\"}",
                "{\"billingItems\": [{\"itemName\": \"Work\", \"unitPrice\": 100, \"quantity\": 1}]}")) {
            change(id, json)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value(LOCKED));
        }
        send(get("/api/v1/document/{id}", id), "")
                .andExpect(jsonPath("$.document.total").value(110.0))
                .andExpect(jsonPath("$.document.currency").value("USD"));
    }

    @Test
    @DisplayName("its due date, notes and terms still change, and a draft changes freely")
    void details() throws Exception {
        String id = document("Invoice", false);

        change(id, """
                {"dueDate": "2026-12-01T12:00:00Z", "additionalInfo": "Thanks", "termsConditions": "Net 30"}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updatedDocument.dueDate").value("2026-12-01"))
                .andExpect(jsonPath("$.updatedDocument.additionalInfo").value("Thanks"))
                .andExpect(jsonPath("$.updatedDocument.termsConditions").value("Net 30"))
                .andExpect(jsonPath("$.updatedDocument.total").value(110.0));
        change(document("Invoice", true), """
                {"currency": "EUR", "rates": "20",
                 "billingItems": [{"itemName": "Work", "unitPrice": 200, "quantity": 1}]}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updatedDocument.currency").value("EUR"))
                .andExpect(jsonPath("$.updatedDocument.total").value(240.0));
    }

    @Test
    @DisplayName("a receipt has no due date to change, and an accepted quotation stays as it was")
    void receiptsAndQuotations() throws Exception {
        String receipt = document("Receipt", false);
        change(receipt, "{\"dueDate\": \"2026-12-01T12:00:00Z\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("A receipt has no due date"));
        change(receipt, "{\"additionalInfo\": \"Paid in cash\"}").andExpect(status().isOk());

        String quotation = document("Quotation", false);
        String url = JsonPath.read(body(send(post("/api/v1/document/{id}/share", quotation), "")), "$.url");
        mockMvc.perform(post("/api/v1/public/share/{token}/accept", url.substring(url.lastIndexOf('/') + 1)))
                .andExpect(status().isOk());
        change(quotation, "{\"additionalInfo\": \"Thanks\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("An accepted quotation can't be changed"));
    }

    @Test
    @DisplayName("a receipt never takes a due date: not made, saved as a draft, turned into one, or issued")
    void receiptsHaveNoDueDate() throws Exception {
        String due = "\"dueDate\": \"2026-12-01T12:00:00Z\"";
        String made = JsonPath.read(
                body(send(post("/api/v1/document/create"), """
                                {"customerId": "%s", "documentType": "Receipt", "currency": "USD", %s,
                                 "billingItems": [{"itemName": "Work", "unitPrice": 100, "quantity": 1}]}
                                """.formatted(client, due))
                        .andExpect(jsonPath("$.newDocument.dueDate").value(nullValue()))),
                "$.newDocument._id");
        assertThat(made).isNotBlank();

        String draft = document("Receipt", true);
        change(draft, "{" + due + "}").andExpect(jsonPath("$.updatedDocument.dueDate").value(nullValue()));
        String invoice = document("Invoice", true);
        change(invoice, "{" + due + "}")
                .andExpect(jsonPath("$.updatedDocument.dueDate").value("2026-12-01"));
        change(invoice, "{\"documentType\": \"Receipt\"}")
                .andExpect(jsonPath("$.updatedDocument.dueDate").value(nullValue()));
        send(post("/api/v1/document/{id}/issue", draft), "{" + due + "}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updatedDocument.dueDate").value(nullValue()));
    }

    @Test
    @DisplayName("only a draft is deleted: an issued document is voided instead, and a void one is kept")
    void deleting() throws Exception {
        String issued = document("Invoice", false);
        send(delete("/api/v1/document/{id}", issued), "")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("An issued document can't be deleted. Void it instead"));
        send(post("/api/v1/document/{id}/void", issued), "{\"reason\": \"Wrong client\"}").andExpect(status().isOk());
        send(delete("/api/v1/document/{id}", issued), "")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("A void document is kept with its number"));

        String draft = document("Invoice", true);
        send(delete("/api/v1/document/{id}", draft), "").andExpect(status().isOk());
        send(get("/api/v1/document/{id}", draft), "").andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("payments settle an issued document without working its amounts out again")
    void amountsKept() throws Exception {
        String id = document("Invoice", false);
        // An amount worked out differently when the document was made.
        jdbc.update("update documents set total = 110.01 where id = ?", UUID.fromString(id));

        String payment = JsonPath.read(
                body(send(post("/api/v1/document/{id}/payment", id), "{\"amountPaid\": 50}")
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.document.total").value(110.01))
                        .andExpect(jsonPath("$.document.totalAmountReceived").value(50.0))
                        .andExpect(jsonPath("$.document.status").value("Not Fully Paid"))),
                "$.document.paymentRecords[0]._id");
        send(patch("/api/v1/document/{id}/payment/{payment}", id, payment), "{\"amountPaid\": 110.01}")
                .andExpect(jsonPath("$.document.total").value(110.01))
                .andExpect(jsonPath("$.document.status").value("Paid"));
        send(delete("/api/v1/document/{id}/payment/{payment}", id, payment), "")
                .andExpect(jsonPath("$.document.total").value(110.01))
                .andExpect(jsonPath("$.document.totalAmountReceived").value(0.0))
                .andExpect(jsonPath("$.document.status").value("Not Paid"));
    }
}
