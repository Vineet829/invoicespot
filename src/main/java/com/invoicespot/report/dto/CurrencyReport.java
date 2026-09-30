package com.invoicespot.report.dto;

import java.math.BigDecimal;
import java.util.List;

public record CurrencyReport(
        String currency,
        BigDecimal billed,
        BigDecimal received,
        BigDecimal outstanding,
        BigDecimal overdue,
        List<MonthTotals> months,
        List<AgeingTotals> ageing,
        List<ClientTotals> clients,
        OtherClients otherClients,
        List<MethodTotals> methods) {}
