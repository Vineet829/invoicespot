package com.invoicespot.document;

import com.invoicespot.business.BusinessAccess;
import com.invoicespot.common.ApiException;
import com.invoicespot.document.dto.ConfirmClaimRequest;
import com.invoicespot.document.dto.CreatePaymentRequest;
import com.invoicespot.document.dto.DismissClaimRequest;
import com.invoicespot.document.dto.PaymentClaimResponse;
import com.invoicespot.document.dto.PublicPaymentReport;
import com.invoicespot.document.dto.ReportPaymentRequest;
import com.invoicespot.document.dto.WaitingClaimResponse;
import com.invoicespot.notification.NotificationService;
import com.invoicespot.notification.NotificationType;
import com.invoicespot.settings.SettingsService;
import com.invoicespot.user.User;
import com.invoicespot.user.UserRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Payments clients report on invoices shared with them. A report changes
// nothing that is owed: someone in the business confirms it, which records it
// as a payment they can adjust first, or dismisses it.
@Service
public class PaymentClaimService {

    static final int MOST_WAITING = 3;
    private static final int MOST_SHOWN = 5;
    private static final int MOST_LISTED = 50;
    private static final List<PaymentClaim.Status> STILL_COUNTING =
            List.of(PaymentClaim.Status.PENDING, PaymentClaim.Status.CONFIRMED);

    private final PaymentClaimRepository claimRepository;
    private final DocumentRepository documentRepository;
    private final DocumentService documentService;
    private final DocumentActivity activity;
    private final NotificationService notificationService;
    private final SettingsService settingsService;
    private final UserRepository userRepository;
    private final Clock clock;

    public PaymentClaimService(
            PaymentClaimRepository claimRepository,
            DocumentRepository documentRepository,
            DocumentService documentService,
            DocumentActivity activity,
            NotificationService notificationService,
            SettingsService settingsService,
            UserRepository userRepository,
            Clock clock) {
        this.claimRepository = claimRepository;
        this.documentRepository = documentRepository;
        this.documentService = documentService;
        this.activity = activity;
        this.notificationService = notificationService;
        this.settingsService = settingsService;
        this.userRepository = userRepository;
        this.clock = clock;
    }

    // An issued invoice with something left to pay takes reports, three at
    // most waiting at a time.
    static boolean takesReports(Document document) {
        return document.getDocumentType() == DocumentType.INVOICE
                && document.state() == DocumentState.ISSUED
                && DocumentTotals.balanceDue(document).signum() > 0;
    }

    boolean canReport(Document document) {
        return takesReports(document)
                && claimRepository.countByDocumentAndStatus(document, PaymentClaim.Status.PENDING) < MOST_WAITING;
    }

    // The invoice is locked, so reports at the same moment take turns.
    @Transactional
    void report(Document shared, ReportPaymentRequest request) {
        Document document = documentRepository.findForUpdateByPkid(shared.getPkid()).orElseThrow();
        if (!takesReports(document)) {
            throw new ApiException(HttpStatus.CONFLICT, "This invoice has nothing left to pay");
        }
        PaymentClaim.Method method = method(request.method());
        BigDecimal amount = request.amount().setScale(2, RoundingMode.HALF_UP);
        BigDecimal balance = DocumentTotals.balanceDue(document);
        if (amount.compareTo(balance) > 0) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "The amount can't be more than the " + DocumentActivity.amount(document, balance)
                            + " still to pay");
        }
        LocalDate today = LocalDate.now(clock.withZone(zoneOf(document)));
        if (request.paidOn().isAfter(today)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "The day you paid can't be after today");
        }
        if (request.paidOn().isBefore(today.minusYears(1))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Report a payment made within the last year");
        }
        if (claimRepository.countByDocumentAndStatus(document, PaymentClaim.Status.PENDING) >= MOST_WAITING) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    MOST_WAITING + " payments reported for this invoice are waiting to be confirmed."
                            + " Please wait until they are");
        }
        String reference = request.reference() == null || request.reference().isBlank()
                ? null
                : request.reference().trim();
        if (reference != null
                && claimRepository.existsByDocumentAndReferenceIgnoreCaseAndStatusIn(
                        document, reference, STILL_COUNTING)) {
            throw new ApiException(HttpStatus.CONFLICT, "A payment with this reference was already reported");
        }
        PaymentClaim claim = new PaymentClaim();
        claim.setBusiness(document.getBusiness());
        claim.setDocument(document);
        claim.setAmount(amount);
        claim.setCurrency(Objects.requireNonNullElse(document.getCurrency(), ""));
        claim.setPaidOn(request.paidOn());
        claim.setMethod(method);
        claim.setReference(reference);
        claim.setCreatedAt(Instant.now(clock));
        claimRepository.save(claim);
        activity.record(document, DocumentEventType.PAYMENT_REPORTED, detail(claim), null);
        notificationService.notify(
                document,
                NotificationType.PAYMENT_REPORTED,
                clientName(document) + " reported paying " + DocumentActivity.amount(document, amount) + " for "
                        + document.getDocumentType().getValue() + " " + document.getDocumentNumber());
    }

    // What the client sees of the payments they reported, newest first.
    List<PublicPaymentReport> reports(Document document) {
        return claimRepository.findByDocumentOrderByCreatedAtDescPkidDesc(document).stream()
                .limit(MOST_SHOWN)
                .map(PublicPaymentReport::from)
                .toList();
    }

    // Every report waiting in the business, newest first, up to a page.
    @Transactional(readOnly = true)
    public List<WaitingClaimResponse> waiting(BusinessAccess access) {
        return claimRepository
                .findWaiting(access.businessPkid(), PaymentClaim.Status.PENDING, PageRequest.of(0, MOST_LISTED))
                .stream()
                .map(WaitingClaimResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PaymentClaimResponse> forDocument(BusinessAccess access, String documentId) {
        return claimRepository.findByDocumentOrderByCreatedAtDescPkidDesc(inBusiness(access, documentId, false))
                .stream()
                .map(PaymentClaimResponse::from)
                .toList();
    }

    // Records the payment the client reported, as reported unless changed,
    // through the same checks as any payment.
    @Transactional
    public PaymentClaimResponse confirm(
            BusinessAccess access, String documentId, String claimId, ConfirmClaimRequest request) {
        Document document = inBusiness(access, documentId, true);
        PaymentClaim claim = waiting(document, claimId);
        User actor = userRepository.getReferenceById(access.userPkid());
        Payment payment = documentService.recordPayment(
                document,
                new CreatePaymentRequest(
                        Objects.requireNonNullElse(request.datePaid(), claim.getPaidOn().toString()),
                        Objects.requireNonNullElse(request.amountPaid(), claim.getAmount()),
                        Objects.requireNonNullElse(request.paymentMethod(), claim.getMethod().recordedAs().getValue()),
                        Objects.requireNonNullElse(request.additionalInfo(), note(claim)),
                        request.tdsAmount()),
                actor);
        claim.setStatus(PaymentClaim.Status.CONFIRMED);
        claim.setPayment(payment);
        decided(claim, actor);
        activity.record(document, DocumentEventType.PAYMENT_REPORT_CONFIRMED, detail(claim), payment.getPkid(), actor);
        return PaymentClaimResponse.from(claim);
    }

    @Transactional
    public PaymentClaimResponse dismiss(
            BusinessAccess access, String documentId, String claimId, DismissClaimRequest request) {
        Document document = inBusiness(access, documentId, true);
        PaymentClaim claim = waiting(document, claimId);
        User actor = userRepository.getReferenceById(access.userPkid());
        String reason = request == null || request.reason() == null || request.reason().isBlank()
                ? null
                : request.reason().trim();
        claim.setStatus(PaymentClaim.Status.DISMISSED);
        claim.setDismissReason(reason);
        decided(claim, actor);
        activity.record(
                document,
                DocumentEventType.PAYMENT_REPORT_DISMISSED,
                detail(claim) + (reason == null ? "" : " · " + reason),
                actor);
        return PaymentClaimResponse.from(claim);
    }

    private void decided(PaymentClaim claim, User actor) {
        claim.setDecidedBy(actor);
        claim.setDecidedAt(Instant.now(clock));
    }

    // The business's document, locked while a report on it is decided.
    private Document inBusiness(BusinessAccess access, String documentId, boolean lock) {
        UUID id;
        try {
            id = UUID.fromString(documentId);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.NOT_FOUND, "That document does not exist");
        }
        return (lock ? documentRepository.findForUpdateByExternalId(id) : documentRepository.findByExternalId(id))
                .filter(document -> document.getBusiness().getPkid().equals(access.businessPkid()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "That document does not exist"));
    }

    private PaymentClaim waiting(Document document, String claimId) {
        UUID id;
        try {
            id = UUID.fromString(claimId);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.NOT_FOUND, "That payment report does not exist");
        }
        PaymentClaim claim = claimRepository.findByIdAndDocument(id, document)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "That payment report does not exist"));
        if (claim.getStatus() != PaymentClaim.Status.PENDING) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "This payment report was already " + claim.getStatus().name().toLowerCase(Locale.ROOT));
        }
        return claim;
    }

    private static PaymentClaim.Method method(String value) {
        try {
            return PaymentClaim.Method.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Choose how you paid");
        }
    }

    // "INR 177.00 · UPI · Ref. 412512345678".
    private static String detail(PaymentClaim claim) {
        return DocumentActivity.amount(claim.getDocument(), claim.getAmount()) + " · " + claim.getMethod().label()
                + (claim.getReference() == null ? "" : " · Ref. " + claim.getReference());
    }

    private static String note(PaymentClaim claim) {
        return "Reported by the client" + (claim.getReference() == null ? "" : " · Ref. " + claim.getReference());
    }

    private ZoneId zoneOf(Document document) {
        return ZoneId.of(settingsService.forBusiness(document.getBusiness().getPkid()).getTimeZone());
    }

    private static String clientName(Document document) {
        CustomerSnapshot client = document.getCustomerSnapshot();
        return client == null || client.getName() == null || client.getName().isBlank()
                ? "Your client"
                : client.getName();
    }
}
