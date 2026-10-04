package com.invoicespot.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.invoicespot.common.ApiException;
import java.time.LocalDate;
import java.time.YearMonth;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ReportPeriodTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 30);

    @Test
    @DisplayName("the last few months start on the first of a month and end today")
    void lastMonths() {
        assertThat(ReportPeriod.of(null, TODAY))
                .isEqualTo(new ReportPeriod("12m", LocalDate.of(2025, 10, 1), TODAY));
        assertThat(ReportPeriod.of("6M", TODAY)).isEqualTo(new ReportPeriod("6m", LocalDate.of(2026, 4, 1), TODAY));
        assertThat(ReportPeriod.of("3m", TODAY).months()).containsExactly(
                YearMonth.of(2026, 7), YearMonth.of(2026, 8), YearMonth.of(2026, 9));
        assertThat(ReportPeriod.of("12m", TODAY).months()).hasSize(12);
    }

    @Test
    @DisplayName("the last 30 or 90 days are the overview's, over every month they touch")
    void lastDays() {
        LocalDate today = LocalDate.of(2026, 10, 4);
        assertThat(ReportPeriod.of("30d", today))
                .isEqualTo(new ReportPeriod("30d", LocalDate.of(2026, 9, 5), today));
        assertThat(ReportPeriod.of("90D", today))
                .isEqualTo(new ReportPeriod("90d", LocalDate.of(2026, 7, 7), today));
        assertThat(ReportPeriod.of("30d", today).months()).containsExactly(YearMonth.of(2026, 9), YearMonth.of(2026, 10));
        assertThat(ReportPeriod.of("90d", today).months()).containsExactly(
                YearMonth.of(2026, 7), YearMonth.of(2026, 8), YearMonth.of(2026, 9), YearMonth.of(2026, 10));
        assertThat(ReportPeriod.of("30d", TODAY).months()).containsExactly(YearMonth.of(2026, 9));
        for (String key : new String[] {"30d", "90d"}) {
            for (LocalDate day : new LocalDate[] {today, TODAY, LocalDate.of(2028, 2, 29), LocalDate.of(2027, 1, 1)}) {
                OverviewWindow window = OverviewWindow.of(key, day);
                assertThat(ReportPeriod.of(key, day)).isEqualTo(new ReportPeriod(key, window.from(), window.to()));
            }
        }
    }

    @Test
    @DisplayName("this year runs to today, and an earlier year covers all of it")
    void years() {
        assertThat(ReportPeriod.of("ytd", TODAY)).isEqualTo(new ReportPeriod("ytd", LocalDate.of(2026, 1, 1), TODAY));
        assertThat(ReportPeriod.of("2025", TODAY))
                .isEqualTo(new ReportPeriod("2025", LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31)));
        assertThat(ReportPeriod.of("2025", TODAY).months()).hasSize(12);
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026", "2027", "1999", "+2025", "7d", "60d", "all"})
    @DisplayName("any other period is refused")
    void unknown(String key) {
        assertThatThrownBy(() -> ReportPeriod.of(key, TODAY))
                .isInstanceOf(ApiException.class)
                .hasMessage("Choose a period from the list");
    }
}
