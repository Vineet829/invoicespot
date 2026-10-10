package com.invoicespot.payment;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;

// A request to pay by UPI, as the link a UPI app opens or a QR code holds:
// upi://pay?pa=…&pn=…&am=…&cu=INR&tn=… Values are percent-encoded as RFC 3986
// says, with an @ left as it is and a space as %20, the form UPI apps read
// most widely. An amount over what UPI apps send at once is left out, for the
// payer to enter.
public record UpiPayment(String vpa, String payee, BigDecimal amount, String note) {

    static final BigDecimal MOST_AT_ONCE = new BigDecimal("100000.00");
    private static final int MOST_CHARACTERS = 50;
    private static final char[] HEX = "0123456789ABCDEF".toCharArray();

    public boolean amountIncluded() {
        return amount.compareTo(MOST_AT_ONCE) <= 0;
    }

    public String uri() {
        StringBuilder uri = new StringBuilder("upi://pay?pa=")
                .append(encode(vpa))
                .append("&pn=")
                .append(encode(capped(payee)));
        if (amountIncluded()) {
            uri.append("&am=").append(amount.setScale(2, RoundingMode.HALF_UP).toPlainString());
        }
        uri.append("&cu=INR");
        if (note != null && !note.isBlank()) {
            uri.append("&tn=").append(encode(capped(note)));
        }
        return uri.toString();
    }

    private static String capped(String value) {
        String trimmed = value.trim();
        return trimmed.codePointCount(0, trimmed.length()) <= MOST_CHARACTERS
                ? trimmed
                : trimmed.substring(0, trimmed.offsetByCodePoints(0, MOST_CHARACTERS));
    }

    private static String encode(String value) {
        StringBuilder encoded = new StringBuilder();
        for (byte b : value.getBytes(StandardCharsets.UTF_8)) {
            char c = (char) (b & 0xFF);
            if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || "-._~@".indexOf(c) >= 0) {
                encoded.append(c);
            } else {
                encoded.append('%').append(HEX[(b >> 4) & 0xF]).append(HEX[b & 0xF]);
            }
        }
        return encoded.toString();
    }
}
