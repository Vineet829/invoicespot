package com.invoicespot.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PagesTest {

    @Test
    @DisplayName("a page number is at least the first, and at most the last a query can start at")
    void number() {
        assertThat(Pages.number(0, 20)).isEqualTo(1);
        assertThat(Pages.number(-5, 20)).isEqualTo(1);
        assertThat(Pages.number(7, 20)).isEqualTo(7);
        assertThat(Pages.number(Integer.MAX_VALUE, 20)).isEqualTo(Integer.MAX_VALUE / 20);
        assertThat((long) (Pages.number(Integer.MAX_VALUE, 20) - 1) * 20).isLessThanOrEqualTo(Integer.MAX_VALUE);
    }
}
