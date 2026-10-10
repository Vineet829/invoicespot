package com.invoicespot.report.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record ClientTotals(
        UUID customerId,
        String name,
        long documents,
        BigDecimal billed,
        BigDecimal paid,
        BigDecimal outstanding) {}
