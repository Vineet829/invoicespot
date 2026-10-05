package com.invoicespot.report;

import com.invoicespot.business.BusinessAccess;
import com.invoicespot.business.Permission;
import com.invoicespot.document.DocumentLedger;
import com.invoicespot.document.dto.BillingItemResponse;
import com.invoicespot.report.dto.DocumentSummaryResponse;
import com.invoicespot.report.dto.OverviewClient;
import com.invoicespot.report.dto.OverviewItem;
import com.invoicespot.report.dto.OverviewOwed;
import com.invoicespot.report.dto.OverviewPayment;
import com.invoicespot.report.dto.OverviewResponse;
import com.invoicespot.report.dto.OverviewSetup;
import com.invoicespot.report.dto.OverviewSpending;
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
import java.util.Objects;
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
// reports do; what is owed is as of today. Spending is what the currency's
// expenses paid in the period cost, by category, as its profit counts them.
// The top clients are those the period billed most, as its billed total
// counts them, and the top items those its lines sold most of before tax,
// which all add up to its income. What is coming up is the currency's, from
// today: the invoices schedules will make and the quotations still open.
// Those who may change the business's details and settings also see what is
// left to set up.
@Service
public class Overview {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);
    private static final List<String> WEEKS = List.of("overdue", "thisWeek", "nextWeek", "weekAfter", "later");
    private static final int TOP_CATEGORIES = 5;
    private static final int TOP_CLIENTS = 5;
    private static final int TOP_ITEMS = 5;
    private static final String OTHER_LINES = "Other lines";

    // The name a document linked to no client was saved with, for SQL over
    // documents aliased d; none for a document linked to one.
    private static final String SAVED_NAME = "(case when d.customer_pkid is null then d.customer_name end)";

    // What a line comes to before tax, as DocumentTotals works it out: its
    // quantity at its unit price, less its discount as a percentage, rounded
    // half up to the cent, for SQL over billing items aliased i. Only the
    // discount is divided, so that nothing is rounded before the cent.
    private static final String LINE_NET =
            "round(i.quantity * coalesce(i.unit_price, 0) * (1 - i.discount / 100), 2)";

    // A credit note's lines take theirs off, for SQL over documents aliased d.
    private static final String SIGN = "(case when d.document_type = 'Credit Note' then -1 else 1 end)";

    private record Money(BigDecimal expected, BigDecimal received, BigDecimal withheld) {}

    private record Counts(long documents, long paid, long partial, long unpaid, long overdue) {}

    // An issued invoice with money still owed on it, and when it falls due.
    private record Owed(Instant dueDate, BigDecimal amount) {}

    private final JdbcClient jdbc;
    private final ReportQueries queries;
    private final PaymentQueries paymentQueries;
    private final NeedsAttention needsAttention;
    private final ComingUp comingUp;
    private final SettingsService settingsService;
    private final Clock clock;

    public Overview(
            JdbcClient jdbc,
            ReportQueries queries,
            PaymentQueries paymentQueries,
            NeedsAttention needsAttention,
            ComingUp comingUp,
            SettingsService settingsService,
            Clock clock) {
        this.jdbc = jdbc;
        this.queries = queries;
        this.paymentQueries = paymentQueries;
        this.needsAttention = needsAttention;
        this.comingUp = comingUp;
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
        String currency = chosen(currencies, requested, settings.getDefaultCurrency());
        Money money = money(businessPkid, currency);
        ZonedDateTime startOfToday = today.atStartOfDay(zone);
        Counts counts = counts(businessPkid, startOfToday);
        MoneyByDay byDay = MoneyByDay.read(queries, businessPkid, currency, window);
        List<Owed> owed = owed(businessPkid, currency);
        List<OverviewItem> items = itemsSold(businessPkid, currency, window);
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
                needsAttention.list(businessPkid, currency, zone, today),
                spending(businessPkid, currency, window, byDay),
                topClients(businessPkid, currency, window),
                items.stream().limit(TOP_ITEMS).toList(),
                items.stream().map(OverviewItem::amount).reduce(ZERO, BigDecimal::add),
                comingUp.read(businessPkid, currency, zone, today),
                access.can(Permission.MANAGE_BUSINESS) ? setup(businessPkid, settings) : null);
    }

    // The money bar over the documents list, in a currency chosen as above:
    // what is owed now, as here, and the Received of the last 30 days, with
    // how many payments, receipts and refunds it came from. Documents without
    // a currency are left out of its choice, as a list's address can't tell
    // them from all currencies.
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public DocumentSummaryResponse documentSummary(BusinessAccess access, String requested) {
        Long businessPkid = access.businessPkid();
        BusinessSettings settings = settingsService.forBusiness(businessPkid);
        ZoneId zone = ZoneId.of(settings.getTimeZone());
        LocalDate today = LocalDate.now(clock.withZone(zone));
        List<String> currencies = currencies(businessPkid, settings.getDefaultCurrency()).stream()
                .filter(currency -> !currency.isEmpty())
                .toList();
        String currency = chosen(currencies, requested, settings.getDefaultCurrency());
        OverviewWindow month = OverviewWindow.of("30d", today);
        return new DocumentSummaryResponse(
                true,
                currency,
                currencies,
                owedNow(owed(businessPkid, currency), today.atStartOfDay(zone)),
                paymentQueries.received(businessPkid, currency, month.from(), month.to()));
    }

    // The currency asked for when the business has money in it; else the
    // first it has money in, its default one coming first; else the default.
    private static String chosen(List<String> currencies, String requested, String defaultCurrency) {
        return currencies.contains(requested)
                ? requested
                : currencies.isEmpty() ? defaultCurrency : currencies.get(0);
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

    // What the period's expenses cost, as its profit and its trend count
    // them, with the five categories that cost most, told apart and ordered
    // as the profit report's are, and the rest added up together.
    private OverviewSpending spending(Long businessPkid, String currency, OverviewWindow window, MoneyByDay money) {
        List<ReportQueries.CategorySpent> categories =
                queries.spentByCategory(businessPkid, window.from(), window.to()).stream()
                        .filter(category -> category.currency().equals(currency))
                        .sorted(ProfitReport.largestFirst(
                                ReportQueries.CategorySpent::amount, ReportQueries.CategorySpent::category))
                        .toList();
        List<ReportQueries.CategorySpent> rest = categories.stream().skip(TOP_CATEGORIES).toList();
        return new OverviewSpending(
                money.between(window.from(), window.to()).expenses(),
                categories.stream()
                        .limit(TOP_CATEGORIES)
                        .map(category -> new OverviewSpending.Category(
                                category.category(), category.amount(), category.expenses()))
                        .toList(),
                rest.isEmpty()
                        ? null
                        : new OverviewSpending.Others(
                                rest.stream().map(ReportQueries.CategorySpent::amount).reduce(ZERO, BigDecimal::add),
                                rest.stream().mapToLong(ReportQueries.CategorySpent::expenses).sum(),
                                rest.size()));
    }

    // The five clients the period billed most, as its billed total counts
    // them, largest first and by name when two were billed the same, each
    // with how many documents billed it and what it owes now on invoices of
    // any date. A client is the one documents are linked to, named as it is
    // now, or, for documents linked to none, the name they were saved with.
    // Clients named alike stay in the order they were added.
    private List<OverviewClient> topClients(Long businessPkid, String currency, OverviewWindow window) {
        return jdbc.sql("""
                        with billed as (
                            select d.customer_pkid, %1$s as saved_name, sum(%2$s) as billed, count(*) as documents
                            from documents d
                            where d.business_pkid = :business and %3$s and coalesce(d.currency, '') = :currency
                              and d.issue_date between :from and :to
                            group by 1, 2
                        ),
                        owed as (
                            select d.customer_pkid, %1$s as saved_name, sum(%4$s) as owed
                            from documents d
                            where d.business_pkid = :business and %5$s and coalesce(d.currency, '') = :currency
                            group by 1, 2
                        )
                        select c.id as client_id, coalesce(c.name, b.saved_name) as client, b.billed,
                               coalesce(o.owed, 0) as owed, b.documents
                        from billed b
                        left join customers c on c.pkid = b.customer_pkid
                        left join owed o on o.customer_pkid is not distinct from b.customer_pkid
                            and o.saved_name is not distinct from b.saved_name
                        order by b.customer_pkid, b.saved_name
                        """.formatted(
                                SAVED_NAME,
                                DocumentLedger.BILLED,
                                DocumentLedger.BILLED_DOCUMENT,
                                DocumentLedger.OWED,
                                DocumentLedger.OWED_INVOICE))
                .param("business", businessPkid)
                .param("currency", currency)
                .param("from", window.from())
                .param("to", window.to())
                .query((result, rowNumber) -> new OverviewClient(
                        result.getObject("client_id", UUID.class),
                        result.getString("client"),
                        result.getBigDecimal("billed"),
                        result.getBigDecimal("owed").setScale(2),
                        result.getLong("documents")))
                .list()
                .stream()
                .sorted(ProfitReport.largestFirst(
                        OverviewClient::billed, client -> Objects.requireNonNullElse(client.client(), "")))
                .limit(TOP_CLIENTS)
                .toList();
    }

    // Every item the lines of the period's invoices and receipts sold, less
    // what the lines of its credit notes took off, largest first and by name
    // when two came to the same. An item is its name, whatever its letter case
    // and the spaces around it, spelled as it was first used, and lines
    // without a name are one item. As each document's lines add up to it
    // before tax, they all add up to the period's income.
    private List<OverviewItem> itemsSold(Long businessPkid, String currency, OverviewWindow window) {
        return jdbc.sql("""
                        select (array_agg(btrim(i.item_name) order by d.pkid, i.position))[1] as item,
                               sum(%1$s * i.quantity) as quantity, sum(%1$s * %2$s) as amount,
                               count(distinct d.pkid) as documents
                        from documents d
                        join document_billing_items i on i.document_pkid = d.pkid
                        where d.business_pkid = :business and %3$s and coalesce(d.currency, '') = :currency
                          and d.issue_date between :from and :to
                        group by lower(nullif(btrim(i.item_name), ''))
                        """.formatted(SIGN, LINE_NET, DocumentLedger.BILLED_DOCUMENT))
                .param("business", businessPkid)
                .param("currency", currency)
                .param("from", window.from())
                .param("to", window.to())
                .query((result, rowNumber) -> {
                    String item = result.getString("item");
                    return new OverviewItem(
                            item == null || item.isEmpty() ? OTHER_LINES : item,
                            BillingItemResponse.plain(result.getBigDecimal("quantity")),
                            result.getBigDecimal("amount"),
                            result.getLong("documents"));
                })
                .list()
                .stream()
                .sorted(ProfitReport.largestFirst(OverviewItem::amount, OverviewItem::item))
                .toList();
    }

    // What is left to set up, step by step: an address and a tax ID, a logo,
    // a way for clients to pay, a first invoice issued and not void, and
    // someone else in the team. A detail of nothing but spaces isn't set.
    private OverviewSetup setup(Long businessPkid, BusinessSettings settings) {
        return jdbc.sql("""
                        select nullif(btrim(b.address), '') is not null and nullif(btrim(b.tax_id), '') is not null
                                   as business_details,
                               nullif(btrim(b.logo), '') is not null as logo,
                               exists (select 1 from documents d
                                       where d.business_pkid = b.pkid and %s and d.document_type = 'Invoice')
                                   as first_invoice,
                               (select count(*) from business_members m where m.business_pkid = b.pkid) > 1 as team
                        from businesses b
                        where b.pkid = :business
                        """.formatted(DocumentLedger.COUNTED))
                .param("business", businessPkid)
                .query((result, rowNumber) -> new OverviewSetup(List.of(
                        new OverviewSetup.Step("businessDetails", result.getBoolean("business_details")),
                        new OverviewSetup.Step("logo", result.getBoolean("logo")),
                        new OverviewSetup.Step("paymentDetails", settings.getPaymentDetails().hasWayToPay()),
                        new OverviewSetup.Step("firstInvoice", result.getBoolean("first_invoice")),
                        new OverviewSetup.Step("team", result.getBoolean("team")))))
                .single();
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
                        select d.due_date, %s as owed
                        from documents d
                        where d.business_pkid = :business and %s and coalesce(d.currency, '') = :currency
                        """.formatted(DocumentLedger.OWED, DocumentLedger.OWED_INVOICE))
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
