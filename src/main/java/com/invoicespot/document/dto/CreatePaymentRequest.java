package com.invoicespot.document.dto;

import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record CreatePaymentRequest(
        String datePaid,
        BigDecimal amountPaid,
        String paymentMethod,
        @Size(max = 255, message = "Payment notes can be at most 255 characters") String additionalInfo) {}
