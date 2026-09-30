package com.invoicespot.document.dto;

import com.invoicespot.document.DocumentStatus;
import com.invoicespot.document.DocumentType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record PublicDocumentResponse(
        boolean success,
        DocumentType documentType,
        String documentNumber,
        DocumentStatus status,
        boolean overdue,
        Instant issuedAt,
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
        boolean canAccept) {}
