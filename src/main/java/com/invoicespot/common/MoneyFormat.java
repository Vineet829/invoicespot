package com.invoicespot.common;

import java.math.BigDecimal;
import java.math.RoundingMode;

// An amount as people read it in its currency: rupees grouped in lakhs and
// crores (1,00,000.00), every other currency in thousands (100,000.00).
// DecimalFormat can't group rupees: it knows only one group size.
public final class MoneyFormat {

    private MoneyFormat() {}

    public static String format(BigDecimal amount, String currency) {
        BigDecimal value = (amount == null ? BigDecimal.ZERO : amount).setScale(2, RoundingMode.HALF_UP);
        String digits = value.abs().toPlainString();
        int point = digits.indexOf('.');
        String whole = grouped(digits.substring(0, point), "INR".equalsIgnoreCase(currency) ? 2 : 3);
        return (value.signum() < 0 ? "-" : "") + whole + digits.substring(point);
    }

    // The last three digits, then groups of the given size.
    private static String grouped(String whole, int size) {
        StringBuilder out = new StringBuilder();
        int group = 3;
        int count = 0;
        for (int index = whole.length() - 1; index >= 0; index--) {
            if (count == group) {
                out.append(',');
                count = 0;
                group = size;
            }
            out.append(whole.charAt(index));
            count++;
        }
        return out.reverse().toString();
    }
}
