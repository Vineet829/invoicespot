package com.invoicespot.report.dto;

import java.math.BigDecimal;
import java.util.List;

// The money bar over the documents list, in one currency: what clients owe
// now, as the overview counts it, and what came in over the last 30 days.
public record DocumentSummaryResponse(
        boolean success,
        String currency,
        List<String> currencies,
        OverviewOwed outstanding,
        Received receivedLast30Days) {

    // Money in less what went back, and how many payments, receipts and
    // refunds it took.
    public record Received(BigDecimal total, long payments) {}
}
