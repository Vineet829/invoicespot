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
        LocalDate dueDate,
        String currency,
        PublicParty business,
        PublicParty client,
        List<BillingItemResponse> items,
        BigDecimal subTotal,
        String taxLabel,
        List<TaxResponse> taxes,
        BigDecimal salesTax,
        BigDecimal total,
        BigDecimal received,
        BigDecimal taxWithheld,
        BigDecimal balance,
        String notes,
        String terms,
        Instant acceptedAt,
        @JsonInclude(JsonInclude.Include.NON_NULL) Instant declinedAt,
        boolean expired,
        boolean canAccept,
        boolean canDecline,
        BigDecimal credited,
        BigDecimal refunded,
        BigDecimal refundDue,
        @JsonInclude(JsonInclude.Include.NON_NULL) PublicCreditedDocument credits,
        @JsonInclude(JsonInclude.Include.NON_NULL) String creditReason,
        @JsonInclude(JsonInclude.Include.NON_NULL) GstResponse gst,
        @JsonInclude(JsonInclude.Include.NON_NULL) PublicPayment payment,
        boolean canReportPayment,
        List<PublicPaymentReport> paymentReports) {

    // The invoice or receipt a credit note credits, as its client knows it.
    public record PublicCreditedDocument(DocumentType documentType, String documentNumber, LocalDate issueDate) {}
}
