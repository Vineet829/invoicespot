package com.invoicespot.document.dto;

import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

// The payment to record for a client's report, as it was reported unless
// changed here.
public record ConfirmClaimRequest(
        String datePaid,
        BigDecimal amountPaid,
        String paymentMethod,
        @Size(max = 255, message = "Payment notes can be at most 255 characters") String additionalInfo,
        BigDecimal tdsAmount) {}
