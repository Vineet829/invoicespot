package com.invoicespot.report.dto;

import java.math.BigDecimal;
import java.time.YearMonth;

public record TdsMonth(YearMonth month, long payments, BigDecimal received, BigDecimal withheld) {}
