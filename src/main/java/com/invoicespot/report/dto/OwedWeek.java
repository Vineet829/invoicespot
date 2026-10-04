package com.invoicespot.report.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

// What clients owe on invoices falling due in one stretch of days: overdue
// has no first day, and later, which takes invoices with no due date too, no
// last day.
public record OwedWeek(String key, LocalDate from, LocalDate to, BigDecimal amount, long invoices) {}
