package com.invoicespot.report;

import com.invoicespot.document.DocumentLedger;
import com.invoicespot.report.dto.OverviewAttention;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

// What needs doing in one currency, most urgent first: payments clients
// reported that wait to be confirmed, overdue invoices, invoices due within
// the week, and drafts. Quotations and the invoices schedules will make are
// what is coming up, so they are listed there instead. Each kind is counted
// in full; six things at most are listed. Each names its client as its
// document does, with the id of the client the document is linked to while
// it is linked.
@Repository
class NeedsAttention {

    private static final int MOST = 6;

    // The first few of one kind, and how many there are in all.
    private record Group(List<OverviewAttention.Item> items, long count) {}

    private record Counted(OverviewAttention.Item item, long count) {}

    private final JdbcClient jdbc;

    NeedsAttention(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    // The week is today and the six days after it, in the business's time zone.
    OverviewAttention list(long businessPkid, String currency, ZoneId zone, LocalDate today) {
        Map<String, Object> params = Map.of(
                "business", businessPkid,
                "currency", currency,
                "startOfToday", Timestamp.from(today.atStartOfDay(zone).toInstant()),
                "startOfNextWeek", Timestamp.from(today.plusDays(7).atStartOfDay(zone).toInstant()),
                "most", MOST);
        Group reports = paymentReports(params);
        Group overdue = owed("overdue", "d.due_date < :startOfToday", params, zone, today);
        Group dueSoon = owed(
                "dueSoon", "d.due_date >= :startOfToday and d.due_date < :startOfNextWeek", params, zone, today);
        Group drafts = drafts(params, zone);
        return new OverviewAttention(
                Stream.of(reports, overdue, dueSoon, drafts)
                        .flatMap(group -> group.items().stream())
                        .limit(MOST)
                        .toList(),
                new OverviewAttention.Counts(reports.count(), overdue.count(), dueSoon.count(), drafts.count()));
    }

    // Newest report first, with the amount and the day the client gave.
    private Group paymentReports(Map<String, Object> params) {
        return group(
                """
                select d.id, d.document_number, d.customer_name, client.id as client_id, c.amount, c.paid_on,
                       count(*) over () as kind_count
                from payment_claims c
                join documents d on d.pkid = c.document_pkid
                left join customers client on client.pkid = d.customer_pkid
                where c.business_pkid = :business and c.status = 'PENDING' and %s and d.document_type = 'Invoice'
                  and coalesce(d.currency, '') = :currency
                order by c.created_at desc, c.pkid desc
                limit :most
                """.formatted(DocumentLedger.COUNTED),
                params,
                (result, rowNumber) ->
                        item("paymentReport", result, result.getObject("paid_on", LocalDate.class), null));
    }

    // Issued invoices with money owed on them, falling due as asked, the
    // earliest due first: days late for those overdue, days left for the rest.
    private Group owed(String kind, String due, Map<String, Object> params, ZoneId zone, LocalDate today) {
        return group(
                """
                select d.id, d.document_number, d.customer_name, client.id as client_id, %1$s as amount, d.due_date,
                       count(*) over () as kind_count
                from documents d
                left join customers client on client.pkid = d.customer_pkid
                where d.business_pkid = :business and %2$s and d.document_type = 'Invoice'
                  and coalesce(d.currency, '') = :currency and %1$s > 0 and %3$s
                order by d.due_date, d.pkid
                limit :most
                """.formatted(DocumentLedger.OWED, DocumentLedger.COUNTED, due),
                params,
                (result, rowNumber) -> {
                    LocalDate date = day(result.getTimestamp("due_date"), zone);
                    return item(kind, result, date, Math.abs(ChronoUnit.DAYS.between(today, date)));
                });
    }

    // Drafts of any kind, the newest first, on the day each was made. A draft
    // has no number until it is issued.
    private Group drafts(Map<String, Object> params, ZoneId zone) {
        return group(
                """
                select d.id, null as document_number, d.customer_name, client.id as client_id, d.total as amount,
                       d.created_at, count(*) over () as kind_count
                from documents d
                left join customers client on client.pkid = d.customer_pkid
                where d.business_pkid = :business and d.issued_at is null and coalesce(d.currency, '') = :currency
                order by d.created_at desc, d.pkid desc
                limit :most
                """,
                params,
                (result, rowNumber) -> item("draft", result, day(result.getTimestamp("created_at"), zone), null));
    }

    private Group group(String sql, Map<String, Object> params, RowMapper<OverviewAttention.Item> item) {
        List<Counted> rows = jdbc.sql(sql)
                .params(params)
                .query((result, rowNumber) ->
                        new Counted(item.mapRow(result, rowNumber), result.getLong("kind_count")))
                .list();
        return new Group(rows.stream().map(Counted::item).toList(), rows.isEmpty() ? 0 : rows.get(0).count());
    }

    private static OverviewAttention.Item item(String kind, ResultSet result, LocalDate date, Long days)
            throws SQLException {
        return new OverviewAttention.Item(
                kind,
                result.getObject("id", UUID.class),
                result.getString("document_number"),
                result.getString("customer_name"),
                result.getObject("client_id", UUID.class),
                result.getBigDecimal("amount"),
                date,
                days);
    }

    // A moment's day in the business's time zone.
    private static LocalDate day(Timestamp moment, ZoneId zone) {
        return LocalDate.ofInstant(moment.toInstant(), zone);
    }
}
