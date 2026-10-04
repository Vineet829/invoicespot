package com.invoicespot.report;

import com.invoicespot.business.BusinessAccess;
import com.invoicespot.report.dto.ProfitCategory;
import com.invoicespot.report.dto.ProfitCurrency;
import com.invoicespot.report.dto.ProfitMonth;
import com.invoicespot.report.dto.ProfitReportResponse;
import com.invoicespot.settings.BusinessSettings;
import com.invoicespot.settings.SettingsService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.function.Function;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

// Profit and loss for a period, in each currency, before tax: what issued
// invoices and receipts earned, less credit notes, by the day each is dated;
// and what expenses cost, by the day each was paid, with the tax on them
// only when it can't be claimed back.
@Service
public class ProfitReport {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

    private final ReportQueries queries;
    private final SettingsService settingsService;
    private final Clock clock;

    public ProfitReport(ReportQueries queries, SettingsService settingsService, Clock clock) {
        this.queries = queries;
        this.settingsService = settingsService;
        this.clock = clock;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ProfitReportResponse report(BusinessAccess access, String periodKey) {
        BusinessSettings settings = settingsService.forBusiness(access.businessPkid());
        LocalDate today = LocalDate.now(clock.withZone(ZoneId.of(settings.getTimeZone())));
        ReportPeriod period = ReportPeriod.of(periodKey, today);
        List<ReportQueries.Earned> earned = queries.earned(access.businessPkid(), period.from(), period.to());
        List<ReportQueries.Spent> spent = queries.spent(access.businessPkid(), period.from(), period.to());
        List<ReportQueries.CategorySpent> categories =
                queries.spentByCategory(access.businessPkid(), period.from(), period.to());
        TreeSet<String> currencies = new TreeSet<>();
        earned.forEach(row -> currencies.add(row.currency()));
        spent.forEach(row -> currencies.add(row.currency()));
        return new ProfitReportResponse(
                true,
                period.key(),
                period.from(),
                period.to(),
                ReportService.inOrder(currencies, settings.getDefaultCurrency()).stream()
                        .map(currency -> currency(currency, earned, spent, categories))
                        .toList());
    }

    // The months with income or expenses, in order, and what each category
    // cost, largest first.
    private static ProfitCurrency currency(
            String currency,
            List<ReportQueries.Earned> earned,
            List<ReportQueries.Spent> spent,
            List<ReportQueries.CategorySpent> categories) {
        Map<YearMonth, BigDecimal> income = new HashMap<>();
        earned.stream()
                .filter(row -> row.currency().equals(currency))
                .forEach(row -> income.merge(YearMonth.from(row.month()), row.income(), BigDecimal::add));
        Map<YearMonth, BigDecimal> costs = new HashMap<>();
        spent.stream()
                .filter(row -> row.currency().equals(currency))
                .forEach(row -> costs.merge(YearMonth.from(row.month()), row.cost(), BigDecimal::add));
        TreeSet<YearMonth> active = new TreeSet<>(income.keySet());
        active.addAll(costs.keySet());
        List<ProfitMonth> months = active.stream()
                .map(month -> {
                    BigDecimal in = income.getOrDefault(month, ZERO);
                    BigDecimal out = costs.getOrDefault(month, ZERO);
                    return new ProfitMonth(month, in, out, in.subtract(out));
                })
                .toList();
        BigDecimal totalIncome = sum(months, ProfitMonth::income);
        BigDecimal totalExpenses = sum(months, ProfitMonth::expenses);
        return new ProfitCurrency(
                currency,
                totalIncome,
                totalExpenses,
                totalIncome.subtract(totalExpenses),
                months,
                categories.stream()
                        .filter(row -> row.currency().equals(currency))
                        .map(row -> new ProfitCategory(row.category(), row.amount()))
                        .sorted(largestFirst(ProfitCategory::expenses, ProfitCategory::category))
                        .toList());
    }

    // Categories that cost most first, and by name, whatever its case, when
    // two cost the same, then by spelling, so that none comes in whatever
    // order the database read it: as they are listed here and on the overview.
    static <T> Comparator<T> largestFirst(Function<T, BigDecimal> cost, Function<T, String> category) {
        return Comparator.comparing(cost, Comparator.<BigDecimal>reverseOrder())
                .thenComparing(row -> category.apply(row).toLowerCase(Locale.ROOT))
                .thenComparing(category);
    }

    private static <T> BigDecimal sum(List<T> rows, Function<T, BigDecimal> amount) {
        return rows.stream().map(amount).reduce(ZERO, BigDecimal::add);
    }
}
