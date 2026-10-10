package com.invoicespot.customer;

import com.invoicespot.common.SearchText;
import com.invoicespot.customer.dto.CustomerTotals;
import com.invoicespot.document.DocumentLedger;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class CustomerDirectory {

    private static final Map<String, String> ORDER_BY = Map.of(
            "name", "lower(c.name)",
            "createdAt", "c.created_at",
            "documents", "documents",
            "balance", "balance");

    private static final String MATCHING = """
            c.business_pkid = :business
              and (lower(c.name) like :pattern escape '!'
                   or lower(c.email) like :pattern escape '!'
                   or lower(coalesce(c.account_no, '')) like :pattern escape '!'
                   or lower(coalesce(c.gstin, '')) like :pattern escape '!')
            """;

    record Row(long pkid, long documents) {}

    record Contact(
            long pkid,
            String name,
            String email,
            String phoneNumber,
            String accountNo,
            String vatTinNo,
            String gstin,
            String address,
            String city,
            String country,
            Instant createdAt,
            long documents) {}

    record Balance(long customerPkid, String currency, BigDecimal outstanding) {}

    private final JdbcClient jdbc;

    CustomerDirectory(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    Page<Row> search(Long businessPkid, CustomerSearch search, String currency) {
        String direction = search.ascending() ? "asc" : "desc";
        String pattern = SearchText.containing(search.query());
        List<Row> rows = jdbc.sql("""
                        select c.pkid,
                               count(d.pkid) as documents,
                               coalesce(sum(%s) filter (
                                   where %s and d.document_type = 'Invoice' and d.status <> 'Paid'
                                     and d.currency = :currency), 0) as balance
                        from customers c
                        left join documents d on d.customer_pkid = c.pkid
                        where %s
                        group by c.pkid
                        order by %s %s, c.pkid %s
                        limit :limit offset :offset
                        """.formatted(DocumentLedger.OWED, DocumentLedger.COUNTED, MATCHING,
                                ORDER_BY.get(search.sort()), direction, direction))
                .param("business", businessPkid)
                .param("pattern", pattern)
                .param("currency", currency)
                .param("limit", search.size())
                .param("offset", (long) (search.page() - 1) * search.size())
                .query((result, rowNumber) -> new Row(result.getLong("pkid"), result.getLong("documents")))
                .list();
        long total = jdbc.sql("select count(*) from customers c where " + MATCHING)
                .param("business", businessPkid)
                .param("pattern", pattern)
                .query(Long.class)
                .single();
        return new PageImpl<>(rows, PageRequest.of(search.page() - 1, search.size()), total);
    }

    // Every client the search matches, in its order, with their details.
    List<Contact> contacts(Long businessPkid, CustomerSearch search, String currency) {
        String direction = search.ascending() ? "asc" : "desc";
        return jdbc.sql("""
                        select c.pkid, c.name, c.email, c.phone_number, c.account_no, c.vat_tin_no, c.gstin,
                               c.address, c.city, c.country, c.created_at,
                               count(d.pkid) as documents,
                               coalesce(sum(%s) filter (
                                   where %s and d.document_type = 'Invoice' and d.status <> 'Paid'
                                     and d.currency = :currency), 0) as balance
                        from customers c
                        left join documents d on d.customer_pkid = c.pkid
                        where %s
                        group by c.pkid
                        order by %s %s, c.pkid %s
                        """.formatted(DocumentLedger.OWED, DocumentLedger.COUNTED, MATCHING,
                                ORDER_BY.get(search.sort()), direction, direction))
                .param("business", businessPkid)
                .param("pattern", SearchText.containing(search.query()))
                .param("currency", currency)
                .query((result, rowNumber) -> new Contact(
                        result.getLong("pkid"),
                        result.getString("name"),
                        result.getString("email"),
                        result.getString("phone_number"),
                        result.getString("account_no"),
                        result.getString("vat_tin_no"),
                        result.getString("gstin"),
                        result.getString("address"),
                        result.getString("city"),
                        result.getString("country"),
                        result.getTimestamp("created_at").toInstant(),
                        result.getLong("documents")))
                .list();
    }

    // What each of a business's clients still owes, per currency.
    List<Balance> balancesOf(Long businessPkid) {
        return jdbc.sql("""
                        select d.customer_pkid, d.currency, sum(%s) as outstanding
                        from documents d
                        where d.business_pkid = :business and d.customer_pkid is not null and %s
                          and d.document_type = 'Invoice' and d.status <> 'Paid'
                        group by d.customer_pkid, d.currency
                        having sum(%s) > 0
                        """.formatted(DocumentLedger.OWED, DocumentLedger.COUNTED, DocumentLedger.OWED))
                .param("business", businessPkid)
                .query((result, rowNumber) -> new Balance(
                        result.getLong("customer_pkid"),
                        result.getString("currency"),
                        result.getBigDecimal("outstanding")))
                .list();
    }

    List<Balance> balances(Collection<Long> customerPkids) {
        if (customerPkids.isEmpty()) {
            return List.of();
        }
        return jdbc.sql("""
                        select d.customer_pkid, d.currency, sum(%s) as outstanding
                        from documents d
                        where d.customer_pkid in (:customers) and %s
                          and d.document_type = 'Invoice' and d.status <> 'Paid'
                        group by d.customer_pkid, d.currency
                        having sum(%s) > 0
                        order by outstanding desc, d.currency
                        """.formatted(DocumentLedger.OWED, DocumentLedger.COUNTED, DocumentLedger.OWED))
                .param("customers", customerPkids)
                .query((result, rowNumber) -> new Balance(
                        result.getLong("customer_pkid"),
                        result.getString("currency"),
                        result.getBigDecimal("outstanding")))
                .list();
    }

    List<CustomerTotals> totals(long customerPkid, Instant startOfToday) {
        return jdbc.sql("""
                        select d.currency,
                               count(*) as documents,
                               coalesce(sum(%s) filter (where d.document_type <> 'Quotation'), 0) as billed,
                               coalesce(sum(d.total_amount_received - d.total_refunded)
                                   filter (where d.document_type <> 'Quotation'), 0) as paid,
                               coalesce(sum(%s)
                                   filter (where d.document_type = 'Invoice' and d.status <> 'Paid'), 0)
                                   as outstanding,
                               coalesce(sum(%s)
                                   filter (where d.document_type = 'Invoice' and d.status <> 'Paid'
                                     and d.due_date < :startOfToday), 0) as overdue
                        from documents d
                        where d.customer_pkid = :customer and %s
                        group by d.currency
                        order by billed desc, d.currency
                        """.formatted(
                                DocumentLedger.BILLED, DocumentLedger.OWED, DocumentLedger.OWED, DocumentLedger.COUNTED))
                .param("customer", customerPkid)
                .param("startOfToday", Timestamp.from(startOfToday))
                .query((result, rowNumber) -> new CustomerTotals(
                        result.getString("currency"),
                        result.getLong("documents"),
                        result.getBigDecimal("billed"),
                        result.getBigDecimal("paid"),
                        result.getBigDecimal("outstanding"),
                        result.getBigDecimal("overdue")))
                .list();
    }
}
