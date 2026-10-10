package com.invoicespot.report.dto;

import java.time.LocalDate;
import java.util.List;

public record ProfitReportResponse(
        boolean success, String period, LocalDate from, LocalDate to, List<ProfitCurrency> currencies) {}
