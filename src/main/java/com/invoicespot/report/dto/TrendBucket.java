package com.invoicespot.report.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

// A day, a week or a month of the overview's trend. Money in is receipts and
// payments on invoices, and money out is what was paid for expenses, tax and
// all, and refunds, so received is money in less refunds. Expenses are what
// they cost, as profit counts them.
public record TrendBucket(
        LocalDate from,
        LocalDate to,
        BigDecimal billed,
        BigDecimal received,
        BigDecimal income,
        BigDecimal expenses,
        BigDecimal moneyIn,
        BigDecimal moneyOut) {}
