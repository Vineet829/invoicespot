import { formatAmount } from "../../utils/money";

// An amount from each currency's totals in a list's result line: as
// "68,848.08", or as "1,200.00 USD, 500.00 EUR" when the business bills in
// several currencies. The server adds up each currency apart. Only amounts
// above zero are named, unless `all` asks for every one.
export const amountsOf = (
  totals = [],
  key,
  { several = totals.length > 1, all = false } = {},
) =>
  totals
    .filter((entry) => all || Number(entry[key]) > 0)
    .map((entry) => {
      const amount = formatAmount(entry[key], entry.currency);
      return several
        ? `${amount} ${entry.currency || "with no currency"}`
        : amount;
    })
    .join(", ");
