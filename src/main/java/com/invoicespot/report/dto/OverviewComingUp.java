package com.invoicespot.report.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

// What is coming up in the overview's currency: the invoices running
// schedules will make over the next 90 days, and the quotations waiting for an
// answer or that expired without one.
public record OverviewComingUp(Recurring recurring, Quotations quotations) {

    // How many schedules are running, the invoices they will make over the
    // next 90 days and what those come to, and the soonest few.
    public record Recurring(long active, Next90Days next90Days, List<Run> nextRuns) {}

    public record Next90Days(long invoices, BigDecimal amount) {}

    // An invoice a schedule will make on the day: a copy of the invoice it
    // repeats, which names it by its number and client, for what that comes to.
    public record Run(
            UUID scheduleId,
            UUID templateId,
            String documentNumber,
            String client,
            BigDecimal amount,
            LocalDate date) {}

    public record Quotations(Awaiting awaiting, Expired expired) {}

    // The quotations still waiting for an answer, what they come to, and the
    // few that expire soonest.
    public record Awaiting(long count, BigDecimal amount, List<Quotation> items) {}

    // The last day a quotation is valid and how many days are left until it,
    // or neither for one that is valid for as long as it takes.
    public record Quotation(
            UUID documentId,
            String documentNumber,
            String client,
            BigDecimal amount,
            LocalDate validUntil,
            Long days) {}

    public record Expired(long count, BigDecimal amount) {}
}
