package com.invoicespot.report;

import com.invoicespot.business.BusinessAccess;
import com.invoicespot.document.DocumentLedger;
import com.invoicespot.report.dto.DocumentSummaryResponse;
import com.invoicespot.report.dto.OverviewClient;
import com.invoicespot.report.dto.OverviewItem;
import com.invoicespot.report.dto.OverviewOwed;
import com.invoicespot.report.dto.OverviewResponse;
import com.invoicespot.report.dto.OverviewTotals;
import com.invoicespot.report.dto.TrendBucket;
import com.invoicespot.settings.BusinessSettings;
import com.invoicespot.settings.SettingsService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
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
// receipts; a quotation is an offer, not income.
// A period's totals, and those of the period before it, count money as the
// reports do, and the tax in what they billed as the tax report does; what is
// owed is as of today.
// The top clients are those the period billed most, as its billed total
// counts them, and the top items those its lines sold most of before tax,
// which all add up to its income. What is coming up is the currency's, from
// today: the invoices schedules will make and the quotations still open.
@Service
public class Overview {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);
    private static final int TOP_CLIENTS = 5;
    private static final int TOP_ITEMS = 5;

    // What is owed, by days late: not due yet, then 1 to 15, 16 to 30, 31 to
    // 45, and over 45.
    private static final List<String> OWED_BANDS =
            List.of("current", "days1to15", "days16to30", "days31to45", "over45");

    // The name a document linked to no client was saved with, for SQL over
    // documents aliased d; none for a document linked to one.
    static final String SAVED_NAME = "(case when d.customer_pkid is null then d.customer_name end)";

    private record Money(BigDecimal expected, BigDecimal received, BigDecimal withheld) {}

    private final JdbcClient jdbc;
    private final ReportQueries queries;
    private final PaymentQueries paymentQueries;
    private final BilledCurrencies billedCurrencies;
    private final OwedInvoices owedInvoices;
    private final ItemQueries itemQueries;
    private final NeedsAttention needsAttention;
    private final ComingUp comingUp;
    private final SettingsService settingsService;
    private final Clock clock;

    public Overview(
            JdbcClient jdbc,
            ReportQueries queries,
            PaymentQueries paymentQueries,
            BilledCurrencies billedCurrencies,
            OwedInvoices owedInvoices,
            ItemQueries itemQueries,
            NeedsAttention needsAttention,
            ComingUp comingUp,
            SettingsService settingsService,
            Clock clock) {
        this.jdbc = jdbc;
        this.queries = queries;
        this.paymentQueries = paymentQueries;
        this.billedCurrencies = billedCurrencies;
        this.owedInvoices = owedInvoices;
        this.itemQueries = itemQueries;
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
        List<String> currencies = billedCurrencies.list(businessPkid, settings.getDefaultCurrency());
        String currency = BilledCurrencies.chosen(currencies, requested, settings.getDefaultCurrency());
        Money money = money(businessPkid, currency);
        MoneyByDay byDay = MoneyByDay.read(queries, businessPkid, currency, window);
        List<OwedInvoices.Owed> owed = owedInvoices.read(businessPkid, currency);
        List<OverviewItem> items = itemQueries.sold(businessPkid, currency, window.from(), window.to()).stream()
                .map(ItemQueries.Sold::item)
                .toList();
        return new OverviewResponse(
                true,
                currencies,
                currency,
                jdbc.sql("select count(*) from customers where business_pkid = :business")
                        .param("business", businessPkid)
                        .query(Long.class)
                        .single(),
                money.expected(),
                money.received(),
                money.withheld(),
                money.expected().subtract(money.received()).subtract(money.withheld()).max(BigDecimal.ZERO),
                window.period(),
                totals(
                        byDay,
                        queries.taxBilled(businessPkid, currency, window),
                        window,
                        activeBefore(businessPkid, currency, window.from())),
                owedNow(owed, zone, today),
                trend(byDay, window),
                needsAttention.list(businessPkid, currency, zone, today),
                topClients(businessPkid, currency, window),
                items.stream().limit(TOP_ITEMS).toList(),
                items.stream().map(OverviewItem::amount).reduce(ZERO, BigDecimal::add),
                comingUp.read(businessPkid, currency, zone, today));
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
        List<String> currencies = billedCurrencies.list(businessPkid, settings.getDefaultCurrency()).stream()
                .filter(currency -> !currency.isEmpty())
                .toList();
        String currency = BilledCurrencies.chosen(currencies, requested, settings.getDefaultCurrency());
        OverviewWindow month = OverviewWindow.of("30d", today);
        return new DocumentSummaryResponse(
                true,
                currency,
                currencies,
                owedNow(owedInvoices.read(businessPkid, currency), zone, today),
                paymentQueries.received(businessPkid, currency, month.from(), month.to()));
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

    // The period's totals beside those of the period before it.
    private static OverviewTotals totals(
            MoneyByDay money, ReportQueries.TaxBilled tax, OverviewWindow window, boolean hasPrevious) {
        MoneyByDay.Sums current = money.between(window.from(), window.to());
        MoneyByDay.Sums previous = money.between(window.previousFrom(), window.previousTo());
        return new OverviewTotals(
                new OverviewTotals.Figure(current.billed(), previous.billed()),
                new OverviewTotals.Figure(current.received(), previous.received()),
                new OverviewTotals.Figure(current.profit(), previous.profit()),
                tax.current(),
                tax.previous(),
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

    // Overdue as the documents list has it: due before today began, by days
    // late as OwedInvoices counts them. The receivables view splits the same
    // invoices the same way.
    static OverviewOwed owedNow(List<OwedInvoices.Owed> invoices, ZoneId zone, LocalDate today) {
        long[] counts = new long[OWED_BANDS.size()];
        BigDecimal[] amounts = new BigDecimal[OWED_BANDS.size()];
        Arrays.fill(amounts, ZERO);
        for (OwedInvoices.Owed invoice : invoices) {
            long days = OwedInvoices.daysLate(invoice, zone, today);
            int band = days <= 0 ? 0 : 1 + OwedInvoices.fifteenDays(days);
            counts[band]++;
            amounts[band] = amounts[band].add(invoice.amount());
        }
        List<OverviewOwed.Band> bands = new ArrayList<>();
        BigDecimal overdue = ZERO;
        for (int band = 0; band < OWED_BANDS.size(); band++) {
            bands.add(new OverviewOwed.Band(OWED_BANDS.get(band), amounts[band], counts[band]));
            if (band > 0) {
                overdue = overdue.add(amounts[band]);
            }
        }
        return new OverviewOwed(
                amounts[0].add(overdue), amounts[0], overdue, invoices.size(), invoices.size() - counts[0], bands);
    }
}
