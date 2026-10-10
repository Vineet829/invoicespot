package com.invoicespot.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MoneyFormatTest {

    @ParameterizedTest(name = "{0} rupees read {1}")
    @CsvSource({
        "0, 0.00",
        "7.5, 7.50",
        "999, 999.00",
        "1000, '1,000.00'",
        "12345.678, '12,345.68'",
        "100000, '1,00,000.00'",
        "1234567, '12,34,567.00'",
        "12345678.9, '1,23,45,678.90'",
        "1000000000000, '10,00,00,00,00,000.00'",
        "-150000, '-1,50,000.00'",
        "0.005, 0.01",
        "-0.001, 0.00"
    })
    @DisplayName("rupees are grouped in lakhs and crores, rounded half up to paise")
    void rupees(String amount, String expected) {
        assertThat(MoneyFormat.format(new BigDecimal(amount), "INR")).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0} {1} reads {2}")
    @CsvSource({
        "100000, USD, '100,000.00'",
        "1234567.891, EUR, '1,234,567.89'",
        "-1500, GBP, '-1,500.00'",
        "999.999, AED, '1,000.00'"
    })
    @DisplayName("every other currency is grouped in thousands")
    void otherCurrencies(String amount, String currency, String expected) {
        assertThat(MoneyFormat.format(new BigDecimal(amount), currency)).isEqualTo(expected);
    }

    @Test
    @DisplayName("a missing amount reads as zero, and a missing or lower-case currency still formats")
    void missing() {
        assertThat(MoneyFormat.format(null, "INR")).isEqualTo("0.00");
        assertThat(MoneyFormat.format(new BigDecimal("250000"), null)).isEqualTo("250,000.00");
        assertThat(MoneyFormat.format(new BigDecimal("250000"), "inr")).isEqualTo("2,50,000.00");
    }
}
