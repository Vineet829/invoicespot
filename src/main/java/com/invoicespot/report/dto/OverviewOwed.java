package com.invoicespot.report.dto;

import java.math.BigDecimal;

// What clients owe on issued invoices today, and how much of it is overdue.
public record OverviewOwed(
        BigDecimal total, BigDecimal notDue, BigDecimal overdue, long invoices, long overdueInvoices) {}
