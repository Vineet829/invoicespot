package com.invoicespot.settings.dto;

import com.invoicespot.settings.BankCodeType;
import com.invoicespot.settings.PaymentDetails;

public record PaymentDetailsResponse(
        String accountHolder,
        String bankName,
        String accountNumber,
        BankCodeType bankCodeType,
        String bankCode,
        String bankCodeLabel,
        String upiId,
        String payLink,
        String instructions) {

    public static PaymentDetailsResponse from(PaymentDetails details) {
        return new PaymentDetailsResponse(
                details.getAccountHolder(),
                details.getBankName(),
                details.getAccountNumber(),
                details.getBankCodeType(),
                details.getBankCode(),
                details.getBankCodeLabel(),
                details.getUpiId(),
                details.getLink(),
                details.getInstructions());
    }
}
