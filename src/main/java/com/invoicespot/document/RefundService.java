package com.invoicespot.document;

import com.invoicespot.common.ApiException;
import com.invoicespot.document.dto.RefundRequest;
import com.invoicespot.user.User;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// Money paid back to a client, on an issued invoice or receipt that a credit
// note left overpaid. A refund never pays back more than is due back, and
// the document is locked while one changes, like a payment.
@Service
public class RefundService {

    private final DocumentRepository documentRepository;
    private final DocumentActivity activity;
    private final Clock clock;

    public RefundService(DocumentRepository documentRepository, DocumentActivity activity, Clock clock) {
        this.documentRepository = documentRepository;
        this.activity = activity;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void add(Document document, RefundRequest request, User actor) {
        requireRefundable(document);
        if (request.amount() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Enter the amount refunded");
        }
        BigDecimal amount = amount(request.amount());
        requireDue(document, amount);
        Refund refund = new Refund();
        refund.setDocument(document);
        refund.setDateRefunded(date(request.dateRefunded()));
        refund.setAmount(amount);
        refund.setPaymentMethod(method(request.paymentMethod()));
        refund.setReference(blankToNull(request.reference()));
        document.getRefunds().add(refund);
        DocumentTotals.settle(document);
        documentRepository.flush();
        activity.record(document, DocumentEventType.REFUND_RECORDED, detail(document, refund), actor);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void update(Document document, String refundId, RefundRequest request, User actor) {
        Refund refund = requireRefund(document, refundId);
        if (request.amount() != null) {
            BigDecimal amount = amount(request.amount());
            requireDue(document, amount.subtract(refund.getAmount()));
            refund.setAmount(amount);
        }
        if (request.dateRefunded() != null) {
            refund.setDateRefunded(date(request.dateRefunded()));
        }
        if (request.paymentMethod() != null) {
            refund.setPaymentMethod(method(request.paymentMethod()));
        }
        if (request.reference() != null) {
            refund.setReference(blankToNull(request.reference()));
        }
        DocumentTotals.settle(document);
        activity.record(document, DocumentEventType.REFUND_EDITED, detail(document, refund), actor);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void delete(Document document, String refundId, User actor) {
        Refund refund = requireRefund(document, refundId);
        document.getRefunds().remove(refund);
        DocumentTotals.settle(document);
        activity.record(document, DocumentEventType.REFUND_DELETED, detail(document, refund), actor);
    }

    // A payment can't drop below what was already paid back.
    static void requireCovered(Document document, BigDecimal paid) {
        if (paid.compareTo(document.getTotalRefunded()) < 0) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Delete the refunds first: " + document.getTotalRefunded().toPlainString() + " was paid back");
        }
    }

    // A receipt pays back only what its credit notes took off, so a note it
    // refunded stays until the refunds go.
    static void requireCreditRefunded(Document credited, BigDecimal creditedAfter) {
        if (credited.getDocumentType() == DocumentType.RECEIPT
                && credited.getTotalRefunded().compareTo(creditedAfter) > 0) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "Delete the refunds on " + credited.getDocumentNumber() + " first");
        }
    }

    private static void requireRefundable(Document document) {
        if (document.getDocumentType() != DocumentType.INVOICE && document.getDocumentType() != DocumentType.RECEIPT) {
            throw new ApiException(HttpStatus.CONFLICT, "Only an invoice or a receipt pays money back");
        }
        if (document.isDraft()) {
            throw new ApiException(HttpStatus.CONFLICT, "Issue this draft before recording a refund");
        }
        if (document.isVoid()) {
            throw new ApiException(HttpStatus.CONFLICT, "A void document can't take refunds");
        }
    }

    private static void requireDue(Document document, BigDecimal extra) {
        BigDecimal due = DocumentTotals.refundDue(document);
        if (due.signum() == 0 && extra.signum() > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "Nothing is due back on " + document.getDocumentNumber());
        }
        if (extra.compareTo(due) > 0) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "That is more than the " + due.toPlainString() + " due back");
        }
    }

    private static Refund requireRefund(Document document, String refundId) {
        Optional<UUID> id;
        try {
            id = Optional.of(UUID.fromString(refundId));
        } catch (IllegalArgumentException exception) {
            id = Optional.empty();
        }
        return id.flatMap(external -> document.getRefunds().stream()
                        .filter(refund -> external.equals(refund.getId()))
                        .findFirst())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "That refund does not exist"));
    }

    private static BigDecimal amount(BigDecimal amount) {
        if (amount.signum() <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Enter an amount greater than zero");
        }
        if (amount.stripTrailingZeros().scale() > 2) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "An amount can have at most 2 decimals");
        }
        return amount.setScale(2);
    }

    private LocalDate date(String value) {
        if (value == null || value.isBlank()) {
            return LocalDate.now(clock);
        }
        try {
            return LocalDate.parse(value.length() > 10 ? value.substring(0, 10) : value);
        } catch (DateTimeParseException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Enter the refund date as YYYY-MM-DD");
        }
    }

    private static PaymentMethod method(String value) {
        if (value == null) {
            return PaymentMethod.CASH;
        }
        try {
            return PaymentMethod.fromValue(value);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid payment method: " + value);
        }
    }

    // "USD 40.00 · Bank Transfer".
    private static String detail(Document document, Refund refund) {
        return DocumentActivity.amount(document, refund.getAmount()) + " · " + refund.getPaymentMethod().getValue();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
