package com.invoicespot.report;

import com.invoicespot.document.DocumentLedger;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

// What is owed now in one currency, as the overview counts it: the issued
// invoices with money still owed on them, each with what is owed, when it
// falls due and who owes it, and how late each is by the business's calendar.
@Repository
class OwedInvoices {

    // An invoice with money owed on it, and the client it is owed by: the
    // one it is linked to, named as it is now, or for an invoice linked to
    // none, no id and the name it was saved with.
    record Owed(Instant dueDate, BigDecimal amount, UUID clientId, String client) {}

    private final JdbcClient jdbc;

    OwedInvoices(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    // In the order the clients were added, those linked to none last, and
    // each client's in the order they were made.
    List<Owed> read(Long businessPkid, String currency) {
        return jdbc.sql("""
                        select d.due_date, %s as owed, c.id as client_id, coalesce(c.name, %s) as client
                        from documents d
                        left join customers c on c.pkid = d.customer_pkid
                        where d.business_pkid = :business and %s and coalesce(d.currency, '') = :currency
                        order by d.customer_pkid, d.pkid
                        """.formatted(DocumentLedger.OWED, Overview.SAVED_NAME, DocumentLedger.OWED_INVOICE))
                .param("business", businessPkid)
                .param("currency", currency)
                .query((result, rowNumber) -> {
                    Timestamp due = result.getTimestamp("due_date");
                    return new Owed(
                            due == null ? null : due.toInstant(),
                            result.getBigDecimal("owed"),
                            result.getObject("client_id", UUID.class),
                            result.getString("client"));
                })
                .list();
    }

    // Days late are today less the due date, by the business's calendar, as
    // the reports' age table counts them; an invoice is overdue from the day
    // after it falls due, and one without a due date is not due yet.
    static long daysLate(Owed invoice, ZoneId zone, LocalDate today) {
        return invoice.dueDate() == null
                ? 0
                : ChronoUnit.DAYS.between(LocalDate.ofInstant(invoice.dueDate(), zone), today);
    }

    // Which 15 days a number of days falls in: up to 15, 16 to 30, 31 to 45,
    // or over 45, for days late as for days left until the due date.
    static int fifteenDays(long days) {
        return days <= 15 ? 0 : days <= 30 ? 1 : days <= 45 ? 2 : 3;
    }
}
