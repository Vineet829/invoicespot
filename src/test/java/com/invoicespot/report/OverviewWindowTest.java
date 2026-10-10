package com.invoicespot.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.invoicespot.common.ApiException;
import com.invoicespot.report.OverviewWindow.Bucket;
import com.invoicespot.report.OverviewWindow.Step;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class OverviewWindowTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 4);

    @Test
    @DisplayName("the last 90 days by default, a week at a time, beside the 90 days before")
    void ninetyDays() {
        OverviewWindow window = new OverviewWindow(
                "90d", LocalDate.of(2026, 7, 7), TODAY, LocalDate.of(2026, 4, 8), LocalDate.of(2026, 7, 6), Step.WEEK);
        assertThat(OverviewWindow.of(null, TODAY)).isEqualTo(window);
        assertThat(OverviewWindow.of(" ", TODAY)).isEqualTo(window);
        assertThat(OverviewWindow.of("90d", TODAY)).isEqualTo(window);
    }

    @Test
    @DisplayName("the last 30 days go a day at a time, beside the 30 days before")
    void thirtyDays() {
        assertThat(OverviewWindow.of("30D", TODAY))
                .isEqualTo(new OverviewWindow(
                        "30d", LocalDate.of(2026, 9, 5), TODAY, LocalDate.of(2026, 8, 6), LocalDate.of(2026, 9, 4),
                        Step.DAY));
    }

    @Test
    @DisplayName("12 months go back to the same day a year ago and a month at a time, beside the 12 months before")
    void twelveMonths() {
        assertThat(OverviewWindow.of("12m", TODAY))
                .isEqualTo(new OverviewWindow(
                        "12m", LocalDate.of(2025, 10, 5), TODAY, LocalDate.of(2024, 10, 5),
                        LocalDate.of(2025, 10, 4), Step.MONTH));
        // At a month's end they are whole months.
        assertThat(OverviewWindow.of("12m", LocalDate.of(2026, 3, 31)))
                .isEqualTo(new OverviewWindow(
                        "12m", LocalDate.of(2025, 4, 1), LocalDate.of(2026, 3, 31), LocalDate.of(2024, 4, 1),
                        LocalDate.of(2025, 3, 31), Step.MONTH));
        assertThat(OverviewWindow.of("12m", LocalDate.of(2026, 12, 31)))
                .isEqualTo(new OverviewWindow(
                        "12m", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), LocalDate.of(2025, 1, 1),
                        LocalDate.of(2025, 12, 31), Step.MONTH));
        // 28 February 2025 ends its month: 12 whole months from March, with
        // no one-day 29 February before them, beside the 12 before those.
        assertThat(OverviewWindow.of("12m", LocalDate.of(2025, 2, 28)))
                .isEqualTo(new OverviewWindow(
                        "12m", LocalDate.of(2024, 3, 1), LocalDate.of(2025, 2, 28), LocalDate.of(2023, 3, 1),
                        LocalDate.of(2024, 2, 29), Step.MONTH));
        // A year before 29 February had no 29th: the 12 months begin on 1 March.
        assertThat(OverviewWindow.of("12m", LocalDate.of(2028, 2, 29)))
                .isEqualTo(new OverviewWindow(
                        "12m", LocalDate.of(2027, 3, 1), LocalDate.of(2028, 2, 29), LocalDate.of(2026, 3, 1),
                        LocalDate.of(2027, 2, 28), Step.MONTH));
    }

    @Test
    @DisplayName("the 12 months before are as long as the 12 months, but for a 29 February in only one of them")
    void twelveMonthsBefore() {
        for (LocalDate today : List.of(
                TODAY, LocalDate.of(2027, 1, 1), LocalDate.of(2026, 3, 31), LocalDate.of(2026, 5, 31),
                LocalDate.of(2026, 12, 31))) {
            OverviewWindow window = OverviewWindow.of("12m", today);
            assertThat(window.previousTo()).isEqualTo(window.from().minusDays(1));
            assertThat(ChronoUnit.DAYS.between(window.previousFrom(), window.previousTo()))
                    .as(today.toString())
                    .isEqualTo(ChronoUnit.DAYS.between(window.from(), window.to()));
        }
        OverviewWindow leapDay = OverviewWindow.of("12m", LocalDate.of(2028, 2, 29));
        assertThat(ChronoUnit.DAYS.between(leapDay.from(), leapDay.to()))
                .isEqualTo(ChronoUnit.DAYS.between(leapDay.previousFrom(), leapDay.previousTo()) + 1);
        // The 12 whole months to 28 February 2025 are a day shorter than the
        // 12 before them, which hold 29 February 2024.
        OverviewWindow afterLeapYear = OverviewWindow.of("12m", LocalDate.of(2025, 2, 28));
        assertThat(ChronoUnit.DAYS.between(afterLeapYear.from(), afterLeapYear.to()) + 1)
                .isEqualTo(ChronoUnit.DAYS.between(afterLeapYear.previousFrom(), afterLeapYear.previousTo()));
    }

    @Test
    @DisplayName("this year runs from 1 January to today, beside the same days last year")
    void thisYear() {
        assertThat(OverviewWindow.of("ytd", TODAY))
                .isEqualTo(new OverviewWindow(
                        "ytd", LocalDate.of(2026, 1, 1), TODAY, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 10, 4),
                        Step.MONTH));
        // 29 February is compared with the year before up to 28 February.
        assertThat(OverviewWindow.of("ytd", LocalDate.of(2028, 2, 29)).previousTo())
                .isEqualTo(LocalDate.of(2027, 2, 28));
    }

    @Test
    @DisplayName("90 days go a week at a time from the first day, the last week ending today")
    void weeks() {
        List<Bucket> weeks = OverviewWindow.of("90d", TODAY).buckets();
        assertThat(weeks).hasSize(13);
        assertThat(weeks.get(0)).isEqualTo(new Bucket(LocalDate.of(2026, 7, 7), LocalDate.of(2026, 7, 13)));
        assertThat(weeks.get(11)).isEqualTo(new Bucket(LocalDate.of(2026, 9, 22), LocalDate.of(2026, 9, 28)));
        assertThat(weeks.get(12)).isEqualTo(new Bucket(LocalDate.of(2026, 9, 29), TODAY));
    }

    @Test
    @DisplayName("30 days go a day at a time")
    void days() {
        List<Bucket> days = OverviewWindow.of("30d", TODAY).buckets();
        assertThat(days).hasSize(30);
        assertThat(days.get(0)).isEqualTo(new Bucket(LocalDate.of(2026, 9, 5), LocalDate.of(2026, 9, 5)));
        assertThat(days.get(29)).isEqualTo(new Bucket(TODAY, TODAY));
    }

    @Test
    @DisplayName("12 months and this year go a calendar month at a time, the first from the period's first day and"
            + " this month to date")
    void months() {
        List<Bucket> months = OverviewWindow.of("12m", TODAY).buckets();
        assertThat(months).hasSize(13);
        assertThat(months.get(0)).isEqualTo(new Bucket(LocalDate.of(2025, 10, 5), LocalDate.of(2025, 10, 31)));
        assertThat(months.get(1)).isEqualTo(new Bucket(LocalDate.of(2025, 11, 1), LocalDate.of(2025, 11, 30)));
        assertThat(months.get(4)).isEqualTo(new Bucket(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28)));
        assertThat(months.get(12)).isEqualTo(new Bucket(LocalDate.of(2026, 10, 1), TODAY));
        // At a month's end, 12 whole months.
        List<Bucket> whole = OverviewWindow.of("12m", LocalDate.of(2026, 3, 31)).buckets();
        assertThat(whole).hasSize(12);
        assertThat(whole.get(0)).isEqualTo(new Bucket(LocalDate.of(2025, 4, 1), LocalDate.of(2025, 4, 30)));
        assertThat(whole.get(11)).isEqualTo(new Bucket(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31)));
        List<Bucket> thisYear = OverviewWindow.of("ytd", TODAY).buckets();
        assertThat(thisYear).hasSize(10);
        assertThat(thisYear.get(0)).isEqualTo(new Bucket(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31)));
        assertThat(thisYear.get(9)).isEqualTo(new Bucket(LocalDate.of(2026, 10, 1), TODAY));
        assertThat(OverviewWindow.of("ytd", LocalDate.of(2026, 1, 1)).buckets())
                .containsExactly(new Bucket(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 1)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"30d", "90d", "12m", "ytd"})
    @DisplayName("the trend covers every day of the period once, in order")
    void covered(String key) {
        for (LocalDate today : List.of(
                TODAY, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31), LocalDate.of(2028, 2, 29),
                LocalDate.of(2026, 12, 31))) {
            OverviewWindow window = OverviewWindow.of(key, today);
            List<Bucket> buckets = window.buckets();
            assertThat(buckets.get(0).from()).isEqualTo(window.from());
            assertThat(buckets.get(buckets.size() - 1).to()).isEqualTo(today);
            for (int bucket = 0; bucket < buckets.size(); bucket++) {
                assertThat(buckets.get(bucket).to()).isAfterOrEqualTo(buckets.get(bucket).from());
                if (bucket > 0) {
                    assertThat(buckets.get(bucket).from()).isEqualTo(buckets.get(bucket - 1).to().plusDays(1));
                }
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"7d", "3m", "6m", "1y", "2025", "all"})
    @DisplayName("any other period is refused")
    void unknown(String key) {
        assertThatThrownBy(() -> OverviewWindow.of(key, TODAY))
                .isInstanceOf(ApiException.class)
                .hasMessage("Choose 30 days, 90 days, 12 months or this year");
    }
}
