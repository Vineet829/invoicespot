package com.invoicespot.tax;

// How a business charges tax: one rate per line, or India's GST split by
// where the supply goes.
public enum TaxRegime {
    STANDARD,
    GST_INDIA
}
