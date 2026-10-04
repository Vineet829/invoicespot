package com.invoicespot.report;

import com.invoicespot.business.BusinessAccess;
import com.invoicespot.document.DocumentLedger;
import com.invoicespot.report.dto.OverviewOwed;
import com.invoicespot.report.dto.OverviewPayment;
import com.invoicespot.report.dto.OverviewResponse;
import com.invoicespot.report.dto.OverviewTotals;
import com.invoicespot.report.dto.OwedWeek;
import com.invoicespot.report.dto.TrendBucket;
import com.invoicespot.settings.BusinessSettings;
import com.invoicespot.settings.SettingsService;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
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
// A period's totals, and those of the period before it, count money as the
// reports do; what is owed is as of today.
@Service
public class Overview {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);
    private static final List<String> WEEKS = List.of("overdue", "thisWeek", "nextWeek", "weekAfter", "later");

    private record Money(BigDecimal expected, BigDecimal received, BigDecimal withheld) {}

    private record Counts(long documents, long paid, long partial, long unpaid, long overdue) {}

    // An issued invoice with money still owed on it, and when it falls due.
    private record Owed(Instant dueDate, BigDecimal amount) {}

    private final JdbcClient jdbc;
    private final ReportQueries queries;
    private final NeedsAttention needsAttention;
    private final SettingsService settingsService;
    private final Clock clock;

    public Overview(
            JdbcClient jdbc,
            ReportQueries queries,
            NeedsAttention needsAttention,
            SettingsService settingsService,
            Clock clock) {
        this.jdbc = jdbc;
        this.queries = queries;
        this.needsAttention = needsAttention;
        this.settingsService = settingsService;
        this.clock = clock;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public OverviewResponse summary(BusinessAccess access, String requested, String periodKey) {
        Long businessPkid = access.businessPkid();
        BusinessSettings settings = settingsService.forBusiness(businessPkid);
        ZoneId zone = ZoneId.of(settings.getTimeZone());
        LocalDate today = LocalDate.now(clock.withZone(zone));
        OverviewWindow window = OverviewWindow.of(periodKey, today);
        List<String> currencies = currencies(businessPkid, settings.getDefaultCurrency());
        String currency = currencies.contains(requested)
                ? requested
                : currencies.isEmpty() ? settings.getDefaultCurrency() : currencies.get(0);
        Money money = money(businessPkid, currency);
        ZonedDateTime startOfToday = today.atStartOfDay(zone);
        Counts counts = counts(businessPkid, startOfToday);
        MoneyByDay byDay = MoneyByDay.read(queries, businessPkid, currency, window);
        List<Owed> owed = owed(businessPkid, currency);
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
                payments(businessPkid, currency, zone),
                window.period(),
                totals(byDay, window, activeBefore(businessPkid, currency, window.from())),
                owedNow(owed, startOfToday),
                trend(byDay, window),
                owedByWeek(owed, zone, today),
                needsAttention.list(businessPkid, currency, zone, today));
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

    // A document's due date is its day in the business's time zone.
    private List<OverviewPayment> payments(Long businessPkid, String currency, ZoneId zone) {
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
                                    due == null ? null : LocalDate.ofInstant(due.toInstant(), zone)));
                })
                .list();
    }

    // The period's totals beside those of the period before it.
    private static OverviewTotals totals(MoneyByDay money, OverviewWindow window, boolean hasPrevious) {
        MoneyByDay.Sums current = money.between(window.from(), window.to());
        MoneyByDay.Sums previous = money.between(window.previousFrom(), window.previousTo());
        return new OverviewTotals(
                new OverviewTotals.Figure(current.billed(), previous.billed()),
                new OverviewTotals.Figure(current.received(), previous.received()),
                new OverviewTotals.Figure(current.profit(), previous.profit()),
                hasPrevious);
    }

    // The period's money a day, a week or a month at a time.
    private static List<TrendBucket> trend(MoneyByDay money, OverviewWindow window) {
        return window.buckets().stream()
                .map(bucket -> {
                    MoneyByDay.Sums sums = money.between(bucket.from(), bucket.to());
                    return new TrendBucket(
                            bucket.from(),
                            bucket.to(),
                            sums.billed(),
                            sums.received(),
                            sums.income(),
                            sums.expenses(),
                            sums.moneyIn(),
                            sums.moneyOut());
                })
                .toList();
    }

    // Whether the currency had money dated before the period: an invoice or
    // receipt, a payment on an invoice, or an expense.
    private boolean activeBefore(Long businessPkid, String currency, LocalDate from) {
        return jdbc.sql("""
                        select exists (
                                   select 1 from documents d
                                   where d.business_pkid = :business and %1$s
                                     and d.document_type in ('Invoice', 'Receipt')
                                     and coalesce(d.currency, '') = :currency and d.issue_date < :from)
                               or exists (
                                   select 1 from payments p
                                   join documents d on d.pkid = p.document_pkid
                                   where d.business_pkid = :business and %1$s and d.document_type = 'Invoice'
                                     and coalesce(d.currency, '') = :currency and p.date_paid < :from)
                               or exists (
                                   select 1 from expenses e
                                   where e.business_pkid = :business and e.currency = :currency
                                     and e.expense_date < :from)
                        """.formatted(DocumentLedger.COUNTED))
                .param("business", businessPkid)
                .param("currency", currency)
                .param("from", from)
                .query(Boolean.class)
                .single();
    }

    // The currency's issued invoices with money still owed on them.
    private List<Owed> owed(Long businessPkid, String currency) {
        return jdbc.sql("""
                        select d.due_date, %1$s as owed
                        from documents d
                        where d.business_pkid = :business and %2$s and d.document_type = 'Invoice'
                          and coalesce(d.currency, '') = :currency and %1$s > 0
                        """.formatted(DocumentLedger.OWED, DocumentLedger.COUNTED))
                .param("business", businessPkid)
                .param("currency", currency)
                .query((result, rowNumber) -> {
                    Timestamp due = result.getTimestamp("due_date");
                    return new Owed(due == null ? null : due.toInstant(), result.getBigDecimal("owed"));
                })
                .list();
    }

    // Overdue as the documents list and the overdue count have it: due before
    // today began.
    private static OverviewOwed owedNow(List<Owed> invoices, ZonedDateTime startOfToday) {
        BigDecimal total = ZERO;
        BigDecimal overdue = ZERO;
        long late = 0;
        for (Owed invoice : invoices) {
            total = total.add(invoice.amount());
            if (invoice.dueDate() != null && invoice.dueDate().isBefore(startOfToday.toInstant())) {
                overdue = overdue.add(invoice.amount());
                late++;
            }
        }
        return new OverviewOwed(total, total.subtract(overdue), overdue, invoices.size(), late);
    }

    // What is owed by the week it falls due, counted from today: overdue
    // before today, then this week and the two after it, then later or with
    // no due date. A due date is its day in the business's time zone.
    private static List<OwedWeek> owedByWeek(List<Owed> invoices, ZoneId zone, LocalDate today) {
        long[] counts = new long[WEEKS.size()];
        BigDecimal[] amounts = new BigDecimal[WEEKS.size()];
        Arrays.fill(amounts, ZERO);
        int later = WEEKS.size() - 1;
        for (Owed invoice : invoices) {
            int week = later;
            if (invoice.dueDate() != null) {
                long days = ChronoUnit.DAYS.between(today, LocalDate.ofInstant(invoice.dueDate(), zone));
                week = days < 0 ? 0 : (int) Math.min(1 + days / 7, later);
            }
            counts[week]++;
            amounts[week] = amounts[week].add(invoice.amount());
        }
        List<OwedWeek> weeks = new ArrayList<>();
        for (int week = 0; week < WEEKS.size(); week++) {
            weeks.add(new OwedWeek(
                    WEEKS.get(week),
                    week == 0 ? null : today.plusDays(7L * (week - 1)),
                    week == later ? null : today.plusDays(7L * week - 1),
                    amounts[week],
                    counts[week]));
        }
        return weeks;
    }
}
