package com.invoicespot.document;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.notNullValue;
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
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

// A client answers a quotation once, through its link: they accept it, or
// decline it and may say why. A quotation left unanswered past its last day
// can't be answered until the business gives it a later one.
class QuotationAnswerIntegrationTest extends IntegrationTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");

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
                 "taxLabel": "Tax", "timeZone": "Asia/Kolkata"}
                """).andExpect(status().isOk());
        client = JsonPath.read(
                body(send(post("/api/v1/customer/create"), """
                        {"name": "Beta LLC", "email": "%s@clients.test", "phoneNumber": "+1"}
                        """.formatted(UUID.randomUUID()))),
                "$.createdCustomer._id");
    }

    private static String noon(LocalDate day) {
        return day.atTime(LocalTime.NOON).atZone(ZONE).toInstant().toString();
    }

    // A quotation valid until the given day, and the token of its share link.
    private String[] quotation(LocalDate validUntil) throws Exception {
        String id = JsonPath.read(
                body(send(post("/api/v1/document/create"), """
                        {"customerId": "%s", "documentType": "Quotation", "currency": "USD", "dueDate": "%s",
                         "billingItems": [{"itemName": "Retainer", "unitPrice": 900, "quantity": 1}]}
                        """.formatted(client, noon(validUntil))).andExpect(status().isOk())),
                "$.newDocument._id");
        String url = JsonPath.read(body(send(post("/api/v1/document/{id}/share", id), "")), "$.url");
        return new String[] {id, url.substring(url.lastIndexOf('/') + 1)};
    }

    private ResultActions answer(String token, String answer, String json) throws Exception {
        return mockMvc.perform(post("/api/v1/public/share/{token}/" + answer, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    @Test
    @DisplayName("a declined quotation says why, tells the business, and can't then be accepted or changed")
    void decline() throws Exception {
        String[] quotation = quotation(LocalDate.now(ZONE).plusDays(10));

        answer(quotation[1], "decline", "{\"reason\": \"  We found a cheaper offer  \"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.declinedAt", notNullValue()))
                .andExpect(jsonPath("$.canAccept").value(false))
                .andExpect(jsonPath("$.canDecline").value(false));
        answer(quotation[1], "decline", "").andExpect(status().isOk());
        answer(quotation[1], "accept", "")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("This quotation was declined"));

        send(get("/api/v1/document/{id}", quotation[0]), "")
                .andExpect(jsonPath("$.document.declinedAt", notNullValue()))
                .andExpect(jsonPath("$.document.declineReason").value("We found a cheaper offer"));
        send(get("/api/v1/document/{id}/activity", quotation[0]), "")
                .andExpect(jsonPath("$.events[0].type").value("DECLINED"))
                .andExpect(jsonPath("$.events[0].detail").value("We found a cheaper offer"));
        send(get("/api/v1/notification"), "")
                .andExpect(jsonPath("$.notifications[*].type", contains("QUOTATION_DECLINED")))
                .andExpect(jsonPath("$.notifications[0].message")
                        .value("Beta LLC declined Quotation QUO-0001: We found a cheaper offer"));
        send(patch("/api/v1/document/{id}", quotation[0]), "{\"additionalInfo\": \"Revised\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("A declined quotation can't be changed"));
    }

    @Test
    @DisplayName("an accepted quotation can't then be declined; a long reason or an invoice is refused")
    void acceptedOnce() throws Exception {
        String[] quotation = quotation(LocalDate.now(ZONE).plusDays(10));

        answer(quotation[1], "decline", "{\"reason\": \"%s\"}".formatted("x".repeat(501)))
                .andExpect(status().isBadRequest());
        answer(quotation[1], "accept", "").andExpect(status().isOk());
        answer(quotation[1], "decline", "")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("This quotation was already accepted"));

        String invoice = JsonPath.read(
                body(send(post("/api/v1/document/create"), """
                        {"customerId": "%s", "billingItems": [{"itemName": "Work", "unitPrice": 1, "quantity": 1}]}
                        """.formatted(client))),
                "$.newDocument._id");
        String url = JsonPath.read(body(send(post("/api/v1/document/{id}/share", invoice), "")), "$.url");
        answer(url.substring(url.lastIndexOf('/') + 1), "decline", "")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Only a quotation can be declined"));
    }

    @Test
    @DisplayName("a quotation is open on its last day, expired the day after, and open again with a later date")
    void expiry() throws Exception {
        LocalDate today = LocalDate.now(ZONE);
        String[] lastDay = quotation(today);
        mockMvc.perform(get("/api/v1/public/share/{token}", lastDay[1]))
                .andExpect(jsonPath("$.expired").value(false))
                .andExpect(jsonPath("$.canAccept").value(true));

        String[] quotation = quotation(today.minusDays(1));
        mockMvc.perform(get("/api/v1/public/share/{token}", quotation[1]))
                .andExpect(jsonPath("$.expired").value(true))
                .andExpect(jsonPath("$.canAccept").value(false))
                .andExpect(jsonPath("$.canDecline").value(false));
        send(get("/api/v1/document/{id}", quotation[0]), "").andExpect(jsonPath("$.document.expired").value(true));
        String expired = "This quotation expired on " + PdfService.DATE.format(today.minusDays(1))
                + ". Ask Test User for a new one";
        answer(quotation[1], "accept", "")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(expired));
        answer(quotation[1], "decline", "")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(expired));

        send(patch("/api/v1/document/{id}", quotation[0]), "{\"dueDate\": \"" + noon(today.plusDays(7)) + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updatedDocument.expired").value(false));
        answer(quotation[1], "accept", "").andExpect(status().isOk()).andExpect(jsonPath("$.expired").value(false));
    }
}
