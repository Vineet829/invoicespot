package com.invoicespot.report;

import com.invoicespot.business.BusinessAccess;
import com.invoicespot.report.dto.DocumentSummaryResponse;
import com.invoicespot.report.dto.MethodTotals;
import com.invoicespot.report.dto.PaymentSummaryResponse;
import com.invoicespot.settings.BusinessSettings;
import com.invoicespot.settings.SettingsService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

// What the payments list adds up to over a period, in one currency chosen as
// the overview chooses it: the same rows, read by the list's own queries, so
// that the total is always the list's received total for the same days, and
// over one of the overview's periods its Received.
@Service
public class PaymentSummary {

    private static final int TOP_PAYERS = 5;

    private final PaymentQueries queries;
    private final BilledCurrencies billedCurrencies;
    private final SettingsService settingsService;
    private final Clock clock;

    public PaymentSummary(
            PaymentQueries queries, BilledCurrencies billedCurrencies, SettingsService settingsService, Clock clock) {
        this.queries = queries;
        this.billedCurrencies = billedCurrencies;
        this.settingsService = settingsService;
        this.clock = clock;
    }

    // Every method is listed, largest first, and the five clients who paid
    // most, each by name when two came to the same; clients named alike stay
    // in the order they were added.
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PaymentSummaryResponse summary(BusinessAccess access, String periodKey, String requested) {
        Long businessPkid = access.businessPkid();
        BusinessSettings settings = settingsService.forBusiness(businessPkid);
        LocalDate today = LocalDate.now(clock.withZone(ZoneId.of(settings.getTimeZone())));
        ListPeriod period = ListPeriod.of(periodKey, today);
        List<String> currencies = billedCurrencies.list(businessPkid, settings.getDefaultCurrency());
        String currency = BilledCurrencies.chosen(currencies, requested, settings.getDefaultCurrency());
        PaymentSearch search = PaymentSearch.dated(currency, period.from(), period.to());
        DocumentSummaryResponse.Received received =
                queries.received(businessPkid, currency, period.from(), period.to());
        return new PaymentSummaryResponse(
                true,
                currency,
                currencies,
                period.view(),
                received.total(),
                period.compared()
                        ? queries.received(businessPkid, currency, period.previousFrom(), period.previousTo())
                                .total()
                        : null,
                received.payments(),
                queries.byMethod(businessPkid, search).stream()
                        .sorted(ProfitReport.largestFirst(MethodTotals::amount, MethodTotals::method))
                        .toList(),
                queries.byPayer(businessPkid, search).stream()
                        .sorted(ProfitReport.largestFirst(
                                PaymentSummaryResponse.Payer::amount,
                                payer -> Objects.requireNonNullElse(payer.client(), "")))
                        .limit(TOP_PAYERS)
                        .toList());
    }
}
