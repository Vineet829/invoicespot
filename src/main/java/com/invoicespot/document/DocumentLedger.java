package com.invoicespot.document;

// The documents whose money counts: balances, reports, statements, the
// overview and reminders read only these. A draft counts once it is issued,
// and a void document no longer does.
public final class DocumentLedger {

    // For SQL over documents aliased d.
    public static final String COUNTED = "d.issued_at is not null and d.voided_at is null";

    private DocumentLedger() {}
}
