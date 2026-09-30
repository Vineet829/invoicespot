package com.invoicespot.document.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.invoicespot.document.DocumentState;
import com.invoicespot.document.DocumentStatus;
import com.invoicespot.document.DocumentType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record PublicDocumentResponse(
        boolean success,
        DocumentType documentType,
        String documentNumber,
        DocumentState state,
        @JsonInclude(JsonInclude.Include.NON_NULL) Instant voidedAt,
        @JsonInclude(JsonInclude.Include.NON_NULL) String voidReason,
        DocumentStatus status,
        boolean overdue,
        LocalDate issueDate,
        Instant dueDate,
        String currency,
        PublicParty business,
        PublicParty client,
        List<BillingItemResponse> items,
        BigDecimal subTotal,
        String taxLabel,
        String rates,
        BigDecimal salesTax,
        BigDecimal total,
        BigDecimal received,
        BigDecimal balance,
        String notes,
        String terms,
        Instant acceptedAt,
        @JsonInclude(JsonInclude.Include.NON_NULL) Instant declinedAt,
        boolean expired,
        boolean canAccept,
        boolean canDecline) {}
