package com.invoicespot.settings;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.util.stream.Stream;
import lombok.Getter;
import lombok.Setter;

// How clients can pay the business. Every part is optional.
@Embeddable
@Getter
@Setter
public class PaymentDetails {

    @Column(name = "pay_account_holder", length = 120)
    private String accountHolder;

    @Column(name = "pay_bank_name", length = 120)
    private String bankName;

    @Column(name = "pay_account_number", length = 34)
    private String accountNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "pay_bank_code_type", length = 12)
    private BankCodeType bankCodeType;

    @Column(name = "pay_bank_code", length = 40)
    private String bankCode;

    // The name of a bank code of another kind, such as "Branch code".
    @Column(name = "pay_bank_code_label", length = 30)
    private String bankCodeLabel;

    @Column(name = "pay_upi_id", length = 100)
    private String upiId;

    @Column(name = "pay_link_url", length = 300)
    private String link;

    @Column(name = "pay_instructions", length = 1000)
    private String instructions;

    // What the bank code is called on documents.
    public String bankCodeName() {
        if (bankCodeType == null) {
            return null;
        }
        return bankCodeType == BankCodeType.OTHER ? bankCodeLabel : bankCodeType.label();
    }

    public boolean isEmpty() {
        return Stream.of(accountHolder, bankName, accountNumber, bankCode, upiId, link, instructions)
                .allMatch(value -> value == null);
    }
}
