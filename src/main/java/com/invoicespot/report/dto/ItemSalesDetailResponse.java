package com.invoicespot.report.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

// One item's sales in a period in one currency, as the sales by item view
// lists it: how many were sold, what its lines came to before tax, its share
// of what every item came to, and on how many documents; who bought it; and
// its newest lines, with how many there are in all.
public record ItemSalesDetailResponse(
        boolean success,
        String item,
        String currency,
        List<String> currencies,
        ViewPeriod period,
        BigDecimal quantity,
        BigDecimal amount,
        BigDecimal share,
        long documents,
        List<Client> clients,
        List<Line> lines,
        long linesTotal) {

    // How many of the item a client bought and what that came to. The id is
    // that of the client the documents are linked to; documents linked to
    // none are one client by the name they were saved with, and have no id.
    public record Client(UUID clientId, String client, BigDecimal quantity, BigDecimal amount) {}

    // One line selling the item, on the document it is on, for the client
    // the document names; a credit note's line takes its quantity and amount
    // off.
    public record Line(
            UUID documentId,
            String documentNumber,
            String documentType,
            LocalDate issueDate,
            String client,
            BigDecimal quantity,
            BigDecimal amount) {}
}
