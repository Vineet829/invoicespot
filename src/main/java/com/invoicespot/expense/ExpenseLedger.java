package com.invoicespot.expense;

// What expenses cost the business, as profit and loss counts it: what each
// cost before tax, and the tax paid on it unless that can be claimed back. The
// reports, the overview and the expenses list add expenses up this way.
public final class ExpenseLedger {

    // For SQL over expenses aliased e.
    public static final String SPENT = "(e.amount + case when e.tax_reclaimable then 0 else e.tax_amount end)";

    private ExpenseLedger() {}
}
