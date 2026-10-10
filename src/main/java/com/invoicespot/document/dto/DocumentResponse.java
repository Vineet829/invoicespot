package com.invoicespot.document.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.invoicespot.common.ExchangeRate;
import com.invoicespot.document.CustomerSnapshot;
import com.invoicespot.document.Document;
import com.invoicespot.document.DocumentState;
import com.invoicespot.document.DocumentStatus;
import com.invoicespot.document.DocumentTotals;
import com.invoicespot.document.DocumentType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

public record DocumentResponse(
        @JsonProperty("_id") UUID id,
        UUID createdBy,
        UUID customerId,
        DocumentCustomerDto customer,
        @JsonInclude(JsonInclude.Include.NON_NULL) SellerResponse seller,
        DocumentType documentType,
        String documentNumber,
        DocumentState state,
        LocalDate issueDate,
        @JsonInclude(JsonInclude.Include.NON_NULL) Instant issuedAt,
        @JsonInclude(JsonInclude.Include.NON_NULL) Instant voidedAt,
        @JsonInclude(JsonInclude.Include.NON_NULL) String voidReason,
        LocalDate dueDate,
        String additionalInfo,
        String termsConditions,
        DocumentStatus status,
        boolean overdue,
        BigDecimal subTotal,
        BigDecimal salesTax,
        String taxLabel,
        String rates,
        List<TaxResponse> taxes,
        BigDecimal total,
        String currency,
        @JsonInclude(JsonInclude.Include.NON_NULL) String exchangeRate,
        @JsonInclude(JsonInclude.Include.NON_NULL) String exchangeBase,
        @JsonInclude(JsonInclude.Include.NON_NULL) GstResponse gst,
        BigDecimal totalAmountReceived,
        // What a list shows as owed: there only on an issued invoice. The
        // ledger's balanceDue is worked out for every document but a
        // quotation.
        BigDecimal balanceDue,
        LedgerResponse ledger,
        List<BillingItemResponse> billingItems,
        List<PaymentRecordDto> paymentRecords,
        List<RefundDto> refunds,
        Instant createdAt,
        Instant updatedAt,
        @JsonInclude(JsonInclude.Include.NON_NULL) DocumentLink source,
        @JsonInclude(JsonInclude.Include.NON_NULL) List<DocumentLink> copies,
        @JsonInclude(JsonInclude.Include.NON_NULL) CreditedDocumentResponse creditedDocument,
        @JsonInclude(JsonInclude.Include.NON_NULL) String creditReason,
        @JsonInclude(JsonInclude.Include.NON_NULL) List<CreditNoteLink> creditNotes,
        @JsonInclude(JsonInclude.Include.NON_NULL) Instant acceptedAt,
        @JsonInclude(JsonInclude.Include.NON_NULL) Instant declinedAt,
        @JsonInclude(JsonInclude.Include.NON_NULL) String declineReason,
        boolean expired,
        int remindersSent,
        @JsonInclude(JsonInclude.Include.NON_NULL) Instant lastRemindedAt,
        boolean showPaymentDetails) {

    public static DocumentResponse from(Document document, Instant startOfToday, ZoneId zone) {
        return from(document, startOfToday, zone, null);
    }

    // One document on its own also names the document it came from, the
    // copies made of it and, for an invoice or receipt, its credit notes.
    public static DocumentResponse detail(
            Document document, Instant startOfToday, ZoneId zone, List<Document> copies) {
        return from(document, startOfToday, zone, copies.stream().map(DocumentLink::from).toList());
    }

    // A due date is sent as its day in the business's time zone.
    private static DocumentResponse from(
            Document document, Instant startOfToday, ZoneId zone, List<DocumentLink> copies) {
        CustomerSnapshot snapshot = document.getCustomerSnapshot();
        DocumentCustomerDto customer = snapshot == null
                ? null
                : new DocumentCustomerDto(
                        snapshot.getName(),
                        snapshot.getEmail(),
                        snapshot.getAccountNo(),
                        snapshot.getVatTinNo(),
                        snapshot.getAddress(),
                        snapshot.getCity(),
                        snapshot.getCountry(),
                        snapshot.getPhoneNumber(),
                        snapshot.getGstin(),
                        snapshot.getStateCode());

        List<BillingItemResponse> billingItems =
                document.getBillingItems().stream().map(BillingItemResponse::from).toList();

        List<PaymentRecordDto> paymentRecords =
                document.getPayments().stream().map(PaymentRecordDto::from).toList();

        return new DocumentResponse(
                document.getId(),
                document.getCreatedBy() == null ? null : document.getCreatedBy().getId(),
                document.getCustomer() == null ? null : document.getCustomer().getId(),
                customer,
                SellerResponse.from(document.getSeller()),
                document.getDocumentType(),
                document.getDocumentNumber(),
                document.state(),
                document.getIssueDate(),
                document.getIssuedAt(),
                document.getVoidedAt(),
                document.getVoidReason(),
                document.dueDay(zone),
                document.getAdditionalInfo(),
                document.getTermsConditions(),
                document.getStatus(),
                DocumentTotals.isOverdue(document, startOfToday),
                document.getSubTotal(),
                document.getSalesTax(),
                document.getTaxLabel(),
                rates(document),
                document.getTaxSummaries().stream().map(TaxResponse::from).toList(),
                document.getTotal(),
                document.getCurrency(),
                ExchangeRate.plain(document.getExchangeRate()),
                document.getExchangeBase(),
                GstResponse.from(document),
                document.getTotalAmountReceived(),
                balanceDue(document),
                LedgerResponse.from(document),
                billingItems,
                paymentRecords,
                document.getRefunds().stream().map(RefundDto::from).toList(),
                document.getCreatedAt(),
                document.getUpdatedAt(),
                copies == null || document.getSource() == null ? null : DocumentLink.from(document.getSource()),
                copies,
                document.getCredited() == null ? null : CreditedDocumentResponse.from(document.getCredited()),
                document.getCreditReason(),
                copies == null || !credits(document)
                        ? null
                        : document.getCreditNotes().stream().map(CreditNoteLink::from).toList(),
                document.getAcceptedAt(),
                document.getDeclinedAt(),
                document.getDeclineReason(),
                document.isExpired(startOfToday),
                document.getRemindersSent(),
                document.getLastRemindedAt(),
                document.isShowPaymentDetails());
    }

    // What is still owed on an issued invoice, as the overview counts it. A
    // quotation, receipt or credit note has no balance due, and neither does
    // a draft or a void invoice.
    private static BigDecimal balanceDue(Document document) {
        return document.getDocumentType() == DocumentType.INVOICE && document.state() == DocumentState.ISSUED
                ? DocumentTotals.balanceDue(document).setScale(2)
                : null;
    }

    // Invoices and receipts are the documents credit notes credit.
    private static boolean credits(Document document) {
        return document.getDocumentType() == DocumentType.INVOICE
                || document.getDocumentType() == DocumentType.RECEIPT;
    }

    // The one rate every line is taxed at, or none when they differ.
    private static String rates(Document document) {
        BigDecimal rate = DocumentTotals.singleRate(document);
        return rate == null ? null : BillingItemResponse.plain(rate).toPlainString();
    }
}
