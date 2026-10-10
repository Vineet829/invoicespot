package com.invoicespot.report.dto;

import java.math.BigDecimal;
import java.util.List;

// Received is the money paid alongside the tax withheld.
public record TdsCurrency(
        String currency,
        BigDecimal received,
        BigDecimal withheld,
        List<TdsClient> clients,
        List<TdsMonth> months,
        List<TdsPayment> payments) {}
