package com.invoicespot.report.dto;

import java.math.BigDecimal;

// Documents a report in the reporting currency leaves out, in their own
// currency: they have no exchange rate to it.
public record NotConvertedTotals(String currency, long documents, BigDecimal amount) {}
