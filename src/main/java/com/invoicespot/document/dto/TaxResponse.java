package com.invoicespot.document.dto;

import com.invoicespot.document.TaxSummary;
import java.math.BigDecimal;

// The tax a document charges at one rate, split into CGST and SGST or IGST
// under India's GST.
public record TaxResponse(
        BigDecimal rate, BigDecimal taxable, BigDecimal tax, BigDecimal cgst, BigDecimal sgst, BigDecimal igst) {

    public static TaxResponse from(TaxSummary summary) {
        return new TaxResponse(
                BillingItemResponse.plain(summary.getRate()),
                summary.getTaxable(),
                summary.getTax(),
                summary.getCgst(),
                summary.getSgst(),
                summary.getIgst());
    }
}
