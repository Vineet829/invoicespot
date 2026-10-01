package com.invoicespot.report;

import com.invoicespot.business.BusinessAccess;
import com.invoicespot.document.DocumentLedger;
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

// The overview's figures, from issued documents; a draft isn't billed yet.
// Income, received and pending are for one currency, from invoices and
// receipts; a quotation is an offer, not income. Status counts and totals
// cover every issued document. Payments are the currency's, most recent first.
@Service
public class Overview {

    private record Money(BigDecimal expected, BigDecimal received, BigDecimal withheld) {}

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
    public OverviewResponse summary(BusinessAccess access, String requested) {
        Long businessPkid = access.businessPkid();
        BusinessSettings settings = settingsService.forBusiness(businessPkid);
        ZoneId zone = ZoneId.of(settings.getTimeZone());
        List<String> currencies = currencies(businessPkid, settings.getDefaultCurrency());
        String currency = currencies.contains(requested)
                ? requested
                : currencies.isEmpty() ? settings.getDefaultCurrency() : currencies.get(0);
        Money money = money(businessPkid, currency);
        Counts counts = counts(businessPkid, LocalDate.now(clock.withZone(zone)).atStartOfDay(zone));
        return new OverviewResponse(
                true,
                currencies,
                currency,
                counts.documents(),
                jdbc.sql("select count(*) from customers where business_pkid = :business")
                        .param("business", businessPkid)
                        .query(Long.class)
                        .single(),
                money.expected(),
                money.received(),
                money.withheld(),
                money.expected().subtract(money.received()).subtract(money.withheld()).max(BigDecimal.ZERO),
                counts.paid(),
                counts.partial(),
                counts.unpaid(),
                counts.overdue(),
                payments(businessPkid, currency));
    }

    // The currencies of the business's invoices and receipts, its default first.
    private List<String> currencies(Long businessPkid, String defaultCurrency) {
        List<String> found = jdbc.sql("""
                        select distinct coalesce(d.currency, '') as currency from documents d
                        where d.business_pkid = :business and %s and d.document_type <> 'Quotation'
                        order by 1
                        """.formatted(DocumentLedger.COUNTED))
                .param("business", businessPkid)
                .query(String.class)
                .list();
        List<String> currencies = new ArrayList<>();
        if (found.contains(defaultCurrency)) {
            currencies.add(defaultCurrency);
        }
        found.stream().filter(currency -> !currency.equals(defaultCurrency)).forEach(currencies::add);
        return currencies;
    }

    private Money money(Long businessPkid, String currency) {
        return jdbc.sql("""
                        select coalesce(sum(%s), 0) as expected,
                               coalesce(sum(d.total_amount_received - d.total_refunded), 0) as received,
                               coalesce(sum(d.total_tds), 0) as withheld
                        from documents d
                        where d.business_pkid = :business and %s and d.document_type <> 'Quotation'
                          and coalesce(d.currency, '') = :currency
                        """.formatted(DocumentLedger.BILLED, DocumentLedger.COUNTED))
                .param("business", businessPkid)
                .param("currency", currency)
                .query((result, rowNumber) -> new Money(
                        result.getBigDecimal("expected").setScale(2),
                        result.getBigDecimal("received").setScale(2),
                        result.getBigDecimal("withheld").setScale(2)))
                .single();
    }

    // Overdue matches the documents list: an unpaid invoice due before today.
    private Counts counts(Long businessPkid, ZonedDateTime startOfToday) {
        return jdbc.sql("""
                        select count(*) as documents,
                               count(*) filter (where status = 'Paid') as paid,
                               count(*) filter (where status = 'Not Fully Paid') as partial,
                               count(*) filter (where status = 'Not Paid') as unpaid,
                               count(*) filter (where document_type = 'Invoice' and status <> 'Paid'
                                   and due_date < :startOfToday) as overdue
                        from documents d
                        where d.business_pkid = :business and %s and d.document_type <> 'Credit Note'
                        """.formatted(DocumentLedger.COUNTED))
                .param("business", businessPkid)
                .param("startOfToday", Timestamp.from(startOfToday.toInstant()))
                .query((result, rowNumber) -> new Counts(
                        result.getLong("documents"),
                        result.getLong("paid"),
                        result.getLong("partial"),
                        result.getLong("unpaid"),
                        result.getLong("overdue")))
                .single();
    }

    private List<OverviewPayment> payments(Long businessPkid, String currency) {
        return jdbc.sql("""
                        select p.id, p.paid_by, p.date_paid, p.payment_method, p.additional_info, p.amount_paid,
                               d.id as document_id, d.document_type, d.document_number, d.total, d.status,
                               d.due_date
                        from payments p
                        join documents d on d.pkid = p.document_pkid
                        where d.business_pkid = :business and %s and coalesce(d.currency, '') = :currency
                        order by p.date_paid desc, p.pkid desc
                        """.formatted(DocumentLedger.COUNTED))
                .param("business", businessPkid)
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
