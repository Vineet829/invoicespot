import { dayMonth, parseDate } from "../../utils/dates";

export const DEFAULT_PERIOD = "12m";

// The last 30 and 90 days, as the overview has them, the last few months,
// this year, then each earlier year since the first invoice, as the server
// lists them.
export const periodOptions = (years = []) => [
  { value: "30d", label: "Last 30 days" },
  { value: "90d", label: "Last 90 days" },
  { value: "3m", label: "Last 3 months" },
  { value: "6m", label: "Last 6 months" },
  { value: "12m", label: "Last 12 months" },
  { value: "ytd", label: "This year" },
  ...years.map((year) => ({ value: String(year), label: String(year) })),
];

// Four equal steps up to a round figure at or above the largest amount.
export const axisTicks = (largest) => {
  const value = Number(largest);
  if (!Number.isFinite(value) || value <= 0) return [0];
  const raw = value / 4;
  const magnitude = 10 ** Math.floor(Math.log10(raw));
  const step =
    [1, 2, 2.5, 5, 10].find((nice) => raw <= nice * magnitude) * magnitude;
  return [0, 1, 2, 3, 4].map((index) => index * step);
};

export { compactAmount } from "../../utils/money";

// A month is sent as "2026-09"; it names that month wherever the reader is.
const firstDay = (month) => {
  const [year, number] = String(month).split("-").map(Number);
  return new Date(Date.UTC(year, number - 1, 1));
};
export const monthName = (month) =>
  firstDay(month).toLocaleDateString("en", {
    month: "short",
    timeZone: "UTC",
  });
export const monthYear = (month) =>
  firstDay(month).toLocaleDateString("en", {
    month: "long",
    year: "numeric",
    timeZone: "UTC",
  });

// A month of a report, named for the days of it that the report's period
// holds: "September 2026" when it holds the whole month, or else as the
// overview names a part month, "Sep 8–30, 2026" or "Oct 1–7, 2026". `from`
// and `to` are the period's first and last days, as the report sends them;
// without them the month is named whole.
export const monthSpan = (month, from, to) => {
  const [year, number] = String(month).split("-").map(Number);
  const first = `${month}-01`;
  const last = `${month}-${new Date(year, number, 0).getDate()}`;
  const start = from > first ? from : first;
  const end = to < last ? to : last;
  if (start === first && end === last) return monthYear(month);
  const days =
    start === end
      ? dayMonth(parseDate(start))
      : `${dayMonth(parseDate(start))}–${parseDate(end).getDate()}`;
  return `${days}, ${year}`;
};

// A month as "2026-09", by the reader's calendar: the one before `today`,
// or the one it is in.
const monthOf = (date) =>
  `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}`;
export const previousMonth = (today = new Date()) =>
  monthOf(new Date(today.getFullYear(), today.getMonth() - 1, 1));
export const currentMonth = (today = new Date()) => monthOf(today);

// The GST helper files are named as the server names them: GSTR-1 tables
// as "gstr1-…", with the month, or the first and last months.
export const gstFileName = (key, from, to) =>
  `invoice-spot-${key === "gstr3b" ? "gstr3b" : `gstr1-${key}`}-${from === to ? from : `${from}-to-${to}`}.csv`;

// A bar's length as a share of the longest one, from 0 to 1.
export const share = (value, largest) => {
  const amount = Number(value);
  const most = Number(largest);
  return most > 0 && amount > 0 ? Math.min(amount / most, 1) : 0;
};

// A month's profit as a share of its income, as a whole percent, or nothing
// when there was no income to measure it against.
export const margin = (profit, income) => {
  const earned = Number(income);
  return earned > 0 ? Math.round((Number(profit) / earned) * 100) : null;
};

// A whole percent for a table; a share too small to round to 1% is "<1%",
// and nothing to show is a dash.
export const percentLabel = (value, exact = value) => {
  if (value == null) return "—";
  if (value === 0 && Number(exact) > 0) return "<1%";
  return `${value.toLocaleString("en")}%`;
};

// The profit and loss months, each with its margin and the profit made so
// far in the period, in cents so that sums don't drift.
export const profitMonths = (months = []) => {
  let soFar = 0;
  return months.map((row) => {
    soFar += Math.round((Number(row.profit) || 0) * 100);
    return {
      ...row,
      margin: margin(row.profit, row.income),
      soFar: soFar / 100,
    };
  });
};

// A part of a total as a whole percent, from 0 to 100.
export const percentOf = (part, total) => {
  const all = Number(total);
  return all > 0 ? Math.round((Number(part) / all) * 100) : 0;
};

// What a currency's tax was charged on: the sales taxed at a rate above
// nothing, how many documents they came from, and what else was sold with
// no tax. A document taxed at two rates is counted at each, never more
// than all the documents.
export const taxedSales = (entry) => {
  const rates = entry?.rates || [];
  const taxed = rates.filter((row) => Number(row.rate) > 0);
  const untaxed = rates.filter((row) => !(Number(row.rate) > 0));
  // Added in cents, so the amounts come out exact.
  const taxable = (rows) =>
    rows.reduce((sum, row) => sum + Math.round(Number(row.taxable) * 100), 0) /
    100;
  const documents = taxed.reduce((sum, row) => sum + row.documents, 0);
  return {
    taxable: taxable(taxed),
    documents: Math.min(documents, entry?.documents ?? 0),
    untaxed: taxable(untaxed),
  };
};
