package com.invoicespot.document.dto;

public record PaymentResultResponse(boolean success, String message, DocumentResponse document) {}
