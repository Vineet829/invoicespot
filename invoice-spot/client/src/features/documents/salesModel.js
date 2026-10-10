import { readSortAndPage, writeSortAndPage } from "../../utils/listState";
import { PERIODS, currencyOf, periodQuery } from "./documentFilters";

// The Sales view of Documents: what was billed in a period, as the
// overview's Billed figure counts it, by type and by tax rate, above the
// documents behind the total.

// The documents are listed newest first, so only the page and its size
// come from the shared list helpers.
const NEWEST_FIRST = { createdAt: { first: "desc" } };

// Unknown or malformed values fall back to the defaults: every date, and
// the business's first currency.
export const readSales = (params) => {
  const { page, size } = readSortAndPage(params, NEWEST_FIRST, "createdAt");
  const period = params.get("period");
  return {
    period: PERIODS.some((option) => option.value === period) ? period : "",
    currency: currencyOf(params.get("currency")),
    page,
    size,
  };
};

// The URL for a view's state, leaving out every default.
export const salesSearch = ({ period, currency, page, size }) => {
  const params = new URLSearchParams();
  if (period) params.set("period", period);
  if (currency) params.set("currency", currency);
  return writeSortAndPage(
    params,
    { sort: "createdAt", dir: "desc", page, size },
    NEWEST_FIRST,
    "createdAt",
  );
};

// What the summary asks the server: the period by name, which it works out
// in the business's time zone, and the currency when one was chosen.
export const salesQuery = ({ period, currency }) => ({
  ...(period && { period }),
  ...(currency && { currency }),
});

// The documents behind the total, as the Documents list finds what was
// billed: issued invoices, receipts and credit notes, in the period and the
// total's currency, newest first.
export const billedQuery = (
  { period, page, size },
  currency,
  today = new Date(),
) => ({
  page,
  size,
  sort: "createdAt",
  dir: "desc",
  billed: "true",
  ...periodQuery(period, today),
  ...(currency && { currency }),
});

// Each type of document billed, as the summary names it.
export const TYPE_NAMES = {
  Invoice: "Invoices",
  Receipt: "Receipts",
  "Credit Note": "Credit notes",
};
