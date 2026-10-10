package com.invoicespot.tax;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;

// Shares an amount out among parts by their values: each part gets its value
// times the factor, rounded half up to the paisa, and what rounding leaves
// over goes to the part with the largest value (the first in the map's order
// on a tie), so the parts add up to the amount exactly. An amount of nothing
// gives every part nothing.
public final class Apportion {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

    private Apportion() {}

    public static <K> Map<K, BigDecimal> of(Map<K, BigDecimal> values, BigDecimal factor, BigDecimal amount) {
        Map<K, BigDecimal> parts = new LinkedHashMap<>();
        BigDecimal given = ZERO;
        K largest = null;
        for (Map.Entry<K, BigDecimal> value : values.entrySet()) {
            BigDecimal part = amount.signum() == 0
                    ? ZERO
                    : value.getValue().multiply(factor).setScale(2, RoundingMode.HALF_UP);
            parts.put(value.getKey(), part);
            given = given.add(part);
            if (largest == null || value.getValue().compareTo(values.get(largest)) > 0) {
                largest = value.getKey();
            }
        }
        if (largest != null) {
            parts.merge(largest, amount.subtract(given), BigDecimal::add);
        }
        return parts;
    }
}
