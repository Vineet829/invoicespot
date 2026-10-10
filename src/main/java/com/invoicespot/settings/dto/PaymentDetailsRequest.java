package com.invoicespot.settings.dto;

import jakarta.validation.constraints.Size;

public record PaymentDetailsRequest(
        @Size(max = 120, message = "The account holder's name can be at most 120 characters") String accountHolder,
        @Size(max = 120, message = "The bank's name can be at most 120 characters") String bankName,
        @Size(max = 34, message = "The account number can be at most 34 characters") String accountNumber,
        String bankCodeType,
        @Size(max = 40, message = "The bank code can be at most 40 characters") String bankCode,
        @Size(max = 30, message = "The bank code's name can be at most 30 characters") String bankCodeLabel,
        @Size(max = 100, message = "The UPI ID can be at most 100 characters") String upiId,
        @Size(max = 300, message = "The payment link can be at most 300 characters") String payLink,
        @Size(max = 1000, message = "Instructions can be at most 1,000 characters") String instructions) {}
