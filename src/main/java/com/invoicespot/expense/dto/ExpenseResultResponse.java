package com.invoicespot.expense.dto;

public record ExpenseResultResponse(boolean success, String message, ExpenseResponse expense) {}
