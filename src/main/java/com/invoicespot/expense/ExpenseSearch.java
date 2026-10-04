package com.invoicespot.expense;

import com.invoicespot.common.ApiException;
import com.invoicespot.common.RecentPeriod;
import java.time.LocalDate;
import org.springframework.http.HttpStatus;

// The Expenses list's search and filters: text in the vendor, category,
// reference or notes, one category, one currency, and the days paid between
// two dates or in one of the overview's periods.
public record ExpenseSearch(
        String query,
        String category,
        String currency,
        String period,
        LocalDate from,
        LocalDate to,
        int page,
        int size) {

    private static final int MAX_PAGE_SIZE = 100;

    public static ExpenseSearch of(
            String query,
            String category,
            String currency,
            String period,
            LocalDate from,
            LocalDate to,
            int page,
            int size) {
        // One of the overview's periods takes the place of dates.
        boolean periodic = period != null && !period.isBlank();
        if (periodic && (from != null || to != null)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Choose a period or dates, not both");
        }
        return new ExpenseSearch(
                query,
                category == null || category.isBlank() ? null : category.trim(),
                currency == null || currency.isBlank() ? null : currency.trim(),
                periodic ? period : null,
                from,
                to,
                Math.max(page, 1),
                Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
    }

    // Every expense of a business.
    public static ExpenseSearch all() {
        return of(null, null, null, null, null, null, 1, 1);
    }

    // The same search with its period, if it has one, as the dates that
    // period covers today: the dates the expenses are then read between.
    ExpenseSearch on(LocalDate today) {
        if (period == null) {
            return this;
        }
        RecentPeriod days = RecentPeriod.of(period, today);
        return new ExpenseSearch(query, category, currency, null, days.from(), days.to(), page, size);
    }
}
