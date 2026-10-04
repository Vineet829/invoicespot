package com.invoicespot.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.invoicespot.IntegrationTest;
import com.invoicespot.user.User;
import com.jayway.jsonpath.JsonPath;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

class PaymentReceiptIntegrationTest extends IntegrationTest {

    private record Paid(String document, String email, List<String> payments) {}

    // An invoice of 100 paid in two parts, the later one recorded first.
    private Paid paidInvoice(User owner) throws Exception {
        String email = "beta-" + UUID.randomUUID() + "@clients.test";
        String customer = mockMvc.perform(post("/api/v1/customer/create")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Beta LLC\", \"email\": \"%s\", \"phoneNumber\": \"+1\"}"
                                .formatted(email)))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String created = mockMvc.perform(post("/api/v1/document/create")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId": "%s", "documentType": "Invoice", "currency": "USD",
                                 "billingItems": [{"itemName": "Work", "unitPrice": 100, "quantity": 1}]}
                                """.formatted((String) JsonPath.read(customer, "$.createdCustomer._id"))))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String document = JsonPath.read(created, "$.newDocument._id");
        for (String payment : List.of("{\"amountPaid\": 30, \"datePaid\": \"2026-09-20\"}",
                "{\"amountPaid\": 40, \"datePaid\": \"2026-09-10\"}")) {
            mockMvc.perform(post("/api/v1/document/{id}/payment", document)
                    .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payment));
        }
        String body = mockMvc.perform(get("/api/v1/document/{id}", document)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return new Paid(document, email, JsonPath.read(body, "$.document.paymentRecords[*]._id"));
    }

    @Test
    @DisplayName("each receipt is a PDF numbered in the order its payment was recorded, whatever the dates")
    void pdf() throws Exception {
        User owner = createUser();
        Paid paid = paidInvoice(owner);

        byte[] body = mockMvc.perform(get("/api/v1/document/{id}/payment/{payment}/receipt",
                                paid.document(), paid.payments().get(1))
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string(
                        HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"Receipt-INV-0001-P1.pdf\""))
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        assertThat(new String(body, 0, 5)).isEqualTo("%PDF-");
    }

    // The file name a payment's receipt is given, which carries its number.
    private String receiptName(User owner, Paid paid, String payment) throws Exception {
        return mockMvc.perform(get("/api/v1/document/{id}/payment/{payment}/receipt", paid.document(), payment)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getHeader(HttpHeaders.CONTENT_DISPOSITION);
    }

    @Test
    @DisplayName("each payment says its receipt's number, which isn't its place among the payments by date")
    void numberListed() throws Exception {
        User owner = createUser();
        Paid paid = paidInvoice(owner);

        mockMvc.perform(get("/api/v1/document/{id}", paid.document()).header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(jsonPath("$.document.paymentRecords[*].receiptNumber", contains(2, 1)));
        assertThat(receiptName(owner, paid, paid.payments().get(0))).contains("Receipt-INV-0001-P2.pdf");
    }

    @Test
    @DisplayName("a receipt keeps its number when its payment's date changes, and a deleted one's isn't given again")
    void numbersKept() throws Exception {
        User owner = createUser();
        Paid paid = paidInvoice(owner);
        String first = paid.payments().get(1);

        mockMvc.perform(patch("/api/v1/document/{id}/payment/{payment}", paid.document(), first)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"datePaid\": \"2026-09-01\", \"amountPaid\": 30}"))
                .andExpect(status().isOk());
        assertThat(receiptName(owner, paid, first)).contains("Receipt-INV-0001-P1.pdf");

        mockMvc.perform(delete("/api/v1/document/{id}/payment/{payment}", paid.document(), paid.payments().get(0))
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/document/{id}/payment", paid.document())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amountPaid\": 10, \"datePaid\": \"2026-09-05\"}"))
                .andExpect(status().isCreated());
        String body = mockMvc.perform(get("/api/v1/document/{id}", paid.document())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andReturn()
                .getResponse()
                .getContentAsString();
        List<String> payments = JsonPath.read(body, "$.document.paymentRecords[*]._id");
        String latest = payments.stream().filter(payment -> !payment.equals(first)).findFirst().orElseThrow();
        assertThat(receiptName(owner, paid, latest)).contains("Receipt-INV-0001-P3.pdf");
    }

    @Test
    @DisplayName("a receipt emails to the client, and the document's timeline records it")
    void email() throws Exception {
        User owner = createUser();
        Paid paid = paidInvoice(owner);
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage((Session) null));

        mockMvc.perform(get("/api/v1/document/{id}/payment/{payment}/receipt/email",
                                paid.document(), paid.payments().get(0))
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(jsonPath("$.to").value(paid.email()))
                .andExpect(jsonPath("$.subject").value("Payment receipt INV-0001-P2 from Test User"));
        mockMvc.perform(post("/api/v1/document/{id}/payment/{payment}/receipt/email",
                                paid.document(), paid.payments().get(0))
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Receipt sent successfully."));

        ArgumentCaptor<MimeMessage> sent = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(sent.capture());
        assertThat(sent.getValue().getAllRecipients()).extracting(Object::toString).containsExactly(paid.email());
        mockMvc.perform(get("/api/v1/document/{id}/activity", paid.document())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(jsonPath("$.events[0].type").value("RECEIPT_EMAILED"))
                .andExpect(jsonPath("$.events[0].detail").value("INV-0001-P2 to " + paid.email()));
    }

    @Test
    @DisplayName("only the owner gets a receipt, for a payment that exists, and the demo account can't email one")
    void rules() throws Exception {
        User owner = createUser();
        Paid paid = paidInvoice(owner);

        mockMvc.perform(get("/api/v1/document/{id}/payment/{payment}/receipt", paid.document(), paid.payments().get(0))
                        .header(HttpHeaders.AUTHORIZATION, bearer(createUser())))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/document/{id}/payment/{payment}/receipt", paid.document(), UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isNotFound());

        User demo = demoUser();
        Paid demoPaid = paidInvoice(demo);
        mockMvc.perform(post("/api/v1/document/{id}/payment/{payment}/receipt/email",
                                demoPaid.document(), demoPaid.payments().get(0))
                        .header(HttpHeaders.AUTHORIZATION, bearer(demo)))
                .andExpect(status().isForbidden());
        verify(mailSender, never()).send(any(MimeMessage.class));
    }
}
