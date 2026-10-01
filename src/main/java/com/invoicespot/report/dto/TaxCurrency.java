package com.invoicespot.report.dto;

import java.math.BigDecimal;
import java.util.List;

// One currency's tax in the period, in all and per rate, and per month and
// rate. `gst` says whether any of it was charged in India GST mode.
public record TaxCurrency(
        String currency,
        boolean gst,
        long documents,
        BigDecimal taxable,
        BigDecimal tax,
        BigDecimal cgst,
        BigDecimal sgst,
        BigDecimal igst,
        BigDecimal reverseChargeTaxable,
        List<TaxTotals> rates,
        List<TaxTotals> months) {}
