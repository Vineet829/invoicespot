package com.invoicespot.document.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.invoicespot.document.Document;
import com.invoicespot.document.DocumentState;
import com.invoicespot.document.DocumentTotals;
import com.invoicespot.document.DocumentType;
import java.math.BigDecimal;

// How a document stands: the tax the client withheld, what its credit notes
// took off, what was paid back, what is still owed or due back, and, on an
// issued invoice or receipt, what credit notes can still take off. A
// quotation is an offer, so nothing is owed on it: it has no balance due, as
// the list has none for it either.
public record LedgerResponse(
        BigDecimal totalTds,
        BigDecimal totalCredited,
        BigDecimal totalRefunded,
        BigDecimal balanceDue,
        BigDecimal refundDue,
        @JsonInclude(JsonInclude.Include.NON_NULL) BigDecimal creditable) {

    public static LedgerResponse from(Document document) {
        boolean credits = document.state() == DocumentState.ISSUED
                && (document.getDocumentType() == DocumentType.INVOICE
                        || document.getDocumentType() == DocumentType.RECEIPT);
        return new LedgerResponse(
                document.getTotalTds(),
                document.getTotalCredited(),
                document.getTotalRefunded(),
                document.getDocumentType() == DocumentType.QUOTATION ? null : DocumentTotals.balanceDue(document),
                DocumentTotals.refundDue(document),
                credits ? document.getTotal().subtract(document.getTotalCredited()).max(BigDecimal.ZERO) : null);
    }
}
