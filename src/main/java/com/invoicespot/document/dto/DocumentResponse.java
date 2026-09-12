package com.invoicespot.document.dto;

import com.invoicespot.document.CustomerSnapshot;
import com.invoicespot.document.Document;
import com.invoicespot.document.DocumentStatus;
import com.invoicespot.document.DocumentType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DocumentResponse(
        UUID id,
        UUID createdBy,
        DocumentCustomerDto customer,
        DocumentType documentType,
        String documentNumber,
        Instant dueDate,
        String additionalInfo,
        String termsConditions,
        DocumentStatus status,
        Double subTotal,
        Double salesTax,
        String rates,
        Double total,
        String currency,
        Double totalAmountReceived,
        List<BillingItemDto> billingItems,
        List<PaymentRecordDto> paymentRecords,
        Instant createdAt,
        Instant updatedAt) {

    public static DocumentResponse from(Document document) {
        CustomerSnapshot snapshot = document.getCustomer();
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

        List<BillingItemDto> billingItems = document.getBillingItems().stream()
                .map(item -> new BillingItemDto(
                        item.getItemName(), item.getUnitPrice(), item.getQuantity(), item.getDiscount()))
                .toList();

        List<PaymentRecordDto> paymentRecords = document.getPaymentRecords().stream()
                .map(record -> new PaymentRecordDto(
                        record.getPaidBy(),
                        record.getDatePaid(),
                        record.getAmountPaid(),
                        record.getPaymentMethod(),
                        record.getAdditionalInfo(),
                        record.getCreatedAt(),
                        record.getUpdatedAt()))
                .toList();

        return new DocumentResponse(
                document.getId(),
                document.getCreatedBy().getId(),
                customer,
                document.getDocumentType(),
                document.getDocumentNumber(),
                document.getDueDate(),
                document.getAdditionalInfo(),
                document.getTermsConditions(),
                document.getStatus(),
                document.getSubTotal(),
                document.getSalesTax(),
                document.getRates(),
                document.getTotal(),
                document.getCurrency(),
                document.getTotalAmountReceived(),
                billingItems,
                paymentRecords,
                document.getCreatedAt(),
                document.getUpdatedAt());
    }
}
