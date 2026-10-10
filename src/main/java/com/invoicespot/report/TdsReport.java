package com.invoicespot.report;

import com.invoicespot.business.BusinessAccess;
import com.invoicespot.common.CsvFile;
import com.invoicespot.report.dto.TdsClient;
import com.invoicespot.report.dto.TdsCurrency;
import com.invoicespot.report.dto.TdsMonth;
import com.invoicespot.report.dto.TdsPayment;
import com.invoicespot.report.dto.TdsReportResponse;
import com.invoicespot.settings.BusinessSettings;
import com.invoicespot.settings.SettingsService;
import com.invoicespot.tax.Gstin;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

// The tax clients withheld from their payments (TDS) in a period, by the date
// paid, in each currency: every payment it was withheld from, and the totals
// per client and per month, to check against the tax credited to you.
@Service
public class TdsReport {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

    private record ClientKey(UUID customerId, String name) {}

    private final ReportQueries queries;
    private final SettingsService settingsService;
    private final Clock clock;

    public TdsReport(ReportQueries queries, SettingsService settingsService, Clock clock) {
        this.queries = queries;
        this.settingsService = settingsService;
        this.clock = clock;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public TdsReportResponse report(BusinessAccess access, String periodKey) {
        BusinessSettings settings = settingsService.forBusiness(access.businessPkid());
        LocalDate today = LocalDate.now(clock.withZone(ZoneId.of(settings.getTimeZone())));
        ReportPeriod period = ReportPeriod.of(periodKey, today);
        Map<String, List<ReportQueries.Withheld>> payments =
                queries.withheld(access.businessPkid(), period.from(), period.to()).stream()
                        .collect(Collectors.groupingBy(
                                ReportQueries.Withheld::currency, LinkedHashMap::new, Collectors.toList()));
        return new TdsReportResponse(
                true,
                period.key(),
                period.from(),
                period.to(),
                ReportService.inOrder(payments.keySet(), settings.getDefaultCurrency()).stream()
                        .map(currency -> currency(currency, payments.get(currency)))
                        .toList());
    }

    // Every payment tax was withheld from, oldest first, for every currency.
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public CsvFile csv(BusinessAccess access, String periodKey) {
        TdsReportResponse report = report(access, periodKey);
        return CsvFile.of(
                "invoice-spot-tax-withheld-" + report.from() + "-to-" + report.to() + ".csv",
                List.of("Currency", "Date paid", "Client", "Client PAN", "Document", "Received", "Tax withheld"),
                report.currencies().stream()
                        .flatMap(currency -> currency.payments().stream()
                                .map(payment -> List.<Object>of(
                                        currency.currency(),
                                        payment.datePaid(),
                                        orBlank(payment.client()),
                                        orBlank(payment.pan()),
                                        orBlank(payment.documentNumber()),
                                        payment.received(),
                                        payment.withheld())))
                        .toList());
    }

    private static TdsCurrency currency(String currency, List<ReportQueries.Withheld> payments) {
        return new TdsCurrency(
                currency,
                sum(payments, ReportQueries.Withheld::received),
                sum(payments, ReportQueries.Withheld::withheld),
                clients(payments),
                months(payments),
                payments.stream()
                        .map(payment -> new TdsPayment(
                                payment.datePaid(),
                                payment.customerId(),
                                payment.name(),
                                pan(payment),
                                payment.documentId(),
                                payment.documentNumber(),
                                payment.received(),
                                payment.withheld()))
                        .toList());
    }

    // The clients who withheld the most come first.
    private static List<TdsClient> clients(List<ReportQueries.Withheld> payments) {
        return payments.stream()
                .collect(Collectors.groupingBy(
                        payment -> new ClientKey(payment.customerId(), payment.name()),
                        LinkedHashMap::new,
                        Collectors.toList()))
                .entrySet()
                .stream()
                .map(client -> new TdsClient(
                        client.getKey().customerId(),
                        client.getKey().name(),
                        client.getValue().stream()
                                .map(TdsReport::pan)
                                .filter(Objects::nonNull)
                                .findFirst()
                                .orElse(null),
                        client.getValue().size(),
                        sum(client.getValue(), ReportQueries.Withheld::received),
                        sum(client.getValue(), ReportQueries.Withheld::withheld)))
                .sorted(Comparator.comparing(TdsClient::withheld)
                        .reversed()
                        .thenComparing(client -> orBlank(client.name()).toLowerCase(Locale.ROOT)))
                .toList();
    }

    // Only the months tax was withheld in, in order.
    private static List<TdsMonth> months(List<ReportQueries.Withheld> payments) {
        return payments.stream()
                .collect(Collectors.groupingBy(
                        payment -> YearMonth.from(payment.datePaid()), TreeMap::new, Collectors.toList()))
                .entrySet()
                .stream()
                .map(month -> new TdsMonth(
                        month.getKey(),
                        month.getValue().size(),
                        sum(month.getValue(), ReportQueries.Withheld::received),
                        sum(month.getValue(), ReportQueries.Withheld::withheld)))
                .toList();
    }

    // A client's PAN is in their GSTIN, the income tax number TDS is filed
    // against.
    private static String pan(ReportQueries.Withheld payment) {
        return payment.gstin() == null ? null : Gstin.pan(payment.gstin());
    }

    private static String orBlank(String value) {
        return value == null ? "" : value;
    }

    private static <T> BigDecimal sum(List<T> rows, Function<T, BigDecimal> amount) {
        return rows.stream().map(amount).reduce(ZERO, BigDecimal::add);
    }
}
