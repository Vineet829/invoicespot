package com.invoicespot.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.invoicespot.common.ApiException;
import com.invoicespot.report.OverviewWindow.Bucket;
import com.invoicespot.report.OverviewWindow.Step;
import java.time.LocalDate;
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
    @DisplayName("12 months start on the first of a month and go a month at a time, beside the 12 months before")
    void twelveMonths() {
        assertThat(OverviewWindow.of("12m", TODAY))
                .isEqualTo(new OverviewWindow(
                        "12m", LocalDate.of(2025, 11, 1), TODAY, LocalDate.of(2024, 11, 1),
                        LocalDate.of(2025, 10, 31), Step.MONTH));
        assertThat(OverviewWindow.of("12m", LocalDate.of(2026, 12, 31)).from()).isEqualTo(LocalDate.of(2026, 1, 1));
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
    @DisplayName("12 months and this year go a calendar month at a time, this month to date")
    void months() {
        List<Bucket> months = OverviewWindow.of("12m", TODAY).buckets();
        assertThat(months).hasSize(12);
        assertThat(months.get(0)).isEqualTo(new Bucket(LocalDate.of(2025, 11, 1), LocalDate.of(2025, 11, 30)));
        assertThat(months.get(3)).isEqualTo(new Bucket(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28)));
        assertThat(months.get(11)).isEqualTo(new Bucket(LocalDate.of(2026, 10, 1), TODAY));
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
