package com.invoicespot.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.invoicespot.common.ApiException;
import com.invoicespot.report.dto.ViewPeriod;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ListPeriodTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 6);

    private static ListPeriod period(
            String key, String from, String to, String previousFrom, String previousTo) {
        return new ListPeriod(
                key, LocalDate.parse(from), LocalDate.parse(to), LocalDate.parse(previousFrom),
                LocalDate.parse(previousTo));
    }

    @Test
    @DisplayName("no period is all dates, with nothing to compare with")
    void allDates() {
        for (String key : new String[] {null, "", "  "}) {
            ListPeriod period = ListPeriod.of(key, TODAY);
            assertThat(period).isEqualTo(new ListPeriod("", null, null, null, null));
            assertThat(period.compared()).isFalse();
            assertThat(period.firstDay()).isEqualTo(LocalDate.of(1, 1, 1));
            assertThat(period.lastDay()).isEqualTo(LocalDate.of(9999, 12, 31));
            assertThat(period.view()).isEqualTo(new ViewPeriod("", null, null, null, null));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"30d", "90d", "12m", "ytd"})
    @DisplayName("the overview's periods are its own days, beside the days it compares them with")
    void overviewPeriods(String key) {
        for (LocalDate today : List.of(
                TODAY, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31), LocalDate.of(2028, 2, 29),
                LocalDate.of(2026, 12, 31))) {
            OverviewWindow window = OverviewWindow.of(key, today);
            assertThat(ListPeriod.of(key.toUpperCase(), today))
                    .isEqualTo(new ListPeriod(
                            key, window.from(), window.to(), window.previousFrom(), window.previousTo()));
        }
        assertThat(ListPeriod.of("30d", TODAY))
                .isEqualTo(period("30d", "2026-09-07", "2026-10-06", "2026-08-08", "2026-09-06"));
        assertThat(ListPeriod.of("12m", TODAY))
                .isEqualTo(period("12m", "2025-11-01", "2026-10-06", "2024-11-01", "2025-10-31"));
        assertThat(ListPeriod.of("ytd", TODAY))
                .isEqualTo(period("ytd", "2026-01-01", "2026-10-06", "2025-01-01", "2025-10-06"));
    }

    @Test
    @DisplayName("a month, quarter or year runs from its first day to its last; one still running is compared"
            + " with the one before up to the same day, and one gone by with the whole one before it")
    void calendarPeriods() {
        assertThat(ListPeriod.of("this-month", TODAY))
                .isEqualTo(period("this-month", "2026-10-01", "2026-10-31", "2026-09-01", "2026-09-06"));
        assertThat(ListPeriod.of("Last-Month", TODAY))
                .isEqualTo(period("last-month", "2026-09-01", "2026-09-30", "2026-08-01", "2026-08-31"));
        assertThat(ListPeriod.of("this-quarter", TODAY))
                .isEqualTo(period("this-quarter", "2026-10-01", "2026-12-31", "2026-07-01", "2026-07-06"));
        assertThat(ListPeriod.of("this-year", TODAY))
                .isEqualTo(period("this-year", "2026-01-01", "2026-12-31", "2025-01-01", "2025-10-06"));
        assertThat(ListPeriod.of("last-year", TODAY))
                .isEqualTo(period("last-year", "2025-01-01", "2025-12-31", "2024-01-01", "2024-12-31"));
        assertThat(ListPeriod.of("this-month", TODAY).compared()).isTrue();
        assertThat(ListPeriod.of("this-month", TODAY).firstDay()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(ListPeriod.of("this-month", TODAY).lastDay()).isEqualTo(LocalDate.of(2026, 10, 31));
    }

    @Test
    @DisplayName("a day the period before doesn't have is compared up to its last day")
    void shorterMonths() {
        LocalDate march = LocalDate.of(2026, 3, 31);
        assertThat(ListPeriod.of("this-month", march))
                .isEqualTo(period("this-month", "2026-03-01", "2026-03-31", "2026-02-01", "2026-02-28"));
        assertThat(ListPeriod.of("last-month", march))
                .isEqualTo(period("last-month", "2026-02-01", "2026-02-28", "2026-01-01", "2026-01-31"));
        assertThat(ListPeriod.of("this-quarter", march))
                .isEqualTo(period("this-quarter", "2026-01-01", "2026-03-31", "2025-10-01", "2025-12-31"));
        assertThat(ListPeriod.of("this-quarter", LocalDate.of(2026, 5, 31)))
                .isEqualTo(period("this-quarter", "2026-04-01", "2026-06-30", "2026-01-01", "2026-02-28"));
        LocalDate leapDay = LocalDate.of(2028, 2, 29);
        assertThat(ListPeriod.of("this-month", leapDay))
                .isEqualTo(period("this-month", "2028-02-01", "2028-02-29", "2028-01-01", "2028-01-29"));
        assertThat(ListPeriod.of("this-year", leapDay))
                .isEqualTo(period("this-year", "2028-01-01", "2028-12-31", "2027-01-01", "2027-02-28"));
        assertThat(ListPeriod.of("last-month", LocalDate.of(2028, 3, 15)))
                .isEqualTo(period("last-month", "2028-02-01", "2028-02-29", "2028-01-01", "2028-01-31"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"7d", "3m", "6m", "2025", "all", "this-week", "next-month"})
    @DisplayName("any other period is refused")
    void unknown(String key) {
        assertThatThrownBy(() -> ListPeriod.of(key, TODAY))
                .isInstanceOf(ApiException.class)
                .hasMessage("Choose a period from the list");
    }
}
