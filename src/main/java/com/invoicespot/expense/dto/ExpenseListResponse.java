package com.invoicespot.expense.dto;

import java.time.LocalDate;
import java.util.List;

// One page of the expenses a search matches; the days they were paid between
// when the search named any, as the dates or the period asked for; and what
// all of them come to in each currency, by currency code. How many there are
// is what the totals count.
public record ExpenseListResponse(
        boolean success,
        long totalExpenses,
        int numberOfPages,
        LocalDate from,
        LocalDate to,
        List<ExpenseResponse> expenses,
        List<ExpenseListTotals> totals) {}
