import {
  readSortAndPage,
  sortOptions,
  writeSortAndPage,
} from "../../utils/listState";
import { countOf } from "../../utils/counts";
import { dateSpan } from "../../utils/dates";
import { PERIODS, currencyOf, periodQuery } from "../documents/documentFilters";
import { amountsOf } from "../documents/listTotals";
import { PAYMENT_METHODS } from "../documents/paymentMethods";

// The Payments list's search, filters, sort and page, as kept in the URL. It
// has the Documents list's periods and currencies.

// A receipt is paid as it is issued, so it has no method of its own; the
// method filter names receipts with this instead.
export const RECEIPT = "receipt";

// The fields the list sorts by. Customers sort from A to Z only.
export const SORT_FIELDS = {
  date: { first: "desc", asc: "Oldest first", desc: "Newest first" },
  amount: { first: "desc", asc: "Lowest amount", desc: "Highest amount" },
  client: { first: "asc", asc: "Customer A–Z" },
};

export const SORT_OPTIONS = sortOptions(SORT_FIELDS).filter(
  (option) => option.label,
);

// Unknown or malformed values fall back to the defaults, and so does an
// order the menu doesn't offer.
export const readPaymentList = (params) => {
  const method = params.get("method");
  const period = params.get("period");
  const sortAndPage = readSortAndPage(params, SORT_FIELDS, "date");
  return {
    q: params.get("q") || "",
    method:
      method === RECEIPT || PAYMENT_METHODS.includes(method) ? method : "",
    period: PERIODS.some((option) => option.value === period) ? period : "",
    currency: currencyOf(params.get("currency")),
    ...sortAndPage,
    ...(!SORT_FIELDS[sortAndPage.sort][sortAndPage.dir] && {
      sort: "date",
      dir: "desc",
    }),
  };
};

// The URL for a list state, leaving out every default.
export const paymentListSearch = ({
  q,
  method,
  period,
  currency,
  ...sortAndPage
}) => {
  const params = new URLSearchParams();
  if (q) params.set("q", q);
  if (method) params.set("method", method);
  if (period) params.set("period", period);
  if (currency) params.set("currency", currency);
  return writeSortAndPage(params, sortAndPage, SORT_FIELDS, "date");
};

export const isPaymentListFiltered = ({ q, method, period, currency }) =>
  Boolean(q || method || period || currency);

// The query the server's payments list takes for a list state.
export const paymentQuery = (
  { q, method, period, currency, sort, dir, page, size },
  today = new Date(),
) => ({
  page,
  size,
  sort,
  dir,
  ...(q && { q }),
  ...(method && { method }),
  ...periodQuery(period, today),
  ...(currency && { currency }),
});

// The methods to filter by: those the business has had payments by, one an
// old link names, then receipts.
export const methodOptions = (methods = [], chosen = "") => [
  { value: "", label: "All methods" },
  ...[
    ...methods,
    ...(chosen && chosen !== RECEIPT && !methods.includes(chosen)
      ? [chosen]
      : []),
  ].map((method) => ({ value: method, label: method })),
  { value: RECEIPT, label: "On a receipt" },
];

// What the Method column says. A receipt was paid as it was issued, and a
// refund names how the money went back.
export const methodLabel = (row) =>
  row.kind === RECEIPT
    ? "On a receipt"
    : row.kind === "refund"
      ? ["Refund", row.method].filter(Boolean).join(" · ")
      : row.method || "—";

// The line above a filtered list: how many payments it holds, from the days
// the server applied, what came in, less refunds, and how many of them were
// receipts, unless the list keeps to receipts. Amounts name their currency
// when the business bills in more than one.
export const paymentResults = (list, data, currencies = []) => {
  const totals = data?.totals || [];
  const several = currencies.length > 1 || totals.length > 1;
  const received = amountsOf(totals, "received", { several, all: true });
  const receipts =
    list.method === RECEIPT
      ? 0
      : totals.reduce((sum, entry) => sum + (Number(entry.receipts) || 0), 0);
  return [
    {
      strong: countOf(data?.total ?? 0, "payment", "payments"),
      after:
        data?.from && data?.to ? ` from ${dateSpan(data.from, data.to)}` : "",
    },
    received && { before: "Received ", strong: received },
    receipts > 0 && {
      after:
        receipts === 1
          ? "1 of them on a receipt"
          : `${receipts.toLocaleString("en")} of them on receipts`,
    },
  ].filter(Boolean);
};
