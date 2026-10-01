package com.invoicespot.common;

import java.math.BigDecimal;
import org.springframework.http.HttpStatus;

// A percentage typed in as text: a number from 0 to 100 with up to 4
// decimals, or nothing.
public final class Percentage {

    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    private Percentage() {}

    // Null when nothing was given; the label names the field when it is wrong.
    public static BigDecimal parse(String value, String label) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            BigDecimal number = new BigDecimal(value.trim());
            if (number.signum() >= 0
                    && number.compareTo(ONE_HUNDRED) <= 0
                    && number.stripTrailingZeros().scale() <= 4) {
                return number;
            }
        } catch (NumberFormatException exception) {
            throw error(label);
        }
        throw error(label);
    }

    // A percentage as it was typed: 18 rather than 18.0000, and never 1E+1.
    public static BigDecimal plain(BigDecimal value) {
        if (value == null) {
            return null;
        }
        BigDecimal stripped = value.stripTrailingZeros();
        return stripped.scale() < 0 ? stripped.setScale(0) : stripped;
    }

    private static ApiException error(String label) {
        return new ApiException(
                HttpStatus.BAD_REQUEST, label + " must be a number from 0 to 100, with up to 4 decimals");
    }
}
