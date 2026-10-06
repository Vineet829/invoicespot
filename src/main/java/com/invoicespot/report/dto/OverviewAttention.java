package com.invoicespot.report.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

// What needs doing, most urgent first, a few at most, and how many of each
// kind there are in all.
public record OverviewAttention(List<Item> items, Counts counts) {

    // The kind is paymentReport, overdue, dueSoon or draft, and the document
    // is the one to open. The client is named as the document names it; its
    // id is that of the client the document is linked to, and none once it
    // isn't linked to one. Days are days late for an overdue invoice and days
    // left for one due soon; a payment report and a draft have none.
    public record Item(
            String kind,
            UUID documentId,
            String documentNumber,
            String client,
            UUID clientId,
            BigDecimal amount,
            LocalDate date,
            Long days) {}

    public record Counts(long paymentReports, long overdue, long dueSoon, long drafts) {}
}
