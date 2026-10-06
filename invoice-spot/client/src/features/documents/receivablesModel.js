import { readSortAndPage, writeSortAndPage } from "../../utils/listState";
import { parseDate } from "../../utils/dates";
import { currencyOf } from "./documentFilters";

// The Receivables view of Documents: what is owed today, as the overview's
// Total receivables splits it, coming in or overdue, above the invoices
// behind each figure.

// The two modes, picked by a pill pair. Coming in is the default, so only
// Overdue is written to the address.
export const MODES = [
  { value: "coming-in", label: "Coming in" },
  { value: "overdue", label: "Overdue" },
];

// What is owed past its due date, in the bands the server splits it into by
// days late, each in a rust that deepens with age.
export const AGE_BANDS = [
  { key: "days1to15", label: "1–15 days", color: "bg-owed-age-1" },
  { key: "days16to30", label: "16–30 days", color: "bg-owed-age-2" },
  { key: "days31to45", label: "31–45 days", color: "bg-owed-age-3" },
  { key: "over45", label: "Over 45 days", color: "bg-owed-age-4" },
];

// What is not due yet, by how soon it falls due, in the not-due lavender,
// paler the further off it is. An invoice with no due date counts in the
// first band.
export const DUE_BANDS = [
  { key: "dueIn0to15", label: "Due in 0–15 days", color: "bg-owed-due" },
  { key: "dueIn16to30", label: "Due in 16–30 days", color: "bg-owed-due/70" },
  { key: "dueIn31to45", label: "Due in 31–45 days", color: "bg-owed-due/45" },
  { key: "dueAfter45", label: "Due after 45 days", color: "bg-owed-due/25" },
];

// A group's bands by key; one the server left out has nothing in it.
export const bandsOf = (group) =>
  Object.fromEntries((group?.bands || []).map((band) => [band.key, band]));

// The invoices are always listed by due date, so only the page and its size
// come from the shared list helpers.
const BY_DUE_DATE = { dueDate: { first: "asc" } };

// Unknown or malformed values fall back to the defaults.
export const readReceivables = (params) => {
  const { page, size } = readSortAndPage(params, BY_DUE_DATE, "dueDate");
  return {
    mode: params.get("mode") === "overdue" ? "overdue" : "coming-in",
    currency: currencyOf(params.get("currency")),
    page,
    size,
  };
};

// The URL for a view's state, leaving out every default.
export const receivablesSearch = ({ mode, currency, page, size }) => {
  const params = new URLSearchParams();
  if (mode === "overdue") params.set("mode", mode);
  if (currency) params.set("currency", currency);
  return writeSortAndPage(
    params,
    { sort: "dueDate", dir: "asc", page, size },
    BY_DUE_DATE,
    "dueDate",
  );
};

// The invoices behind a mode's figure, as the documents list finds them, in
// the figure's currency: those owed and overdue, most late first, or those
// owed and not due yet, soonest first. Asking for what is owed as well as
// overdue keeps the rows to exactly the invoices the Overdue figure counts.
// Both are by due date, so those with no due date come last.
export const receivablesQuery = ({ mode, page, size }, currency) => ({
  page,
  size,
  sort: "dueDate",
  dir: "asc",
  ...(mode === "overdue"
    ? { overdue: "true", outstanding: "true" }
    : { outstanding: "true", due: "current" }),
  ...(currency && { currency }),
});

// Whole days from today to a due date, by the reader's calendar: below zero
// once it has passed.
const DAY = 24 * 60 * 60 * 1000;
export const daysUntil = (dueDate, today = new Date()) => {
  const start = new Date(
    today.getFullYear(),
    today.getMonth(),
    today.getDate(),
  );
  return Math.round((parseDate(dueDate) - start) / DAY);
};

// How late an overdue invoice is. The server counts days by the business's
// clock, so an invoice it calls overdue is at least a day late here too.
export const lateLabel = (dueDate, today) => {
  const days = Math.max(1, -daysUntil(dueDate, today));
  return `${days.toLocaleString("en")} ${days === 1 ? "day" : "days"} late`;
};

// When an invoice not yet due falls due, as the overview's attention list
// words it; one with no due date says so.
export const dueLabel = (dueDate, today) => {
  if (!dueDate) return "No due date";
  const days = Math.max(0, daysUntil(dueDate, today));
  if (days === 0) return "Due today";
  if (days === 1) return "Due tomorrow";
  return `Due in ${days.toLocaleString("en")} days`;
};
