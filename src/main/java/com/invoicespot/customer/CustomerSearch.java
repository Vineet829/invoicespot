package com.invoicespot.customer;

import com.invoicespot.common.ApiException;
import java.util.Set;
import org.springframework.http.HttpStatus;

public record CustomerSearch(String query, String sort, boolean ascending, int page, int size) {

    private static final int MAX_PAGE_SIZE = 100;
    private static final Set<String> SORTABLE = Set.of("name", "createdAt", "documents", "balance");

    public static CustomerSearch of(String query, String sort, String direction, int page, int size) {
        String field = sort == null || sort.isBlank() ? "createdAt" : sort;
        if (!SORTABLE.contains(field)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "Clients can be sorted by name, createdAt, documents or balance");
        }
        return new CustomerSearch(
                query,
                field,
                "asc".equalsIgnoreCase(direction),
                Math.max(page, 1),
                Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
    }
}
