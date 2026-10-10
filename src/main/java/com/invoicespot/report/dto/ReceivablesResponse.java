package com.invoicespot.report.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

// What clients owe now in one currency, as the overview's total receivables
// counts it: in all, and split into what is not due yet and what is overdue.
public record ReceivablesResponse(
        boolean success,
        String currency,
        List<String> currencies,
        BigDecimal total,
        long invoices,
        Group current,
        Group overdue) {

    // What is owed in one of the two and on how many invoices, in stretches
    // of 15 days until or since the due date, and the five clients who owe
    // most of it.
    public record Group(BigDecimal amount, long invoices, List<OverviewOwed.Band> bands, List<Client> clients) {}

    // What a client owes in the group, on how many invoices. The id is that
    // of the client the invoices are linked to; invoices linked to none are
    // one client by the name they were saved with, and have no id.
    public record Client(UUID clientId, String client, BigDecimal amount, long invoices) {}
}
