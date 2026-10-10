package com.invoicespot.document;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.invoicespot.IntegrationTest;
import com.invoicespot.business.Business;
import com.invoicespot.business.BusinessRole;
import com.invoicespot.user.User;
import com.jayway.jsonpath.JsonPath;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

// Payments clients report from a shared invoice, and what the business does
// with them. Each test reports from its own address, so the limit per network
// is its own.
class PaymentClaimIntegrationTest extends IntegrationTest {

    private static final AtomicInteger NETWORKS = new AtomicInteger();

    private final LocalDate today = LocalDate.now(ZoneOffset.UTC);
    // UTC+14, the time zone furthest ahead: a day after its today is still to
    // come everywhere.
    private final LocalDate latest = LocalDate.now(ZoneId.of("Pacific/Kiritimati"));
    private User owner;
    private String network;

    private record Shared(String id, String token) {}

    @BeforeEach
    void owner() {
        owner = createUser();
        network = "10.20." + NETWORKS.incrementAndGet() + ".1";
    }

    // An issued invoice of USD 200 for Beta LLC, shared by link.
    private Shared invoice() throws Exception {
        String customer = mockMvc.perform(post("/api/v1/customer/create")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Beta LLC\", \"email\": \"%s\", \"phoneNumber\": \"+1\"}"
                                .formatted("beta-" + UUID.randomUUID() + "@clients.test")))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String id = JsonPath.read(
                mockMvc.perform(post("/api/v1/document/create")
                                .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"customerId": "%s", "documentType": "Invoice", "currency": "USD",
                                         "billingItems": [{"itemName": "Design", "unitPrice": 100, "quantity": 2}]}
                                        """.formatted((String) JsonPath.read(customer, "$.createdCustomer._id"))))
                        .andReturn()
                        .getResponse()
                        .getContentAsString(),
                "$.newDocument._id");
        String url = JsonPath.read(
                mockMvc.perform(post("/api/v1/document/{id}/share", id)
                                .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                        .andReturn()
                        .getResponse()
                        .getContentAsString(),
                "$.url");
        return new Shared(id, url.substring(url.lastIndexOf('/') + 1));
    }

    private void anotherNetwork() {
        network = "10.21." + NETWORKS.incrementAndGet() + ".1";
    }

    private ResultActions report(Shared invoice, String json) throws Exception {
        return mockMvc.perform(post("/api/v1/public/share/{token}/payment-claims", invoice.token())
                .with(request -> {
                    request.setRemoteAddr(network);
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private String reported(String amount, LocalDate paidOn, String method, String reference) {
        String withReference = reference == null ? "" : ", \"reference\": \"" + reference + "\"";
        return """
                {"amount": %s, "paidOn": "%s", "method": "%s"%s}
                """.formatted(amount, paidOn, method, withReference);
    }

    private ResultActions claims(String bearer, Shared invoice) throws Exception {
        return mockMvc.perform(get("/api/v1/document/{id}/payment-claims", invoice.id())
                .header(HttpHeaders.AUTHORIZATION, bearer));
    }

    private String claimId(Shared invoice, int index) throws Exception {
        return JsonPath.read(
                claims(bearer(owner), invoice).andReturn().getResponse().getContentAsString(),
                "$.claims[" + index + "].id");
    }

    private ResultActions decide(String bearer, Shared invoice, String claim, String decision, String json)
            throws Exception {
        return mockMvc.perform(post("/api/v1/document/{id}/payment-claims/{claim}/" + decision, invoice.id(), claim)
                .header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private ResultActions document(Shared invoice) throws Exception {
        return mockMvc.perform(
                get("/api/v1/document/{id}", invoice.id()).header(HttpHeaders.AUTHORIZATION, bearer(owner)));
    }

    @Test
    @DisplayName("a client's report waits for the business: they hear of it, it's on the timeline, nothing is paid yet")
    void waits() throws Exception {
        Shared invoice = invoice();

        report(invoice, reported("150", today, "UPI", "UTR123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(200.0))
                .andExpect(jsonPath("$.canReportPayment").value(true))
                .andExpect(jsonPath("$.paymentReports[0].amount").value(150.0))
                .andExpect(jsonPath("$.paymentReports[0].method").value("UPI"))
                .andExpect(jsonPath("$.paymentReports[0].status").value("PENDING"));
        mockMvc.perform(get("/api/v1/notification").header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(jsonPath("$.notifications[0].type").value("PAYMENT_REPORTED"))
                .andExpect(jsonPath("$.notifications[0].message")
                        .value("Beta LLC reported paying USD 150.00 for Invoice INV-0001"));
        mockMvc.perform(get("/api/v1/document/{id}/activity", invoice.id())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(jsonPath("$.events[0].type").value("PAYMENT_REPORTED"))
                .andExpect(jsonPath("$.events[0].detail").value("USD 150.00 · UPI · Ref. UTR123"));
        claims(bearer(owner), invoice)
                .andExpect(jsonPath("$.claims[0].status").value("PENDING"))
                .andExpect(jsonPath("$.claims[0].reference").value("UTR123"));
        document(invoice).andExpect(jsonPath("$.document.paymentRecords").isEmpty());
    }

    @Test
    @DisplayName("a report that can't be right is refused: too much, with too many decimals, after today, a reference"
            + " twice, a fourth waiting")
    void refused() throws Exception {
        Shared invoice = invoice();

        report(invoice, reported("250", today, "UPI", null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("The amount can't be more than the USD 200.00 still to pay"));
        report(invoice, reported("50", latest.plusDays(1), "UPI", null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("The day you paid can't be after today"));
        report(invoice, reported("1.234", today, "UPI", null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Enter the amount with at most 15 whole digits and 2 decimals"));
        report(invoice, reported("50", today, "CARD", null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Choose how you paid"));
        report(invoice, reported("50", today, "UPI", "A1")).andExpect(status().isOk());
        report(invoice, reported("50", today, "BANK_TRANSFER", "a1"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("A payment with this reference was already reported"));

        anotherNetwork();
        report(invoice, reported("50", today, "BANK_TRANSFER", null)).andExpect(status().isOk());
        report(invoice, reported("50", today, "OTHER", null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canReportPayment").value(false));
        report(invoice, reported("50", today, "UPI", null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        "3 payments reported for this invoice are waiting to be confirmed."
                                + " Please wait until they are"));

        Shared voided = invoice();
        mockMvc.perform(post("/api/v1/document/{id}/void", voided.id())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\": \"Sent to the wrong client\"}"))
                .andExpect(status().isOk());
        report(voided, reported("50", today, "UPI", null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("This invoice has nothing left to pay"));
    }

    @Test
    @DisplayName("a client a day ahead of the business can report a payment made on their own today")
    void clientAheadOfBusiness() throws Exception {
        // Pago Pago, UTC-11, is always at least a day behind UTC+14.
        mockMvc.perform(put("/api/v1/settings")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"defaultCurrency": "USD", "paymentTermsDays": 7, "defaultTaxRate": 0,
                                 "taxLabel": "Tax", "timeZone": "Pacific/Pago_Pago"}
                                """))
                .andExpect(status().isOk());
        Shared invoice = invoice();

        report(invoice, reported("50", latest.plusDays(1), "UPI", null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("The day you paid can't be after today"));
        report(invoice, reported("50", latest, "UPI", null)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("a network reporting more than five payments an hour is asked to wait, refused reports counting too")
    void slowedDown() throws Exception {
        Shared first = invoice();
        Shared second = invoice();
        for (int report = 0; report < 3; report++) {
            report(first, reported("10", today, "UPI", null)).andExpect(status().isOk());
        }
        report(second, reported("10", today, "UPI", null)).andExpect(status().isOk());
        report(second, reported("500", today, "UPI", null)).andExpect(status().isBadRequest());

        report(second, reported("10", today, "UPI", null))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message", startsWith("Too many payments reported from this network")));
        anotherNetwork();
        report(second, reported("10", today, "UPI", null)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("confirming records the payment as reported or as changed, once; only members who record payments may")
    void confirm() throws Exception {
        Shared invoice = invoice();
        Business business = businessOf(owner);
        report(invoice, reported("150", today.minusDays(2), "UPI", "X9")).andExpect(status().isOk());
        String first = claimId(invoice, 0);

        decide(bearer(createMember(business, BusinessRole.VIEWER), business), invoice, first, "confirm", "{}")
                .andExpect(status().isForbidden());
        decide(bearer(createUser()), invoice, first, "confirm", "{}").andExpect(status().isNotFound());
        decide(bearer(owner), invoice, first, "confirm", "{}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("The payment was recorded"))
                .andExpect(jsonPath("$.claim.status").value("CONFIRMED"));
        document(invoice)
                .andExpect(jsonPath("$.document.paymentRecords[0].amountPaid").value(150.0))
                .andExpect(jsonPath("$.document.paymentRecords[0].paymentMethod").value("UPI"))
                .andExpect(jsonPath("$.document.paymentRecords[0].datePaid").value(today.minusDays(2).toString()))
                .andExpect(jsonPath("$.document.paymentRecords[0].additionalInfo").value(
                        "Reported by the client · Ref. X9"));
        decide(bearer(owner), invoice, first, "confirm", "{}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("This payment report was already confirmed"));

        report(invoice, reported("50", today, "BANK_TRANSFER", null)).andExpect(status().isOk());
        decide(bearer(owner), invoice, claimId(invoice, 0), "confirm", "{\"amountPaid\": 40, \"tdsAmount\": 10}")
                .andExpect(status().isOk());
        document(invoice)
                .andExpect(jsonPath("$.document.status").value("Paid"))
                .andExpect(jsonPath("$.document.paymentRecords[1].amountPaid").value(40.0))
                .andExpect(jsonPath("$.document.paymentRecords[1].paymentMethod").value("Bank Transfer"));
        mockMvc.perform(get("/api/v1/public/share/{token}", invoice.token()))
                .andExpect(jsonPath("$.canReportPayment").value(false))
                .andExpect(jsonPath("$.paymentReports[*].status", contains("CONFIRMED", "CONFIRMED")));
    }

    @Test
    @DisplayName("dismissing a report keeps its reason inside the business and changes nothing that is owed")
    void dismiss() throws Exception {
        Shared invoice = invoice();
        report(invoice, reported("80", today, "OTHER", null)).andExpect(status().isOk());

        decide(bearer(owner), invoice, claimId(invoice, 0), "dismiss", "{\"reason\": \"Not in the bank\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.claim.status").value("DISMISSED"))
                .andExpect(jsonPath("$.claim.dismissReason").value("Not in the bank"));
        mockMvc.perform(get("/api/v1/document/{id}/activity", invoice.id())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(jsonPath("$.events[0].type").value("PAYMENT_REPORT_DISMISSED"))
                .andExpect(jsonPath("$.events[0].detail").value("USD 80.00 · Another way · Not in the bank"));
        mockMvc.perform(get("/api/v1/public/share/{token}", invoice.token()))
                .andExpect(jsonPath("$.balance").value(200.0))
                .andExpect(jsonPath("$.paymentReports[0].status").value("DISMISSED"))
                .andExpect(content().string(not(containsString("Not in the bank"))));
    }

    @Test
    @DisplayName("reports waiting anywhere in the business are listed newest first, and only that business's")
    void waitingList() throws Exception {
        Shared first = invoice();
        Shared second = invoice();
        report(first, reported("30", today, "UPI", null)).andExpect(status().isOk());
        report(second, reported("40", today, "BANK_TRANSFER", null)).andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/payment-claims").header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.claims[*].documentNumber", contains("INV-0002", "INV-0001")))
                .andExpect(jsonPath("$.claims[0].client").value("Beta LLC"))
                .andExpect(jsonPath("$.claims[0].amount").value(40.0))
                .andExpect(jsonPath("$.claims[0].methodLabel").value("Bank transfer"))
                .andExpect(jsonPath("$.claims[0].documentId").value(second.id()));
        decide(bearer(owner), second, claimId(second, 0), "dismiss", "{}").andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/payment-claims").header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(jsonPath("$.claims[*].documentNumber", contains("INV-0001")));
        mockMvc.perform(get("/api/v1/payment-claims").header(HttpHeaders.AUTHORIZATION, bearer(createUser())))
                .andExpect(jsonPath("$.claims").isEmpty());
    }

    @Test
    @DisplayName("deleting a confirmed report's payment shows it as not confirmed, and the client may report it again")
    void paymentDeleted() throws Exception {
        Shared invoice = invoice();
        report(invoice, reported("150", today, "UPI", "X9")).andExpect(status().isOk());
        decide(bearer(owner), invoice, claimId(invoice, 0), "confirm", "{}").andExpect(status().isOk());
        String payment = JsonPath.read(
                document(invoice).andReturn().getResponse().getContentAsString(),
                "$.document.paymentRecords[0]._id");

        mockMvc.perform(delete("/api/v1/document/{id}/payment/{payment}", invoice.id(), payment)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/document/{id}/activity", invoice.id())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(jsonPath("$.events[0].type").value("PAYMENT_REPORT_DISMISSED"))
                .andExpect(jsonPath("$.events[0].detail")
                        .value("USD 150.00 · UPI · Ref. X9 · Its payment was deleted"))
                .andExpect(jsonPath("$.events[0].byYou").value(true))
                .andExpect(jsonPath("$.events[1].type").value("PAYMENT_DELETED"));
        claims(bearer(owner), invoice)
                .andExpect(jsonPath("$.claims[0].status").value("DISMISSED"))
                .andExpect(jsonPath("$.claims[0].dismissReason").value("Its payment was deleted"));
        mockMvc.perform(get("/api/v1/public/share/{token}", invoice.token()))
                .andExpect(jsonPath("$.balance").value(200.0))
                .andExpect(jsonPath("$.paymentReports[0].status").value("DISMISSED"))
                .andExpect(content().string(not(containsString("Its payment was deleted"))));
        report(invoice, reported("150", today, "UPI", "X9"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentReports[*].status", contains("PENDING", "DISMISSED")));
    }

    @Test
    @DisplayName("voiding an invoice dismisses the reports waiting on it, and the client sees they weren't confirmed")
    void documentVoided() throws Exception {
        Shared invoice = invoice();
        report(invoice, reported("30", today, "UPI", "V1")).andExpect(status().isOk());
        report(invoice, reported("40", today, "BANK_TRANSFER", null)).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/payment-claims").header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(jsonPath("$.claims[*].documentNumber", contains("INV-0001", "INV-0001")));

        mockMvc.perform(post("/api/v1/document/{id}/void", invoice.id())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\": \"Sent to the wrong client\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/payment-claims").header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(jsonPath("$.claims").isEmpty());
        claims(bearer(owner), invoice)
                .andExpect(jsonPath("$.claims[*].status", contains("DISMISSED", "DISMISSED")))
                .andExpect(jsonPath(
                        "$.claims[*].dismissReason", contains("The document was voided", "The document was voided")));
        mockMvc.perform(get("/api/v1/document/{id}/activity", invoice.id())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(jsonPath("$.events[0].type").value("PAYMENT_REPORT_DISMISSED"))
                .andExpect(jsonPath("$.events[0].detail")
                        .value("USD 40.00 · Bank transfer · The document was voided"))
                .andExpect(jsonPath("$.events[0].byYou").value(true))
                .andExpect(jsonPath("$.events[1].type").value("PAYMENT_REPORT_DISMISSED"))
                .andExpect(jsonPath("$.events[1].detail")
                        .value("USD 30.00 · UPI · Ref. V1 · The document was voided"))
                .andExpect(jsonPath("$.events[2].type").value("VOIDED"));
        mockMvc.perform(get("/api/v1/public/share/{token}", invoice.token()))
                .andExpect(jsonPath("$.state").value("void"))
                .andExpect(jsonPath("$.paymentReports[*].status", contains("DISMISSED", "DISMISSED")))
                .andExpect(content().string(not(containsString("The document was voided"))));
    }
}
