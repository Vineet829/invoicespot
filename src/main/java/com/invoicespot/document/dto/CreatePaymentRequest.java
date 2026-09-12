package com.invoicespot.document.dto;

public record CreatePaymentRequest(
        String datePaid, Double amountPaid, String paymentMethod, String additionalInfo) {}
