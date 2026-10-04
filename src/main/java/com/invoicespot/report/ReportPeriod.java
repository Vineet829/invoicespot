package com.invoicespot.report;

import com.invoicespot.common.ApiException;
import com.invoicespot.common.RecentPeriod;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;

// The stretch of time a report covers. The last 30 or 90 days are the
// overview's, ending today; the last few months start on the first of a month
// and end today; a year runs from 1 January, to today for this one.
record ReportPeriod(String key, LocalDate from, LocalDate to) {

    static final String DEFAULT = "12m";
    static final int FIRST_YEAR = 2000;

    static ReportPeriod of(String key, LocalDate today) {
        String value = key == null || key.isBlank() ? DEFAULT : key.trim().toLowerCase(Locale.ROOT);
        return switch (value) {
            case "30d", "90d" -> lastDays(value, today);
            case "3m" -> lastMonths(value, 3, today);
            case "6m" -> lastMonths(value, 6, today);
            case "12m" -> lastMonths(value, 12, today);
            case "ytd" -> new ReportPeriod(value, today.withDayOfYear(1), today);
            default -> year(value, today);
        };
    }

    // The first and last of the months these days touch may be partial.
    private static ReportPeriod lastDays(String key, LocalDate today) {
        RecentPeriod days = RecentPeriod.of(key, today);
        return new ReportPeriod(key, days.from(), days.to());
    }

    private static ReportPeriod lastMonths(String key, int months, LocalDate today) {
        return new ReportPeriod(key, today.withDayOfMonth(1).minusMonths(months - 1), today);
    }

    private static ReportPeriod year(String key, LocalDate today) {
        if (!key.matches("\\d{4}")) {
            throw unknown();
        }
        int year = Integer.parseInt(key);
        if (year < FIRST_YEAR || year >= today.getYear()) {
            throw unknown();
        }
        return new ReportPeriod(key, LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31));
    }

    private static ApiException unknown() {
        return new ApiException(HttpStatus.BAD_REQUEST, "Choose a period from the list");
    }

    List<YearMonth> months() {
        List<YearMonth> months = new ArrayList<>();
        YearMonth last = YearMonth.from(to);
        for (YearMonth month = YearMonth.from(from); !month.isAfter(last); month = month.plusMonths(1)) {
            months.add(month);
        }
        return months;
    }
}
