package com.invoicespot.report.dto;

import java.time.LocalDate;
import java.util.List;

public record ReportResponse(
        boolean success,
        String period,
        LocalDate from,
        LocalDate to,
        LocalDate today,
        List<Integer> years,
        String view,
        String baseCurrency,
        List<CurrencyReport> currencies,
        List<NotConvertedTotals> notConverted) {}
