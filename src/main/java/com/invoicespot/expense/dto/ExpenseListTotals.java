package com.invoicespot.expense.dto;

import java.math.BigDecimal;

// What the listed expenses in one currency come to, on every page: how many
// there are, and what was paid for them, as their totals add up, tax that can
// be claimed back and all. Profit and loss leaves that tax out of their cost.
public record ExpenseListTotals(String currency, long expenses, BigDecimal spent) {}
