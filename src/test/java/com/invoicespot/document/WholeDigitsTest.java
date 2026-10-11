package com.invoicespot.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.invoicespot.common.ApiException;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WholeDigitsTest {

    @Test
    @DisplayName("an amount keeps at most 15 digits before the point, counted however it is written")
    void wholeDigits() {
        BigDecimal largest = new BigDecimal("999999999999999.99");
        assertThat(DocumentService.wholeDigits(largest)).isSameAs(largest);
        assertThat(DocumentService.wholeDigits(new BigDecimal("1E+14"))).isEqualByComparingTo("100000000000000");

        for (String amount : new String[] {"1000000000000000", "1E+15", "1E+30"}) {
            assertThatThrownBy(() -> DocumentService.wholeDigits(new BigDecimal(amount)))
                    .isInstanceOf(ApiException.class)
                    .hasMessage("Amounts can have at most 15 whole digits");
        }
    }
}
