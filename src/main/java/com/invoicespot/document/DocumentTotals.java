package com.invoicespot.document;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Objects;

public final class DocumentTotals {

    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    private DocumentTotals() {}

    public static BigDecimal lineTotal(BillingItem item) {
        BigDecimal quantity = BigDecimal.valueOf(item.getQuantity() == null ? 0 : item.getQuantity());
        BigDecimal kept = ONE_HUNDRED.subtract(orZero(item.getDiscount()));
        return quantity.multiply(orZero(item.getUnitPrice()))
                .multiply(kept)
                .divide(ONE_HUNDRED, 2, RoundingMode.HALF_UP);
    }

    public static void apply(Document document) {
        BigDecimal subTotal = document.getBillingItems().stream()
                .map(DocumentTotals::lineTotal)
                .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
        BigDecimal salesTax = subTotal.multiply(orZero(document.getRates()))
                .divide(ONE_HUNDRED, 2, RoundingMode.HALF_UP);
        BigDecimal total = subTotal.add(salesTax);
        document.setSubTotal(subTotal);
        document.setSalesTax(salesTax);
        document.setTotal(total);
        if (document.getDocumentType() == DocumentType.RECEIPT) {
            document.setTotalAmountReceived(total);
            document.setStatus(DocumentStatus.PAID);
            return;
        }
        BigDecimal received = received(document);
        document.setTotalAmountReceived(received);
        document.setStatus(status(total, received));
    }

    public static BigDecimal received(Document document) {
        return document.getPayments().stream()
                .map(Payment::getAmountPaid)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
    }

    public static boolean isOverdue(Document document, Instant startOfToday) {
        return document.getDocumentType() == DocumentType.INVOICE
                && document.getStatus() != DocumentStatus.PAID
                && document.getDueDate() != null
                && document.getDueDate().isBefore(startOfToday);
    }

    public static DocumentStatus status(BigDecimal total, BigDecimal received) {
        if (total.signum() > 0 && received.compareTo(total) >= 0) {
            return DocumentStatus.PAID;
        }
        return received.signum() > 0 ? DocumentStatus.NOT_FULLY_PAID : DocumentStatus.NOT_PAID;
    }

    private static BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
