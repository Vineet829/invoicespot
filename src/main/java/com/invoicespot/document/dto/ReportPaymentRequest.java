package com.invoicespot.document.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

// A client's report of a payment: how much, when, how, and the reference
// their bank or UPI app gave it.
public record ReportPaymentRequest(
        @NotNull(message = "Enter the amount you paid")
                @DecimalMin(value = "0.01", message = "Enter the amount you paid")
                @Digits(
                        integer = 15,
                        fraction = 2,
                        message = "Enter the amount with at most 15 whole digits and 2 decimals")
                BigDecimal amount,
        @NotNull(message = "Enter the day you paid") LocalDate paidOn,
        @NotNull(message = "Choose how you paid") String method,
        @Size(max = 60, message = "The reference can be at most 60 characters") String reference) {}
