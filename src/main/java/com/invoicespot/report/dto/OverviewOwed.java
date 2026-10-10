package com.invoicespot.report.dto;

import java.math.BigDecimal;
import java.util.List;

// What clients owe on issued invoices today, and how much of it is overdue.
// The bands split all of it by days late: not due yet, then 1 to 15, 16 to 30
// and 31 to 45 days past the due date, and over 45.
public record OverviewOwed(
        BigDecimal total,
        BigDecimal notDue,
        BigDecimal overdue,
        long invoices,
        long overdueInvoices,
        List<Band> bands) {

    // What is owed in one band, and on how many invoices.
    public record Band(String key, BigDecimal amount, long invoices) {}
}
