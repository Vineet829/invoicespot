package com.invoicespot.expense.dto;

import java.util.List;

public record ExpenseListResponse(
        boolean success, long totalExpenses, int numberOfPages, List<ExpenseResponse> expenses) {}
