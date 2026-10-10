package com.invoicespot.report.dto;

import java.math.BigDecimal;
import java.util.List;

public record ProfitCurrency(
        String currency,
        BigDecimal income,
        BigDecimal expenses,
        BigDecimal profit,
        List<ProfitMonth> months,
        List<ProfitCategory> categories) {}
