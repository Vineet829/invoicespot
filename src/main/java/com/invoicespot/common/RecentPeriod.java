package com.invoicespot.common;

import java.time.LocalDate;
import java.util.Locale;
import org.springframework.http.HttpStatus;

// The last 30 or 90 days, or the last 12 months from the first of a month,
// ending today: the overview's periods, and what the lists it opens can be
// narrowed to.
public record RecentPeriod(String key, LocalDate from, LocalDate to) {

    // Any other key is refused; this year to date is a pair of dates there.
    public static RecentPeriod of(String key, LocalDate today) {
        String value = key.trim().toLowerCase(Locale.ROOT);
        return switch (value) {
            case "30d" -> new RecentPeriod(value, today.minusDays(29), today);
            case "90d" -> new RecentPeriod(value, today.minusDays(89), today);
            case "12m" -> new RecentPeriod(value, today.withDayOfMonth(1).minusMonths(11), today);
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "Choose 30 days, 90 days or 12 months");
        };
    }
}
