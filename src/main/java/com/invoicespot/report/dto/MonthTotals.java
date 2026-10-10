package com.invoicespot.report.dto;

import java.math.BigDecimal;
import java.time.YearMonth;

public record MonthTotals(YearMonth month, BigDecimal billed, BigDecimal received) {}
