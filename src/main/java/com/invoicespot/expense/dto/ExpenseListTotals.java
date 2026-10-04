package com.invoicespot.expense.dto;

import java.math.BigDecimal;

// What the listed expenses in one currency come to, on every page: how many
// there are, and what they cost the business, as profit and loss counts it,
// so tax that can be claimed back is left out.
public record ExpenseListTotals(String currency, long expenses, BigDecimal spent) {}
