package com.invoicespot.report.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.YearMonth;

// What was taxed at one rate, in a month or over the whole period: the value
// and the tax charged on it, split as GST was charged, and the value of
// supplies whose tax the client pays under reverse charge. Credit notes are
// taken off.
public record TaxTotals(
        @JsonInclude(JsonInclude.Include.NON_NULL) YearMonth month,
        BigDecimal rate,
        long documents,
        BigDecimal taxable,
        BigDecimal tax,
        BigDecimal cgst,
        BigDecimal sgst,
        BigDecimal igst,
        BigDecimal reverseChargeTaxable) {}
