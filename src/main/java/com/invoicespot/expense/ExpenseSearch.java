package com.invoicespot.expense;

import com.invoicespot.common.SearchText;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

// The Expenses list's search and filters: text in the vendor, category,
// reference or notes, one category, and the days paid between two dates.
public record ExpenseSearch(String query, String category, LocalDate from, LocalDate to, int page, int size) {

    private static final int MAX_PAGE_SIZE = 100;

    // Newest first, and the last recorded first within a day.
    static final Sort ORDER = Sort.by(Sort.Order.desc("expenseDate"), Sort.Order.desc("pkid"));

    public static ExpenseSearch of(String query, String category, LocalDate from, LocalDate to, int page, int size) {
        return new ExpenseSearch(
                query,
                category == null || category.isBlank() ? null : category.trim(),
                from,
                to,
                Math.max(page, 1),
                Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
    }

    // Every expense of a business.
    public static ExpenseSearch all() {
        return of(null, null, null, null, 1, 1);
    }

    Specification<Expense> matching(Long businessPkid) {
        return (root, criteria, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("business").get("pkid"), businessPkid));
            if (query() != null && !query().isBlank()) {
                String like = SearchText.containing(query());
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("vendor")), like, SearchText.ESCAPE),
                        cb.like(cb.lower(root.get("category")), like, SearchText.ESCAPE),
                        cb.like(cb.lower(root.get("reference")), like, SearchText.ESCAPE),
                        cb.like(cb.lower(root.get("notes")), like, SearchText.ESCAPE)));
            }
            if (category != null) {
                predicates.add(cb.equal(cb.lower(root.get("category")), category.toLowerCase(Locale.ROOT)));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("expenseDate"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("expenseDate"), to));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
