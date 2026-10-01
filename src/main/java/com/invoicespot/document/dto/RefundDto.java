package com.invoicespot.document.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.invoicespot.document.PaymentMethod;
import com.invoicespot.document.Refund;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record RefundDto(
        @JsonProperty("_id") UUID id,
        LocalDate dateRefunded,
        BigDecimal amount,
        PaymentMethod paymentMethod,
        String reference,
        Instant createdAt,
        Instant updatedAt) {

    public static RefundDto from(Refund refund) {
        return new RefundDto(
                refund.getId(),
                refund.getDateRefunded(),
                refund.getAmount(),
                refund.getPaymentMethod(),
                refund.getReference(),
                refund.getCreatedAt(),
                refund.getUpdatedAt());
    }
}
