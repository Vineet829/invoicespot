package com.invoicespot.report.dto;

import java.math.BigDecimal;

// How many of the listed payments, receipts and refunds are in one currency,
// how many of those are receipts, and what they come to: what was received,
// less what went back.
public record PaymentTotals(String currency, long payments, long receipts, BigDecimal received) {}
