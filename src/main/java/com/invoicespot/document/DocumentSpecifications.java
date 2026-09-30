package com.invoicespot.document;

import com.invoicespot.common.SearchText;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

final class DocumentSpecifications {

    private DocumentSpecifications() {}

    static Specification<Document> matching(
            Long businessPkid, DocumentSearch search, Instant startOfToday, ZoneId zone) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("business").get("pkid"), businessPkid));
            if (search.query() != null && !search.query().isBlank()) {
                String like = SearchText.containing(search.query());
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("documentNumber")), like, SearchText.ESCAPE),
                        cb.like(cb.lower(root.get("customerSnapshot").get("name")), like, SearchText.ESCAPE),
                        cb.like(cb.lower(root.get("customerSnapshot").get("email")), like, SearchText.ESCAPE)));
            }
            if (search.status() != null) {
                predicates.add(cb.equal(root.get("status"), search.status()));
            }
            if (search.type() != null) {
                predicates.add(cb.equal(root.get("documentType"), search.type()));
            }
            if (search.overdueOnly()) {
                predicates.add(cb.equal(root.get("documentType"), DocumentType.INVOICE));
                predicates.add(cb.notEqual(root.get("status"), DocumentStatus.PAID));
                predicates.add(cb.lessThan(root.get("dueDate"), startOfToday));
            }
            if (search.customerId() != null) {
                predicates.add(cb.equal(root.get("customer").get("id"), search.customerId()));
            }
            if (search.issuedFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(
                        root.get("createdAt"), search.issuedFrom().atStartOfDay(zone).toInstant()));
            }
            if (search.issuedTo() != null) {
                predicates.add(cb.lessThan(
                        root.get("createdAt"), search.issuedTo().plusDays(1).atStartOfDay(zone).toInstant()));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
