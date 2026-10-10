package com.invoicespot.tax;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.function.Function;

// An amount written out for a GST invoice. Rupees count in the Indian way, in
// thousands, lakhs and crores, with the paise after them: "Rupees One Lakh
// Twenty Three Thousand Four Hundred Fifty Six and Seventy Eight Paise Only".
// Other currencies are written cheque style, with the cents as a fraction,
// since each names its minor unit differently: "USD One Thousand Two Hundred
// Thirty Four and 56/100".
public final class AmountInWords {

    private static final String[] ONES = {
        "Zero", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine", "Ten", "Eleven", "Twelve",
        "Thirteen", "Fourteen", "Fifteen", "Sixteen", "Seventeen", "Eighteen", "Nineteen",
    };
    private static final String[] TENS = {
        "", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety",
    };
    private static final BigInteger HUNDRED = BigInteger.valueOf(100);
    private static final BigInteger THOUSAND = BigInteger.valueOf(1_000);
    private static final BigInteger LAKH = BigInteger.valueOf(100_000);
    private static final BigInteger CRORE = BigInteger.valueOf(10_000_000);
    private static final BigInteger MILLION = BigInteger.valueOf(1_000_000);
    private static final BigInteger BILLION = BigInteger.valueOf(1_000_000_000);

    private AmountInWords() {}

    public static String of(BigDecimal amount, String currency) {
        BigDecimal cents = amount.abs().setScale(2, RoundingMode.HALF_UP);
        BigInteger whole = cents.toBigInteger();
        int fraction = cents.remainder(BigDecimal.ONE).movePointRight(2).intValueExact();
        if ("INR".equals(currency)) {
            return "Rupees " + indian(whole)
                    + (fraction > 0 ? " and " + indian(BigInteger.valueOf(fraction)) + " Paise" : "")
                    + " Only";
        }
        return (currency == null || currency.isBlank() ? "" : currency + " ")
                + international(whole)
                + " and " + String.format("%02d", fraction) + "/100";
    }

    // Crores can themselves run to lakhs and more: "One Lakh Crore".
    static String indian(BigInteger number) {
        return join(
                number,
                new BigInteger[] {CRORE, LAKH, THOUSAND},
                new String[] {"Crore", "Lakh", "Thousand"},
                AmountInWords::indian);
    }

    static String international(BigInteger number) {
        return join(
                number,
                new BigInteger[] {BILLION, MILLION, THOUSAND},
                new String[] {"Billion", "Million", "Thousand"},
                AmountInWords::international);
    }

    private static String join(
            BigInteger number, BigInteger[] units, String[] names, Function<BigInteger, String> self) {
        for (int index = 0; index < units.length; index++) {
            if (number.compareTo(units[index]) >= 0) {
                BigInteger[] parts = number.divideAndRemainder(units[index]);
                String rest = parts[1].signum() == 0 ? "" : " " + self.apply(parts[1]);
                return self.apply(parts[0]) + " " + names[index] + rest;
            }
        }
        return belowThousand(number.intValueExact());
    }

    private static String belowThousand(int number) {
        if (number >= 100) {
            return ONES[number / 100] + " Hundred" + (number % 100 == 0 ? "" : " " + belowHundred(number % 100));
        }
        return belowHundred(number);
    }

    private static String belowHundred(int number) {
        if (number < 20) {
            return ONES[number];
        }
        return TENS[number / 10] + (number % 10 == 0 ? "" : " " + ONES[number % 10]);
    }
}
