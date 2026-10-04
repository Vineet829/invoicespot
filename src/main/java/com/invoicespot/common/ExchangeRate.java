package com.invoicespot.common;

import java.math.BigDecimal;
import org.springframework.http.HttpStatus;

// An exchange rate typed in as text, "1 {currency} = x {base}": a number
// greater than zero with up to 10 decimals, or nothing.
public final class ExchangeRate {

    private static final int MAX_DECIMALS = 10;
    private static final int MAX_WHOLE_DIGITS = 10;

    private ExchangeRate() {}

    // Null when nothing was given.
    public static BigDecimal parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String text = value.trim();
        // Only plain digits are read, with a decimal point: never 1e2, and
        // never so many digits that reading them as a number takes seconds.
        if (!text.matches("[0-9]{0,20}(\\.[0-9]{0,20})?")) {
            throw error("Enter the exchange rate as a number greater than zero");
        }
        BigDecimal rate;
        try {
            rate = new BigDecimal(text);
        } catch (NumberFormatException exception) {
            throw error("Enter the exchange rate as a number greater than zero");
        }
        if (rate.signum() <= 0) {
            throw error("Enter the exchange rate as a number greater than zero");
        }
        BigDecimal stripped = rate.stripTrailingZeros();
        if (stripped.scale() > MAX_DECIMALS) {
            throw error("The exchange rate can have at most 10 decimals");
        }
        if (stripped.precision() - stripped.scale() > MAX_WHOLE_DIGITS) {
            throw error("The exchange rate is too large");
        }
        return rate.setScale(MAX_DECIMALS);
    }

    // A rate as it was typed: 83.25 rather than 83.2500000000, and never 1E+2.
    public static String plain(BigDecimal rate) {
        if (rate == null) {
            return null;
        }
        BigDecimal stripped = rate.stripTrailingZeros();
        return (stripped.scale() < 0 ? stripped.setScale(0) : stripped).toPlainString();
    }

    private static ApiException error(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, message);
    }
}
