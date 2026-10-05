package com.invoicespot.report.dto;

import java.math.BigDecimal;
import java.util.List;

public record OverviewResponse(
        boolean success,
        List<String> currencies,
        String currency,
        long clients,
        BigDecimal expectedIncome,
        BigDecimal cashReceived,
        BigDecimal taxWithheld,
        BigDecimal cashPending,
        OverviewPeriod period,
        OverviewTotals totals,
        OverviewOwed owedNow,
        List<TrendBucket> trend,
        OverviewAttention attention,
        OverviewSpending spending,
        List<OverviewClient> topClients,
        List<OverviewItem> topItems,
        BigDecimal itemsTotal,
        OverviewComingUp comingUp) {}
