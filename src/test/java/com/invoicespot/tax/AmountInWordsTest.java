package com.invoicespot.tax;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AmountInWordsTest {

    private static String words(String amount, String currency) {
        return AmountInWords.of(new BigDecimal(amount), currency);
    }

    @Test
    @DisplayName("rupees count in thousands, lakhs and crores, with the paise after them")
    void rupees() {
        assertThat(words("123456.78", "INR"))
                .isEqualTo("Rupees One Lakh Twenty Three Thousand Four Hundred Fifty Six and Seventy Eight Paise Only");
        assertThat(words("10000000", "INR")).isEqualTo("Rupees One Crore Only");
        assertThat(words("1000000000000", "INR")).isEqualTo("Rupees One Lakh Crore Only");
        assertThat(words("0.50", "INR")).isEqualTo("Rupees Zero and Fifty Paise Only");
        assertThat(words("1015.05", "INR")).isEqualTo("Rupees One Thousand Fifteen and Five Paise Only");
        assertThat(words("2360", "INR")).isEqualTo("Rupees Two Thousand Three Hundred Sixty Only");
    }

    @Test
    @DisplayName("other currencies are written cheque style, with the cents as a fraction")
    void others() {
        assertThat(words("1234.56", "USD")).isEqualTo("USD One Thousand Two Hundred Thirty Four and 56/100");
        assertThat(words("2500000", "EUR")).isEqualTo("EUR Two Million Five Hundred Thousand and 00/100");
        assertThat(words("90", "GBP")).isEqualTo("GBP Ninety and 00/100");
    }
}
