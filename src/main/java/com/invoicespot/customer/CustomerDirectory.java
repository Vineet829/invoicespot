package com.invoicespot.customer;

import com.invoicespot.common.SearchText;
import com.invoicespot.customer.dto.CustomerTotals;
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
            c.created_by_pkid = :owner
              and (lower(c.name) like :pattern escape '!'
                   or lower(c.email) like :pattern escape '!'
                   or lower(coalesce(c.account_no, '')) like :pattern escape '!')
            """;

    record Row(long pkid, long documents) {}

    record Balance(long customerPkid, String currency, BigDecimal outstanding) {}

    private final JdbcClient jdbc;

    CustomerDirectory(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    Page<Row> search(Long ownerPkid, CustomerSearch search, String currency) {
        String direction = search.ascending() ? "asc" : "desc";
        String pattern = SearchText.containing(search.query());
        List<Row> rows = jdbc.sql("""
                        select c.pkid,
                               count(d.pkid) as documents,
                               coalesce(sum(d.total - d.total_amount_received) filter (
                                   where d.document_type = 'Invoice' and d.status <> 'Paid'
                                     and d.currency = :currency), 0) as balance
                        from customers c
                        left join documents d on d.customer_pkid = c.pkid
                        where %s
                        group by c.pkid
                        order by %s %s, c.pkid %s
                        limit :limit offset :offset
                        """.formatted(MATCHING, ORDER_BY.get(search.sort()), direction, direction))
                .param("owner", ownerPkid)
                .param("pattern", pattern)
                .param("currency", currency)
                .param("limit", search.size())
                .param("offset", (long) (search.page() - 1) * search.size())
                .query((result, rowNumber) -> new Row(result.getLong("pkid"), result.getLong("documents")))
                .list();
        long total = jdbc.sql("select count(*) from customers c where " + MATCHING)
                .param("owner", ownerPkid)
                .param("pattern", pattern)
                .query(Long.class)
                .single();
        return new PageImpl<>(rows, PageRequest.of(search.page() - 1, search.size()), total);
    }

    List<Balance> balances(Collection<Long> customerPkids) {
        if (customerPkids.isEmpty()) {
            return List.of();
        }
        return jdbc.sql("""
                        select d.customer_pkid, d.currency, sum(d.total - d.total_amount_received) as outstanding
                        from documents d
                        where d.customer_pkid in (:customers)
                          and d.document_type = 'Invoice' and d.status <> 'Paid'
                        group by d.customer_pkid, d.currency
                        having sum(d.total - d.total_amount_received) > 0
                        order by outstanding desc, d.currency
                        """)
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
                               coalesce(sum(d.total) filter (where d.document_type <> 'Quotation'), 0) as billed,
                               coalesce(sum(d.total_amount_received)
                                   filter (where d.document_type <> 'Quotation'), 0) as paid,
                               coalesce(sum(d.total - d.total_amount_received)
                                   filter (where d.document_type = 'Invoice' and d.status <> 'Paid'), 0)
                                   as outstanding,
                               coalesce(sum(d.total - d.total_amount_received)
                                   filter (where d.document_type = 'Invoice' and d.status <> 'Paid'
                                     and d.due_date < :startOfToday), 0) as overdue
                        from documents d
                        where d.customer_pkid = :customer
                        group by d.currency
                        order by billed desc, d.currency
                        """)
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
