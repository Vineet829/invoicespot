package com.invoicespot.report;

import com.invoicespot.common.SearchText;
import com.invoicespot.document.DocumentLedger;
import com.invoicespot.report.dto.DocumentSummaryResponse;
import com.invoicespot.report.dto.PaymentEntry;
import com.invoicespot.report.dto.PaymentTotals;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

// Money in and out of a business's issued documents, a payment, receipt or
// refund at a time, from the columns the reports count what was received
// from: a payment on an invoice on the day it was paid, a receipt's total on
// the day it is dated, and a refund, as money going back, on the day it went.
@Repository
class PaymentQueries {

    // What each one was on: the document, the client named on it, and its currency.
    private static final String DOCUMENT = """
            d.id as document_id, d.document_number, d.document_type, d.customer_name as client,
            d.customer_email as client_email, d.currency""";

    // Every one of them, in the business :business; a sequence orders rows
    // of one kind as they were recorded.
    private static final String ROWS = """
            select 'payment' as kind, p.id, p.pkid as sequence, p.date_paid as day, p.amount_paid as amount,
                   p.payment_method as method, p.additional_info as note, %2$s
            from payments p
            join documents d on d.pkid = p.document_pkid
            where d.business_pkid = :business and %1$s and d.document_type = 'Invoice'
            union all
            select 'receipt', d.id, d.pkid, d.issue_date, d.total, null, null, %2$s
            from documents d
            where d.business_pkid = :business and %1$s and d.document_type = 'Receipt'
            union all
            select 'refund', r.id, r.pkid, r.date_refunded, -r.amount, r.payment_method, r.reference, %2$s
            from refunds r
            join documents d on d.pkid = r.document_pkid
            where d.business_pkid = :business and %1$s
            """.formatted(DocumentLedger.COUNTED, DOCUMENT);

    // What each of the list's sorts orders the rows aliased m by; a client is
    // ordered whatever its case.
    private static final Map<String, String> ORDER_BY = Map.of(
            "date", "m.day",
            "amount", "m.amount",
            "client", "lower(m.client)");

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

    // A search's conditions on the rows aliased m, and their values.
    private record Filter(String where, Map<String, Object> params) {}

    private final JdbcClient jdbc;

    PaymentQueries(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    // One page of the rows a search with dates matches, in its order; the
    // totals count them all.
    List<PaymentEntry> page(long businessPkid, PaymentSearch search) {
        Filter filter = filter(businessPkid, search);
        return jdbc.sql("""
                        select m.* from (%s) m
                        where %s
                        order by %s
                        limit :limit offset :offset
                        """.formatted(ROWS, filter.where(), orderBy(search)))
                .params(filter.params())
                .param("limit", search.size())
                .param("offset", (long) (search.page() - 1) * search.size())
                .query(PaymentQueries::entry)
                .list();
    }

    // What every row the search matches comes to in each currency, by
    // currency code, and how many of them are receipts; rows without a
    // currency are listed under none.
    List<PaymentTotals> totals(long businessPkid, PaymentSearch search) {
        Filter filter = filter(businessPkid, search);
        return jdbc.sql("""
                        select coalesce(m.currency, '') as currency, count(*) as payments,
                               count(*) filter (where m.kind = 'receipt') as receipts, sum(m.amount) as received
                        from (%s) m
                        where %s
                        group by 1
                        order by 1
                        """.formatted(ROWS, filter.where()))
                .params(filter.params())
                .query((result, rowNumber) -> new PaymentTotals(
                        result.getString("currency"),
                        result.getLong("payments"),
                        result.getLong("receipts"),
                        result.getBigDecimal("received").setScale(2)))
                .list();
    }

    // What one currency's payments, receipts and refunds dated in a stretch
    // of days come to, as the overview's Received, and how many there are:
    // the payments list's totals for them.
    DocumentSummaryResponse.Received received(long businessPkid, String currency, LocalDate from, LocalDate to) {
        return totals(businessPkid, PaymentSearch.dated(currency, from, to)).stream()
                .findFirst()
                .map(totals -> new DocumentSummaryResponse.Received(totals.received(), totals.payments()))
                .orElse(new DocumentSummaryResponse.Received(ZERO, 0));
    }

    // The methods the business was paid or paid back by, by name.
    List<String> methods(long businessPkid) {
        return jdbc.sql("select distinct m.method from (%s) m where m.method is not null order by 1"
                        .formatted(ROWS))
                .param("business", businessPkid)
                .query(String.class)
                .list();
    }

    // The field asked for in the direction asked for. Rows alike in it come
    // in a fixed order: by kind, then in the order each was recorded.
    private static String orderBy(PaymentSearch search) {
        String direction = search.ascending() ? "asc" : "desc";
        return ORDER_BY.get(search.sort()) + " " + direction + ", m.kind " + direction + ", m.sequence " + direction;
    }

    // A search's dates are the days its rows are dated on; its period, if it
    // had one, is read as dates by then.
    private static Filter filter(long businessPkid, PaymentSearch search) {
        List<String> conditions = new ArrayList<>(List.of("true"));
        Map<String, Object> params = new HashMap<>(Map.of("business", businessPkid));
        if (search.query() != null && !search.query().isBlank()) {
            conditions.add("""
                    (lower(m.document_number) like :pattern escape '!'
                     or lower(m.client) like :pattern escape '!'
                     or lower(m.client_email) like :pattern escape '!')""");
            params.put("pattern", SearchText.containing(search.query()));
        }
        if (PaymentSearch.RECEIPT.equals(search.method())) {
            conditions.add("m.kind = 'receipt'");
        } else if (search.method() != null) {
            conditions.add("m.method = :method");
            params.put("method", search.method());
        }
        if (search.currency() != null) {
            conditions.add("coalesce(m.currency, '') = :currency");
            params.put("currency", search.currency());
        }
        if (search.from() != null) {
            conditions.add("m.day >= :from");
            params.put("from", search.from());
        }
        if (search.to() != null) {
            conditions.add("m.day <= :to");
            params.put("to", search.to());
        }
        return new Filter(String.join(" and ", conditions), params);
    }

    private static PaymentEntry entry(ResultSet result, int rowNumber) throws SQLException {
        return new PaymentEntry(
                result.getString("kind"),
                result.getObject("id", UUID.class),
                result.getObject("day", LocalDate.class),
                result.getString("client"),
                new PaymentEntry.Document(
                        result.getObject("document_id", UUID.class),
                        result.getString("document_number"),
                        result.getString("document_type")),
                result.getString("method"),
                result.getString("note"),
                result.getBigDecimal("amount"),
                result.getString("currency"));
    }
}
