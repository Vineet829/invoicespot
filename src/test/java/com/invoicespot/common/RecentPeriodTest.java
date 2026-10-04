package com.invoicespot.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RecentPeriodTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 4);

    @Test
    @DisplayName("the last 30 or 90 days end today; the last 12 months start on the first of a month")
    void periods() {
        assertThat(RecentPeriod.of("30d", TODAY)).isEqualTo(new RecentPeriod("30d", LocalDate.of(2026, 9, 5), TODAY));
        assertThat(RecentPeriod.of(" 90D ", TODAY))
                .isEqualTo(new RecentPeriod("90d", LocalDate.of(2026, 7, 7), TODAY));
        assertThat(RecentPeriod.of("12m", TODAY))
                .isEqualTo(new RecentPeriod("12m", LocalDate.of(2025, 11, 1), TODAY));
        assertThat(RecentPeriod.of("12m", LocalDate.of(2028, 2, 29)).from()).isEqualTo(LocalDate.of(2027, 3, 1));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ytd", "7d", "3m", "2025", ""})
    @DisplayName("any other period is refused")
    void unknown(String key) {
        assertThatThrownBy(() -> RecentPeriod.of(key, TODAY))
                .isInstanceOf(ApiException.class)
                .hasMessage("Choose 30 days, 90 days or 12 months");
    }
}
