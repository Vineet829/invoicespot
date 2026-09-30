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
import java.util.function.Consumer;
import java.util.function.Function;
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
                "This document's client has no email address", "document", id,
                to -> documentService.recordEmailed(owner, id, to));
    }

    public EmailDraft receiptDraft(User owner, String id, String paymentId) {
        return defaults(
                owner,
                documentService.receiptEnvelope(owner, id, paymentId),
                settingsService.forUser(owner.getPkid()));
    }

    public void emailReceipt(User owner, String id, String paymentId, EmailDocumentRequest request) {
        send(owner, () -> documentService.renderReceipt(owner, id, paymentId), request,
                "This document's client has no email address", "receipt", paymentId,
                to -> documentService.recordReceiptEmailed(owner, id, paymentId, to));
    }

    public EmailDraft reminderDraft(User owner, String id) {
        return reminder(owner, documentService.reminderFacts(owner, id));
    }

    public void remind(User owner, String id, EmailDocumentRequest request) {
        ReminderFacts facts = documentService.reminderFacts(owner, id);
        send(owner, () -> documentService.renderPdf(owner, id), pdf -> reminder(owner, facts), request,
                "This document's client has no email address", "reminder", id,
                to -> documentService.recordReminder(owner, id, to, false));
    }

    // An automatic reminder: no rate limit, since the reminder settings bound
    // it, and a failure is thrown for the daily job to log.
    public void remindAutomatically(User owner, String id) throws MessagingException {
        ReminderFacts facts = documentService.reminderFacts(owner, id);
        DocumentPdf pdf = documentService.renderPdf(owner, id);
        EmailDraft draft = reminder(owner, facts);
        mailService.sendDocument(
                draft.to(), owner.getEmail(), draft.subject(), draft.message(), pdf.fileName(), pdf.content());
        documentService.recordReminder(owner, id, draft.to(), true);
    }

    // A recurring invoice sent as it is made: the standard message, no rate
    // limit, and a failure thrown for the daily job to log.
    public void emailAutomatically(User owner, String id) throws MessagingException {
        DocumentPdf pdf = documentService.renderPdf(owner, id);
        EmailDraft draft = defaults(owner, pdf.envelope(), settingsService.forUser(owner.getPkid()));
        if (isBlank(draft.to())) {
            throw new MessagingException("The client of document " + id + " has no email address");
        }
        mailService.sendDocument(
                draft.to(), owner.getEmail(), draft.subject(), draft.message(), pdf.fileName(), pdf.content());
        documentService.recordEmailedAutomatically(owner, id, draft.to());
    }

    public EmailDraft statementDraft(User owner, String customerId) {
        return defaults(
                owner, statementService.envelope(owner, customerId), settingsService.forUser(owner.getPkid()));
    }

    public void emailStatement(User owner, String customerId, EmailDocumentRequest request) {
        send(owner, () -> statementService.render(owner, customerId), request,
                "This client has no email address", "statement", customerId, to -> {});
    }

    private void send(
            User owner,
            Supplier<DocumentPdf> render,
            EmailDocumentRequest request,
            String noRecipient,
            String kind,
            String id,
            Consumer<String> sent) {
        send(owner, render, pdf -> defaults(owner, pdf.envelope(), settingsService.forUser(owner.getPkid())),
                request, noRecipient, kind, id, sent);
    }

    private void send(
            User owner,
            Supplier<DocumentPdf> render,
            Function<DocumentPdf, EmailDraft> draft,
            EmailDocumentRequest request,
            String noRecipient,
            String kind,
            String id,
            Consumer<String> sent) {
        demoAccountGuard.requireNotDemo(owner, "emailing");
        rateLimiter.consume(RateLimit.OUTGOING_EMAIL, owner.getId().toString());
        DocumentPdf pdf = render.get();
        EmailDraft defaults = draft.apply(pdf);
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
        sent.accept(to);
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

    // A reminder names what is still owed and when it was, or is, due.
    private static EmailDraft reminder(User owner, ReminderFacts facts) {
        String sender = PdfService.senderName(owner);
        String client = isBlank(facts.clientName()) ? "there" : facts.clientName();
        String due = facts.due() == null
                ? " is still open"
                : facts.overdue() ? " was due on " + facts.due() + " and is still open" : " is due on " + facts.due();
        return new EmailDraft(
                facts.clientEmail(),
                "Reminder: " + facts.title() + " from " + sender,
                "Hello " + client + ",\n\nA friendly reminder that " + facts.title() + " for " + facts.balance() + due
                        + ". A copy is attached.\n\nThank you,\n" + sender);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
