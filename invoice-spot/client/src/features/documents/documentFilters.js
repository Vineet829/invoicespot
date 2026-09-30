import {
  readSortAndPage,
  sortOptions,
  writeSortAndPage,
} from "../../utils/listState";

// The Documents list's search, filters, sort and page, as kept in the URL.

export const STATUS_FILTERS = [
  { value: "", label: "All" },
  { value: "unpaid", label: "Unpaid", status: "Not Paid" },
  { value: "partial", label: "Partially paid", status: "Not Fully Paid" },
  { value: "paid", label: "Paid", status: "Paid" },
  { value: "overdue", label: "Overdue" },
];

export const TYPE_FILTERS = [
  { value: "", label: "All types" },
  { value: "invoice", label: "Invoices", type: "Invoice" },
  { value: "quotation", label: "Quotations", type: "Quotation" },
  { value: "receipt", label: "Receipts", type: "Receipt" },
];

// Periods of issue dates. The browser's calendar picks the days, and the
// server reads them in the business's time zone.
export const PERIODS = [
  { value: "", label: "All dates" },
  { value: "this-month", label: "This month" },
  { value: "last-month", label: "Last month" },
  { value: "this-quarter", label: "This quarter" },
  { value: "this-year", label: "This year" },
  { value: "last-year", label: "Last year" },
];

const localDay = (date) =>
  [
    date.getFullYear(),
    String(date.getMonth() + 1).padStart(2, "0"),
    String(date.getDate()).padStart(2, "0"),
  ].join("-");

// The first and last day of a period, as the server's from and to dates.
export const periodRange = (period, today) => {
  const year = today.getFullYear();
  const month = today.getMonth();
  const quarter = month - (month % 3);
  const [from, to] =
    {
      "this-month": [new Date(year, month, 1), new Date(year, month + 1, 0)],
      "last-month": [new Date(year, month - 1, 1), new Date(year, month, 0)],
      "this-quarter": [
        new Date(year, quarter, 1),
        new Date(year, quarter + 3, 0),
      ],
      "this-year": [new Date(year, 0, 1), new Date(year, 11, 31)],
      "last-year": [new Date(year - 1, 0, 1), new Date(year - 1, 11, 31)],
    }[period] || [];
  return from ? { from: localDay(from), to: localDay(to) } : {};
};

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

// Unknown or malformed values fall back to the defaults.
export const readListState = (params) => ({
  q: params.get("q") || "",
  status: oneOf(params.get("status"), STATUS_FILTERS),
  type: oneOf(params.get("type"), TYPE_FILTERS),
  period: oneOf(params.get("period"), PERIODS),
  ...readSortAndPage(params, SORT_FIELDS, "createdAt"),
});

// The URL for a list state, leaving out every default.
export const listSearch = ({ q, status, type, period, ...sortAndPage }) => {
  const params = new URLSearchParams();
  if (q) params.set("q", q);
  if (status) params.set("status", status);
  if (type) params.set("type", type);
  if (period) params.set("period", period);
  return writeSortAndPage(params, sortAndPage, SORT_FIELDS, "createdAt");
};

export const isFiltered = ({ q, status, type, period }) =>
  Boolean(q || status || type || period);

// The query the server's document list takes for a list state.
// The list's filters and order, as the server reads them; an export of the
// list sends these without a page.
export const documentFilterQuery = (
  { q, status, type, period, sort, dir },
  today = new Date(),
) => {
  const statusFilter = STATUS_FILTERS.find((filter) => filter.value === status);
  const typeFilter = TYPE_FILTERS.find((filter) => filter.value === type);
  return {
    sort,
    dir,
    ...(q && { q }),
    ...(statusFilter?.status && { status: statusFilter.status }),
    ...(status === "overdue" && { overdue: "true" }),
    ...(typeFilter?.type && { type: typeFilter.type }),
    ...periodRange(period, today),
  };
};

export const documentQuery = (list, today = new Date()) => ({
  page: list.page,
  size: list.size,
  ...documentFilterQuery(list, today),
});
