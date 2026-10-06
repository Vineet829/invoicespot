package com.invoicespot.report;

import com.invoicespot.business.BusinessAccess;
import com.invoicespot.common.ApiException;
import com.invoicespot.report.dto.ItemSalesDetailResponse;
import com.invoicespot.report.dto.ItemSalesResponse;
import com.invoicespot.report.dto.OverviewItem;
import com.invoicespot.settings.BusinessSettings;
import com.invoicespot.settings.SettingsService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

// Sales by item: every item sold in a period, in one currency chosen as the
// overview chooses it, read by the query the overview's top items are read
// by, with no limit, and each item's own sales page.
@Service
public class ItemSales {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);
    private static final BigDecimal NO_SHARE = BigDecimal.ZERO.setScale(4);
    private static final int MAX_PAGE_SIZE = 100;
    private static final int MOST_LINES = 50;

    // What an item's sales are read in: the currency chosen, and the days.
    private record Scope(List<String> currencies, String currency, ListPeriod period) {}

    private final ItemQueries queries;
    private final BilledCurrencies billedCurrencies;
    private final SettingsService settingsService;
    private final Clock clock;

    public ItemSales(
            ItemQueries queries, BilledCurrencies billedCurrencies, SettingsService settingsService, Clock clock) {
        this.queries = queries;
        this.billedCurrencies = billedCurrencies;
        this.settingsService = settingsService;
        this.clock = clock;
    }

    // Largest first and by name when two came to the same, as the overview
    // lists them. The total is what every item came to, on every page: the
    // overview's itemsTotal for the same days.
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ItemSalesResponse list(
            BusinessAccess access, String periodKey, String requested, int pageNumber, int pageSize) {
        int page = Math.max(pageNumber, 1);
        int size = Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);
        Scope scope = scope(access, periodKey, requested);
        List<OverviewItem> items = sold(access, scope);
        BigDecimal total = total(items);
        return new ItemSalesResponse(
                true,
                scope.currency(),
                scope.currencies(),
                scope.period().view(),
                total,
                items.size(),
                page,
                size,
                (items.size() + size - 1) / size,
                items.stream()
                        .skip((long) (page - 1) * size)
                        .limit(size)
                        .map(item -> new ItemSalesResponse.Item(
                                item.item(), item.quantity(), item.amount(), share(item.amount(), total),
                                item.documents()))
                        .toList());
    }

    // An item is asked for by name, whatever its letter case and the spaces
    // around it, as the list tells items apart; the lines without a name are
    // asked for as the list names them. An item the period sold none of has
    // nothing in it.
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ItemSalesDetailResponse detail(BusinessAccess access, String name, String periodKey, String requested) {
        if (name == null || name.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Choose an item");
        }
        String asked = name.strip();
        String named = asked.equalsIgnoreCase(ItemQueries.OTHER_LINES) ? null : asked;
        Scope scope = scope(access, periodKey, requested);
        Long businessPkid = access.businessPkid();
        ListPeriod period = scope.period();
        BigDecimal total = total(sold(access, scope));
        OverviewItem item = queries.sold(businessPkid, scope.currency(), period.firstDay(), period.lastDay(), named)
                .stream()
                .map(ItemQueries.Sold::item)
                .findFirst()
                .orElse(new OverviewItem(named == null ? ItemQueries.OTHER_LINES : asked, BigDecimal.ZERO, ZERO, 0));
        ItemQueries.Lines lines = queries.lines(
                businessPkid, scope.currency(), period.firstDay(), period.lastDay(), named, MOST_LINES);
        return new ItemSalesDetailResponse(
                true,
                item.item(),
                scope.currency(),
                scope.currencies(),
                period.view(),
                item.quantity(),
                item.amount(),
                share(item.amount(), total),
                item.documents(),
                queries.clients(businessPkid, scope.currency(), period.firstDay(), period.lastDay(), named),
                lines.lines(),
                lines.total());
    }

    private Scope scope(BusinessAccess access, String periodKey, String requested) {
        BusinessSettings settings = settingsService.forBusiness(access.businessPkid());
        LocalDate today = LocalDate.now(clock.withZone(ZoneId.of(settings.getTimeZone())));
        ListPeriod period = ListPeriod.of(periodKey, today);
        List<String> currencies = billedCurrencies.list(access.businessPkid(), settings.getDefaultCurrency());
        return new Scope(
                currencies, BilledCurrencies.chosen(currencies, requested, settings.getDefaultCurrency()), period);
    }

    private List<OverviewItem> sold(BusinessAccess access, Scope scope) {
        return queries.sold(
                        access.businessPkid(), scope.currency(), scope.period().firstDay(), scope.period().lastDay())
                .stream()
                .map(ItemQueries.Sold::item)
                .toList();
    }

    // What every item came to, as the overview adds up its itemsTotal.
    private static BigDecimal total(List<OverviewItem> items) {
        return items.stream().map(OverviewItem::amount).reduce(ZERO, BigDecimal::add);
    }

    // An amount's part of the total, to four places; none of a total that
    // isn't more than nothing.
    private static BigDecimal share(BigDecimal amount, BigDecimal total) {
        return total.signum() > 0 ? amount.divide(total, 4, RoundingMode.HALF_UP) : NO_SHARE;
    }
}
