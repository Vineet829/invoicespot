package com.invoicespot.document;

import com.invoicespot.common.ApiException;
import com.invoicespot.common.RecentPeriod;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.http.HttpStatus;

public record DocumentSearch(
        String query,
        DocumentState state,
        DocumentStatus status,
        DocumentType type,
        boolean overdueOnly,
        boolean outstanding,
        boolean billed,
        DueWeek due,
        UUID customerId,
        String currency,
        String period,
        LocalDate issuedFrom,
        LocalDate issuedTo,
        String sort,
        boolean ascending,
        int page,
        int size) {

    private static final int MAX_PAGE_SIZE = 100;
    private static final Set<String> SORTABLE = Set.of("createdAt", "dueDate", "total", "number", "client", "status");

    public static DocumentSearch of(
            String query,
            String state,
            String status,
            String type,
            Boolean overdueOnly,
            Boolean outstanding,
            Boolean billed,
            String due,
            String customerId,
            String currency,
            String period,
            LocalDate issuedFrom,
            LocalDate issuedTo,
            String sortField,
            String sortDirection,
            int page,
            int size) {
        // One of the overview's periods takes the place of dates.
        boolean periodic = period != null && !period.isBlank();
        if (periodic && (issuedFrom != null || issuedTo != null)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Choose a period or dates, not both");
        }
        return new DocumentSearch(
                query,
                state == null || state.isBlank() ? null : parse(() -> DocumentState.fromValue(state), "state"),
                status == null || status.isBlank() ? null : parse(() -> DocumentStatus.fromValue(status), "status"),
                type == null || type.isBlank() ? null : parse(() -> DocumentType.fromValue(type), "document type"),
                Boolean.TRUE.equals(overdueOnly),
                Boolean.TRUE.equals(outstanding),
                Boolean.TRUE.equals(billed),
                due == null || due.isBlank() ? null : parse(() -> DueWeek.fromValue(due), "due date"),
                customerId == null || customerId.isBlank() ? null : parse(() -> UUID.fromString(customerId), "client"),
                currency == null || currency.isBlank() ? null : currency.trim(),
                periodic ? period : null,
                issuedFrom,
                issuedTo,
                sort(sortField),
                "asc".equalsIgnoreCase(sortDirection),
                Math.max(page, 1),
                Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
    }

    // Every document of a business, the newest first.
    public static DocumentSearch all() {
        return of(null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, 1, 1);
    }

    // The same search with its period, if it has one, as the dates that
    // period covers today: the dates the documents are then read between.
    DocumentSearch on(LocalDate today) {
        if (period == null) {
            return this;
        }
        RecentPeriod days = RecentPeriod.of(period, today);
        return new DocumentSearch(
                query, state, status, type, overdueOnly, outstanding, billed, due, customerId, currency, null,
                days.from(), days.to(), sort, ascending, page, size);
    }

    private static String sort(String field) {
        String sort = field == null ? "createdAt" : field;
        if (!SORTABLE.contains(sort)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "Documents can be sorted by createdAt, dueDate, total, number, client or status");
        }
        return sort;
    }

    private static <T> T parse(Supplier<T> parser, String label) {
        try {
            return parser.get();
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Unknown " + label + " filter");
        }
    }
}
