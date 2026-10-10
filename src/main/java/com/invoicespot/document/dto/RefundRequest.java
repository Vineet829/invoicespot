package com.invoicespot.document.dto;

import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

// Everything is optional when a refund is changed; a new one needs its amount.
public record RefundRequest(
        String dateRefunded,
        BigDecimal amount,
        String paymentMethod,
        @Size(max = 255, message = "The reference can be at most 255 characters") String reference) {}
