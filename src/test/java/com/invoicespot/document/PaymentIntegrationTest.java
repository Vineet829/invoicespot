package com.invoicespot.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.invoicespot.IntegrationTest;
import com.invoicespot.user.User;
import com.jayway.jsonpath.JsonPath;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class PaymentIntegrationTest extends IntegrationTest {

    // UTC+14, the time zone furthest ahead: a day after its today is still to
    // come everywhere.
    private static final ZoneId FURTHEST_AHEAD = ZoneId.of("Pacific/Kiritimati");

    private String createDocument(User owner, String type) throws Exception {
        String customer = mockMvc.perform(post("/api/v1/customer/create")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Payer\", \"email\": \"payer-%s@clients.test\", \"phoneNumber\": \"+1 555 0100\"}"
                                .formatted(UUID.randomUUID())))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String body = mockMvc.perform(post("/api/v1/document/create")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId": "%s", "documentType": "%s", "currency": "USD", "rates": "0",
                                 "billingItems": [{"itemName": "Design", "unitPrice": 100, "quantity": 1, "discount": "0"}]}
                                """.formatted(JsonPath.read(customer, "$.createdCustomer._id"), type)))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(body, "$.newDocument._id");
    }

    private ResultActions pay(User owner, String documentId, String json) throws Exception {
        return mockMvc.perform(post("/api/v1/document/{id}/payment", documentId)
                .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private void timeZone(User owner, ZoneId zone) throws Exception {
        mockMvc.perform(put("/api/v1/settings")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"defaultCurrency": "USD", "paymentTermsDays": 7, "defaultTaxRate": 0,
                                 "taxLabel": "Tax", "timeZone": "%s"}
                                """.formatted(zone.getId())))
                .andExpect(status().isOk());
    }

    // A time zone whose today isn't UTC's right now: fourteen hours ahead of
    // UTC from 10:00 UTC, eleven hours behind it before then.
    private static ZoneId awayFromUtc() {
        return LocalDate.now(FURTHEST_AHEAD).equals(LocalDate.now(ZoneOffset.UTC))
                ? ZoneId.of("Pacific/Pago_Pago")
                : FURTHEST_AHEAD;
    }

    @Test
    @DisplayName("recording, editing and deleting payments keeps the amount received and status right")
    void paymentLifecycle() throws Exception {
        User owner = createUser();
        String documentId = createDocument(owner, "Invoice");

        String body = pay(owner, documentId, "{\"datePaid\": \"2026-09-30\", \"amountPaid\": 40, \"paymentMethod\": \"Bank Transfer\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.document.status").value("Not Fully Paid"))
                .andExpect(jsonPath("$.document.totalAmountReceived").value(40))
                .andExpect(jsonPath("$.document.paymentRecords[0].datePaid").value("2026-09-30"))
                .andExpect(jsonPath("$.document.paymentRecords[0].paidBy").value("Payer"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String paymentId = JsonPath.read(body, "$.document.paymentRecords[0]._id");

        mockMvc.perform(patch("/api/v1/document/{id}/payment/{paymentId}", documentId, paymentId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amountPaid\": 100}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.document.status").value("Paid"))
                .andExpect(jsonPath("$.document.totalAmountReceived").value(100));

        mockMvc.perform(delete("/api/v1/document/{id}/payment/{paymentId}", documentId, paymentId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.document.status").value("Not Paid"))
                .andExpect(jsonPath("$.document.paymentRecords").isEmpty());
    }

    @Test
    @DisplayName("a payment can come by UPI or by cheque, and its receipt says so")
    void upiAndCheque() throws Exception {
        User owner = createUser();
        String documentId = createDocument(owner, "Invoice");

        pay(owner, documentId, "{\"amountPaid\": 30, \"paymentMethod\": \"UPI\"}").andExpect(status().isCreated());
        String body = pay(owner, documentId, "{\"amountPaid\": 70, \"paymentMethod\": \"Cheque\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.document.paymentRecords[*].paymentMethod", contains("UPI", "Cheque")))
                .andExpect(jsonPath("$.document.status").value("Paid"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String payment = JsonPath.read(body, "$.document.paymentRecords[0]._id");

        byte[] receipt = mockMvc.perform(get("/api/v1/document/{id}/payment/{payment}/receipt", documentId, payment)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray();
        assertThat(PdfText.of(receipt)).contains("Paid by UPI");
        pay(owner, createDocument(owner, "Invoice"), "{\"amountPaid\": 10, \"paymentMethod\": \"Barter\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid payment method: Barter"));
    }

    @Test
    @DisplayName("a payment can't exceed what is still owed, on entry or on edit")
    void refusesOverpayment() throws Exception {
        User owner = createUser();
        String documentId = createDocument(owner, "Invoice");
        String body = pay(owner, documentId, "{\"amountPaid\": 60}").andReturn().getResponse().getContentAsString();
        String paymentId = JsonPath.read(body, "$.document.paymentRecords[0]._id");

        pay(owner, documentId, "{\"amountPaid\": 40.01}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("That is more than the 40.00 still owed"));
        mockMvc.perform(patch("/api/v1/document/{id}/payment/{paymentId}", documentId, paymentId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amountPaid\": 100.5}"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("quotations and receipts take no payments")
    void refusesQuotationAndReceipt() throws Exception {
        User owner = createUser();

        pay(owner, createDocument(owner, "Quotation"), "{\"amountPaid\": 10}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("A quotation can't take payments"));
        pay(owner, createDocument(owner, "Receipt"), "{\"amountPaid\": 10}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("A receipt is already paid in full"));
    }

    @Test
    @DisplayName("bad amounts and dates are refused, and a timestamp counts as its UTC date")
    void validatesInput() throws Exception {
        User owner = createUser();
        String documentId = createDocument(owner, "Invoice");

        pay(owner, documentId, "{\"amountPaid\": 0}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Enter an amount greater than zero"));
        pay(owner, documentId, "{\"amountPaid\": 1.005}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("An amount can have at most 2 decimals"));
        pay(owner, documentId, "{\"amountPaid\": 1234567890123456}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Amounts can have at most 15 whole digits"));
        pay(owner, documentId, "{\"amountPaid\": 5, \"tdsAmount\": 1E+20}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Amounts can have at most 15 whole digits"));
        pay(owner, documentId, "{\"amountPaid\": 5, \"datePaid\": \"30/09/2026\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Enter the payment date as YYYY-MM-DD"));
        pay(owner, documentId, "{\"amountPaid\": 5, \"datePaid\": \"2026-09-30T06:30:00.000Z\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.document.paymentRecords[0].datePaid").value("2026-09-30"));
    }

    @Test
    @DisplayName("another user can't touch the payments, and unknown payments are not found")
    void ownership() throws Exception {
        User owner = createUser();
        String documentId = createDocument(owner, "Invoice");
        String body = pay(owner, documentId, "{\"amountPaid\": 10}").andReturn().getResponse().getContentAsString();
        String paymentId = JsonPath.read(body, "$.document.paymentRecords[0]._id");

        mockMvc.perform(delete("/api/v1/document/{id}/payment/{paymentId}", documentId, paymentId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(createUser())))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/document/{id}/payment/{paymentId}", documentId, UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("That payment does not exist"));
    }

    @Test
    @DisplayName("a payment is dated no later than today somewhere, and today where the business is if no day is given")
    void notAfterToday() throws Exception {
        User owner = createUser();
        ZoneId zone = awayFromUtc();
        timeZone(owner, zone);
        LocalDate local = LocalDate.now(zone);
        LocalDate tomorrowFurthestAhead = LocalDate.now(FURTHEST_AHEAD).plusDays(1);
        String documentId = createDocument(owner, "Invoice");
        String cheque = "{\"datePaid\": \"%s\", \"amountPaid\": 40, \"paymentMethod\": \"Cheque\"}";

        for (LocalDate future : List.of(local.plusDays(2), tomorrowFurthestAhead)) {
            pay(owner, documentId, cheque.formatted(future))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("A payment can't be dated after today"));
        }
        String paymentId = JsonPath.read(
                pay(owner, documentId, cheque.formatted(local))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString(),
                "$.document.paymentRecords[0]._id");
        mockMvc.perform(patch("/api/v1/document/{id}/payment/{paymentId}", documentId, paymentId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"datePaid\": \"%s\"}".formatted(tomorrowFurthestAhead)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("A payment can't be dated after today"));
        // Without a day, it is dated today where the business is.
        pay(owner, documentId, "{\"amountPaid\": 10, \"paymentMethod\": \"Cash\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.document.paymentRecords[1].datePaid").value(local.toString()));
    }

    @Test
    @DisplayName("a payment dated today in UTC+14 is taken while it is still the day before where the business is")
    void todayAheadOfTheBusiness() throws Exception {
        User owner = createUser();
        // Ten hours behind UTC, so always a day behind UTC+14.
        timeZone(owner, ZoneId.of("Pacific/Honolulu"));
        LocalDate latest = LocalDate.now(FURTHEST_AHEAD);
        String documentId = createDocument(owner, "Invoice");
        String cheque = "{\"datePaid\": \"%s\", \"amountPaid\": 40, \"paymentMethod\": \"Cheque\"}";

        String paymentId = JsonPath.read(
                pay(owner, documentId, cheque.formatted(latest.minusDays(1)))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString(),
                "$.document.paymentRecords[0]._id");
        mockMvc.perform(patch("/api/v1/document/{id}/payment/{paymentId}", documentId, paymentId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"datePaid\": \"%s\"}".formatted(latest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.document.paymentRecords[0].datePaid").value(latest.toString()));
        pay(owner, documentId, cheque.formatted(latest))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.document.paymentRecords[1].datePaid").value(latest.toString()));
    }
}
