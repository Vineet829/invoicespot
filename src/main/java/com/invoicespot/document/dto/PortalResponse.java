package com.invoicespot.document.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.invoicespot.document.DocumentState;
import com.invoicespot.document.DocumentStatus;
import com.invoicespot.document.DocumentType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

// A client's portal: the business, the client's name, what they owe in each
// currency and a page of their documents. It holds nothing a share link
// doesn't show, and no ids: documents are found by their number.
public record PortalResponse(
        boolean success,
        PublicParty business,
        String client,
        List<Balance> balances,
        List<Entry> documents,
        long total,
        int page,
        int pages) {

    // What the client owes in one currency, as their statement shows it.
    public record Balance(
            String currency,
            BigDecimal invoiced,
            BigDecimal paid,
            BigDecimal credited,
            BigDecimal refunded,
            BigDecimal due,
            BigDecimal overdue) {}

    // One of the client's documents; only an invoice has a balance.
    public record Entry(
            DocumentType documentType,
            String documentNumber,
            DocumentState state,
            DocumentStatus status,
            boolean overdue,
            boolean expired,
            @JsonInclude(JsonInclude.Include.NON_NULL) Instant acceptedAt,
            @JsonInclude(JsonInclude.Include.NON_NULL) Instant declinedAt,
            LocalDate issueDate,
            @JsonInclude(JsonInclude.Include.NON_NULL) LocalDate dueDate,
            String currency,
            BigDecimal total,
            @JsonInclude(JsonInclude.Include.NON_NULL) BigDecimal balance) {}
}
