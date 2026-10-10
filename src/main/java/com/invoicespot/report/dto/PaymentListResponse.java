package com.invoicespot.report.dto;

import java.time.LocalDate;
import java.util.List;

// One page of the payments, receipts and refunds a search matches; the days
// they were dated between when the search named any, as the dates or the
// period asked for; what all of them come to in each currency, by currency
// code, which counts them; and the payment methods the business has been paid
// or paid back by, for the method filter.
public record PaymentListResponse(
        boolean success,
        long total,
        int numberOfPages,
        int page,
        int size,
        LocalDate from,
        LocalDate to,
        List<PaymentEntry> payments,
        List<PaymentTotals> totals,
        List<String> methods) {}
