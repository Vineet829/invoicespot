package com.invoicespot.document.dto;

public record RecurringEnvelope(boolean success, String message, RecurringResponse schedule) {}
