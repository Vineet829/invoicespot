package com.invoicespot.document.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.invoicespot.document.PaymentClaim;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

// A client's payment report as the business sees it.
public record PaymentClaimResponse(
        UUID id,
        BigDecimal amount,
        String currency,
        LocalDate paidOn,
        PaymentClaim.Method method,
        String methodLabel,
        String reference,
        PaymentClaim.Status status,
        Instant reportedAt,
        @JsonInclude(JsonInclude.Include.NON_NULL) Instant decidedAt,
        @JsonInclude(JsonInclude.Include.NON_NULL) String dismissReason) {

    public static PaymentClaimResponse from(PaymentClaim claim) {
        return new PaymentClaimResponse(
                claim.getId(),
                claim.getAmount(),
                claim.getCurrency(),
                claim.getPaidOn(),
                claim.getMethod(),
                claim.getMethod().label(),
                claim.getReference(),
                claim.getStatus(),
                claim.getCreatedAt(),
                claim.getDecidedAt(),
                claim.getDismissReason());
    }
}
