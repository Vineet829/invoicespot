package com.invoicespot.report.dto;

import java.math.BigDecimal;
import java.util.List;

// What was billed in a period in one currency, as the overview's Billed counts
// it, beside the period before it when there is one to compare with; how many
// documents billed it; what each kind of document billed, a credit note's
// taking its total off; and the tax in it at each rate, as the tax report
// counts it.
public record SalesResponse(
        boolean success,
        String currency,
        List<String> currencies,
        ViewPeriod period,
        BigDecimal total,
        BigDecimal previousTotal,
        long documents,
        List<TypeTotals> byType,
        Tax tax) {

    // An invoice, a receipt or a credit note: how many there were and what
    // they billed.
    public record TypeTotals(String type, long documents, BigDecimal amount) {}

    public record Tax(BigDecimal total, List<Rate> rates) {}

    // What was taxed at one rate, the tax charged on it, and on how many
    // documents.
    public record Rate(BigDecimal rate, BigDecimal taxable, BigDecimal tax, long documents) {}
}
