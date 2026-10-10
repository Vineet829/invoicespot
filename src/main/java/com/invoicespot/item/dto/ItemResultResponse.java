package com.invoicespot.item.dto;

public record ItemResultResponse(boolean success, String message, ItemResponse item) {}
