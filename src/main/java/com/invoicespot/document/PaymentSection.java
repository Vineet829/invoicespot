package com.invoicespot.document;

import com.invoicespot.payment.UpiPayment;
import com.invoicespot.settings.PaymentDetails;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

// How a client can pay an issued invoice that still has a balance, unless it
// was chosen not to show: the business's payment details as they are now, so
// an old unpaid invoice shows the current account. A UPI ID only pays rupees,
// and comes with a request to pay the balance by UPI.
public record PaymentSection(
        String accountHolder,
        String bankName,
        String accountNumber,
        String bankCodeName,
        String bankCode,
        String upiId,
        String link,
        String instructions,
        UpiPayment upi) {

    public static Optional<PaymentSection> of(Document document, PaymentDetails details) {
        if (document.getDocumentType() != DocumentType.INVOICE
                || !document.isShowPaymentDetails()
                || document.state() != DocumentState.ISSUED
                || DocumentTotals.balanceDue(document).signum() <= 0) {
            return Optional.empty();
        }
        String upiId = "INR".equals(document.getCurrency()) ? details.getUpiId() : null;
        PaymentSection section = new PaymentSection(
                details.getAccountHolder(),
                details.getBankName(),
                details.getAccountNumber(),
                details.bankCodeName(),
                details.getBankCode(),
                upiId,
                details.getLink(),
                details.getInstructions(),
                upiId == null
                        ? null
                        : new UpiPayment(
                                upiId,
                                Objects.requireNonNullElseGet(details.getAccountHolder(), () -> sellerName(document)),
                                DocumentTotals.balanceDue(document),
                                "Invoice " + document.getDocumentNumber()));
        return section.isEmpty() ? Optional.empty() : Optional.of(section);
    }

    // The business as the invoice went out with it.
    private static String sellerName(Document document) {
        SellerSnapshot kept = document.getSeller();
        String name = kept == null ? document.getBusiness().getName() : kept.getName();
        return Objects.requireNonNullElse(name, "");
    }

    // How to pay, as lines of an email.
    public String text() {
        List<String> lines = new ArrayList<>();
        lines.add("How to pay:");
        if (accountHolder != null) {
            lines.add("Account holder: " + accountHolder);
        }
        if (bankName != null) {
            lines.add("Bank: " + bankName);
        }
        if (accountNumber != null) {
            lines.add("Account number: " + accountNumber);
        }
        if (bankCode != null) {
            lines.add(bankCodeName + ": " + bankCode);
        }
        if (upiId != null) {
            lines.add("UPI ID: " + upiId);
        }
        if (link != null) {
            lines.add("Pay online: " + link);
        }
        if (instructions != null) {
            lines.add(instructions);
        }
        return String.join("\n", lines);
    }

    private boolean isEmpty() {
        return Stream.of(accountHolder, bankName, accountNumber, bankCode, upiId, link, instructions)
                .allMatch(Objects::isNull);
    }
}
