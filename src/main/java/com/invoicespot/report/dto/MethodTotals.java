package com.invoicespot.report.dto;

import java.math.BigDecimal;

public record MethodTotals(String method, long payments, BigDecimal amount) {}
