package com.invoicespot.report.dto;

import java.math.BigDecimal;

// Billed, received and profit in the period and in the one before it, counted
// as the reports count them, and the tax included in what each period billed,
// as the tax report counts it. The one before means something only when the
// business's records reach back to its first day.
public record OverviewTotals(
        Figure billed,
        Figure received,
        Figure profit,
        BigDecimal tax,
        BigDecimal previousTax,
        boolean hasPrevious) {

    public record Figure(BigDecimal current, BigDecimal previous) {}
}
