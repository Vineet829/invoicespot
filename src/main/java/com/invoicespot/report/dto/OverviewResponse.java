package com.invoicespot.report.dto;

import java.math.BigDecimal;
import java.util.List;

public record OverviewResponse(
        boolean success,
        List<String> currencies,
        String currency,
        long documents,
        long clients,
        BigDecimal expectedIncome,
        BigDecimal cashReceived,
        BigDecimal taxWithheld,
        BigDecimal cashPending,
        long paid,
        long partial,
        long unpaid,
        long overdue,
        List<OverviewPayment> payments) {}
