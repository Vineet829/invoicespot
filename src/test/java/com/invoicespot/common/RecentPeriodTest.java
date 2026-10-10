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
    @DisplayName("the last 30 or 90 days end today; the last 12 months go back to the same day a year ago")
    void periods() {
        assertThat(RecentPeriod.of("30d", TODAY)).isEqualTo(new RecentPeriod("30d", LocalDate.of(2026, 9, 5), TODAY));
        assertThat(RecentPeriod.of(" 90D ", TODAY))
                .isEqualTo(new RecentPeriod("90d", LocalDate.of(2026, 7, 7), TODAY));
        assertThat(RecentPeriod.of("12m", TODAY))
                .isEqualTo(new RecentPeriod("12m", LocalDate.of(2025, 10, 5), TODAY));
        assertThat(RecentPeriod.of("12m", LocalDate.of(2026, 3, 31)).from()).isEqualTo(LocalDate.of(2025, 4, 1));
        assertThat(RecentPeriod.of("12m", LocalDate.of(2026, 12, 31)).from()).isEqualTo(LocalDate.of(2026, 1, 1));
        // A year before 29 February had no 29th, so the 12 months begin on 1 March.
        assertThat(RecentPeriod.of("12m", LocalDate.of(2028, 2, 29)).from()).isEqualTo(LocalDate.of(2027, 3, 1));
        // 28 February 2029 ends its month, so its 12 months begin on 1 March,
        // not on a 29 February of their own.
        assertThat(RecentPeriod.of("12m", LocalDate.of(2029, 2, 28)).from()).isEqualTo(LocalDate.of(2028, 3, 1));
    }

    @Test
    @DisplayName("the last few months are whole months on a month's last day, and go back to the same day otherwise")
    void monthsBack() {
        assertThat(RecentPeriod.monthsBack(LocalDate.of(2026, 10, 8), 3)).isEqualTo(LocalDate.of(2026, 7, 9));
        assertThat(RecentPeriod.monthsBack(LocalDate.of(2026, 9, 30), 6)).isEqualTo(LocalDate.of(2026, 4, 1));
        assertThat(RecentPeriod.monthsBack(LocalDate.of(2026, 10, 31), 12)).isEqualTo(LocalDate.of(2025, 11, 1));
        assertThat(RecentPeriod.monthsBack(LocalDate.of(2026, 5, 30), 3)).isEqualTo(LocalDate.of(2026, 3, 1));
        assertThat(RecentPeriod.monthsBack(LocalDate.of(2026, 3, 30), 3)).isEqualTo(LocalDate.of(2025, 12, 31));
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
