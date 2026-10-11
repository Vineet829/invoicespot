package com.invoicespot.document.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record UpdatePaymentRequest(
        String datePaid,
        @DecimalMax(value = "999999999999999.99", message = "Amounts can have at most 15 whole digits")
                BigDecimal amountPaid,
        String paymentMethod,
        @Size(max = 255, message = "Payment notes can be at most 255 characters") String additionalInfo,
        @DecimalMax(value = "999999999999999.99", message = "Amounts can have at most 15 whole digits")
                BigDecimal tdsAmount) {}
