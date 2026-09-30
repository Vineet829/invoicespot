package com.invoicespot.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.invoicespot.IntegrationTest;
import com.invoicespot.user.User;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

class DocumentPdfIntegrationTest extends IntegrationTest {

    @Autowired
    private DocumentRepository documentRepository;

    // An issued invoice, made out by the owner's business as it is now.
    private Document documentFor(User owner, String clientEmail) {
        Document document = new Document();
        document.setBusiness(businessOf(owner));
        document.setSeller(SellerSnapshot.of(document.getBusiness()));
        document.setTaxLabel("Tax");
        document.setIssuedAt(Instant.parse("2026-09-30T06:00:00Z"));
        document.setCreatedBy(owner);
        CustomerSnapshot customer = new CustomerSnapshot();
        customer.setName("Beta LLC");
        customer.setEmail(clientEmail);
        document.setCustomerSnapshot(customer);
        document.setDocumentNumber("2026-September-ABC123");
        document.setIssueDate(LocalDate.of(2026, 9, 30));
        document.setCurrency("USD");
        document.setSubTotal(new BigDecimal("100"));
        document.setSalesTax(new BigDecimal("18"));
        document.setRates(new BigDecimal("18"));
        document.setTotal(new BigDecimal("118"));
        document.setTotalAmountReceived(BigDecimal.ZERO);
        BillingItem item = new BillingItem();
        item.setItemName("Consulting");
        item.setUnitPrice(new BigDecimal("100"));
        item.setQuantity(1);
        item.setDiscount(BigDecimal.ZERO);
        document.getBillingItems().add(item);
        return documentRepository.save(document);
    }

    @Test
    @DisplayName("PDF routes, old and new, refuse requests without a sign-in")
    void anonymousRequestsAreRejected() throws Exception {
        Document document = documentFor(createUser(), "beta@x.com");

        mockMvc.perform(get("/api/v1/document/{id}/pdf", document.getId())).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/document/{id}/email", document.getId())).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/document/send-pdf").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/document/get-pdf")).andExpect(status().isUnauthorized());
        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("the owner gets their document as an inline PDF named after its number")
    void ownerDownloadsPdf() throws Exception {
        User owner = createUser();
        Document document = documentFor(owner, "beta@x.com");

        byte[] body = mockMvc.perform(get("/api/v1/document/{id}/pdf", document.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string(
                        HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"2026-September-ABC123.pdf\""))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        assertThat(new String(body, 0, 5)).isEqualTo("%PDF-");
    }

    @Test
    @DisplayName("another user can neither download nor email someone else's document")
    void otherUsersAreForbidden() throws Exception {
        Document document = documentFor(createUser(), "beta@x.com");
        String stranger = bearer(createUser());

        mockMvc.perform(get("/api/v1/document/{id}/pdf", document.getId()).header(HttpHeaders.AUTHORIZATION, stranger))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("You are not authorized to view this document. It's not yours"));
        mockMvc.perform(post("/api/v1/document/{id}/email", document.getId())
                        .header(HttpHeaders.AUTHORIZATION, stranger))
                .andExpect(status().isForbidden());
        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("emailing sends the PDF to the document's client, with replies going to the owner")
    void ownerEmailsDocument() throws Exception {
        User owner = createUser();
        Document document = documentFor(owner, "beta@x.com");
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage((Session) null));

        mockMvc.perform(post("/api/v1/document/{id}/email", document.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        ArgumentCaptor<MimeMessage> sent = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(sent.capture());
        MimeMessage message = sent.getValue();
        assertThat(message.getAllRecipients()).extracting(Object::toString).containsExactly("beta@x.com");
        assertThat(message.getReplyTo()).extracting(Object::toString).containsExactly(owner.getEmail());
        assertThat(message.getSubject()).isEqualTo("Invoice 2026-September-ABC123 from Test User");
    }

    @Test
    @DisplayName("a document whose client has no email address cannot be emailed")
    void clientWithoutEmail() throws Exception {
        User owner = createUser();
        Document document = documentFor(owner, " ");

        mockMvc.perform(post("/api/v1/document/{id}/email", document.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("This document's client has no email address"));
    }

    @Test
    @DisplayName("the email draft offers the client's address and names the document and sender")
    void emailDraft() throws Exception {
        User owner = createUser();
        Document document = documentFor(owner, "beta@x.com");

        mockMvc.perform(get("/api/v1/document/{id}/email", document.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.to").value("beta@x.com"))
                .andExpect(jsonPath("$.subject").value("Invoice 2026-September-ABC123 from Test User"))
                .andExpect(jsonPath("$.message").value(startsWith("Hello Beta LLC,")));
    }

    @Test
    @DisplayName("a chosen recipient and subject replace the defaults, and line breaks never reach the subject")
    void customEmail() throws Exception {
        User owner = createUser();
        Document document = documentFor(owner, "beta@x.com");
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage((Session) null));

        mockMvc.perform(post("/api/v1/document/{id}/email", document.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"to": "accounts@beta.test",
                                 "subject": "Your invoice\\r\\nBcc: someone@else.test",
                                 "message": "Hi team, the invoice is attached."}
                                """))
                .andExpect(status().isOk());

        ArgumentCaptor<MimeMessage> sent = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(sent.capture());
        MimeMessage message = sent.getValue();
        assertThat(message.getAllRecipients()).extracting(Object::toString).containsExactly("accounts@beta.test");
        assertThat(message.getSubject()).isEqualTo("Your invoice Bcc: someone@else.test");
    }

    @Test
    @DisplayName("an invalid recipient address is refused before anything is sent")
    void invalidRecipient() throws Exception {
        User owner = createUser();
        Document document = documentFor(owner, "beta@x.com");

        mockMvc.perform(post("/api/v1/document/{id}/email", document.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"to\": \"not-an-address\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Enter a valid email address"));
        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("the email draft follows the saved message, with the client, document and business filled in")
    void savedMessage() throws Exception {
        User owner = createUser();
        Document document = documentFor(owner, "beta@x.com");
        mockMvc.perform(put("/api/v1/settings")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"defaultCurrency": "USD", "paymentTermsDays": 7, "defaultTaxRate": 0, "taxLabel": "Tax",
                                 "emailMessage": "Hi {client}, {document} is attached. {business}", "timeZone": "UTC"}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/document/{id}/email", document.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(jsonPath("$.message").value(
                        "Hi Beta LLC, Invoice 2026-September-ABC123 is attached. Test User"));
    }
}
