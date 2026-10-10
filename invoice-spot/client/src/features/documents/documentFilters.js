import {
  readSortAndPage,
  sortOptions,
  writeSortAndPage,
} from "../../utils/listState";
import { countOf } from "../../utils/counts";
import { dateSpan } from "../../utils/dates";
import { amountsOf } from "./listTotals";

// The Documents list's search, filters, sort and page, as kept in the URL.

// Payment statuses are an issued document's; drafts and void documents have
// filters of their own. Outstanding is every issued invoice with money still
// owed on it, overdue or not.
export const STATUS_FILTERS = [
  { value: "", label: "All" },
  { value: "outstanding", label: "Outstanding" },
  { value: "unpaid", label: "Unpaid", status: "Not Paid" },
  { value: "partial", label: "Partially paid", status: "Not Fully Paid" },
  { value: "paid", label: "Paid", status: "Paid" },
  { value: "overdue", label: "Overdue" },
  { value: "drafts", label: "Drafts", state: "draft" },
  { value: "void", label: "Void", state: "void" },
];

// Billed is what the overview's Billed adds up: issued invoices, receipts
// and credit notes.
export const TYPE_FILTERS = [
  { value: "", label: "All types" },
  { value: "billed", label: "Billed: invoices, receipts and credit notes" },
  { value: "invoice", label: "Invoices", type: "Invoice" },
  { value: "quotation", label: "Quotations", type: "Quotation" },
  { value: "receipt", label: "Receipts", type: "Receipt" },
  { value: "credit-note", label: "Credit notes", type: "Credit Note" },
];

// Periods of issue dates. The last 30 or 90 days and the last 12 months are
// the overview's, so the server works out their days, in the business's time
// zone. The others are picked here from the business's today, which the
// pages pass in, and the server reads them in the same time zone.
export const PERIODS = [
  { value: "", label: "All dates" },
  { value: "30d", label: "Last 30 days", recent: true },
  { value: "90d", label: "Last 90 days", recent: true },
  { value: "12m", label: "Last 12 months", recent: true },
  { value: "this-month", label: "This month" },
  { value: "last-month", label: "Last month" },
  { value: "this-quarter", label: "This quarter" },
  { value: "this-year", label: "This year" },
  { value: "last-year", label: "Last year" },
];

// Due dates by the week, counted from today; later includes invoices with
// no due date. Only the address sets one, so it shows as a filter to remove
// rather than in a menu.
export const DUE_FILTERS = [
  { value: "this-week", label: "Due this week" },
  { value: "next-week", label: "Due next week" },
  { value: "week-after", label: "Due the week after" },
  { value: "later", label: "Due later" },
];

const localDay = (date) =>
  [
    date.getFullYear(),
    String(date.getMonth() + 1).padStart(2, "0"),
    String(date.getDate()).padStart(2, "0"),
  ].join("-");

// The first and last day of a period, as the server's from and to dates.
// This month, quarter and year run to today, as the server's views count
// them; last month and last year are whole.
export const periodRange = (period, today) => {
  const year = today.getFullYear();
  const month = today.getMonth();
  const quarter = month - (month % 3);
  const [from, to] =
    {
      "this-month": [new Date(year, month, 1), today],
      "last-month": [new Date(year, month - 1, 1), new Date(year, month, 0)],
      "this-quarter": [new Date(year, quarter, 1), today],
      "this-year": [new Date(year, 0, 1), today],
      "last-year": [new Date(year - 1, 0, 1), new Date(year - 1, 11, 31)],
    }[period] || [];
  return from ? { from: localDay(from), to: localDay(to) } : {};
};

// What the server is sent for a period: the overview's periods by name, the
// others as from and to dates.
export const periodQuery = (period, today) =>
  PERIODS.find((option) => option.value === period)?.recent
    ? { period }
    : periodRange(period, today);

// The fields the list sorts by. Status has filters instead: stored
// statuses would sort alphabetically, not by payment.
export const SORT_FIELDS = {
  createdAt: { first: "desc", asc: "Oldest first", desc: "Newest first" },
  dueDate: { first: "asc", asc: "Due soonest", desc: "Due latest" },
  total: { first: "desc", asc: "Lowest amount", desc: "Highest amount" },
  client: { first: "asc", asc: "Customer A–Z", desc: "Customer Z–A" },
  number: { first: "asc", asc: "Number A–Z", desc: "Number Z–A" },
};

export const SORT_OPTIONS = sortOptions(SORT_FIELDS);

const oneOf = (value, filters) =>
  filters.some((filter) => filter.value === value) ? value : "";

// A currency is a three-letter code, as documents keep it.
export const currencyOf = (value) =>
  /^[A-Z]{3}$/.test(value || "") ? value : "";

// Unknown or malformed values fall back to the defaults.
export const readListState = (params) => ({
  q: params.get("q") || "",
  status: oneOf(params.get("status"), STATUS_FILTERS),
  due: oneOf(params.get("due"), DUE_FILTERS),
  type: oneOf(params.get("type"), TYPE_FILTERS),
  period: oneOf(params.get("period"), PERIODS),
  currency: currencyOf(params.get("currency")),
  ...readSortAndPage(params, SORT_FIELDS, "createdAt"),
});

// The URL for a list state, leaving out every default.
export const listSearch = ({
  q,
  status,
  due,
  type,
  period,
  currency,
  ...sortAndPage
}) => {
  const params = new URLSearchParams();
  if (q) params.set("q", q);
  if (status) params.set("status", status);
  if (due) params.set("due", due);
  if (type) params.set("type", type);
  if (period) params.set("period", period);
  if (currency) params.set("currency", currency);
  return writeSortAndPage(params, sortAndPage, SORT_FIELDS, "createdAt");
};

export const isFiltered = ({ q, status, due, type, period, currency }) =>
  Boolean(q || status || due || type || period || currency);

// The query the server's document list takes for a list state.
// The list's filters and order, as the server reads them; an export of the
// list sends these without a page.
export const documentFilterQuery = (
  { q, status, due, type, period, currency, sort, dir },
  today = new Date(),
) => {
  const statusFilter = STATUS_FILTERS.find((filter) => filter.value === status);
  const typeFilter = TYPE_FILTERS.find((filter) => filter.value === type);
  return {
    sort,
    dir,
    ...(q && { q }),
    ...(statusFilter?.status && { status: statusFilter.status }),
    ...(statusFilter?.state && { state: statusFilter.state }),
    ...(status === "overdue" && { overdue: "true" }),
    ...(status === "outstanding" && { outstanding: "true" }),
    ...(due && { due }),
    ...(typeFilter?.type && { type: typeFilter.type }),
    ...(type === "billed" && { billed: "true" }),
    ...periodQuery(period, today),
    ...(currency && { currency }),
  };
};

export const documentQuery = (list, today = new Date()) => ({
  page: list.page,
  size: list.size,
  ...documentFilterQuery(list, today),
});

// The line above a filtered list: how many documents it holds, the days of
// issue the server applied, and what they add up to, from the totals it
// sends with each page. Outstanding invoices are named by what is owed on
// them and how much of it is overdue. Amounts name their currency when the
// business bills in more than one.
export const documentResults = (list, data, currencies = []) => {
  const totals = data?.totals || [];
  const count = data?.totalDocuments ?? 0;
  const several = currencies.length > 1 || totals.length > 1;
  const issued =
    data?.issuedFrom && data?.issuedTo
      ? ` issued ${dateSpan(data.issuedFrom, data.issuedTo)}`
      : "";
  const owed = list.status === "outstanding";
  const billed = !owed && amountsOf(totals, "billed", { several });
  const balanceDue = amountsOf(totals, "balanceDue", { several });
  const overdue = owed && amountsOf(totals, "overdue", { several });
  const late = totals.reduce(
    (sum, entry) => sum + (Number(entry.overdueDocuments) || 0),
    0,
  );
  return [
    owed
      ? {
          strong: countOf(count, "invoice", "invoices"),
          after: ` with money owed${issued && `,${issued}`}`,
        }
      : { strong: countOf(count, "document", "documents"), after: issued },
    billed && { before: "Billed ", strong: billed },
    balanceDue && { before: "Balance due ", strong: balanceDue },
    overdue && {
      after: `${overdue} overdue on ${late.toLocaleString("en")}`,
      tone: "overdue",
    },
  ].filter(Boolean);
};
