package com.invoicespot.document.dto;

import java.math.BigDecimal;
import java.util.List;

public record TaxRateSuggestionsResponse(boolean success, List<Rate> rates, List<String> units) {

    // A rate, and how many lines and items use it.
    public record Rate(BigDecimal rate, long uses) {}
}
