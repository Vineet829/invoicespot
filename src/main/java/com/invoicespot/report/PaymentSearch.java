package com.invoicespot.report;

import com.invoicespot.common.ApiException;
import com.invoicespot.common.RecentPeriod;
import com.invoicespot.document.PaymentMethod;
import java.time.LocalDate;
import java.util.Set;
import org.springframework.http.HttpStatus;

// The payments list's search and filters: text in the client's name or email
// or the document's number, one payment method or receipts, one currency, and
// the days paid in one of the overview's periods or between two dates.
public record PaymentSearch(
        String query,
        String method,
        String currency,
        String period,
        LocalDate from,
        LocalDate to,
        String sort,
        boolean ascending,
        int page,
        int size) {

    // What the method filter takes for receipts, which have no method.
    static final String RECEIPT = "receipt";
    private static final int MAX_PAGE_SIZE = 100;
    private static final Set<String> SORTABLE = Set.of("date", "amount", "client");

    public static PaymentSearch of(
            String query,
            String method,
            String currency,
            String period,
            LocalDate from,
            LocalDate to,
            String sortField,
            String sortDirection,
            int page,
            int size) {
        // One of the overview's periods takes the place of dates.
        boolean periodic = period != null && !period.isBlank();
        if (periodic && (from != null || to != null)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Choose a period or dates, not both");
        }
        return new PaymentSearch(
                query,
                method == null || method.isBlank() ? null : method(method.trim()),
                currency == null || currency.isBlank() ? null : currency.trim(),
                periodic ? period : null,
                from,
                to,
                sort(sortField),
                "asc".equalsIgnoreCase(sortDirection),
                Math.max(page, 1),
                Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
    }

    // One currency's rows, the newest first, dated between two days; the
    // currency may be none.
    static PaymentSearch dated(String currency, LocalDate from, LocalDate to) {
        return new PaymentSearch(null, null, currency, null, from, to, "date", false, 1, 1);
    }

    // The same search with its period, if it has one, as the dates that
    // period covers today: the dates the rows are then read between.
    PaymentSearch on(LocalDate today) {
        if (period == null) {
            return this;
        }
        RecentPeriod days = RecentPeriod.of(period, today);
        return new PaymentSearch(query, method, currency, null, days.from(), days.to(), sort, ascending, page, size);
    }

    // A payment method's name, as payments and refunds are recorded with it,
    // whatever its case.
    private static String method(String value) {
        if (value.equalsIgnoreCase(RECEIPT)) {
            return RECEIPT;
        }
        for (PaymentMethod method : PaymentMethod.values()) {
            if (method.getValue().equalsIgnoreCase(value)) {
                return method.getValue();
            }
        }
        throw new ApiException(HttpStatus.BAD_REQUEST, "Unknown payment method filter");
    }

    private static String sort(String field) {
        String sort = field == null ? "date" : field;
        if (!SORTABLE.contains(sort)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Payments can be sorted by date, amount or client");
        }
        return sort;
    }
}
