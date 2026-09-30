package com.invoicespot.customer.dto;

public record ImportResultResponse(boolean success, String message, int added, int skipped) {}
