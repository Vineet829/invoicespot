import { readSortAndPage, writeSortAndPage } from "../../utils/listState";
import { PERIODS, currencyOf } from "../documents/documentFilters";

// Items › Sales by item: what each item sold in a period, as the overview's
// Top items counts it, with no limit, and a page for each item's sales: who
// bought it, and each line it was sold on.

// Lines saved without a name are added up under this one, whose page lists
// those lines. An item's name is as its lines spell it.
export const OTHER_LINES = "Other lines";
export const itemName = (item) =>
  (typeof item === "string" && item.trim()) || OTHER_LINES;

const periodOf = (params) => {
  const period = params.get("period");
  return PERIODS.some((option) => option.value === period) ? period : "";
};

// The items are listed as the server ranks them, so only the page and its
// size come from the shared list helpers.
const BY_AMOUNT = { amount: { first: "desc" } };

// Unknown or malformed values fall back to the defaults: every date, and
// the business's first currency.
export const readItemSales = (params) => {
  const { page, size } = readSortAndPage(params, BY_AMOUNT, "amount");
  return {
    period: periodOf(params),
    currency: currencyOf(params.get("currency")),
    page,
    size,
  };
};

// The URL for the list's state, leaving out every default.
export const itemSalesSearch = ({ period, currency, page, size }) => {
  const params = new URLSearchParams();
  if (period) params.set("period", period);
  if (currency) params.set("currency", currency);
  return writeSortAndPage(
    params,
    { sort: "amount", dir: "desc", page, size },
    BY_AMOUNT,
    "amount",
  );
};

// What the list asks the server: the period by name, which it works out in
// the business's time zone, the currency when one was chosen, and the page.
export const itemSalesQuery = ({ period, currency, page, size }) => ({
  ...(period && { period }),
  ...(currency && { currency }),
  page,
  size,
});

// An item's page: the item's name, as the lines name it, and the period
// and currency of the list it was opened from.
export const readItemDetail = (params) => ({
  name: (params.get("name") || "").trim(),
  period: periodOf(params),
  currency: currencyOf(params.get("currency")),
});

export const itemDetailSearch = ({ name, period, currency }) => {
  const params = new URLSearchParams();
  if (name) params.set("name", name);
  if (period) params.set("period", period);
  if (currency) params.set("currency", currency);
  return params;
};

export const itemDetailQuery = ({ name, period, currency }) => ({
  name,
  ...(period && { period }),
  ...(currency && { currency }),
});

// The page of an item's sales over a period, in a currency when one was
// chosen.
export const itemSalesLink = (name, period, currency) =>
  `/items/sales/item?${itemDetailSearch({ name, period, currency })}`;

// An item's share of the income, as a whole percentage, with a share too
// small to round to 1% shown as under it, as the overview's Top items does.
export const shareLabel = (share) => {
  const percent = Number(share) > 0 ? Number(share) * 100 : 0;
  return percent > 0 && percent < 0.5 ? "<1%" : `${Math.round(percent)}%`;
};

// How many of an item were sold, to two places at most.
export const soldCount = (quantity) =>
  Number(quantity || 0).toLocaleString("en", { maximumFractionDigits: 2 });
