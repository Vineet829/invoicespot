package com.invoicespot.report.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

// A day, a week or a month of the overview's trend. Money in is receipts and
// payments on invoices, and money out is expenses and refunds, so received is
// money in less refunds.
public record TrendBucket(
        LocalDate from,
        LocalDate to,
        BigDecimal billed,
        BigDecimal received,
        BigDecimal income,
        BigDecimal expenses,
        BigDecimal moneyIn,
        BigDecimal moneyOut) {}
