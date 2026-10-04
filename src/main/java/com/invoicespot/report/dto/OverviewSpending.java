package com.invoicespot.report.dto;

import java.math.BigDecimal;
import java.util.List;

// What the period's expenses in the overview's currency cost, as its profit
// counts them: the five categories that cost most, largest first and by name
// when two cost the same, and the rest added up together, or none when there
// are no more.
public record OverviewSpending(BigDecimal total, List<Category> categories, Others others) {

    public record Category(String category, BigDecimal amount, long expenses) {}

    // How many expenses the rest are, in how many categories.
    public record Others(BigDecimal amount, long expenses, long categories) {}
}
