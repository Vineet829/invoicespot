package com.invoicespot.document;

import com.invoicespot.business.Business;
import com.invoicespot.business.BusinessAccess;
import com.invoicespot.business.BusinessService;
import com.invoicespot.common.AfterCommit;
import com.invoicespot.common.ApiException;
import com.invoicespot.customer.Customer;
import com.invoicespot.document.dto.EmailDocumentRequest;
import com.invoicespot.document.dto.EmailPreviewResponse;
import com.invoicespot.mail.MailService;
import com.invoicespot.mail.Placeholders;
import com.invoicespot.ratelimit.RateLimit;
import com.invoicespot.ratelimit.RateLimiter;
import com.invoicespot.settings.EmailTemplateKind;
import com.invoicespot.settings.EmailTemplateService;
import com.invoicespot.settings.EmailText;
import com.invoicespot.settings.dto.EmailTemplateRequest;
import com.invoicespot.user.DemoAccountGuard;
import jakarta.mail.MessagingException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.mail.MailSendException;
import org.springframework.stereotype.Service;

@Service
public class DocumentMailService {

    private static final Logger LOGGER = LoggerFactory.getLogger(DocumentMailService.class);
    private static final ShareService.EmailLink NO_LINK = new ShareService.EmailLink(null, null);
    private static final String PORTAL_SUBJECT = "Your documents from {business}";
    private static final String PORTAL_BODY = "Hello {client},\n\nHere is where you can see every document from"
            + " {business}, what you owe and your statement: {link}\n\n{business}";

    private final DocumentService documentService;
    private final MailService mailService;
    private final DemoAccountGuard demoAccountGuard;
    private final RateLimiter rateLimiter;
    private final EmailTemplateService emailTemplates;
    private final ShareService shareService;
    private final PortalService portalService;
    private final StatementService statementService;
    private final BusinessService businessService;

    public DocumentMailService(
            DocumentService documentService,
            MailService mailService,
            DemoAccountGuard demoAccountGuard,
            RateLimiter rateLimiter,
            EmailTemplateService emailTemplates,
            ShareService shareService,
            PortalService portalService,
            StatementService statementService,
            BusinessService businessService) {
        this.documentService = documentService;
        this.mailService = mailService;
        this.demoAccountGuard = demoAccountGuard;
        this.rateLimiter = rateLimiter;
        this.emailTemplates = emailTemplates;
        this.shareService = shareService;
        this.portalService = portalService;
        this.statementService = statementService;
        this.businessService = businessService;
    }

    public EmailDraft draft(BusinessAccess access, String id) {
        return draft(access, documentService.envelopeToSend(access, id));
    }

    public void email(BusinessAccess access, String id, EmailDocumentRequest request) {
        send(access, () -> documentService.renderPdfToSend(access, id), request,
                "This document's client has no email address", "document", id,
                to -> documentService.recordEmailed(access, id, to));
    }

    public EmailDraft receiptDraft(BusinessAccess access, String id, String paymentId) {
        return draft(access, documentService.receiptEnvelope(access, id, paymentId));
    }

    public void emailReceipt(BusinessAccess access, String id, String paymentId, EmailDocumentRequest request) {
        send(access, () -> documentService.renderReceipt(access, id, paymentId), request,
                "This document's client has no email address", "receipt", paymentId,
                to -> documentService.recordReceiptEmailed(access, id, paymentId, to));
    }

    public EmailDraft reminderDraft(BusinessAccess access, String id) {
        return draft(access, documentService.reminderEnvelope(access, id));
    }

    public void remind(BusinessAccess access, String id, EmailDocumentRequest request) {
        DocumentEnvelope envelope = documentService.reminderEnvelope(access, id);
        send(access, () -> documentService.renderPdf(access, id), pdf -> draft(access, envelope), request,
                "This document's client has no email address", "reminder", id,
                to -> documentService.recordReminder(access, id, to, false));
    }

    // An automatic reminder: no rate limit, since the reminder settings bound
    // it, and a failure is thrown for the daily job to log.
    // An automatic reminder is recorded first and goes out once that is
    // saved, so a run that fails after sending it never sends it twice. One
    // that fails to go out is logged by the run, and the next one follows on
    // its own day.
    public void remindAutomatically(BusinessAccess access, String id) {
        DocumentEnvelope envelope = documentService.reminderEnvelope(access, id);
        DocumentPdf pdf = documentService.renderPdf(access, id);
        EmailDraft draft = automaticDraft(access, envelope);
        documentService.recordReminder(access, id, draft.to(), true);
        String replyTo = replyTo(access);
        AfterCommit.run(() -> {
            try {
                mailService.sendDocument(
                        draft.to(), replyTo, draft.subject(), draft.message(), pdf.fileName(), pdf.content());
            } catch (MessagingException exception) {
                throw new MailSendException("The reminder could not be sent", exception);
            }
        });
    }

    // A recurring invoice sent as it is made: the invoice email as the
    // business wrote it, no rate limit, and a failure thrown for the daily job
    // to log.
    public void emailAutomatically(BusinessAccess access, String id) throws MessagingException {
        DocumentPdf pdf = documentService.renderPdfToSend(access, id);
        EmailDraft draft = automaticDraft(access, pdf.envelope());
        if (isBlank(draft.to())) {
            throw new MessagingException("The client of document " + id + " has no email address");
        }
        mailService.sendDocument(
                draft.to(), replyTo(access), draft.subject(), draft.message(), pdf.fileName(), pdf.content());
        documentService.recordEmailedAutomatically(access, id, draft.to());
    }

    public EmailDraft statementDraft(BusinessAccess access, String customerId) {
        return draft(access, statementService.envelope(access, customerId));
    }

    // What an email of a kind would say with the subject and message given,
    // filled in from the business's latest document it could go with. A link
    // not made yet shows where it will go.
    public EmailPreviewResponse preview(BusinessAccess access, String kindName, EmailTemplateRequest request) {
        EmailTemplateKind kind = EmailTemplateService.kind(kindName);
        EmailText text = EmailTemplateService.checked(kind, request);
        DocumentEnvelope envelope = kind == EmailTemplateKind.STATEMENT
                ? statementService.latestEnvelope(access)
                : documentService.latestEnvelope(access, kind);
        String url = linksIn(envelope, text) ? shareService.peekForEmail(access, envelope.documentId()).url() : null;
        EmailDraft draft = draft(access, envelope, text, new ShareService.EmailLink(
                url == null && linksIn(envelope, text) ? "[share link, made when you send]" : url, null));
        return new EmailPreviewResponse(
                true, envelope.title(), envelope.recipientName(), draft.to(), draft.subject(), draft.message());
    }

    // The email that gives a client their portal link. Like a document's,
    // the link shows when it can be shown again; otherwise {link} stays, to
    // be filled when the email is sent.
    public EmailDraft portalDraft(BusinessAccess access, String customerId) {
        Customer client = portalService.client(access, customerId);
        ShareService.EmailLink link = portalService.peekForEmail(access, customerId);
        Map<String, String> values = new HashMap<>();
        values.put("client", isBlank(client.getName()) ? "there" : client.getName());
        values.put("business", PdfService.senderName(seller(access)));
        if (link.url() != null) {
            values.put("link", link.url());
        }
        return new EmailDraft(
                client.getEmail(),
                Placeholders.fill(PORTAL_SUBJECT, values),
                Placeholders.fill(PORTAL_BODY, values),
                link.replaces() == null ? null : replacing("this client's portal link", link.replaces()));
    }

    public void emailPortal(BusinessAccess access, String customerId, EmailDocumentRequest request) {
        demoAccountGuard.requireNotDemo(access.user(), "emailing");
        rateLimiter.consume(RateLimit.OUTGOING_EMAIL, access.user().getId().toString());
        EmailDraft defaults = portalDraft(access, customerId);
        String to = isBlank(request.to()) ? defaults.to() : request.to().trim();
        if (isBlank(to)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "This client has no email address");
        }
        String subject = isBlank(request.subject())
                ? defaults.subject()
                : request.subject().replaceAll("[\\r\\n]+", " ").trim();
        String message = isBlank(request.message()) ? defaults.message() : request.message();
        ShareService.SentLink link = null;
        if (subject.contains("{link}") || message.contains("{link}")) {
            link = portalService.linkForEmail(access, customerId);
            Map<String, String> values = Map.of("link", link.url());
            subject = Placeholders.fill(subject, values);
            message = Placeholders.fill(message, values);
        }
        try {
            mailService.sendFromBusiness(to, replyTo(access), subject, message);
        } catch (MessagingException | MailException exception) {
            LOGGER.error("Failed to email the portal link of client {}", customerId, exception);
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY, "The portal link could not be emailed. Please try again later.");
        }
        if (link != null) {
            portalService.keep(access, customerId, link);
        }
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
        send(access, render, pdf -> draft(access, pdf.envelope()), request, noRecipient, kind, id, sent);
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
        // A {link} still in the email becomes the document's link, made now
        // if it has none. One that replaces a link that can't be shown again
        // is made only once the email has gone, so an email that fails leaves
        // the client's link working.
        DocumentEnvelope envelope = pdf.envelope();
        Optional<ShareService.SentLink> link = Optional.empty();
        if (envelope != null && linksIn(envelope, new EmailText(subject, message))) {
            link = shareService.linkForEmail(access, envelope.documentId(), true, access.user());
            Map<String, String> values = Map.of("link", link.map(ShareService.SentLink::url).orElse(""));
            subject = Placeholders.fill(subject, values);
            message = Placeholders.fill(message, values);
        }
        try {
            mailService.sendDocument(to, replyTo(access), subject, message, pdf.fileName(), pdf.content());
        } catch (MessagingException | MailException exception) {
            LOGGER.error("Failed to email {} {}", kind, id, exception);
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY, "The " + kind + " could not be emailed. Please try again later.");
        }
        link.ifPresent(made -> shareService.keep(access, envelope.documentId(), made, access.user()));
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

    // An email to look over before sending, as the business wrote its kind or
    // as the standard text says, with the client's and business's names and
    // the document's own values filled in. Its link shows when it can be shown
    // again; otherwise {link} stays, to be filled when the email is sent, and
    // a note says when that will replace an older link.
    private EmailDraft draft(BusinessAccess access, DocumentEnvelope envelope) {
        EmailText text = emailTemplates.text(access.businessPkid(), envelope.kind());
        return draft(
                access,
                envelope,
                text,
                linksIn(envelope, text) ? shareService.peekForEmail(access, envelope.documentId()) : NO_LINK);
    }

    // An email sent on its own carries the document's link when it has one
    // that can be shown again, or gets one without replacing another; the line
    // with the link is left out otherwise.
    private EmailDraft automaticDraft(BusinessAccess access, DocumentEnvelope envelope) {
        EmailText text = emailTemplates.text(access.businessPkid(), envelope.kind());
        return draft(
                access,
                envelope,
                text,
                linksIn(envelope, text)
                        ? new ShareService.EmailLink(
                                shareService.linkForEmail(access, envelope.documentId(), false, null)
                                        .map(ShareService.SentLink::url)
                                        .orElse(""),
                                null)
                        : NO_LINK);
    }

    private EmailDraft draft(
            BusinessAccess access, DocumentEnvelope envelope, EmailText text, ShareService.EmailLink link) {
        Map<String, String> values = new HashMap<>(envelope.values());
        values.put("client", isBlank(envelope.recipientName()) ? "there" : envelope.recipientName());
        values.put("business", PdfService.senderName(seller(access)));
        if (link.url() != null) {
            values.put("link", link.url());
        }
        return new EmailDraft(
                envelope.recipientEmail(),
                Placeholders.fill(text.subject(), values).replaceAll("[\\r\\n]+", " ").trim(),
                Placeholders.fill(text.body(), values),
                link.replaces() == null ? null : replacing("this document's share link", link.replaces()));
    }

    private static String replacing(String link, String madeOn) {
        return "Sending replaces " + link + " made on " + madeOn + ". The link in emails sent before stops working.";
    }

    // Whether an email, as written, carries its document's link.
    private static boolean linksIn(DocumentEnvelope envelope, EmailText text) {
        return envelope.documentId() != null
                && envelope.kind().fills("link")
                && (text.subject().contains("{link}") || text.body().contains("{link}"));
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
