package com.invoicespot.customer.dto;

import java.math.BigDecimal;

public record CustomerBalance(String currency, BigDecimal outstanding) {}
