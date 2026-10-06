package com.invoicespot.report;

import com.invoicespot.business.BusinessAccess;
import com.invoicespot.report.dto.SalesResponse;
import com.invoicespot.report.dto.TaxCurrency;
import com.invoicespot.settings.BusinessSettings;
import com.invoicespot.settings.SettingsService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

// The documents' view of what was sold in a period, in one currency chosen as
// the overview chooses it: the documents the overview's Billed counts, dated
// in the period, by kind of document, and the tax in them at each rate, read
// by the tax report's own code.
@Service
public class Sales {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

    // The kinds of document that bill, in the order they are listed; each is
    // listed even when none of it was issued in the period.
    private static final List<String> TYPES = List.of("Invoice", "Receipt", "Credit Note");

    private final ReportQueries queries;
    private final BilledCurrencies billedCurrencies;
    private final SettingsService settingsService;
    private final Clock clock;

    public Sales(
            ReportQueries queries, BilledCurrencies billedCurrencies, SettingsService settingsService, Clock clock) {
        this.queries = queries;
        this.billedCurrencies = billedCurrencies;
        this.settingsService = settingsService;
        this.clock = clock;
    }

    // Today is the business's, in its time zone.
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public SalesResponse summary(BusinessAccess access, String periodKey, String requested) {
        Long businessPkid = access.businessPkid();
        BusinessSettings settings = settingsService.forBusiness(businessPkid);
        LocalDate today = LocalDate.now(clock.withZone(ZoneId.of(settings.getTimeZone())));
        ListPeriod period = ListPeriod.of(periodKey, today);
        List<String> currencies = billedCurrencies.list(businessPkid, settings.getDefaultCurrency());
        String currency = BilledCurrencies.chosen(currencies, requested, settings.getDefaultCurrency());
        Map<String, ReportQueries.BilledByType> billed =
                queries.billedByType(businessPkid, currency, period.firstDay(), period.lastDay()).stream()
                        .collect(Collectors.toMap(ReportQueries.BilledByType::type, Function.identity()));
        List<SalesResponse.TypeTotals> byType = TYPES.stream()
                .map(type -> {
                    ReportQueries.BilledByType kind = billed.get(type);
                    return kind == null
                            ? new SalesResponse.TypeTotals(type, 0, ZERO)
                            : new SalesResponse.TypeTotals(type, kind.documents(), kind.amount());
                })
                .toList();
        TaxCurrency tax = TaxReport.currency(
                currency,
                queries.taxed(businessPkid, period.firstDay(), period.lastDay(), ReportQueries.View.OWN).stream()
                        .filter(row -> row.currency().equals(currency))
                        .toList());
        return new SalesResponse(
                true,
                currency,
                currencies,
                period.view(),
                byType.stream().map(SalesResponse.TypeTotals::amount).reduce(ZERO, BigDecimal::add),
                period.compared()
                        ? queries.billedByType(businessPkid, currency, period.previousFrom(), period.previousTo())
                                .stream()
                                .map(ReportQueries.BilledByType::amount)
                                .reduce(ZERO, BigDecimal::add)
                        : null,
                byType.stream().mapToLong(SalesResponse.TypeTotals::documents).sum(),
                byType,
                new SalesResponse.Tax(
                        tax.tax(),
                        tax.rates().stream()
                                .map(rate -> new SalesResponse.Rate(
                                        rate.rate(), rate.taxable(), rate.tax(), rate.documents()))
                                .toList()));
    }
}
