package com.invoicespot.report.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record TdsClient(
        UUID customerId, String name, String pan, long payments, BigDecimal received, BigDecimal withheld) {}
