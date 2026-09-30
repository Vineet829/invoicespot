package com.invoicespot.report;

import com.invoicespot.report.dto.OverviewPayment;
import com.invoicespot.report.dto.OverviewResponse;
import com.invoicespot.settings.BusinessSettings;
import com.invoicespot.settings.SettingsService;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

// The overview's figures. Income, received and pending are for one currency,
// from invoices and receipts; a quotation is an offer, not income. Status
// counts and totals cover every document. Payments are the currency's, most
// recent first.
@Service
public class Overview {

    private record Money(BigDecimal expected, BigDecimal received) {}

    private record Counts(long documents, long paid, long partial, long unpaid, long overdue) {}

    private final JdbcClient jdbc;
    private final SettingsService settingsService;
    private final Clock clock;

    public Overview(JdbcClient jdbc, SettingsService settingsService, Clock clock) {
        this.jdbc = jdbc;
        this.settingsService = settingsService;
        this.clock = clock;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public OverviewResponse summary(Long ownerPkid, String requested) {
        BusinessSettings settings = settingsService.forUser(ownerPkid);
        ZoneId zone = ZoneId.of(settings.getTimeZone());
        List<String> currencies = currencies(ownerPkid, settings.getDefaultCurrency());
        String currency = currencies.contains(requested)
                ? requested
                : currencies.isEmpty() ? settings.getDefaultCurrency() : currencies.get(0);
        Money money = money(ownerPkid, currency);
        Counts counts = counts(ownerPkid, LocalDate.now(clock.withZone(zone)).atStartOfDay(zone));
        return new OverviewResponse(
                true,
                currencies,
                currency,
                counts.documents(),
                jdbc.sql("select count(*) from customers where created_by_pkid = :owner")
                        .param("owner", ownerPkid)
                        .query(Long.class)
                        .single(),
                money.expected(),
                money.received(),
                money.expected().subtract(money.received()),
                counts.paid(),
                counts.partial(),
                counts.unpaid(),
                counts.overdue(),
                payments(ownerPkid, currency));
    }

    // The currencies of the owner's invoices and receipts, their default first.
    private List<String> currencies(Long ownerPkid, String defaultCurrency) {
        List<String> found = jdbc.sql("""
                        select distinct coalesce(currency, '') as currency from documents
                        where created_by_pkid = :owner and document_type <> 'Quotation'
                        order by 1
                        """)
                .param("owner", ownerPkid)
                .query(String.class)
                .list();
        List<String> currencies = new ArrayList<>();
        if (found.contains(defaultCurrency)) {
            currencies.add(defaultCurrency);
        }
        found.stream().filter(currency -> !currency.equals(defaultCurrency)).forEach(currencies::add);
        return currencies;
    }

    private Money money(Long ownerPkid, String currency) {
        return jdbc.sql("""
                        select coalesce(sum(total), 0) as expected,
                               coalesce(sum(total_amount_received), 0) as received
                        from documents
                        where created_by_pkid = :owner and document_type <> 'Quotation'
                          and coalesce(currency, '') = :currency
                        """)
                .param("owner", ownerPkid)
                .param("currency", currency)
                .query((result, rowNumber) -> new Money(
                        result.getBigDecimal("expected").setScale(2),
                        result.getBigDecimal("received").setScale(2)))
                .single();
    }

    // Overdue matches the documents list: an unpaid invoice due before today.
    private Counts counts(Long ownerPkid, ZonedDateTime startOfToday) {
        return jdbc.sql("""
                        select count(*) as documents,
                               count(*) filter (where status = 'Paid') as paid,
                               count(*) filter (where status = 'Not Fully Paid') as partial,
                               count(*) filter (where status = 'Not Paid') as unpaid,
                               count(*) filter (where document_type = 'Invoice' and status <> 'Paid'
                                   and due_date < :startOfToday) as overdue
                        from documents
                        where created_by_pkid = :owner
                        """)
                .param("owner", ownerPkid)
                .param("startOfToday", Timestamp.from(startOfToday.toInstant()))
                .query((result, rowNumber) -> new Counts(
                        result.getLong("documents"),
                        result.getLong("paid"),
                        result.getLong("partial"),
                        result.getLong("unpaid"),
                        result.getLong("overdue")))
                .single();
    }

    private List<OverviewPayment> payments(Long ownerPkid, String currency) {
        return jdbc.sql("""
                        select p.id, p.paid_by, p.date_paid, p.payment_method, p.additional_info, p.amount_paid,
                               d.id as document_id, d.document_type, d.document_number, d.total, d.status,
                               d.due_date
                        from payments p
                        join documents d on d.pkid = p.document_pkid
                        where d.created_by_pkid = :owner and coalesce(d.currency, '') = :currency
                        order by p.date_paid desc, p.pkid desc
                        """)
                .param("owner", ownerPkid)
                .param("currency", currency)
                .query((result, rowNumber) -> {
                    Timestamp due = result.getTimestamp("due_date");
                    return new OverviewPayment(
                            result.getObject("id", UUID.class),
                            result.getString("paid_by"),
                            result.getObject("date_paid", LocalDate.class),
                            result.getString("payment_method"),
                            result.getString("additional_info"),
                            result.getBigDecimal("amount_paid"),
                            new OverviewPayment.Document(
                                    result.getObject("document_id", UUID.class),
                                    result.getString("document_type"),
                                    result.getString("document_number"),
                                    result.getBigDecimal("total"),
                                    result.getString("status"),
                                    due == null ? null : due.toInstant()));
                })
                .list();
    }
}
