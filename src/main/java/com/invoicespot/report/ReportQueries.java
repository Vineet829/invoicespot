package com.invoicespot.report;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

// Reports read invoices and receipts only; a quotation is an offer, not money
// billed. A receipt is paid in full when it is issued, so the money it records
// counts on its own date and payments are read from invoices.
@Repository
class ReportQueries {

    record Billed(String currency, boolean receipt, Instant createdAt, BigDecimal total) {}

    record Paid(String currency, LocalDate datePaid, BigDecimal amount, String method) {}

    record Unpaid(String currency, Instant dueDate, BigDecimal outstanding) {}

    record Client(String currency, UUID customerId, String name, long documents, BigDecimal billed, BigDecimal paid) {}

    private final JdbcClient jdbc;

    ReportQueries(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    List<Billed> billed(long businessPkid, Instant from, Instant to) {
        return jdbc.sql("""
                        select coalesce(d.currency, '') as currency, d.document_type, d.created_at, d.total
                        from documents d
                        where d.business_pkid = :business
                          and d.document_type in ('Invoice', 'Receipt')
                          and d.created_at >= :from and d.created_at < :to
                        """)
                .param("business", businessPkid)
                .param("from", Timestamp.from(from))
                .param("to", Timestamp.from(to))
                .query((result, rowNumber) -> new Billed(
                        result.getString("currency"),
                        "Receipt".equals(result.getString("document_type")),
                        result.getTimestamp("created_at").toInstant(),
                        result.getBigDecimal("total")))
                .list();
    }

    List<Paid> paid(long businessPkid, LocalDate from, LocalDate to) {
        return jdbc.sql("""
                        select coalesce(d.currency, '') as currency, p.date_paid, p.amount_paid, p.payment_method
                        from payments p
                        join documents d on d.pkid = p.document_pkid
                        where d.business_pkid = :business
                          and d.document_type = 'Invoice'
                          and p.date_paid between :from and :to
                        """)
                .param("business", businessPkid)
                .param("from", from)
                .param("to", to)
                .query((result, rowNumber) -> new Paid(
                        result.getString("currency"),
                        result.getObject("date_paid", LocalDate.class),
                        result.getBigDecimal("amount_paid"),
                        result.getString("payment_method")))
                .list();
    }

    List<Unpaid> unpaid(long businessPkid) {
        return jdbc.sql("""
                        select coalesce(d.currency, '') as currency, d.due_date,
                               d.total - d.total_amount_received as outstanding
                        from documents d
                        where d.business_pkid = :business
                          and d.document_type = 'Invoice' and d.status <> 'Paid'
                        """)
                .param("business", businessPkid)
                .query((result, rowNumber) -> {
                    Timestamp due = result.getTimestamp("due_date");
                    return new Unpaid(
                            result.getString("currency"),
                            due == null ? null : due.toInstant(),
                            result.getBigDecimal("outstanding"));
                })
                .list();
    }

    // A client that was deleted is still named on its documents.
    List<Client> clients(long businessPkid, Instant from, Instant to) {
        return jdbc.sql("""
                        select coalesce(d.currency, '') as currency, c.id as customer_id,
                               coalesce(c.name, d.customer_name) as name,
                               count(*) as documents,
                               sum(d.total) as billed,
                               sum(d.total_amount_received) as paid
                        from documents d
                        left join customers c on c.pkid = d.customer_pkid
                        where d.business_pkid = :business
                          and d.document_type in ('Invoice', 'Receipt')
                          and d.created_at >= :from and d.created_at < :to
                        group by 1, 2, 3
                        order by billed desc, lower(coalesce(c.name, d.customer_name))
                        """)
                .param("business", businessPkid)
                .param("from", Timestamp.from(from))
                .param("to", Timestamp.from(to))
                .query((result, rowNumber) -> new Client(
                        result.getString("currency"),
                        result.getObject("customer_id", UUID.class),
                        result.getString("name"),
                        result.getLong("documents"),
                        result.getBigDecimal("billed"),
                        result.getBigDecimal("paid")))
                .list();
    }

    Optional<Instant> firstDocument(long businessPkid) {
        return jdbc.sql("""
                        select min(d.created_at) from documents d
                        where d.business_pkid = :business and d.document_type in ('Invoice', 'Receipt')
                        """)
                .param("business", businessPkid)
                .query(Timestamp.class)
                .optional()
                .map(Timestamp::toInstant);
    }

    Optional<LocalDate> firstPayment(long businessPkid) {
        return jdbc.sql("""
                        select min(p.date_paid) from payments p
                        join documents d on d.pkid = p.document_pkid
                        where d.business_pkid = :business and d.document_type = 'Invoice'
                        """)
                .param("business", businessPkid)
                .query((result, rowNumber) -> result.getObject(1, LocalDate.class))
                .optional();
    }
}
