package com.invoicespot.report.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

// What came in over a period in one currency, as the payments list adds it up:
// in all, beside the period before it when there is one to compare with; how
// many payments, receipts and refunds it took; by method, the largest first,
// receipts being one, "On a receipt", as the list calls them; and the five
// clients who paid most.
public record PaymentSummaryResponse(
        boolean success,
        String currency,
        List<String> currencies,
        ViewPeriod period,
        BigDecimal total,
        BigDecimal previousTotal,
        long payments,
        List<MethodTotals> methods,
        List<Payer> payers) {

    // What a client paid, less what went back to it, and in how many
    // payments, receipts and refunds. The id is that of the client the
    // documents are linked to; documents linked to none are one client by the
    // name they were saved with, and have no id.
    public record Payer(UUID clientId, String client, long payments, BigDecimal amount) {}
}
