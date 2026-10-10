package com.invoicespot.settings.dto;

public record PaymentDetailsEnvelope(boolean success, String message, PaymentDetailsResponse paymentDetails) {}
