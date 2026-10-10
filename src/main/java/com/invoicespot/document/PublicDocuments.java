package com.invoicespot.document;

import com.invoicespot.business.Business;
import com.invoicespot.common.ApiException;
import com.invoicespot.document.dto.BillingItemResponse;
import com.invoicespot.document.dto.GstResponse;
import com.invoicespot.document.dto.PublicDocumentResponse;
import com.invoicespot.document.dto.PublicParty;
import com.invoicespot.document.dto.PublicPayment;
import com.invoicespot.document.dto.ReportPaymentRequest;
import com.invoicespot.document.dto.TaxResponse;
import com.invoicespot.notification.NotificationService;
import com.invoicespot.notification.NotificationType;
import com.invoicespot.settings.BusinessSettings;
import com.invoicespot.settings.SettingsService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

// A document as its client sees it, whichever link they came by: what it
// says, its PDF, and what they may do with it, answering a quotation or
// reporting a payment. Callers find the document from their link and hold
// the transaction. The document is locked as it is first read for an answer
// or a report: by the caller, or, for a share-link report, by
// PaymentClaimService.report.
@Service
public class PublicDocuments {

    private final DocumentActivity activity;
    private final PdfService pdfService;
    private final SettingsService settingsService;
    private final NotificationService notificationService;
    private final PaymentClaimService paymentClaims;
    private final Clock clock;

    public PublicDocuments(
            DocumentActivity activity,
            PdfService pdfService,
            SettingsService settingsService,
            NotificationService notificationService,
            PaymentClaimService paymentClaims,
            Clock clock) {
        this.activity = activity;
        this.pdfService = pdfService;
        this.settingsService = settingsService;
        this.notificationService = notificationService;
        this.paymentClaims = paymentClaims;
        this.clock = clock;
    }

    DocumentPdf pdf(Document document) {
        Business seller = document.getBusiness();
        return new DocumentPdf(
                DocumentService.pdfFileName(document),
                pdfService.render(document, seller, settingsService.forBusiness(seller.getPkid())),
                null);
    }

    // A client answers a quotation once, while it is still valid. Giving the
    // same answer again changes nothing.
    PublicDocumentResponse accept(Document document) {
        if (document.getDocumentType() != DocumentType.QUOTATION) {
            throw new ApiException(HttpStatus.CONFLICT, "Only a quotation can be accepted");
        }
        DocumentService.requireNotVoid(document, "This quotation was withdrawn, so it can't be accepted");
        if (document.getDeclinedAt() != null) {
            throw new ApiException(HttpStatus.CONFLICT, "This quotation was declined");
        }
        if (document.getAcceptedAt() == null) {
            requireOpen(document);
            document.setAcceptedAt(Instant.now(clock));
            activity.record(document, DocumentEventType.ACCEPTED, null, null);
            notificationService.notify(
                    document,
                    NotificationType.QUOTATION_ACCEPTED,
                    clientName(document) + " accepted " + title(document));
        }
        return view(document);
    }

    PublicDocumentResponse decline(Document document, String reason) {
        if (document.getDocumentType() != DocumentType.QUOTATION) {
            throw new ApiException(HttpStatus.CONFLICT, "Only a quotation can be declined");
        }
        DocumentService.requireNotVoid(document, "This quotation was withdrawn");
        if (document.getAcceptedAt() != null) {
            throw new ApiException(HttpStatus.CONFLICT, "This quotation was already accepted");
        }
        if (document.getDeclinedAt() == null) {
            requireOpen(document);
            String why = reason == null || reason.isBlank() ? null : reason.trim();
            document.setDeclinedAt(Instant.now(clock));
            document.setDeclineReason(why);
            activity.record(document, DocumentEventType.DECLINED, why, null);
            notificationService.notify(
                    document,
                    NotificationType.QUOTATION_DECLINED,
                    clientName(document) + " declined " + title(document) + (why == null ? "" : ": " + why));
        }
        return view(document);
    }

    // A client's report of a payment, for the business to confirm.
    PublicDocumentResponse reportPayment(Document document, ReportPaymentRequest request) {
        paymentClaims.report(document, request);
        return view(document);
    }

    private void requireOpen(Document document) {
        ZoneId zone = ZoneId.of(settingsService.forBusiness(document.getBusiness().getPkid()).getTimeZone());
        if (document.isExpired(startOfToday(zone))) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "This quotation expired on " + PdfService.DATE.format(document.getDueDate().atZone(zone))
                            + ". Ask " + sellerParty(document, document.getBusiness()).name() + " for a new one");
        }
    }

    private Instant startOfToday(ZoneId zone) {
        return LocalDate.now(clock.withZone(zone)).atStartOfDay(zone).toInstant();
    }

    // The business as the document went out with it, or as it is now until the
    // document keeps its own copy.
    static PublicParty sellerParty(Document document, Business business) {
        SellerSnapshot kept = Objects.requireNonNullElseGet(document.getSeller(), () -> SellerSnapshot.of(business));
        return new PublicParty(
                Objects.requireNonNullElse(kept.getName(), ""), kept.getLogo(), PdfService.senderLines(document, kept));
    }

    PublicDocumentResponse view(Document document) {
        Business seller = document.getBusiness();
        BusinessSettings settings = settingsService.forBusiness(seller.getPkid());
        ZoneId zone = ZoneId.of(settings.getTimeZone());
        Instant startOfToday = startOfToday(zone);
        CustomerSnapshot client = Objects.requireNonNullElseGet(document.getCustomerSnapshot(), CustomerSnapshot::new);
        BigDecimal total = orZero(document.getTotal());
        BigDecimal received = orZero(document.getTotalAmountReceived());
        boolean quotation = document.getDocumentType() == DocumentType.QUOTATION;
        boolean creditNote = document.getDocumentType() == DocumentType.CREDIT_NOTE;
        Document credited = document.getCredited();
        boolean voided = document.isVoid();
        boolean expired = document.isExpired(startOfToday);
        boolean answerable = quotation && !voided && !expired
                && document.getAcceptedAt() == null && document.getDeclinedAt() == null;
        return new PublicDocumentResponse(
                true,
                document.getDocumentType(),
                document.getDocumentNumber(),
                document.state(),
                document.getVoidedAt(),
                document.getVoidReason(),
                document.getStatus(),
                DocumentTotals.isOverdue(document, startOfToday),
                document.getIssueDate(),
                document.dueDay(zone),
                document.getCurrency(),
                sellerParty(document, seller),
                new PublicParty(
                        client.getName(),
                        null,
                        lines(client.getEmail(), client.getPhoneNumber(), client.getAddress(), client.getCity(),
                                client.getCountry(),
                                client.getGstin() == null ? null : "GSTIN " + client.getGstin())),
                document.getBillingItems().stream().map(BillingItemResponse::from).toList(),
                document.getSubTotal(),
                document.getTaxLabel(),
                document.getTaxSummaries().stream().map(TaxResponse::from).toList(),
                document.getSalesTax(),
                total,
                received,
                orZero(document.getTotalTds()),
                quotation || creditNote ? total : voided ? BigDecimal.ZERO : DocumentTotals.balanceDue(document),
                document.getAdditionalInfo(),
                document.getTermsConditions(),
                document.getAcceptedAt(),
                document.getDeclinedAt(),
                expired,
                answerable,
                answerable,
                orZero(document.getTotalCredited()),
                orZero(document.getTotalRefunded()),
                voided ? BigDecimal.ZERO : DocumentTotals.refundDue(document),
                credited == null
                        ? null
                        : new PublicDocumentResponse.PublicCreditedDocument(
                                credited.getDocumentType(), credited.getDocumentNumber(), credited.getIssueDate()),
                document.getCreditReason(),
                GstResponse.from(document),
                PaymentSection.of(document, settings.getPaymentDetails())
                        .map(PublicPayment::from)
                        .orElse(null),
                paymentClaims.canReport(document),
                paymentClaims.reports(document));
    }

    static String clientName(Document document) {
        CustomerSnapshot client = document.getCustomerSnapshot();
        return client == null || client.getName() == null || client.getName().isBlank()
                ? "Your client"
                : client.getName();
    }

    static String title(Document document) {
        return document.getDocumentType().getValue() + " " + document.getDocumentNumber();
    }

    private static List<String> lines(String... values) {
        return Stream.of(values).filter(value -> value != null && !value.isBlank()).toList();
    }

    private static BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
