package com.invoicespot.report;

import com.invoicespot.common.ApiException;
import com.invoicespot.report.dto.ViewPeriod;
import java.time.LocalDate;
import java.time.Period;
import java.time.temporal.IsoFields;
import java.util.Locale;
import org.springframework.http.HttpStatus;

// The days a view the overview opens covers, by the business's calendar, as
// the documents list's period filter names them, and the days just before
// them to compare with. The last 30 or 90 days, the last 12 months and this
// year to date are the overview's own, compared as it compares them, so that
// a view agrees with the figure that opened it. A calendar month, quarter or
// year runs from its first day to its last, as the lists read it: one still
// running is compared with the one before up to the same day, as this year
// to date is, and one gone by with the whole one before it. No period at all
// is all dates, with nothing to compare with.
record ListPeriod(String key, LocalDate from, LocalDate to, LocalDate previousFrom, LocalDate previousTo) {

    // What all dates are read between: any day something can be dated.
    private static final LocalDate FIRST_DAY = LocalDate.of(1, 1, 1);
    private static final LocalDate LAST_DAY = LocalDate.of(9999, 12, 31);

    static ListPeriod of(String key, LocalDate today) {
        String value = key == null ? "" : key.trim().toLowerCase(Locale.ROOT);
        return switch (value) {
            case "" -> new ListPeriod(value, null, null, null, null);
            case "30d", "90d", "12m", "ytd" -> {
                OverviewWindow window = OverviewWindow.of(value, today);
                yield new ListPeriod(value, window.from(), window.to(), window.previousFrom(), window.previousTo());
            }
            case "this-month" -> running(value, today.withDayOfMonth(1), Period.ofMonths(1), today);
            case "last-month" -> past(value, today.withDayOfMonth(1).minusMonths(1), Period.ofMonths(1));
            case "this-quarter" -> running(value, today.with(IsoFields.DAY_OF_QUARTER, 1), Period.ofMonths(3), today);
            case "this-year" -> running(value, today.withDayOfYear(1), Period.ofYears(1), today);
            case "last-year" -> past(value, today.withDayOfYear(1).minusYears(1), Period.ofYears(1));
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "Choose a period from the list");
        };
    }

    // To its last day, beside the one before up to today's day in it; a day
    // that one doesn't have, such as 31 March's in February, is its last.
    private static ListPeriod running(String key, LocalDate first, Period length, LocalDate today) {
        return new ListPeriod(key, first, first.plus(length).minusDays(1), first.minus(length), today.minus(length));
    }

    private static ListPeriod past(String key, LocalDate first, Period length) {
        return new ListPeriod(key, first, first.plus(length).minusDays(1), first.minus(length), first.minusDays(1));
    }

    // Whether there are days before to compare with.
    boolean compared() {
        return previousFrom != null;
    }

    // The first and last days the period is read between.
    LocalDate firstDay() {
        return from == null ? FIRST_DAY : from;
    }

    LocalDate lastDay() {
        return to == null ? LAST_DAY : to;
    }

    ViewPeriod view() {
        return new ViewPeriod(key, from, to, previousFrom, previousTo);
    }
}
