package com.invoicespot.document;

import com.invoicespot.business.Business;
import com.invoicespot.business.BusinessAccess;
import com.invoicespot.business.BusinessService;
import com.invoicespot.common.ApiException;
import com.invoicespot.document.dto.EmailDocumentRequest;
import com.invoicespot.mail.MailService;
import com.invoicespot.ratelimit.RateLimit;
import com.invoicespot.ratelimit.RateLimiter;
import com.invoicespot.settings.BusinessSettings;
import com.invoicespot.settings.SettingsService;
import com.invoicespot.user.DemoAccountGuard;
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
    private final BusinessService businessService;

    public DocumentMailService(
            DocumentService documentService,
            MailService mailService,
            DemoAccountGuard demoAccountGuard,
            RateLimiter rateLimiter,
            SettingsService settingsService,
            StatementService statementService,
            BusinessService businessService) {
        this.documentService = documentService;
        this.mailService = mailService;
        this.demoAccountGuard = demoAccountGuard;
        this.rateLimiter = rateLimiter;
        this.settingsService = settingsService;
        this.statementService = statementService;
        this.businessService = businessService;
    }

    public EmailDraft draft(BusinessAccess access, String id) {
        return defaults(access, documentService.envelopeToSend(access, id));
    }

    public void email(BusinessAccess access, String id, EmailDocumentRequest request) {
        send(access, () -> documentService.renderPdfToSend(access, id), request,
                "This document's client has no email address", "document", id,
                to -> documentService.recordEmailed(access, id, to));
    }

    public EmailDraft receiptDraft(BusinessAccess access, String id, String paymentId) {
        return defaults(access, documentService.receiptEnvelope(access, id, paymentId));
    }

    public void emailReceipt(BusinessAccess access, String id, String paymentId, EmailDocumentRequest request) {
        send(access, () -> documentService.renderReceipt(access, id, paymentId), request,
                "This document's client has no email address", "receipt", paymentId,
                to -> documentService.recordReceiptEmailed(access, id, paymentId, to));
    }

    public EmailDraft reminderDraft(BusinessAccess access, String id) {
        return reminder(seller(access), documentService.reminderFacts(access, id));
    }

    public void remind(BusinessAccess access, String id, EmailDocumentRequest request) {
        ReminderFacts facts = documentService.reminderFacts(access, id);
        send(access, () -> documentService.renderPdf(access, id), pdf -> reminder(seller(access), facts), request,
                "This document's client has no email address", "reminder", id,
                to -> documentService.recordReminder(access, id, to, false));
    }

    // An automatic reminder: no rate limit, since the reminder settings bound
    // it, and a failure is thrown for the daily job to log.
    public void remindAutomatically(BusinessAccess access, String id) throws MessagingException {
        ReminderFacts facts = documentService.reminderFacts(access, id);
        DocumentPdf pdf = documentService.renderPdf(access, id);
        EmailDraft draft = reminder(seller(access), facts);
        mailService.sendDocument(
                draft.to(), replyTo(access), draft.subject(), draft.message(), pdf.fileName(), pdf.content());
        documentService.recordReminder(access, id, draft.to(), true);
    }

    // A recurring invoice sent as it is made: the standard message, no rate
    // limit, and a failure thrown for the daily job to log.
    public void emailAutomatically(BusinessAccess access, String id) throws MessagingException {
        DocumentPdf pdf = documentService.renderPdfToSend(access, id);
        EmailDraft draft = defaults(access, pdf.envelope());
        if (isBlank(draft.to())) {
            throw new MessagingException("The client of document " + id + " has no email address");
        }
        mailService.sendDocument(
                draft.to(), replyTo(access), draft.subject(), draft.message(), pdf.fileName(), pdf.content());
        documentService.recordEmailedAutomatically(access, id, draft.to());
    }

    public EmailDraft statementDraft(BusinessAccess access, String customerId) {
        return defaults(access, statementService.envelope(access, customerId));
    }

    public void emailStatement(BusinessAccess access, String customerId, EmailDocumentRequest request) {
        send(access, () -> statementService.render(access, customerId), request,
                "This client has no email address", "statement", customerId, to -> {});
    }

    private void send(
            BusinessAccess access,
            Supplier<DocumentPdf> render,
            EmailDocumentRequest request,
            String noRecipient,
            String kind,
            String id,
            Consumer<String> sent) {
        send(access, render, pdf -> defaults(access, pdf.envelope()), request, noRecipient, kind, id, sent);
    }

    private void send(
            BusinessAccess access,
            Supplier<DocumentPdf> render,
            Function<DocumentPdf, EmailDraft> draft,
            EmailDocumentRequest request,
            String noRecipient,
            String kind,
            String id,
            Consumer<String> sent) {
        demoAccountGuard.requireNotDemo(access.user(), "emailing");
        rateLimiter.consume(RateLimit.OUTGOING_EMAIL, access.user().getId().toString());
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
            mailService.sendDocument(to, replyTo(access), subject, message, pdf.fileName(), pdf.content());
        } catch (MessagingException | MailException exception) {
            LOGGER.error("Failed to email {} {}", kind, id, exception);
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY, "The " + kind + " could not be emailed. Please try again later.");
        }
        sent.accept(to);
    }

    private Business seller(BusinessAccess access) {
        return businessService.require(access.businessPkid());
    }

    // Replies go to the business, or to the person sending without one.
    private String replyTo(BusinessAccess access) {
        String email = seller(access).getEmail();
        return isBlank(email) ? access.user().getEmail() : email;
    }

    private EmailDraft defaults(BusinessAccess access, DocumentEnvelope envelope) {
        return defaults(seller(access), envelope, settingsService.forBusiness(access.businessPkid()));
    }

    private static EmailDraft defaults(Business seller, DocumentEnvelope envelope, BusinessSettings settings) {
        String sender = PdfService.senderName(seller);
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
    private static EmailDraft reminder(Business seller, ReminderFacts facts) {
        String sender = PdfService.senderName(seller);
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
