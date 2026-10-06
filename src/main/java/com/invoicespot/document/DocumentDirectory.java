package com.invoicespot.document;

import com.invoicespot.common.SearchText;
import com.invoicespot.document.dto.DocumentListTotals;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

// The documents list's queries. Its filters are read in SQL, as the overview
// and the reports read documents, so that the list, its CSV file and what it
// comes to cover the same documents. Callers read in a transaction that sees
// the documents as they were at one moment, so the keys read first and the
// documents then read by them agree.
@Repository
class DocumentDirectory {

    // What each of the list's sorts orders documents aliased d by; a number
    // or a client is ordered whatever its case.
    private static final Map<String, String> ORDER_BY = Map.of(
            "createdAt", "d.created_at",
            "dueDate", "d.due_date",
            "total", "d.total",
            "number", "lower(d.document_number)",
            "client", "lower(d.customer_name)",
            "status", "d.status");

    // The most keys one query for a CSV file reads documents by, so that a
    // long list never becomes one query with a key for every document.
    private static final int BATCH = 500;

    // A search's conditions on documents aliased d, and their values.
    private record Filter(String where, Map<String, Object> params) {}

    private final JdbcClient jdbc;
    private final DocumentRepository documentRepository;

    DocumentDirectory(JdbcClient jdbc, DocumentRepository documentRepository) {
        this.jdbc = jdbc;
        this.documentRepository = documentRepository;
    }

    // One page of the documents the search matches, in its order, with
    // their clients; the totals count them all. Days are the business's, in
    // its time zone, and a period has been read as the dates it covers.
    List<Document> page(Long businessPkid, DocumentSearch search, LocalDate today, ZoneId zone) {
        Filter filter = filter(businessPkid, search, today, zone);
        List<Long> pkids = jdbc.sql("""
                        select d.pkid from documents d
                        where %s
                        order by %s
                        limit :limit offset :offset
                        """.formatted(filter.where(), orderBy(search)))
                .params(filter.params())
                .param("limit", search.size())
                .param("offset", (long) (search.page() - 1) * search.size())
                .query(Long.class)
                .list();
        return load(pkids, documentRepository::findByPkidIn);
    }

    // Every document the search matches, in its order. A CSV file names each
    // document's client from the document itself, so clients aren't read.
    List<Document> all(Long businessPkid, DocumentSearch search, LocalDate today, ZoneId zone) {
        Filter filter = filter(businessPkid, search, today, zone);
        List<Long> pkids = jdbc.sql("select d.pkid from documents d where %s order by %s"
                        .formatted(filter.where(), orderBy(search)))
                .params(filter.params())
                .query(Long.class)
                .list();
        List<Document> documents = new ArrayList<>();
        for (int start = 0; start < pkids.size(); start += BATCH) {
            List<Long> batch = pkids.subList(start, Math.min(start + BATCH, pkids.size()));
            documents.addAll(load(batch, documentRepository::findAllById));
        }
        return documents;
    }

    // What every document the search matches comes to in each currency: how
    // many there are, what the invoices, receipts and credit notes among them
    // billed, and what is owed on the invoices, as each row's balance due
    // says, with how much of that was due before today.
    List<DocumentListTotals> totals(Long businessPkid, DocumentSearch search, LocalDate today, ZoneId zone) {
        Filter filter = filter(businessPkid, search, today, zone);
        return jdbc.sql("""
                        select coalesce(d.currency, '') as currency,
                               count(*) as documents,
                               coalesce(sum(%1$s) filter (where %3$s), 0) as billed,
                               coalesce(sum(%2$s) filter (where %4$s), 0) as balance_due,
                               coalesce(sum(%2$s) filter (where %4$s and d.due_date < :startOfToday), 0) as overdue,
                               count(*) filter (where %4$s and d.due_date < :startOfToday) as overdue_documents
                        from documents d
                        where %5$s
                        group by 1
                        order by 1
                        """.formatted(
                                DocumentLedger.BILLED,
                                DocumentLedger.OWED,
                                DocumentLedger.BILLED_DOCUMENT,
                                DocumentLedger.OWED_INVOICE,
                                filter.where()))
                .params(filter.params())
                .param("startOfToday", startOf(today, zone))
                .query((result, rowNumber) -> new DocumentListTotals(
                        result.getString("currency"),
                        result.getLong("documents"),
                        result.getBigDecimal("billed").setScale(2),
                        result.getBigDecimal("balance_due").setScale(2),
                        result.getBigDecimal("overdue").setScale(2),
                        result.getLong("overdue_documents")))
                .list();
    }

    // The documents with these keys, in the same order. Read without one
    // moment's view, a document deleted since its key was read is left out.
    private static List<Document> load(List<Long> pkids, Function<List<Long>, List<Document>> reader) {
        if (pkids.isEmpty()) {
            return List.of();
        }
        Map<Long, Document> found = reader.apply(pkids).stream()
                .collect(Collectors.toMap(Document::getPkid, Function.identity()));
        return pkids.stream().map(found::get).filter(Objects::nonNull).toList();
    }

    // The field asked for, then the order the documents were made in, both
    // in the direction asked for.
    private static String orderBy(DocumentSearch search) {
        String direction = search.ascending() ? "asc" : "desc";
        return ORDER_BY.get(search.sort()) + " " + direction + ", d.pkid " + direction;
    }

    private static Filter filter(Long businessPkid, DocumentSearch search, LocalDate today, ZoneId zone) {
        List<String> conditions = new ArrayList<>(List.of("d.business_pkid = :business"));
        Map<String, Object> params = new HashMap<>(Map.of("business", businessPkid));
        if (search.query() != null && !search.query().isBlank()) {
            conditions.add("""
                    (lower(d.document_number) like :pattern escape '!'
                     or lower(d.customer_name) like :pattern escape '!'
                     or lower(d.customer_email) like :pattern escape '!')""");
            params.put("pattern", SearchText.containing(search.query()));
        }
        // Payment statuses are an issued document's; a draft has none yet, and
        // a void one none any more.
        if (search.state() == DocumentState.DRAFT) {
            conditions.add("d.issued_at is null");
        }
        if (search.state() == DocumentState.VOID) {
            conditions.add("d.voided_at is not null");
        }
        if (search.state() == DocumentState.ISSUED || search.status() != null || search.overdueOnly()) {
            conditions.add(DocumentLedger.COUNTED);
        }
        // A credit note has no payment status.
        if (search.status() != null) {
            conditions.add("d.status = :status and d.document_type <> 'Credit Note'");
            params.put("status", search.status().getValue());
        }
        if (search.type() != null) {
            conditions.add("d.document_type = :type");
            params.put("type", search.type().getValue());
        }
        if (search.overdueOnly()) {
            conditions.add("d.document_type = 'Invoice' and d.status <> 'Paid' and d.due_date < :startOfToday");
            params.put("startOfToday", startOf(today, zone));
        }
        // What the overview counts as owed now, and what its Billed adds up.
        if (search.outstanding()) {
            conditions.add(DocumentLedger.OWED_INVOICE);
        }
        if (search.billed()) {
            conditions.add(DocumentLedger.BILLED_DOCUMENT);
        }
        // A week begins when its first day does in the business's time zone.
        // Current keeps to the invoices with money owed on them, as the
        // overview's band of what is not due yet does, and begins today: an
        // invoice is overdue only once the day after its due date has begun.
        if (search.due() != null) {
            LocalDate first = search.due().firstDay(today);
            params.put("dueFrom", startOf(first, zone));
            if (search.due() == DueWeek.CURRENT) {
                conditions.add(DocumentLedger.OWED_INVOICE);
            }
            if (search.due().runsOn()) {
                conditions.add("(d.due_date >= :dueFrom or d.due_date is null)");
            } else {
                conditions.add("d.due_date >= :dueFrom and d.due_date < :dueBefore");
                params.put("dueBefore", startOf(first.plusWeeks(1), zone));
            }
        }
        if (search.customerId() != null) {
            conditions.add("d.customer_pkid in (select c.pkid from customers c where c.id = :customer)");
            params.put("customer", search.customerId());
        }
        if (search.currency() != null) {
            conditions.add("coalesce(d.currency, '') = :currency");
            params.put("currency", search.currency());
        }
        if (search.issuedFrom() != null) {
            conditions.add("d.issue_date >= :from");
            params.put("from", search.issuedFrom());
        }
        if (search.issuedTo() != null) {
            conditions.add("d.issue_date <= :to");
            params.put("to", search.issuedTo());
        }
        return new Filter(String.join(" and ", conditions), params);
    }

    private static Timestamp startOf(LocalDate day, ZoneId zone) {
        return Timestamp.from(day.atStartOfDay(zone).toInstant());
    }
}
