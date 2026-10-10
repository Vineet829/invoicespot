package com.invoicespot.tax;

// Where a GST supply goes: within the seller's state (CGST and SGST), to
// another state (IGST), or out of India under a letter of undertaking (no
// tax) or with IGST paid.
public enum SupplyType {
    INTRA,
    INTER,
    EXPORT_LUT,
    EXPORT_IGST;

    public boolean export() {
        return this == EXPORT_LUT || this == EXPORT_IGST;
    }
}
