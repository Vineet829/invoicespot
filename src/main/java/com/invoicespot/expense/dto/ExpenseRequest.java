package com.invoicespot.expense.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

// An expense to record, or the changes to one: on a change, what is left out
// stays as it was, and a blank vendor, payment method, reference, note or
// exchange rate removes it. The amount leaves out the tax.
public record ExpenseRequest(
        LocalDate date,
        @Size(max = 255, message = "Vendor names can be at most 255 characters") String vendor,
        @Size(max = 60, message = "Categories can be at most 60 characters") String category,
        @DecimalMin(value = "0", inclusive = false, message = "Enter an amount greater than zero")
                @Digits(integer = 15, fraction = 2, message = "Amounts can have at most 2 decimals")
                BigDecimal amount,
        @DecimalMin(value = "0", message = "Tax paid can't be negative")
                @Digits(integer = 15, fraction = 2, message = "Tax paid can have at most 2 decimals")
                BigDecimal taxAmount,
        Boolean taxReclaimable,
        @Pattern(regexp = "[A-Z]{3}", message = "Currency must be a three-letter code such as USD")
                String currency,
        String exchangeRate,
        String paymentMethod,
        @Size(max = 100, message = "References can be at most 100 characters") String reference,
        @Size(max = 5000, message = "Notes can be at most 5,000 characters") String notes) {}
