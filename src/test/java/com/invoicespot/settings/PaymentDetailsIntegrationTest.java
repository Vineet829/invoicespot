package com.invoicespot.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.invoicespot.IntegrationTest;
import com.invoicespot.business.Business;
import com.invoicespot.business.BusinessRole;
import com.invoicespot.document.PdfText;
import com.invoicespot.user.User;
import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class PaymentDetailsIntegrationTest extends IntegrationTest {

    private static final String DETAILS = """
            {"accountHolder": "Ada & Co", "bankName": "HDFC Bank", "accountNumber": "50100123456789",
             "bankCodeType": "IFSC", "bankCode": "hdfc0001234", "upiId": "Ada@OKHDFCBANK",
             "payLink": "https://pay.example.com/ada", "instructions": "Use the invoice number as the reference."}
            """;

    private User owner;

    @BeforeEach
    void owner() {
        owner = createUser();
    }

    private ResultActions save(String bearer, String json) throws Exception {
        return mockMvc.perform(put("/api/v1/settings/payment-details")
                .header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private ResultActions read(String bearer) throws Exception {
        return mockMvc.perform(get("/api/v1/settings/payment-details").header(HttpHeaders.AUTHORIZATION, bearer));
    }

    private String document(User user, String type, String currency) throws Exception {
        return document(user, type, currency, "");
    }

    private String document(User user, String type, String currency, String more) throws Exception {
        String customer = mockMvc.perform(post("/api/v1/customer/create")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Beta LLC\", \"email\": \"%s\", \"phoneNumber\": \"+1\"}"
                                .formatted("beta-" + UUID.randomUUID() + "@clients.test")))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String body = mockMvc.perform(post("/api/v1/document/create")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId": "%s", "documentType": "%s", "currency": "%s"%s,
                                 "billingItems": [{"itemName": "Design", "unitPrice": 100, "quantity": 2}]}
                                """.formatted(
                                        (String) JsonPath.read(customer, "$.createdCustomer._id"),
                                        type,
                                        currency,
                                        more)))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(body, "$.newDocument._id");
    }

    private ResultActions shared(User user, String id) throws Exception {
        String url = JsonPath.read(
                mockMvc.perform(post("/api/v1/document/{id}/share", id).header(HttpHeaders.AUTHORIZATION, bearer(user)))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString(),
                "$.url");
        return mockMvc.perform(get("/api/v1/public/share/{token}", url.substring(url.lastIndexOf('/') + 1)))
                .andExpect(status().isOk());
    }

    private String pdfText(User user, String id) throws Exception {
        return PdfText.of(mockMvc.perform(
                        get("/api/v1/document/{id}/pdf", id).header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray());
    }

    @Test
    @DisplayName("payment details are saved in their standard form, read by any member, and changed only by admins")
    void roundTrip() throws Exception {
        Business business = businessOf(owner);
        User member = createMember(business, BusinessRole.MEMBER);
        read(bearer(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentDetails.accountNumber").value(nullValue()));

        save(bearer(owner), DETAILS)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Your payment details were saved"))
                .andExpect(jsonPath("$.paymentDetails.bankCode").value("HDFC0001234"))
                .andExpect(jsonPath("$.paymentDetails.upiId").value("ada@okhdfcbank"));
        read(bearer(member, business))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentDetails.accountHolder").value("Ada & Co"))
                .andExpect(jsonPath("$.paymentDetails.bankCodeType").value("IFSC"))
                .andExpect(jsonPath("$.paymentDetails.payLink").value("https://pay.example.com/ada"));
        save(bearer(member, business), "{}").andExpect(status().isForbidden());

        save(bearer(owner), "{\"bankCodeType\": \"IFSC\", \"bankCode\": \"HDFC1001234\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "Enter an IFSC such as HDFC0001234: 4 letters, a zero, then 6 letters or digits"));
        save(bearer(owner), "{\"accountNumber\": \"%s\"}".formatted("1".repeat(35)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("The account number can be at most 34 characters"));
        read(bearer(owner)).andExpect(jsonPath("$.paymentDetails.bankCode").value("HDFC0001234"));

        save(bearer(owner), "{\"accountHolder\": \" \"}").andExpect(status().isOk());
        read(bearer(owner))
                .andExpect(jsonPath("$.paymentDetails.accountHolder").value(nullValue()))
                .andExpect(jsonPath("$.paymentDetails.bankCode").value(nullValue()))
                .andExpect(jsonPath("$.paymentDetails.upiId").value(nullValue()));
    }

    @Test
    @DisplayName("an unpaid invoice says how to pay it, on its share page and PDF, and a UPI ID only on rupee ones")
    void unpaidInvoices() throws Exception {
        save(bearer(owner), DETAILS).andExpect(status().isOk());
        String rupees = document(owner, "Invoice", "INR");

        shared(owner, rupees)
                .andExpect(jsonPath("$.payment.accountHolder").value("Ada & Co"))
                .andExpect(jsonPath("$.payment.bankName").value("HDFC Bank"))
                .andExpect(jsonPath("$.payment.accountNumber").value("50100123456789"))
                .andExpect(jsonPath("$.payment.bankCodeLabel").value("IFSC"))
                .andExpect(jsonPath("$.payment.bankCode").value("HDFC0001234"))
                .andExpect(jsonPath("$.payment.upiId").value("ada@okhdfcbank"))
                .andExpect(jsonPath("$.payment.payLink").value("https://pay.example.com/ada"))
                .andExpect(jsonPath("$.payment.instructions").value("Use the invoice number as the reference."));
        assertThat(pdfText(owner, rupees))
                .contains("HOW TO PAY", "Account holder", "Ada & Co", "50100123456789", "IFSC", "HDFC0001234")
                .contains("ada@okhdfcbank", "https://pay.example.com/ada", "Use the invoice number as the reference.");

        String dollars = document(owner, "Invoice", "USD");
        shared(owner, dollars)
                .andExpect(jsonPath("$.payment.accountNumber").value("50100123456789"))
                .andExpect(jsonPath("$.payment.upiId").value(nullValue()));
        assertThat(pdfText(owner, dollars)).contains("HOW TO PAY").doesNotContain("UPI ID", "ada@okhdfcbank");

        mockMvc.perform(post("/api/v1/document/{id}/payment", rupees)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amountPaid\": 200}"))
                .andExpect(status().isCreated());
        shared(owner, rupees).andExpect(jsonPath("$.payment").doesNotExist());
        assertThat(pdfText(owner, rupees)).doesNotContain("HOW TO PAY", "50100123456789");

        String quotation = document(owner, "Quotation", "INR");
        shared(owner, quotation).andExpect(jsonPath("$.payment").doesNotExist());
        assertThat(pdfText(owner, quotation)).doesNotContain("HOW TO PAY");

        User other = createUser();
        shared(other, document(other, "Invoice", "INR")).andExpect(jsonPath("$.payment").doesNotExist());
    }

    private ResultActions change(String id, String json) throws Exception {
        return mockMvc.perform(patch("/api/v1/document/{id}", id)
                .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    @Test
    @DisplayName("each invoice can leave out how to pay, and change that once issued, and its copies keep the choice")
    void perInvoice() throws Exception {
        save(bearer(owner), DETAILS).andExpect(status().isOk());
        String invoice = document(owner, "Invoice", "INR", ", \"showPaymentDetails\": false");

        mockMvc.perform(get("/api/v1/document/{id}", invoice).header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(jsonPath("$.document.showPaymentDetails").value(false));
        shared(owner, invoice).andExpect(jsonPath("$.payment").doesNotExist());
        assertThat(pdfText(owner, invoice)).doesNotContain("HOW TO PAY", "50100123456789");

        change(invoice, "{\"showPaymentDetails\": true}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updatedDocument.showPaymentDetails").value(true));
        shared(owner, invoice).andExpect(jsonPath("$.payment.accountNumber").value("50100123456789"));
        change(invoice, "{\"showPaymentDetails\": false}").andExpect(status().isOk());
        shared(owner, invoice).andExpect(jsonPath("$.payment").doesNotExist());

        mockMvc.perform(post("/api/v1/document/{id}/duplicate", invoice)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(jsonPath("$.newDocument.state").value("draft"))
                .andExpect(jsonPath("$.newDocument.showPaymentDetails").value(false));
        mockMvc.perform(get("/api/v1/document/{id}", document(owner, "Invoice", "INR"))
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(jsonPath("$.document.showPaymentDetails").value(true));
    }
}
