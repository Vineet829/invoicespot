package com.invoicespot.report.dto;

import java.math.BigDecimal;

// Billed, received and profit in the period and in the one before it, counted
// as the reports count them. The one before means something only when the
// business had money dated before the period.
public record OverviewTotals(Figure billed, Figure received, Figure profit, boolean hasPrevious) {

    public record Figure(BigDecimal current, BigDecimal previous) {}
}
