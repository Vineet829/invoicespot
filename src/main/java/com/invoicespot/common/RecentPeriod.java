package com.invoicespot.common;

import java.time.LocalDate;
import java.util.Locale;
import org.springframework.http.HttpStatus;

// The last 30 or 90 days, or the last 12 months, ending today: the overview's
// periods, and what the lists it opens can be narrowed to. The 12 months go
// back to the same day, as monthsBack counts them.
public record RecentPeriod(String key, LocalDate from, LocalDate to) {

    // Any other key is refused; this year to date is a pair of dates there.
    public static RecentPeriod of(String key, LocalDate today) {
        String value = key.trim().toLowerCase(Locale.ROOT);
        return switch (value) {
            case "30d" -> new RecentPeriod(value, today.minusDays(29), today);
            case "90d" -> new RecentPeriod(value, today.minusDays(89), today);
            case "12m" -> new RecentPeriod(value, monthsBack(today, 12), today);
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "Choose 30 days, 90 days or 12 months");
        };
    }

    // The first day of the last few months to today: the day after today's
    // date that many months ago, or after the last day of that month when it
    // is shorter, so the last 3 months to 30 May begin on 1 March. On a
    // month's last day they are that many whole months instead, so the last
    // 6 months to 30 September begin on 1 April, not on 31 March.
    public static LocalDate monthsBack(LocalDate today, int months) {
        return today.getDayOfMonth() == today.lengthOfMonth()
                ? today.withDayOfMonth(1).minusMonths(months - 1)
                : today.minusMonths(months).plusDays(1);
    }
}
