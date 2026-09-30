package com.invoicespot.customer.dto;

import java.math.BigDecimal;

public record CustomerTotals(
        String currency,
        long documents,
        BigDecimal billed,
        BigDecimal paid,
        BigDecimal outstanding,
        BigDecimal overdue) {}
