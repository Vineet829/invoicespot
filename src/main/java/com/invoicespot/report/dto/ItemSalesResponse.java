package com.invoicespot.report.dto;

import java.math.BigDecimal;
import java.util.List;

// One page of the items sold in a period in one currency, as the overview's
// top items count them, largest first; what all of them came to, which is the
// period's income; and how many there are.
public record ItemSalesResponse(
        boolean success,
        String currency,
        List<String> currencies,
        ViewPeriod period,
        BigDecimal total,
        long totalItems,
        int page,
        int size,
        int numberOfPages,
        List<Item> items) {

    // How many of an item were sold, what its lines came to before tax, its
    // share of what every item came to, and on how many documents. A credit
    // note's lines take theirs off. Lines without a name are one item, "Other
    // lines".
    public record Item(String item, BigDecimal quantity, BigDecimal amount, BigDecimal share, long documents) {}
}
