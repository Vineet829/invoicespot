package com.invoicespot.report;

import com.invoicespot.common.ApiException;
import com.invoicespot.common.RecentPeriod;
import com.invoicespot.report.dto.OverviewPeriod;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;

// The days the overview covers, ending today, and the days just before them
// to compare with: the last 30 or 90 days and as many before them, the last
// 12 months from the first of a month and the 12 before them, or this year to
// date and last year up to the same day. The first three are RecentPeriod's,
// which the lists the overview opens read too.
record OverviewWindow(
        String key, LocalDate from, LocalDate to, LocalDate previousFrom, LocalDate previousTo, Step step) {

    static final String DEFAULT = "90d";

    // How the period's trend is cut up.
    enum Step {
        DAY,
        WEEK,
        MONTH
    }

    static OverviewWindow of(String key, LocalDate today) {
        String value = key == null || key.isBlank() ? DEFAULT : key.trim().toLowerCase(Locale.ROOT);
        return switch (value) {
            case "30d" -> lastDays(RecentPeriod.of(value, today), Step.DAY);
            case "90d" -> lastDays(RecentPeriod.of(value, today), Step.WEEK);
            case "12m" -> {
                LocalDate from = RecentPeriod.of(value, today).from();
                yield new OverviewWindow(value, from, today, from.minusMonths(12), from.minusDays(1), Step.MONTH);
            }
            // Compared with last year up to the same day, or to 28 February from the 29th.
            case "ytd" -> {
                LocalDate from = today.withDayOfYear(1);
                yield new OverviewWindow(value, from, today, from.minusYears(1), today.minusYears(1), Step.MONTH);
            }
            default -> throw new ApiException(
                    HttpStatus.BAD_REQUEST, "Choose 30 days, 90 days, 12 months or this year");
        };
    }

    // As many days just before them to compare with.
    private static OverviewWindow lastDays(RecentPeriod period, Step step) {
        LocalDate from = period.from();
        long days = ChronoUnit.DAYS.between(from, period.to()) + 1;
        return new OverviewWindow(period.key(), from, period.to(), from.minusDays(days), from.minusDays(1), step);
    }

    // A stretch of the period's trend, from one day to another.
    record Bucket(LocalDate from, LocalDate to) {}

    // Day by day, week by week from the first day with the last week ending
    // today, or by calendar month with this one to date.
    List<Bucket> buckets() {
        List<Bucket> buckets = new ArrayList<>();
        for (LocalDate start = from; !start.isAfter(to); start = next(start)) {
            LocalDate end = next(start).minusDays(1);
            buckets.add(new Bucket(start, end.isAfter(to) ? to : end));
        }
        return buckets;
    }

    private LocalDate next(LocalDate start) {
        return switch (step) {
            case DAY -> start.plusDays(1);
            case WEEK -> start.plusWeeks(1);
            case MONTH -> start.withDayOfMonth(1).plusMonths(1);
        };
    }

    OverviewPeriod period() {
        return new OverviewPeriod(key, from, to, previousFrom, previousTo, step.name().toLowerCase(Locale.ROOT));
    }
}
