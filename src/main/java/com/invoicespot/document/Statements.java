package com.invoicespot.document;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
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
            BigDecimal credited,
            BigDecimal refunded,
            BigDecimal due,
            BigDecimal overdue) {}

    private enum Kind {
        INVOICE,
        PAYMENT,
        CREDIT,
        REFUND
    }

    private record Entry(
            LocalDate date,
            int order,
            int sequence,
            String description,
            BigDecimal charge,
            BigDecimal payment,
            Kind kind) {}

    private Statements() {}

    static List<Account> accounts(List<Document> invoices, Instant startOfToday) {
        Map<String, List<Document>> byCurrency = invoices.stream()
                .collect(Collectors.groupingBy(
                        invoice -> Objects.requireNonNullElse(invoice.getCurrency(), ""),
                        LinkedHashMap::new,
                        Collectors.toList()));
        return byCurrency.entrySet().stream()
                .map(currency -> account(currency.getKey(), currency.getValue(), startOfToday))
                .toList();
    }

    private static Account account(String currency, List<Document> invoices, Instant startOfToday) {
        List<Entry> entries = new ArrayList<>();
        BigDecimal overdue = BigDecimal.ZERO;
        for (Document invoice : invoices) {
            String number = Objects.requireNonNullElse(invoice.getDocumentNumber(), "");
            BigDecimal total = orZero(invoice.getTotal());
            entries.add(new Entry(
                    invoice.getIssueDate(),
                    0,
                    entries.size(),
                    "Invoice " + number,
                    total,
                    null,
                    Kind.INVOICE));
            // Tax the client withheld settles the invoice like a payment, on a
            // line of its own.
            for (Payment payment : invoice.getPayments()) {
                if (orZero(payment.getAmountPaid()).signum() > 0 || orZero(payment.getTdsAmount()).signum() == 0) {
                    entries.add(new Entry(
                            payment.getDatePaid(),
                            1,
                            entries.size(),
                            "Payment for " + number + " (" + payment.getPaymentMethod().getValue() + ")",
                            null,
                            orZero(payment.getAmountPaid()),
                            Kind.PAYMENT));
                }
                if (orZero(payment.getTdsAmount()).signum() > 0) {
                    entries.add(new Entry(
                            payment.getDatePaid(),
                            1,
                            entries.size(),
                            "Tax withheld by you on " + number,
                            null,
                            payment.getTdsAmount(),
                            Kind.PAYMENT));
                }
            }
            // A credit note settles the invoice like a payment, on its own date.
            for (Document note : invoice.getCreditNotes()) {
                if (note.state() == DocumentState.ISSUED) {
                    entries.add(new Entry(
                            note.getIssueDate(),
                            1,
                            entries.size(),
                            "Credit note " + note.getDocumentNumber() + " for " + number,
                            null,
                            orZero(note.getTotal()),
                            Kind.CREDIT));
                }
            }
            // Money paid back is owed again, like a charge.
            for (Refund refund : invoice.getRefunds()) {
                entries.add(new Entry(
                        refund.getDateRefunded(),
                        1,
                        entries.size(),
                        "Refund on " + number,
                        refund.getAmount(),
                        null,
                        Kind.REFUND));
            }
            if (DocumentTotals.isOverdue(invoice, startOfToday)) {
                overdue = overdue.add(DocumentTotals.balanceDue(invoice));
            }
        }
        entries.sort(Comparator.comparing(Entry::date)
                .thenComparingInt(Entry::order)
                .thenComparingInt(Entry::sequence));

        List<Line> lines = new ArrayList<>();
        BigDecimal balance = BigDecimal.ZERO;
        BigDecimal charged = BigDecimal.ZERO;
        BigDecimal paid = BigDecimal.ZERO;
        BigDecimal credited = BigDecimal.ZERO;
        BigDecimal refunded = BigDecimal.ZERO;
        for (Entry entry : entries) {
            if (entry.charge() != null) {
                balance = balance.add(entry.charge());
            }
            if (entry.payment() != null) {
                balance = balance.subtract(entry.payment());
            }
            switch (entry.kind()) {
                case INVOICE -> charged = charged.add(entry.charge());
                case PAYMENT -> paid = paid.add(entry.payment());
                case CREDIT -> credited = credited.add(entry.payment());
                case REFUND -> refunded = refunded.add(entry.charge());
            }
            lines.add(new Line(entry.date(), entry.description(), entry.charge(), entry.payment(), balance));
        }
        return new Account(currency, lines, charged, paid, credited, refunded, balance, overdue);
    }

    private static BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
