package com.invoicespot.report;

import com.invoicespot.business.BusinessAccess;
import com.invoicespot.report.dto.OverviewOwed;
import com.invoicespot.report.dto.ReceivablesResponse;
import com.invoicespot.settings.BusinessSettings;
import com.invoicespot.settings.SettingsService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

// The documents' view of what clients owe now, in one currency chosen as the
// overview chooses it: the same invoices as the overview's total receivables,
// split into what is not due yet and what is overdue as it splits them, each
// with its own bands and the five clients who owe most of it.
@Service
public class Receivables {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);
    private static final int TOP_CLIENTS = 5;

    // What is not due yet, by days left until the due date: up to 15, 16 to
    // 30, 31 to 45, and over 45. An invoice without a due date is in the first.
    private static final List<String> DUE_BANDS = List.of("dueIn0to15", "dueIn16to30", "dueIn31to45", "dueAfter45");

    // Who owes an invoice: a client by its id, or invoices linked to none by
    // the name they were saved with.
    private record Debtor(UUID clientId, String client) {}

    private final OwedInvoices owedInvoices;
    private final BilledCurrencies billedCurrencies;
    private final SettingsService settingsService;
    private final Clock clock;

    public Receivables(
            OwedInvoices owedInvoices, BilledCurrencies billedCurrencies, SettingsService settingsService, Clock clock) {
        this.owedInvoices = owedInvoices;
        this.billedCurrencies = billedCurrencies;
        this.settingsService = settingsService;
        this.clock = clock;
    }

    // The totals are the overview's owedNow for the same currency, worked out
    // by the same code from the same invoices, and the overdue bands are its
    // bands past the first.
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ReceivablesResponse summary(BusinessAccess access, String requested) {
        Long businessPkid = access.businessPkid();
        BusinessSettings settings = settingsService.forBusiness(businessPkid);
        ZoneId zone = ZoneId.of(settings.getTimeZone());
        LocalDate today = LocalDate.now(clock.withZone(zone));
        List<String> currencies = billedCurrencies.list(businessPkid, settings.getDefaultCurrency());
        String currency = BilledCurrencies.chosen(currencies, requested, settings.getDefaultCurrency());
        List<OwedInvoices.Owed> owed = owedInvoices.read(businessPkid, currency);
        OverviewOwed owedNow = Overview.owedNow(owed, zone, today);
        List<OwedInvoices.Owed> notDue = owed.stream()
                .filter(invoice -> OwedInvoices.daysLate(invoice, zone, today) <= 0)
                .toList();
        List<OwedInvoices.Owed> overdue = owed.stream()
                .filter(invoice -> OwedInvoices.daysLate(invoice, zone, today) > 0)
                .toList();
        return new ReceivablesResponse(
                true,
                currency,
                currencies,
                owedNow.total(),
                owedNow.invoices(),
                new ReceivablesResponse.Group(
                        owedNow.notDue(),
                        owedNow.invoices() - owedNow.overdueInvoices(),
                        dueBands(notDue, zone, today),
                        topClients(notDue)),
                new ReceivablesResponse.Group(
                        owedNow.overdue(),
                        owedNow.overdueInvoices(),
                        owedNow.bands().subList(1, owedNow.bands().size()),
                        topClients(overdue)));
    }

    // What is not due yet, by days left in the same stretches of 15 days as
    // days late are.
    private static List<OverviewOwed.Band> dueBands(List<OwedInvoices.Owed> invoices, ZoneId zone, LocalDate today) {
        long[] counts = new long[DUE_BANDS.size()];
        BigDecimal[] amounts = new BigDecimal[DUE_BANDS.size()];
        Arrays.fill(amounts, ZERO);
        for (OwedInvoices.Owed invoice : invoices) {
            int band = OwedInvoices.fifteenDays(-OwedInvoices.daysLate(invoice, zone, today));
            counts[band]++;
            amounts[band] = amounts[band].add(invoice.amount());
        }
        List<OverviewOwed.Band> bands = new ArrayList<>();
        for (int band = 0; band < DUE_BANDS.size(); band++) {
            bands.add(new OverviewOwed.Band(DUE_BANDS.get(band), amounts[band], counts[band]));
        }
        return bands;
    }

    // The five clients who owe most, largest first and by name when two owe
    // the same. Clients named alike stay in the order they were added, and
    // invoices linked to no client come after them.
    private static List<ReceivablesResponse.Client> topClients(List<OwedInvoices.Owed> invoices) {
        Map<Debtor, List<OwedInvoices.Owed>> byClient = new LinkedHashMap<>();
        for (OwedInvoices.Owed invoice : invoices) {
            byClient.computeIfAbsent(new Debtor(invoice.clientId(), invoice.client()), key -> new ArrayList<>())
                    .add(invoice);
        }
        return byClient.entrySet().stream()
                .map(client -> new ReceivablesResponse.Client(
                        client.getKey().clientId(),
                        client.getKey().client(),
                        client.getValue().stream().map(OwedInvoices.Owed::amount).reduce(ZERO, BigDecimal::add),
                        client.getValue().size()))
                .sorted(ProfitReport.largestFirst(
                        ReceivablesResponse.Client::amount,
                        client -> Objects.requireNonNullElse(client.client(), "")))
                .limit(TOP_CLIENTS)
                .toList();
    }
}
