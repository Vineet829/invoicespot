package com.invoicespot.document.dto;

import com.invoicespot.document.PaymentSection;
import com.invoicespot.payment.UpiPayment;
import java.math.BigDecimal;

// How the client can pay this invoice, whichever details the business gave,
// and on a rupee invoice with a UPI ID the UPI link to pay its balance.
public record PublicPayment(
        String accountHolder,
        String bankName,
        String accountNumber,
        String bankCodeLabel,
        String bankCode,
        String upiId,
        String payLink,
        String instructions,
        PublicUpi upi) {

    // The link a UPI app opens, and whether it carries the amount; one over
    // what UPI sends at once leaves it for the payer to enter.
    public record PublicUpi(String uri, BigDecimal amount, boolean amountIncluded) {

        static PublicUpi from(UpiPayment upi) {
            return upi == null ? null : new PublicUpi(upi.uri(), upi.amount(), upi.amountIncluded());
        }
    }

    public static PublicPayment from(PaymentSection section) {
        return new PublicPayment(
                section.accountHolder(),
                section.bankName(),
                section.accountNumber(),
                section.bankCodeName(),
                section.bankCode(),
                section.upiId(),
                section.link(),
                section.instructions(),
                PublicUpi.from(section.upi()));
    }
}
