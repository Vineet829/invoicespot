package com.invoicespot.report.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

// One page of the clients billed in a period in one currency, as the reports'
// top clients count them, and what every one of them comes to.
public record CustomerSalesResponse(
        boolean success,
        String currency,
        List<String> currencies,
        ViewPeriod period,
        long totalClients,
        int page,
        int size,
        int numberOfPages,
        Totals totals,
        List<Client> clients) {

    public record Totals(long documents, BigDecimal billed, BigDecimal paid, BigDecimal owed) {}

    // How many documents billed a client in the period, what they billed,
    // what was paid on them less what went back, and what is still owed on
    // them, tax the client withheld counting as settled. The id is that of
    // the client the documents are linked to; documents linked to none are
    // one client by the name they were saved with, and have no id.
    public record Client(
            UUID clientId, String client, long documents, BigDecimal billed, BigDecimal paid, BigDecimal owed) {}
}
