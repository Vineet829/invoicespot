package com.invoicespot.document.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

// Everything is optional when a refund is changed; a new one needs its amount.
public record RefundRequest(
        String dateRefunded,
        @DecimalMax(value = "999999999999999.99", message = "Amounts can have at most 15 whole digits")
                BigDecimal amount,
        String paymentMethod,
        @Size(max = 255, message = "The reference can be at most 255 characters") String reference) {}
