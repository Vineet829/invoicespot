package com.invoicespot.document.dto;

import java.math.BigDecimal;

// What the listed documents in one currency come to, on every page: how many
// there are, what the invoices, receipts and credit notes among them billed,
// and what is still owed on the invoices, with how much of it is overdue and
// on how many. Documents without a currency are listed under none.
public record DocumentListTotals(
        String currency,
        long documents,
        BigDecimal billed,
        BigDecimal balanceDue,
        BigDecimal overdue,
        long overdueDocuments) {}
