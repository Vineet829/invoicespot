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
