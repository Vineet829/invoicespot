package com.invoicespot.document.dto;

public record BillingItemDto(String itemName, Double unitPrice, Integer quantity, String discount) {}
