package com.invoicespot.report;

import com.invoicespot.business.BusinessAccess;
import com.invoicespot.common.CsvFile;
import com.invoicespot.document.dto.BillingItemResponse;
import com.invoicespot.report.dto.NotConvertedTotals;
import com.invoicespot.report.dto.TaxCurrency;
import com.invoicespot.report.dto.TaxReportResponse;
import com.invoicespot.report.dto.TaxTotals;
import com.invoicespot.settings.BusinessSettings;
import com.invoicespot.settings.SettingsService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

// The tax charged in a period at each rate, by the day each document is dated:
// invoices and receipts add theirs and credit notes take theirs off, in each
// currency or in the reporting one. Under India's GST it is split as it was
// charged; on a supply under reverse charge the client pays the tax, so its
// value is kept apart and none of it counts as charged.
@Service
public class TaxReport {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

    // Running totals for one rate, in a month or over the period.
    private static final class Sum {

        private final Set<UUID> documents = new HashSet<>();
        private BigDecimal taxable = ZERO;
        private BigDecimal tax = ZERO;
        private BigDecimal cgst = ZERO;
        private BigDecimal sgst = ZERO;
        private BigDecimal igst = ZERO;
        private BigDecimal reverseChargeTaxable = ZERO;

        void add(ReportQueries.Taxed row) {
            documents.add(row.documentId());
            if (row.reverseCharge()) {
                reverseChargeTaxable = reverseChargeTaxable.add(signed(row, row.taxable()));
                return;
            }
            taxable = taxable.add(signed(row, row.taxable()));
            tax = tax.add(signed(row, row.tax()));
            cgst = cgst.add(signed(row, row.cgst()));
            sgst = sgst.add(signed(row, row.sgst()));
            igst = igst.add(signed(row, row.igst()));
        }

        TaxTotals totals(YearMonth month, BigDecimal rate) {
            return new TaxTotals(
                    month, rate, documents.size(), taxable, tax, cgst, sgst, igst, reverseChargeTaxable);
        }

        private static BigDecimal signed(ReportQueries.Taxed row, BigDecimal amount) {
            return row.creditNote() ? amount.negate() : amount;
        }
    }

    private final ReportQueries queries;
    private final SettingsService settingsService;
    private final Clock clock;

    public TaxReport(ReportQueries queries, SettingsService settingsService, Clock clock) {
        this.queries = queries;
        this.settingsService = settingsService;
        this.clock = clock;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public TaxReportResponse report(BusinessAccess access, String periodKey, String viewKey) {
        BusinessSettings settings = settingsService.forBusiness(access.businessPkid());
        LocalDate today = LocalDate.now(clock.withZone(ZoneId.of(settings.getTimeZone())));
        ReportPeriod period = ReportPeriod.of(periodKey, today);
        ReportQueries.View view = ReportService.view(viewKey, settings);
        Map<String, List<ReportQueries.Taxed>> taxed =
                queries.taxed(access.businessPkid(), period.from(), period.to(), view).stream()
                        .collect(Collectors.groupingBy(
                                ReportQueries.Taxed::currency, LinkedHashMap::new, Collectors.toList()));
        return new TaxReportResponse(
                true,
                period.key(),
                period.from(),
                period.to(),
                view.converted() ? ReportService.BASE_VIEW : ReportService.CURRENCY_VIEW,
                settings.getBaseCurrency(),
                ReportService.inOrder(taxed.keySet(), settings.getDefaultCurrency()).stream()
                        .map(currency -> currency(currency, taxed.get(currency)))
                        .toList(),
                view.converted()
                        ? queries.notConvertedDated(access.businessPkid(), period.from(), period.to(), view.base())
                                .stream()
                                .map(left -> new NotConvertedTotals(left.currency(), left.documents(), left.amount()))
                                .toList()
                        : List.of());
    }

    // Every month and rate, for every currency. The amounts are plain numbers.
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public CsvFile csv(BusinessAccess access, String periodKey, String viewKey) {
        TaxReportResponse report = report(access, periodKey, viewKey);
        return CsvFile.of(
                "invoice-spot-tax-by-rate-" + report.from() + "-to-" + report.to() + ".csv",
                List.of("Currency", "Month", "Rate %", "Documents", "Taxable", "Tax", "CGST", "SGST", "IGST",
                        "Reverse-charge taxable"),
                report.currencies().stream()
                        .flatMap(currency -> currency.months().stream()
                                .map(row -> List.<Object>of(
                                        currency.currency(),
                                        row.month(),
                                        row.rate().toPlainString(),
                                        row.documents(),
                                        row.taxable(),
                                        row.tax(),
                                        row.cgst(),
                                        row.sgst(),
                                        row.igst(),
                                        row.reverseChargeTaxable())))
                        .toList());
    }

    // Rates in order, 18 and 18.00 being one; months in order, each with the
    // rates charged in it. The documents' sales view reads one currency's tax
    // from here too, so that it is always this report's.
    static TaxCurrency currency(String currency, List<ReportQueries.Taxed> rows) {
        Sum all = new Sum();
        Map<BigDecimal, Sum> rates = new TreeMap<>();
        Map<YearMonth, Map<BigDecimal, Sum>> months = new TreeMap<>();
        for (ReportQueries.Taxed row : rows) {
            BigDecimal rate = BillingItemResponse.plain(row.rate());
            all.add(row);
            rates.computeIfAbsent(rate, key -> new Sum()).add(row);
            months.computeIfAbsent(YearMonth.from(row.issueDate()), key -> new TreeMap<>())
                    .computeIfAbsent(rate, key -> new Sum())
                    .add(row);
        }
        return new TaxCurrency(
                currency,
                rows.stream().anyMatch(ReportQueries.Taxed::gst),
                all.documents.size(),
                all.taxable,
                all.tax,
                all.cgst,
                all.sgst,
                all.igst,
                all.reverseChargeTaxable,
                rates.entrySet().stream()
                        .map(rate -> rate.getValue().totals(null, rate.getKey()))
                        .toList(),
                months.entrySet().stream()
                        .flatMap(month -> month.getValue().entrySet().stream()
                                .map(rate -> rate.getValue().totals(month.getKey(), rate.getKey())))
                        .toList());
    }
}
