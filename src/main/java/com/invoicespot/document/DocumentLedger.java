package com.invoicespot.document;

// The documents whose money counts: balances, reports, statements, the
// overview and reminders read only these. A draft counts once it is issued,
// and a void document no longer does.
public final class DocumentLedger {

    // For SQL over documents aliased d.
    public static final String COUNTED = "d.issued_at is not null and d.voided_at is null";

    // What is still owed on each invoice: its total less what its credit
    // notes took off, the money received and the tax the client withheld,
    // plus what was paid back.
    public static final String OWED =
            "(d.total - d.total_credited - d.total_amount_received - d.total_tds + d.total_refunded)";

    // What each invoice or receipt billed; a credit note takes its total off.
    public static final String BILLED = "(case when d.document_type = 'Credit Note' then -d.total else d.total end)";

    // The documents what was billed adds up: invoices and receipts, and the
    // credit notes that take money off them.
    public static final String BILLED_DOCUMENT =
            COUNTED + " and d.document_type in ('Invoice', 'Receipt', 'Credit Note')";

    // The invoices with money still owed on them, overdue or not.
    public static final String OWED_INVOICE = COUNTED + " and d.document_type = 'Invoice' and " + OWED + " > 0";

    private DocumentLedger() {}
}
