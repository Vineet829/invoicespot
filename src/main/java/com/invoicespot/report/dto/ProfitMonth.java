package com.invoicespot.report.dto;

import java.math.BigDecimal;
import java.time.YearMonth;

public record ProfitMonth(YearMonth month, BigDecimal income, BigDecimal expenses, BigDecimal profit) {}
