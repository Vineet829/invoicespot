package com.invoicespot.document;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

final class Statements {

    record Line(LocalDate date, String description, BigDecimal charge, BigDecimal payment, BigDecimal balance) {}

    record Account(
            String currency,
            List<Line> lines,
            BigDecimal charged,
            BigDecimal paid,
            BigDecimal due,
            BigDecimal overdue) {}

    private record Entry(
            LocalDate date, int order, int sequence, String description, BigDecimal charge, BigDecimal payment) {}

    private Statements() {}

    static List<Account> accounts(List<Document> invoices, ZoneId zone, Instant startOfToday) {
        Map<String, List<Document>> byCurrency = invoices.stream()
                .collect(Collectors.groupingBy(
                        invoice -> Objects.requireNonNullElse(invoice.getCurrency(), ""),
                        LinkedHashMap::new,
                        Collectors.toList()));
        return byCurrency.entrySet().stream()
                .map(currency -> account(currency.getKey(), currency.getValue(), zone, startOfToday))
                .toList();
    }

    private static Account account(String currency, List<Document> invoices, ZoneId zone, Instant startOfToday) {
        List<Entry> entries = new ArrayList<>();
        BigDecimal overdue = BigDecimal.ZERO;
        for (Document invoice : invoices) {
            String number = Objects.requireNonNullElse(invoice.getDocumentNumber(), "");
            BigDecimal total = orZero(invoice.getTotal());
            entries.add(new Entry(
                    invoice.getCreatedAt().atZone(zone).toLocalDate(),
                    0,
                    entries.size(),
                    "Invoice " + number,
                    total,
                    null));
            for (Payment payment : invoice.getPayments()) {
                entries.add(new Entry(
                        payment.getDatePaid(),
                        1,
                        entries.size(),
                        "Payment for " + number + " (" + payment.getPaymentMethod().getValue() + ")",
                        null,
                        orZero(payment.getAmountPaid())));
            }
            if (DocumentTotals.isOverdue(invoice, startOfToday)) {
                overdue = overdue.add(total.subtract(orZero(invoice.getTotalAmountReceived())));
            }
        }
        entries.sort(Comparator.comparing(Entry::date)
                .thenComparingInt(Entry::order)
                .thenComparingInt(Entry::sequence));

        List<Line> lines = new ArrayList<>();
        BigDecimal balance = BigDecimal.ZERO;
        BigDecimal charged = BigDecimal.ZERO;
        BigDecimal paid = BigDecimal.ZERO;
        for (Entry entry : entries) {
            if (entry.charge() != null) {
                balance = balance.add(entry.charge());
                charged = charged.add(entry.charge());
            }
            if (entry.payment() != null) {
                balance = balance.subtract(entry.payment());
                paid = paid.add(entry.payment());
            }
            lines.add(new Line(entry.date(), entry.description(), entry.charge(), entry.payment(), balance));
        }
        return new Account(currency, lines, charged, paid, balance, overdue);
    }

    private static BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
