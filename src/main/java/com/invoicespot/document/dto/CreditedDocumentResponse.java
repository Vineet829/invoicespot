package com.invoicespot.document.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.invoicespot.document.Document;
import com.invoicespot.document.DocumentState;
import com.invoicespot.document.DocumentType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

// The invoice or receipt a credit note credits, and what is left to credit
// on it besides the note's own draft.
public record CreditedDocumentResponse(
        @JsonProperty("_id") UUID id,
        String documentNumber,
        DocumentType documentType,
        DocumentState state,
        LocalDate issueDate,
        BigDecimal total,
        BigDecimal creditable) {

    public static CreditedDocumentResponse from(Document credited) {
        return new CreditedDocumentResponse(
                credited.getId(),
                credited.getDocumentNumber(),
                credited.getDocumentType(),
                credited.state(),
                credited.getIssueDate(),
                credited.getTotal(),
                credited.getTotal().subtract(credited.getTotalCredited()).max(BigDecimal.ZERO));
    }
}
