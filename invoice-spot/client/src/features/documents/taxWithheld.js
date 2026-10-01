// Tax a client withholds from a payment (TDS) and pays to the tax office for
// you. It settles the invoice like money received, so the payment forms ask
// for it beside the amount paid.
export const TAX_WITHHELD_LABEL = "Tax withheld by client (TDS)";
export const TAX_WITHHELD_HINT =
  "Paid to the tax office for you. It counts toward the balance.";

// An amount typed into a form, to the cent, so limits add up exactly.
export const cents = (value) => Math.round(Number(value || 0) * 100) / 100;
