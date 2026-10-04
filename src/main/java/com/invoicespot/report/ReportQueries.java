package com.invoicespot.report;

import com.invoicespot.document.DocumentLedger;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

// Reports read issued invoices and receipts only; a quotation is an offer, not
// money billed, and a draft isn't billed yet. A credit note takes its total off
// what was billed, on its own date. A receipt is paid in full when it is
// issued, so the money it records counts on its own date and payments are read
// from invoices.
@Repository
class ReportQueries {

    record Billed(String currency, boolean receipt, LocalDate issueDate, BigDecimal total) {}

    record Paid(String currency, LocalDate datePaid, BigDecimal amount, String method) {}

    record Refunded(String currency, LocalDate dateRefunded, BigDecimal amount) {}

    record Unpaid(String currency, Instant dueDate, BigDecimal outstanding) {}

    record Client(
            String currency,
            UUID customerId,
            String name,
            long documents,
            BigDecimal billed,
            BigDecimal paid,
            BigDecimal withheld) {}

    record Withheld(
            String currency,
            LocalDate datePaid,
            UUID customerId,
            String name,
            String gstin,
            UUID documentId,
            String documentNumber,
            BigDecimal received,
            BigDecimal withheld) {}

    record Taxed(
            String currency,
            UUID documentId,
            boolean creditNote,
            boolean gst,
            boolean reverseCharge,
            LocalDate issueDate,
            BigDecimal rate,
            BigDecimal taxable,
            BigDecimal tax,
            BigDecimal cgst,
            BigDecimal sgst,
            BigDecimal igst) {}

    record NotConverted(String currency, long documents, BigDecimal amount) {}

    record Earned(String currency, LocalDate month, BigDecimal income) {}

    record Spent(String currency, LocalDate month, String category, BigDecimal cost) {}

    record DocumentDay(LocalDate day, BigDecimal billed, BigDecimal receipts, BigDecimal earned) {}

    record Daily(LocalDate day, BigDecimal amount) {}

    // Income and costs, as profit and loss counts them by month or by day.
    private static final String EARNED =
            "(case when d.document_type = 'Credit Note' then -d.sub_total else d.sub_total end)";
    private static final String SPENT = "(e.amount + case when e.tax_reclaimable then 0 else e.tax_amount end)";

    // A document in the reporting currency, or with a rate typed in against it.
    private static final String CONVERTIBLE = "(coalesce(d.currency, '') = :base"
            + " or (d.exchange_rate is not null and d.exchange_base = :base))";

    // How amounts are read: each in its document's own currency, or in the
    // reporting currency at the document's own rate, rounded to the cent one
    // amount at a time. A document that can't be converted is left out of
    // the converted view, and listed on its own.
    record View(String base) {

        static final View OWN = new View(null);

        boolean converted() {
            return base != null;
        }

        String currency() {
            return converted() ? "cast(:base as text)" : "coalesce(d.currency, '')";
        }

        String amount(String column) {
            return converted()
                    ? "(case when coalesce(d.currency, '') = :base then %1$s else round(%1$s * d.exchange_rate, 2) end)"
                            .formatted(column)
                    : column;
        }

        String convertible() {
            return converted() ? CONVERTIBLE : "true";
        }
    }

    private final JdbcClient jdbc;

    ReportQueries(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    // Documents count on the day they are dated.
    List<Billed> billed(long businessPkid, LocalDate from, LocalDate to, View view) {
        return jdbc.sql("""
                        select %s as currency, d.document_type, d.issue_date, %s as total
                        from documents d
                        where d.business_pkid = :business and %s and %s
                          and d.issue_date between :from and :to
                        """.formatted(
                                view.currency(),
                                view.amount(DocumentLedger.BILLED),
                                DocumentLedger.BILLED_DOCUMENT,
                                view.convertible()))
                .param("business", businessPkid)
                .param("from", from)
                .param("to", to)
                .param("base", view.base())
                .query((result, rowNumber) -> new Billed(
                        result.getString("currency"),
                        "Receipt".equals(result.getString("document_type")),
                        result.getObject("issue_date", LocalDate.class),
                        result.getBigDecimal("total")))
                .list();
    }

    // A payment converts at its invoice's rate.
    List<Paid> paid(long businessPkid, LocalDate from, LocalDate to, View view) {
        return jdbc.sql("""
                        select %s as currency, p.date_paid, %s as amount_paid, p.payment_method
                        from payments p
                        join documents d on d.pkid = p.document_pkid
                        where d.business_pkid = :business and %s and %s
                          and d.document_type = 'Invoice'
                          and p.date_paid between :from and :to
                        """.formatted(
                                view.currency(), view.amount("p.amount_paid"), DocumentLedger.COUNTED, view.convertible()))
                .param("business", businessPkid)
                .param("from", from)
                .param("to", to)
                .param("base", view.base())
                .query((result, rowNumber) -> new Paid(
                        result.getString("currency"),
                        result.getObject("date_paid", LocalDate.class),
                        result.getBigDecimal("amount_paid"),
                        result.getString("payment_method")))
                .list();
    }

    // Money paid back on invoices and receipts, at the document's rate.
    List<Refunded> refunded(long businessPkid, LocalDate from, LocalDate to, View view) {
        return jdbc.sql("""
                        select %s as currency, r.date_refunded, %s as amount
                        from refunds r
                        join documents d on d.pkid = r.document_pkid
                        where d.business_pkid = :business and %s and %s
                          and r.date_refunded between :from and :to
                        """.formatted(
                                view.currency(), view.amount("r.amount"), DocumentLedger.COUNTED, view.convertible()))
                .param("business", businessPkid)
                .param("from", from)
                .param("to", to)
                .param("base", view.base())
                .query((result, rowNumber) -> new Refunded(
                        result.getString("currency"),
                        result.getObject("date_refunded", LocalDate.class),
                        result.getBigDecimal("amount")))
                .list();
    }

    List<Unpaid> unpaid(long businessPkid, View view) {
        return jdbc.sql("""
                        select %s as currency, d.due_date, %s as outstanding
                        from documents d
                        where d.business_pkid = :business and %s and %s
                          and d.document_type = 'Invoice' and d.status <> 'Paid'
                        """.formatted(
                                view.currency(), view.amount(DocumentLedger.OWED), DocumentLedger.COUNTED,
                                view.convertible()))
                .param("business", businessPkid)
                .param("base", view.base())
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
    List<Client> clients(long businessPkid, LocalDate from, LocalDate to, View view) {
        return jdbc.sql("""
                        select %s as currency, c.id as customer_id,
                               coalesce(c.name, d.customer_name) as name,
                               count(*) as documents,
                               sum(%s) as billed,
                               sum(%s) as paid,
                               sum(%s) as withheld
                        from documents d
                        left join customers c on c.pkid = d.customer_pkid
                        where d.business_pkid = :business and %s and %s
                          and d.issue_date between :from and :to
                        group by 1, 2, 3
                        order by billed desc, lower(coalesce(c.name, d.customer_name))
                        """.formatted(
                                view.currency(),
                                view.amount(DocumentLedger.BILLED),
                                view.amount("(d.total_amount_received - d.total_refunded)"),
                                view.amount("d.total_tds"),
                                DocumentLedger.BILLED_DOCUMENT,
                                view.convertible()))
                .param("business", businessPkid)
                .param("from", from)
                .param("to", to)
                .param("base", view.base())
                .query((result, rowNumber) -> new Client(
                        result.getString("currency"),
                        result.getObject("customer_id", UUID.class),
                        result.getString("name"),
                        result.getLong("documents"),
                        result.getBigDecimal("billed"),
                        result.getBigDecimal("paid"),
                        result.getBigDecimal("withheld")))
                .list();
    }

    // What issued invoices and receipts earned before tax, less credit notes,
    // by the month each is dated.
    List<Earned> earned(long businessPkid, LocalDate from, LocalDate to) {
        return jdbc.sql("""
                        select coalesce(d.currency, '') as currency,
                               cast(date_trunc('month', d.issue_date) as date) as month,
                               sum(%s) as income
                        from documents d
                        where d.business_pkid = :business and %s
                          and d.issue_date between :from and :to
                        group by 1, 2
                        """.formatted(EARNED, DocumentLedger.BILLED_DOCUMENT))
                .param("business", businessPkid)
                .param("from", from)
                .param("to", to)
                .query((result, rowNumber) -> new Earned(
                        result.getString("currency"),
                        result.getObject("month", LocalDate.class),
                        result.getBigDecimal("income")))
                .list();
    }

    // What expenses cost, by the month each was paid and its category: the
    // amount, and the tax on it unless that can be claimed back.
    List<Spent> spent(long businessPkid, LocalDate from, LocalDate to) {
        return jdbc.sql("""
                        select e.currency, cast(date_trunc('month', e.expense_date) as date) as month, e.category,
                               sum(%s) as cost
                        from expenses e
                        where e.business_pkid = :business and e.expense_date between :from and :to
                        group by 1, 2, 3
                        """.formatted(SPENT))
                .param("business", businessPkid)
                .param("from", from)
                .param("to", to)
                .query((result, rowNumber) -> new Spent(
                        result.getString("currency"),
                        result.getObject("month", LocalDate.class),
                        result.getString("category"),
                        result.getBigDecimal("cost")))
                .list();
    }

    // The overview's money in one currency, a day at a time over its period
    // and the period before it: what documents billed and earned, by the day
    // each is dated, and what the receipts among them took in.
    List<DocumentDay> documentsByDay(long businessPkid, String currency, OverviewWindow window) {
        return jdbc.sql("""
                        select d.issue_date as day, sum(%1$s) as billed,
                               coalesce(sum(%1$s) filter (where d.document_type = 'Receipt'), 0) as receipts,
                               sum(%2$s) as earned
                        from documents d
                        where d.business_pkid = :business and %3$s
                          and coalesce(d.currency, '') = :currency and %4$s
                        group by 1
                        """.formatted(
                                DocumentLedger.BILLED,
                                EARNED,
                                DocumentLedger.BILLED_DOCUMENT,
                                inOverview("d.issue_date")))
                .params(overview(businessPkid, currency, window))
                .query((result, rowNumber) -> new DocumentDay(
                        result.getObject("day", LocalDate.class),
                        result.getBigDecimal("billed"),
                        result.getBigDecimal("receipts"),
                        result.getBigDecimal("earned")))
                .list();
    }

    // What was paid on the currency's invoices, by the day it was paid.
    List<Daily> paidByDay(long businessPkid, String currency, OverviewWindow window) {
        return jdbc.sql("""
                        select p.date_paid as day, sum(p.amount_paid) as amount
                        from payments p
                        join documents d on d.pkid = p.document_pkid
                        where d.business_pkid = :business and %s and d.document_type = 'Invoice'
                          and coalesce(d.currency, '') = :currency and %s
                        group by 1
                        """.formatted(DocumentLedger.COUNTED, inOverview("p.date_paid")))
                .params(overview(businessPkid, currency, window))
                .query(ReportQueries::daily)
                .list();
    }

    // What was paid back on its invoices and receipts, by the day it went.
    List<Daily> refundedByDay(long businessPkid, String currency, OverviewWindow window) {
        return jdbc.sql("""
                        select r.date_refunded as day, sum(r.amount) as amount
                        from refunds r
                        join documents d on d.pkid = r.document_pkid
                        where d.business_pkid = :business and %s
                          and coalesce(d.currency, '') = :currency and %s
                        group by 1
                        """.formatted(DocumentLedger.COUNTED, inOverview("r.date_refunded")))
                .params(overview(businessPkid, currency, window))
                .query(ReportQueries::daily)
                .list();
    }

    // What its expenses cost, by the day each was paid.
    List<Daily> spentByDay(long businessPkid, String currency, OverviewWindow window) {
        return jdbc.sql("""
                        select e.expense_date as day, sum(%s) as amount
                        from expenses e
                        where e.business_pkid = :business and e.currency = :currency and %s
                        group by 1
                        """.formatted(SPENT, inOverview("e.expense_date")))
                .params(overview(businessPkid, currency, window))
                .query(ReportQueries::daily)
                .list();
    }

    // The business, the currency, and the overview's period and the period
    // before it.
    private static Map<String, Object> overview(long businessPkid, String currency, OverviewWindow window) {
        return Map.of(
                "business", businessPkid,
                "currency", currency,
                "previousFrom", window.previousFrom(),
                "previousTo", window.previousTo(),
                "from", window.from(),
                "to", window.to());
    }

    // A day in the overview's period or in the period before it. The two
    // meet, except that this year to date is compared with last year's.
    private static String inOverview(String day) {
        return "(%1$s between :previousFrom and :previousTo or %1$s between :from and :to)".formatted(day);
    }

    private static Daily daily(ResultSet result, int rowNumber) throws SQLException {
        return new Daily(result.getObject("day", LocalDate.class), result.getBigDecimal("amount"));
    }

    // The payments a client withheld tax from, by the date paid. A client that
    // was deleted is still named on its documents.
    List<Withheld> withheld(long businessPkid, LocalDate from, LocalDate to) {
        return jdbc.sql("""
                        select coalesce(d.currency, '') as currency, p.date_paid, c.id as customer_id,
                               coalesce(c.name, d.customer_name) as name,
                               coalesce(c.gstin, d.customer_gstin) as gstin, d.id as document_id,
                               d.document_number, p.amount_paid, p.tds_amount
                        from payments p
                        join documents d on d.pkid = p.document_pkid
                        left join customers c on c.pkid = d.customer_pkid
                        where d.business_pkid = :business and %s
                          and d.document_type = 'Invoice'
                          and p.tds_amount > 0
                          and p.date_paid between :from and :to
                        order by p.date_paid, d.document_number, p.pkid
                        """.formatted(DocumentLedger.COUNTED))
                .param("business", businessPkid)
                .param("from", from)
                .param("to", to)
                .query((result, rowNumber) -> new Withheld(
                        result.getString("currency"),
                        result.getObject("date_paid", LocalDate.class),
                        result.getObject("customer_id", UUID.class),
                        result.getString("name"),
                        result.getString("gstin"),
                        result.getObject("document_id", UUID.class),
                        result.getString("document_number"),
                        result.getBigDecimal("amount_paid"),
                        result.getBigDecimal("tds_amount")))
                .list();
    }

    // What each document dated in the period was taxed at each rate, as it was
    // issued; the caller takes a credit note's off.
    List<Taxed> taxed(long businessPkid, LocalDate from, LocalDate to, View view) {
        return jdbc.sql("""
                        select %s as currency, d.id as document_id, d.document_type, d.tax_regime,
                               d.reverse_charge, d.issue_date, s.rate, %s as taxable, %s as tax,
                               %s as cgst, %s as sgst, %s as igst
                        from document_tax_summaries s
                        join documents d on d.pkid = s.document_pkid
                        where d.business_pkid = :business and %s and %s
                          and d.issue_date between :from and :to
                        order by d.issue_date, d.pkid, s.rate
                        """.formatted(
                                view.currency(),
                                view.amount("s.taxable"),
                                view.amount("s.tax"),
                                view.amount("s.cgst"),
                                view.amount("s.sgst"),
                                view.amount("s.igst"),
                                DocumentLedger.BILLED_DOCUMENT,
                                view.convertible()))
                .param("business", businessPkid)
                .param("from", from)
                .param("to", to)
                .param("base", view.base())
                .query((result, rowNumber) -> new Taxed(
                        result.getString("currency"),
                        result.getObject("document_id", UUID.class),
                        "Credit Note".equals(result.getString("document_type")),
                        "GST_INDIA".equals(result.getString("tax_regime")),
                        result.getBoolean("reverse_charge"),
                        result.getObject("issue_date", LocalDate.class),
                        result.getBigDecimal("rate"),
                        result.getBigDecimal("taxable"),
                        result.getBigDecimal("tax"),
                        result.getBigDecimal("cgst"),
                        result.getBigDecimal("sgst"),
                        result.getBigDecimal("igst")))
                .list();
    }

    // The documents dated in the period a converted report leaves out, per
    // currency, with what they billed.
    List<NotConverted> notConvertedDated(long businessPkid, LocalDate from, LocalDate to, String base) {
        return jdbc.sql("""
                        select coalesce(d.currency, '') as currency, count(*) as documents, sum(%s) as amount
                        from documents d
                        where d.business_pkid = :business and %s and not %s
                          and d.issue_date between :from and :to
                        group by 1
                        order by 1
                        """.formatted(DocumentLedger.BILLED, DocumentLedger.BILLED_DOCUMENT, CONVERTIBLE))
                .param("business", businessPkid)
                .param("from", from)
                .param("to", to)
                .param("base", base)
                .query((result, rowNumber) -> new NotConverted(
                        result.getString("currency"), result.getLong("documents"), result.getBigDecimal("amount")))
                .list();
    }

    // The documents a converted report leaves out, per currency: billed in the
    // period, paid in it, or still unpaid, with no rate to the reporting
    // currency. Documents without a currency are listed under none.
    List<NotConverted> notConverted(long businessPkid, LocalDate from, LocalDate to, String base) {
        return jdbc.sql("""
                        select coalesce(d.currency, '') as currency, count(*) as documents, sum(%s) as amount
                        from documents d
                        where d.business_pkid = :business and %s and not %s
                          and ((%s and d.issue_date between :from and :to)
                            or (d.document_type = 'Invoice' and d.status <> 'Paid')
                            or (d.document_type = 'Invoice' and exists (
                                select 1 from payments p
                                where p.document_pkid = d.pkid and p.date_paid between :from and :to))
                            or exists (
                                select 1 from refunds r
                                where r.document_pkid = d.pkid and r.date_refunded between :from and :to))
                        group by 1
                        order by 1
                        """.formatted(
                                DocumentLedger.BILLED,
                                DocumentLedger.COUNTED,
                                CONVERTIBLE,
                                DocumentLedger.BILLED_DOCUMENT))
                .param("business", businessPkid)
                .param("from", from)
                .param("to", to)
                .param("base", base)
                .query((result, rowNumber) -> new NotConverted(
                        result.getString("currency"), result.getLong("documents"), result.getBigDecimal("amount")))
                .list();
    }

    Optional<LocalDate> firstDocument(long businessPkid) {
        return jdbc.sql("""
                        select min(d.issue_date) from documents d
                        where d.business_pkid = :business and %s and d.document_type in ('Invoice', 'Receipt')
                        """.formatted(DocumentLedger.COUNTED))
                .param("business", businessPkid)
                .query((result, rowNumber) -> result.getObject(1, LocalDate.class))
                .optional();
    }

    Optional<LocalDate> firstPayment(long businessPkid) {
        return jdbc.sql("""
                        select min(p.date_paid) from payments p
                        join documents d on d.pkid = p.document_pkid
                        where d.business_pkid = :business and %s and d.document_type = 'Invoice'
                        """.formatted(DocumentLedger.COUNTED))
                .param("business", businessPkid)
                .query((result, rowNumber) -> result.getObject(1, LocalDate.class))
                .optional();
    }
}
