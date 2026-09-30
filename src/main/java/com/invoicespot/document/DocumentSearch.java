package com.invoicespot.document;

import com.invoicespot.common.ApiException;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;

public record DocumentSearch(
        String query,
        DocumentState state,
        DocumentStatus status,
        DocumentType type,
        boolean overdueOnly,
        UUID customerId,
        LocalDate issuedFrom,
        LocalDate issuedTo,
        Sort sort,
        int page,
        int size) {

    private static final int MAX_PAGE_SIZE = 100;
    private static final Map<String, String> SORTABLE = Map.of(
            "createdAt", "createdAt",
            "dueDate", "dueDate",
            "total", "total",
            "number", "documentNumber",
            "client", "customerSnapshot.name",
            "status", "status");

    public static DocumentSearch of(
            String query,
            String state,
            String status,
            String type,
            Boolean overdueOnly,
            String customerId,
            LocalDate issuedFrom,
            LocalDate issuedTo,
            String sortField,
            String sortDirection,
            int page,
            int size) {
        return new DocumentSearch(
                query,
                state == null || state.isBlank() ? null : parse(() -> DocumentState.fromValue(state), "state"),
                status == null || status.isBlank() ? null : parse(() -> DocumentStatus.fromValue(status), "status"),
                type == null || type.isBlank() ? null : parse(() -> DocumentType.fromValue(type), "document type"),
                Boolean.TRUE.equals(overdueOnly),
                customerId == null || customerId.isBlank() ? null : parse(() -> UUID.fromString(customerId), "client"),
                issuedFrom,
                issuedTo,
                sort(sortField, sortDirection),
                Math.max(page, 1),
                Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
    }

    private static Sort sort(String field, String direction) {
        String property = SORTABLE.get(field == null ? "createdAt" : field);
        if (property == null) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "Documents can be sorted by createdAt, dueDate, total, number, client or status");
        }
        Sort.Direction order = "asc".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC;
        Sort.Order primary = new Sort.Order(order, property);
        if (property.equals("documentNumber") || property.equals("customerSnapshot.name")) {
            primary = primary.ignoreCase();
        }
        return Sort.by(primary, new Sort.Order(order, "pkid"));
    }

    private static <T> T parse(Supplier<T> parser, String label) {
        try {
            return parser.get();
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Unknown " + label + " filter");
        }
    }
}
