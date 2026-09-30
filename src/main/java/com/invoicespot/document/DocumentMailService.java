package com.invoicespot.document;

import com.invoicespot.common.ApiException;
import com.invoicespot.document.dto.EmailDocumentRequest;
import com.invoicespot.mail.MailService;
import com.invoicespot.ratelimit.RateLimit;
import com.invoicespot.ratelimit.RateLimiter;
import com.invoicespot.user.DemoAccountGuard;
import com.invoicespot.user.User;
import jakarta.mail.MessagingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Service;

@Service
public class DocumentMailService {

    private static final Logger LOGGER = LoggerFactory.getLogger(DocumentMailService.class);

    private final DocumentService documentService;
    private final MailService mailService;
    private final DemoAccountGuard demoAccountGuard;
    private final RateLimiter rateLimiter;

    public DocumentMailService(
            DocumentService documentService,
            MailService mailService,
            DemoAccountGuard demoAccountGuard,
            RateLimiter rateLimiter) {
        this.documentService = documentService;
        this.mailService = mailService;
        this.demoAccountGuard = demoAccountGuard;
        this.rateLimiter = rateLimiter;
    }

    public EmailDraft draft(User owner, String id) {
        return defaults(owner, documentService.envelope(owner, id));
    }

    public void email(User owner, String id, EmailDocumentRequest request) {
        demoAccountGuard.requireNotDemo(owner, "emailing");
        rateLimiter.consume(RateLimit.OUTGOING_EMAIL, owner.getId().toString());
        DocumentPdf pdf = documentService.renderPdf(owner, id);
        EmailDraft defaults = defaults(owner, pdf.envelope());
        String to = isBlank(request.to()) ? defaults.to() : request.to().trim();
        if (isBlank(to)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "This document's client has no email address");
        }
        String subject = isBlank(request.subject())
                ? defaults.subject()
                : request.subject().replaceAll("[\\r\\n]+", " ").trim();
        String message = isBlank(request.message()) ? defaults.message() : request.message();
        try {
            mailService.sendDocument(to, owner.getEmail(), subject, message, pdf.fileName(), pdf.content());
        } catch (MessagingException | MailException exception) {
            LOGGER.error("Failed to email document {}", id, exception);
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY, "The document could not be emailed. Please try again later.");
        }
    }

    private static EmailDraft defaults(User owner, DocumentEnvelope envelope) {
        String sender = PdfService.senderName(owner);
        String greeting = isBlank(envelope.recipientName()) ? "Hello," : "Hello " + envelope.recipientName() + ",";
        return new EmailDraft(
                envelope.recipientEmail(),
                envelope.title() + " from " + sender,
                greeting + "\n\nPlease find " + envelope.title() + " from " + sender + " attached.\n\n" + sender);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
