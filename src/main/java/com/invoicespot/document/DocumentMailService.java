package com.invoicespot.document;

import com.invoicespot.common.ApiException;
import com.invoicespot.document.dto.EmailDocumentRequest;
import com.invoicespot.mail.MailService;
import com.invoicespot.ratelimit.RateLimit;
import com.invoicespot.ratelimit.RateLimiter;
import com.invoicespot.settings.BusinessSettings;
import com.invoicespot.settings.SettingsService;
import com.invoicespot.user.DemoAccountGuard;
import com.invoicespot.user.User;
import jakarta.mail.MessagingException;
import java.util.function.Supplier;
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
    private final SettingsService settingsService;
    private final StatementService statementService;

    public DocumentMailService(
            DocumentService documentService,
            MailService mailService,
            DemoAccountGuard demoAccountGuard,
            RateLimiter rateLimiter,
            SettingsService settingsService,
            StatementService statementService) {
        this.documentService = documentService;
        this.mailService = mailService;
        this.demoAccountGuard = demoAccountGuard;
        this.rateLimiter = rateLimiter;
        this.settingsService = settingsService;
        this.statementService = statementService;
    }

    public EmailDraft draft(User owner, String id) {
        return defaults(owner, documentService.envelope(owner, id), settingsService.forUser(owner.getPkid()));
    }

    public void email(User owner, String id, EmailDocumentRequest request) {
        send(owner, () -> documentService.renderPdf(owner, id), request,
                "This document's client has no email address", "document", id);
    }

    public EmailDraft statementDraft(User owner, String customerId) {
        return defaults(
                owner, statementService.envelope(owner, customerId), settingsService.forUser(owner.getPkid()));
    }

    public void emailStatement(User owner, String customerId, EmailDocumentRequest request) {
        send(owner, () -> statementService.render(owner, customerId), request,
                "This client has no email address", "statement", customerId);
    }

    private void send(
            User owner,
            Supplier<DocumentPdf> render,
            EmailDocumentRequest request,
            String noRecipient,
            String kind,
            String id) {
        demoAccountGuard.requireNotDemo(owner, "emailing");
        rateLimiter.consume(RateLimit.OUTGOING_EMAIL, owner.getId().toString());
        DocumentPdf pdf = render.get();
        EmailDraft defaults = defaults(owner, pdf.envelope(), settingsService.forUser(owner.getPkid()));
        String to = isBlank(request.to()) ? defaults.to() : request.to().trim();
        if (isBlank(to)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, noRecipient);
        }
        String subject = isBlank(request.subject())
                ? defaults.subject()
                : request.subject().replaceAll("[\\r\\n]+", " ").trim();
        String message = isBlank(request.message()) ? defaults.message() : request.message();
        try {
            mailService.sendDocument(to, owner.getEmail(), subject, message, pdf.fileName(), pdf.content());
        } catch (MessagingException | MailException exception) {
            LOGGER.error("Failed to email {} {}", kind, id, exception);
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY, "The " + kind + " could not be emailed. Please try again later.");
        }
    }

    private static EmailDraft defaults(User owner, DocumentEnvelope envelope, BusinessSettings settings) {
        String sender = PdfService.senderName(owner);
        String client = isBlank(envelope.recipientName()) ? "there" : envelope.recipientName();
        String message = isBlank(settings.getEmailMessage())
                ? "Hello {client},\n\nPlease find {document} from {business} attached.\n\n{business}"
                : settings.getEmailMessage();
        return new EmailDraft(
                envelope.recipientEmail(),
                envelope.title() + " from " + sender,
                message.replace("{client}", client)
                        .replace("{document}", envelope.title())
                        .replace("{business}", sender));
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
