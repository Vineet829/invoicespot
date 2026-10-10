package com.invoicespot.report.dto;

import java.math.BigDecimal;

public record OtherClients(long clients, long documents, BigDecimal billed, BigDecimal paid, BigDecimal outstanding) {}
