package com.invoicespot.expense;

// What expenses cost the business, and what was paid for them. Profit and
// loss counts the cost: what each cost before tax, and the tax paid on it
// unless that can be claimed back; the reports and the overview's profit add
// expenses up this way. Money going out counts what was paid, tax and all, as
// each expense's total does: the expenses list adds expenses up this way, and
// so does the overview's money out.
public final class ExpenseLedger {

    // For SQL over expenses aliased e.
    public static final String SPENT = "(e.amount + case when e.tax_reclaimable then 0 else e.tax_amount end)";

    // What was paid, with the tax on it whether or not it can be claimed back.
    public static final String PAID = "(e.amount + e.tax_amount)";

    private ExpenseLedger() {}
}
