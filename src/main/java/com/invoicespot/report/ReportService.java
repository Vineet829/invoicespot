package com.invoicespot.report;

import com.invoicespot.business.BusinessAccess;
import com.invoicespot.common.ApiException;
import com.invoicespot.common.CsvFile;
import com.invoicespot.report.dto.AgeingTotals;
import com.invoicespot.report.dto.ClientTotals;
import com.invoicespot.report.dto.CurrencyReport;
import com.invoicespot.report.dto.MethodTotals;
import com.invoicespot.report.dto.MonthTotals;
import com.invoicespot.report.dto.NotConvertedTotals;
import com.invoicespot.report.dto.OtherClients;
import com.invoicespot.report.dto.ReportResponse;
import com.invoicespot.settings.BusinessSettings;
import com.invoicespot.settings.SettingsService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

// Every figure is summed per currency, or in the reporting currency at each
// document's exchange rate. Billed and received cover the period; what is
// still owed, and how overdue it is, is as of today.
@Service
public class ReportService {

    private static final int TOP_CLIENTS = 5;
    static final String CURRENCY_VIEW = "currency";
    static final String BASE_VIEW = "base";
    private static final Set<String> SECTIONS = Set.of("income", "ageing", "clients", "methods");
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);
    private static final List<String> AGEING_KEYS =
            List.of("notDue", "days1to30", "days31to60", "days61to90", "over90");
    private static final List<String> AGEING_LABELS =
            List.of("Not due yet", "1–30 days", "31–60 days", "61–90 days", "Over 90 days");

    private record Rows(
            Map<String, List<ReportQueries.Billed>> billed,
            Map<String, List<ReportQueries.Paid>> paid,
            Map<String, List<ReportQueries.Refunded>> refunded,
            Map<String, List<ReportQueries.Unpaid>> unpaid,
            Map<String, List<ReportQueries.Client>> clients) {}

    private final ReportQueries queries;
    private final SettingsService settingsService;
    private final Clock clock;

    public ReportService(ReportQueries queries, SettingsService settingsService, Clock clock) {
        this.queries = queries;
        this.settingsService = settingsService;
        this.clock = clock;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ReportResponse report(BusinessAccess access, String periodKey, String viewKey) {
        return build(access.businessPkid(), periodKey, viewKey, TOP_CLIENTS);
    }

    // One section of the report for every currency, with every client ranked
    // rather than the top few. The amounts are plain numbers.
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public CsvFile csv(BusinessAccess access, String periodKey, String viewKey, String section) {
        if (!SECTIONS.contains(section)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "That report does not exist");
        }
        ReportResponse report = build(access.businessPkid(), periodKey, viewKey, Integer.MAX_VALUE);
        String period = report.from() + "-to-" + report.to();
        List<CurrencyReport> currencies = report.currencies();
        return switch (section) {
            case "income" -> CsvFile.of(
                    "invoice-spot-income-" + period + ".csv",
                    List.of("Currency", "Month", "Billed", "Received"),
                    rows(currencies, currency -> currency.months().stream()
                            .map(month -> List.<Object>of(
                                    currency.currency(), month.month(), month.billed(), month.received()))));
            case "ageing" -> CsvFile.of(
                    "invoice-spot-unpaid-by-age-" + report.today() + ".csv",
                    List.of("Currency", "Age", "Invoices", "Owed"),
                    rows(currencies, currency -> currency.ageing().stream()
                            .map(age -> List.<Object>of(
                                    currency.currency(), age.label(), age.invoices(), age.outstanding()))));
            case "clients" -> CsvFile.of(
                    "invoice-spot-clients-" + period + ".csv",
                    List.of("Currency", "Client", "Documents", "Billed", "Paid", "Owed"),
                    rows(currencies, currency -> currency.clients().stream()
                            .map(client -> List.<Object>of(
                                    currency.currency(),
                                    client.name() == null ? "" : client.name(),
                                    client.documents(),
                                    client.billed(),
                                    client.paid(),
                                    client.outstanding()))));
            case "methods" -> CsvFile.of(
                    "invoice-spot-payment-methods-" + period + ".csv",
                    List.of("Currency", "Method", "Payments", "Amount"),
                    rows(currencies, currency -> currency.methods().stream()
                            .map(method -> List.<Object>of(
                                    currency.currency(), method.method(), method.payments(), method.amount()))));
            default -> throw new IllegalArgumentException(section);
        };
    }

    private static List<List<Object>> rows(
            List<CurrencyReport> currencies, Function<CurrencyReport, Stream<List<Object>>> rows) {
        return currencies.stream().flatMap(rows).toList();
    }

    private ReportResponse build(Long businessPkid, String periodKey, String viewKey, int topClients) {
        BusinessSettings settings = settingsService.forBusiness(businessPkid);
        ZoneId zone = ZoneId.of(settings.getTimeZone());
        LocalDate today = LocalDate.now(clock.withZone(zone));
        ReportPeriod period = ReportPeriod.of(periodKey, today);
        ReportQueries.View view = view(viewKey, settings);

        Rows rows = new Rows(
                byCurrency(
                        queries.billed(businessPkid, period.from(), period.to(), view), ReportQueries.Billed::currency),
                byCurrency(queries.paid(businessPkid, period.from(), period.to(), view), ReportQueries.Paid::currency),
                byCurrency(
                        queries.refunded(businessPkid, period.from(), period.to(), view),
                        ReportQueries.Refunded::currency),
                byCurrency(queries.unpaid(businessPkid, view), ReportQueries.Unpaid::currency),
                byCurrency(
                        queries.clients(businessPkid, period.from(), period.to(), view),
                        ReportQueries.Client::currency));

        TreeSet<String> currencies = new TreeSet<>();
        Stream.of(rows.billed(), rows.paid(), rows.refunded(), rows.unpaid())
                .forEach(found -> currencies.addAll(found.keySet()));
        List<String> ordered = inOrder(currencies, settings.getDefaultCurrency());
        return new ReportResponse(
                true,
                period.key(),
                period.from(),
                period.to(),
                today,
                years(businessPkid, today),
                view.converted() ? BASE_VIEW : CURRENCY_VIEW,
                settings.getBaseCurrency(),
                ordered.stream()
                        .map(currency -> currencyReport(currency, rows, period, zone, today, topClients))
                        .toList(),
                view.converted()
                        ? queries.notConverted(businessPkid, period.from(), period.to(), view.base()).stream()
                                .map(left -> new NotConvertedTotals(left.currency(), left.documents(), left.amount()))
                                .toList()
                        : List.of());
    }

    // Each document in its own currency, or everything in the reporting one.
    static ReportQueries.View view(String key, BusinessSettings settings) {
        if (key == null || key.isBlank() || key.equals(CURRENCY_VIEW)) {
            return ReportQueries.View.OWN;
        }
        if (key.equals(BASE_VIEW)) {
            return new ReportQueries.View(settings.getBaseCurrency());
        }
        throw new ApiException(HttpStatus.BAD_REQUEST, "Show the report by currency or in your reporting currency");
    }

    // The business's own currency first, then the others alphabetically.
    static List<String> inOrder(Collection<String> found, String first) {
        TreeSet<String> others = new TreeSet<>(found);
        List<String> ordered = new ArrayList<>();
        if (others.remove(first)) {
            ordered.add(first);
        }
        ordered.addAll(others);
        return ordered;
    }

    private static CurrencyReport currencyReport(
            String currency, Rows rows, ReportPeriod period, ZoneId zone, LocalDate today, int topClients) {
        Map<YearMonth, BigDecimal> billedByMonth = new HashMap<>();
        Map<YearMonth, BigDecimal> receivedByMonth = new HashMap<>();
        for (ReportQueries.Billed document : rows.billed().getOrDefault(currency, List.of())) {
            YearMonth month = YearMonth.from(document.issueDate());
            billedByMonth.merge(month, document.total(), BigDecimal::add);
            if (document.receipt()) {
                receivedByMonth.merge(month, document.total(), BigDecimal::add);
            }
        }
        List<ReportQueries.Paid> payments = rows.paid().getOrDefault(currency, List.of());
        for (ReportQueries.Paid payment : payments) {
            receivedByMonth.merge(YearMonth.from(payment.datePaid()), payment.amount(), BigDecimal::add);
        }
        // Money paid back comes off what was received, in the month it went.
        for (ReportQueries.Refunded refund : rows.refunded().getOrDefault(currency, List.of())) {
            receivedByMonth.merge(YearMonth.from(refund.dateRefunded()), refund.amount().negate(), BigDecimal::add);
        }
        List<MonthTotals> months = period.months().stream()
                .map(month -> new MonthTotals(
                        month, billedByMonth.getOrDefault(month, ZERO), receivedByMonth.getOrDefault(month, ZERO)))
                .toList();
        List<AgeingTotals> ageing = ageing(rows.unpaid().getOrDefault(currency, List.of()), zone, today);
        List<ReportQueries.Client> ranked = rows.clients().getOrDefault(currency, List.of());
        List<ReportQueries.Client> others = ranked.stream().skip(topClients).toList();
        return new CurrencyReport(
                currency,
                sum(months, MonthTotals::billed),
                sum(months, MonthTotals::received),
                sum(ageing, AgeingTotals::outstanding),
                sum(ageing.subList(1, ageing.size()), AgeingTotals::outstanding),
                months,
                ageing,
                ranked.stream().limit(topClients).map(ReportService::clientTotals).toList(),
                others.isEmpty()
                        ? null
                        : new OtherClients(
                                others.size(),
                                others.stream().mapToLong(ReportQueries.Client::documents).sum(),
                                sum(others, ReportQueries.Client::billed),
                                sum(others, ReportQueries.Client::paid),
                                sum(others, ReportService::outstanding)),
                methods(payments));
    }

    // An invoice is overdue from the day after its due date, by the business's
    // calendar; one without a due date is never overdue.
    private static List<AgeingTotals> ageing(List<ReportQueries.Unpaid> invoices, ZoneId zone, LocalDate today) {
        long[] counts = new long[AGEING_KEYS.size()];
        BigDecimal[] amounts = new BigDecimal[AGEING_KEYS.size()];
        Arrays.fill(amounts, ZERO);
        for (ReportQueries.Unpaid invoice : invoices) {
            long days = invoice.dueDate() == null
                    ? 0
                    : ChronoUnit.DAYS.between(invoice.dueDate().atZone(zone).toLocalDate(), today);
            int bucket = days <= 0 ? 0 : days <= 30 ? 1 : days <= 60 ? 2 : days <= 90 ? 3 : 4;
            counts[bucket]++;
            amounts[bucket] = amounts[bucket].add(invoice.outstanding());
        }
        List<AgeingTotals> ageing = new ArrayList<>();
        for (int bucket = 0; bucket < AGEING_KEYS.size(); bucket++) {
            ageing.add(new AgeingTotals(
                    AGEING_KEYS.get(bucket), AGEING_LABELS.get(bucket), counts[bucket], amounts[bucket]));
        }
        return ageing;
    }

    private static List<MethodTotals> methods(List<ReportQueries.Paid> payments) {
        return payments.stream()
                .collect(Collectors.groupingBy(ReportQueries.Paid::method, LinkedHashMap::new, Collectors.toList()))
                .entrySet()
                .stream()
                .map(method -> new MethodTotals(
                        method.getKey(), method.getValue().size(), sum(method.getValue(), ReportQueries.Paid::amount)))
                .sorted(Comparator.comparing(MethodTotals::amount).reversed().thenComparing(MethodTotals::method))
                .toList();
    }

    private static ClientTotals clientTotals(ReportQueries.Client client) {
        return new ClientTotals(
                client.customerId(),
                client.name(),
                client.documents(),
                client.billed(),
                client.paid(),
                outstanding(client));
    }

    // What a client still owes on the period's documents: tax they withheld
    // counts as settled, though it isn't money paid.
    private static BigDecimal outstanding(ReportQueries.Client client) {
        return client.billed().subtract(client.paid()).subtract(client.withheld());
    }

    // The years before this one, back to the first invoice, receipt or
    // payment, for the period picker.
    private List<Integer> years(Long businessPkid, LocalDate today) {
        OptionalInt first = Stream.of(
                        queries.firstDocument(businessPkid).map(LocalDate::getYear),
                        queries.firstPayment(businessPkid).map(LocalDate::getYear))
                .flatMap(Optional::stream)
                .mapToInt(Integer::intValue)
                .min();
        List<Integer> years = new ArrayList<>();
        if (first.isPresent()) {
            for (int year = today.getYear() - 1; year >= Math.max(first.getAsInt(), ReportPeriod.FIRST_YEAR); year--) {
                years.add(year);
            }
        }
        return years;
    }

    private static <T> Map<String, List<T>> byCurrency(List<T> rows, Function<T, String> currency) {
        return rows.stream().collect(Collectors.groupingBy(currency, LinkedHashMap::new, Collectors.toList()));
    }

    private static <T> BigDecimal sum(List<T> rows, Function<T, BigDecimal> amount) {
        return rows.stream().map(amount).reduce(ZERO, BigDecimal::add);
    }
}
