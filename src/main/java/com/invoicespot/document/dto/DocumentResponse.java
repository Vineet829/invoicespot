package com.invoicespot.document.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.invoicespot.document.CustomerSnapshot;
import com.invoicespot.document.Document;
import com.invoicespot.document.DocumentStatus;
import com.invoicespot.document.DocumentTotals;
import com.invoicespot.document.DocumentType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DocumentResponse(
        @JsonProperty("_id") UUID id,
        UUID createdBy,
        UUID customerId,
        DocumentCustomerDto customer,
        DocumentType documentType,
        String documentNumber,
        Instant dueDate,
        String additionalInfo,
        String termsConditions,
        DocumentStatus status,
        boolean overdue,
        BigDecimal subTotal,
        BigDecimal salesTax,
        String rates,
        BigDecimal total,
        String currency,
        BigDecimal totalAmountReceived,
        List<BillingItemResponse> billingItems,
        List<PaymentRecordDto> paymentRecords,
        Instant createdAt,
        Instant updatedAt) {

    public static DocumentResponse from(Document document, Instant startOfToday) {
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
                        snapshot.getPhoneNumber());

        List<BillingItemResponse> billingItems = document.getBillingItems().stream()
                .map(item -> new BillingItemResponse(
                        item.getItemName(),
                        item.getUnitPrice(),
                        item.getQuantity(),
                        plain(item.getDiscount()),
                        DocumentTotals.lineTotal(item)))
                .toList();

        List<PaymentRecordDto> paymentRecords =
                document.getPayments().stream().map(PaymentRecordDto::from).toList();

        return new DocumentResponse(
                document.getId(),
                document.getCreatedBy().getId(),
                document.getCustomer() == null ? null : document.getCustomer().getId(),
                customer,
                document.getDocumentType(),
                document.getDocumentNumber(),
                document.getDueDate(),
                document.getAdditionalInfo(),
                document.getTermsConditions(),
                document.getStatus(),
                DocumentTotals.isOverdue(document, startOfToday),
                document.getSubTotal(),
                document.getSalesTax(),
                plain(document.getRates()),
                document.getTotal(),
                document.getCurrency(),
                document.getTotalAmountReceived(),
                billingItems,
                paymentRecords,
                document.getCreatedAt(),
                document.getUpdatedAt());
    }

    private static String plain(BigDecimal value) {
        return value == null ? "0" : value.stripTrailingZeros().toPlainString();
    }
}
