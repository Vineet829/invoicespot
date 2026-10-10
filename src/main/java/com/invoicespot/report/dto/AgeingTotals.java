package com.invoicespot.report.dto;

import java.math.BigDecimal;

public record AgeingTotals(String key, String label, long invoices, BigDecimal outstanding) {}
