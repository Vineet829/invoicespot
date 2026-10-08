package com.invoicespot.report;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.NavigableMap;
import java.util.TreeMap;

// What one currency billed, received, earned and spent each day of the
// overview's period and the period before it, totalled as the reports total
// them: billed and received as ReportService counts them, income and
// expenses as ProfitReport does. What went out for expenses is what was paid,
// as the expenses list adds it up.
final class MoneyByDay {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

    // What came to over some days. Money in is receipts and payments, and
    // money out is what was paid for expenses, tax that can be claimed back
    // and all, and refunds: received is money in less refunds.
    record Sums(
            BigDecimal billed,
            BigDecimal received,
            BigDecimal income,
            BigDecimal expenses,
            BigDecimal moneyIn,
            BigDecimal moneyOut) {

        BigDecimal profit() {
            return income.subtract(expenses);
        }
    }

    private final NavigableMap<LocalDate, BigDecimal> billed = new TreeMap<>();
    // Receipts on the day they are dated, and payments on invoices.
    private final NavigableMap<LocalDate, BigDecimal> moneyIn = new TreeMap<>();
    private final NavigableMap<LocalDate, BigDecimal> refunded = new TreeMap<>();
    private final NavigableMap<LocalDate, BigDecimal> income = new TreeMap<>();
    private final NavigableMap<LocalDate, BigDecimal> expenses = new TreeMap<>();
    private final NavigableMap<LocalDate, BigDecimal> paidOut = new TreeMap<>();

    private MoneyByDay() {}

    static MoneyByDay read(ReportQueries queries, long businessPkid, String currency, OverviewWindow window) {
        MoneyByDay money = new MoneyByDay();
        for (ReportQueries.DocumentDay day : queries.documentsByDay(businessPkid, currency, window)) {
            add(money.billed, day.day(), day.billed());
            add(money.moneyIn, day.day(), day.receipts());
            add(money.income, day.day(), day.earned());
        }
        for (ReportQueries.Daily day : queries.paidByDay(businessPkid, currency, window)) {
            add(money.moneyIn, day.day(), day.amount());
        }
        for (ReportQueries.Daily day : queries.refundedByDay(businessPkid, currency, window)) {
            add(money.refunded, day.day(), day.amount());
        }
        for (ReportQueries.SpentDay day : queries.spentByDay(businessPkid, currency, window)) {
            add(money.expenses, day.day(), day.cost());
            add(money.paidOut, day.day(), day.paid());
        }
        return money;
    }

    // Money paid back comes off what was received, on the day it went.
    Sums between(LocalDate from, LocalDate to) {
        BigDecimal in = sum(moneyIn, from, to);
        BigDecimal paidBack = sum(refunded, from, to);
        return new Sums(
                sum(billed, from, to),
                in.subtract(paidBack),
                sum(income, from, to),
                sum(expenses, from, to),
                in,
                sum(paidOut, from, to).add(paidBack));
    }

    private static void add(NavigableMap<LocalDate, BigDecimal> days, LocalDate day, BigDecimal amount) {
        days.merge(day, amount, BigDecimal::add);
    }

    private static BigDecimal sum(NavigableMap<LocalDate, BigDecimal> days, LocalDate from, LocalDate to) {
        return days.subMap(from, true, to, true).values().stream().reduce(ZERO, BigDecimal::add);
    }
}
