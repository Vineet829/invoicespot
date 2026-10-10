package com.invoicespot.expense.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.invoicespot.common.ExchangeRate;
import com.invoicespot.document.PaymentMethod;
import com.invoicespot.expense.Expense;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ExpenseResponse(
        @JsonProperty("_id") UUID id,
        LocalDate date,
        String vendor,
        String category,
        BigDecimal amount,
        BigDecimal taxAmount,
        boolean taxReclaimable,
        BigDecimal total,
        String currency,
        @JsonInclude(JsonInclude.Include.NON_NULL) String exchangeRate,
        @JsonInclude(JsonInclude.Include.NON_NULL) String exchangeBase,
        PaymentMethod paymentMethod,
        String reference,
        String notes,
        Instant createdAt,
        Instant updatedAt) {

    public static ExpenseResponse from(Expense expense) {
        return new ExpenseResponse(
                expense.getId(),
                expense.getExpenseDate(),
                expense.getVendor(),
                expense.getCategory(),
                expense.getAmount(),
                expense.getTaxAmount(),
                expense.isTaxReclaimable(),
                expense.total(),
                expense.getCurrency(),
                ExchangeRate.plain(expense.getExchangeRate()),
                expense.getExchangeBase(),
                expense.getPaymentMethod(),
                expense.getReference(),
                expense.getNotes(),
                expense.getCreatedAt(),
                expense.getUpdatedAt());
    }
}
