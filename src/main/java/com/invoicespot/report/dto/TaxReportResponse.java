package com.invoicespot.report.dto;

import java.time.LocalDate;
import java.util.List;

public record TaxReportResponse(
        boolean success,
        String period,
        LocalDate from,
        LocalDate to,
        String view,
        String baseCurrency,
        List<TaxCurrency> currencies,
        List<NotConvertedTotals> notConverted) {}
