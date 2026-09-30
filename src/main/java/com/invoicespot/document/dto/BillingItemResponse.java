package com.invoicespot.document.dto;

import java.math.BigDecimal;

public record BillingItemResponse(
        String itemName, BigDecimal unitPrice, Integer quantity, String discount, BigDecimal lineTotal) {}
