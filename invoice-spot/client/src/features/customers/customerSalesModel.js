import {
  readSortAndPage,
  sortOptions,
  writeSortAndPage,
} from "../../utils/listState";
import { PERIODS, currencyOf } from "../documents/documentFilters";

// Clients › Sales by customer: every client billed in a period, with what
// they paid on it and still owe, as the Reports page's Top clients counts
// them, a page at a time.

// The fields the table sorts by. What was billed, largest first, is the
// default.
export const SORT_FIELDS = {
  billed: { first: "desc", asc: "Least billed", desc: "Most billed" },
  paid: { first: "desc", asc: "Least paid", desc: "Most paid" },
  owed: { first: "desc", asc: "Least owed", desc: "Most owed" },
  name: { first: "asc", asc: "Name A–Z", desc: "Name Z–A" },
};

export const SORT_OPTIONS = sortOptions(SORT_FIELDS);

// Unknown or malformed values fall back to the defaults: every date, the
// business's first currency, and the most billed first.
export const readCustomerSales = (params) => {
  const period = params.get("period");
  return {
    period: PERIODS.some((option) => option.value === period) ? period : "",
    currency: currencyOf(params.get("currency")),
    ...readSortAndPage(params, SORT_FIELDS, "billed"),
  };
};

// The URL for a view's state, leaving out every default.
export const customerSalesSearch = ({ period, currency, ...sortAndPage }) => {
  const params = new URLSearchParams();
  if (period) params.set("period", period);
  if (currency) params.set("currency", currency);
  return writeSortAndPage(params, sortAndPage, SORT_FIELDS, "billed");
};

// What the view asks the server: the period by name, which it works out in
// the business's time zone, the currency when one was chosen, and the order
// and page.
export const customerSalesQuery = ({
  period,
  currency,
  sort,
  dir,
  page,
  size,
}) => ({
  ...(period && { period }),
  ...(currency && { currency }),
  sort,
  dir,
  page,
  size,
});
