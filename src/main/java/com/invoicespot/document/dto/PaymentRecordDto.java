package com.invoicespot.document.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.invoicespot.document.Payment;
import com.invoicespot.document.PaymentMethod;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record PaymentRecordDto(
        @JsonProperty("_id") UUID id,
        String paidBy,
        LocalDate datePaid,
        BigDecimal amountPaid,
        BigDecimal tdsAmount,
        PaymentMethod paymentMethod,
        String additionalInfo,
        // The number its receipt carries, kept from when it was recorded.
        int receiptNumber,
        Instant createdAt,
        Instant updatedAt) {

    public static PaymentRecordDto from(Payment payment) {
        return new PaymentRecordDto(
                payment.getId(),
                payment.getPaidBy(),
                payment.getDatePaid(),
                payment.getAmountPaid(),
                payment.getTdsAmount(),
                payment.getPaymentMethod(),
                payment.getAdditionalInfo(),
                payment.getReceiptNumber(),
                payment.getCreatedAt(),
                payment.getUpdatedAt());
    }
}
