package com.invoicespot.document.dto;

import com.invoicespot.document.PaymentClaim;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

// A payment the client reported, and whether the business confirmed it. Any
// reason for dismissing it stays with the business.
public record PublicPaymentReport(
        BigDecimal amount, LocalDate paidOn, String method, PaymentClaim.Status status, Instant reportedAt) {

    public static PublicPaymentReport from(PaymentClaim claim) {
        return new PublicPaymentReport(
                claim.getAmount(),
                claim.getPaidOn(),
                claim.getMethod().label(),
                claim.getStatus(),
                claim.getCreatedAt());
    }
}
