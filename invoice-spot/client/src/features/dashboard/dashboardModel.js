import { dateSpan, dayMonth, dayMonthYear, parseDate } from "../../utils/dates";
import { compactAmount, formatAmount } from "../../utils/money";
import {
  documentsLink,
  expensesLink,
  paymentsLink,
} from "../documents/listLinks";
import { share } from "../reports/reportModel";
const asAmount = (value) =>
  Number.isFinite(Number(value)) ? Number(value) : 0;

export const formatMoney = (value, currency) =>
  formatAmount(asAmount(value), currency);

// The periods the overview can show, as its pills name them. The last 90
// days is the default, so it stays out of the address. Each names the same
// period on the Reports page, where the last 12 months is the default, and
// on the Documents, Payments and Expenses lists. `these` refers back to the
// period the subtitle names.
export const DEFAULT_PERIOD = "90d";
export const PERIODS = [
  {
    key: "30d",
    label: "30 days",
    name: "Last 30 days",
    called: "the last 30 days",
    during: "in the last 30 days",
    these: "these 30 days",
    inThese: "in these 30 days",
    compared: "vs previous 30 days",
    report: "30d",
    list: "30d",
  },
  {
    key: "90d",
    label: "90 days",
    name: "Last 90 days",
    called: "the last 90 days",
    during: "in the last 90 days",
    these: "these 90 days",
    inThese: "in these 90 days",
    compared: "vs previous 90 days",
    report: "90d",
    list: "90d",
  },
  {
    key: "12m",
    label: "12 months",
    name: "Last 12 months",
    called: "the last 12 months",
    during: "in the last 12 months",
    these: "these 12 months",
    inThese: "in these 12 months",
    compared: "vs previous 12 months",
    list: "12m",
  },
  {
    key: "ytd",
    label: "This year",
    name: "This year",
    called: "this year",
    during: "this year",
    these: "this year",
    inThese: "this year",
    compared: "vs same period last year",
    report: "ytd",
    list: "this-year",
  },
];
// A period from the address; anything else is the default.
export const findPeriod = (key) =>
  PERIODS.find((period) => period.key === key) ||
  PERIODS.find((period) => period.key === DEFAULT_PERIOD);

// A section of the Reports page, for the period shown here when one is
// given and Reports has it, and in the currency when one was chosen.
export const reportLink = (section, period, currency) => {
  const search = new URLSearchParams();
  const report = period && findPeriod(period).report;
  if (report) search.set("period", report);
  if (currency) search.set("currency", currency);
  return `/reports${search.toString() ? `?${search}` : ""}#${section}`;
};

// The records behind the overview's figures, for the period shown and, when
// one was chosen, the currency: what was billed and the payments received.
export const billedLink = (period, currency) =>
  documentsLink({ type: "billed", period: findPeriod(period).list, currency });
export const receivedLink = (period, currency) =>
  paymentsLink(findPeriod(period).list, currency);
// The expenses behind Spending, for the period shown, in the currency its
// figures count: a category's, or every one without a category. Recording
// an expense from it opens the add dialog over that currency's expenses.
export const spendingLink = (period, currency, category) =>
  expensesLink({ category, period: findPeriod(period).list, currency });
export const recordExpenseLink = (currency) =>
  expensesLink({ new: "1", currency });

// Spending by category as the panel lists it: the largest categories, as
// the server sends them, then the rest as one row when there are more.
// Each row has its share of the total, with a share too small to round to
// 1% shown as under it, and a bar against the largest row.
const shareOfTotal = (amount, total) => {
  const percent = total > 0 && amount > 0 ? (amount / total) * 100 : 0;
  return percent > 0 && percent < 0.5 ? "<1%" : `${Math.round(percent)}%`;
};
export const spendingRows = (spending) => {
  const rows = [
    ...(spending?.categories || []),
    ...(spending?.others ? [{ ...spending.others, others: true }] : []),
  ];
  const largest = Math.max(0, ...rows.map((row) => asAmount(row.amount)));
  return rows.map((row) => ({
    ...row,
    share: shareOfTotal(asAmount(row.amount), asAmount(spending.total)),
    bar: share(row.amount, largest),
  }));
};

// The clients billed the most in the period, largest first, each with a bar
// against the largest. A client's name opens the client; documents saved
// without a linked client are added up under their saved name, which opens
// nothing.
export const topClientRows = (topClients = []) => {
  const largest = Math.max(0, ...topClients.map((row) => asAmount(row.billed)));
  return topClients.map((row) => ({
    ...row,
    bar: share(row.billed, largest),
    to: row.clientId ? `/single-customer/${row.clientId}` : null,
  }));
};

// The items that brought in the most in the period, before tax, each with a
// bar against the largest and its share of the income behind Profit, which
// every item sold adds up to. An item opens the Items list searched for it.
// Lines saved without a name are added up as other lines, which open
// nothing.
const OTHER_LINES = "Other lines";
export const itemLink = (item) => `/items?${new URLSearchParams({ q: item })}`;
export const topItemRows = (topItems = [], itemsTotal) => {
  const largest = Math.max(0, ...topItems.map((row) => asAmount(row.amount)));
  const income = asAmount(itemsTotal);
  return topItems.map((row) => {
    const item = typeof row.item === "string" ? row.item.trim() : "";
    const named = item !== "" && item !== OTHER_LINES;
    return {
      ...row,
      name: named ? item : OTHER_LINES,
      bar: share(row.amount, largest),
      share: income > 0 ? shareOfTotal(asAmount(row.amount), income) : null,
      to: named ? itemLink(item) : null,
    };
  });
};

// What is coming up. Each of the next recurring invoices opens the
// recurring list; its template is named by its number, or as a draft, which
// has none yet. Each quotation waiting for an answer opens the quotation,
// named with its last valid day, or as having none: one without a date
// waits too, without a count of days. Those past their day open the
// quotations, sorted by their last valid day. The quotations' figures count
// the overview's currency, so their lists keep to it when one was chosen.
export const recurringLink = "/recurring";
export const quotationsLink = (currency) =>
  documentsLink({ type: "quotation", currency });
export const expiredQuotationsLink = (currency) =>
  documentsLink({ type: "quotation", currency, sort: "dueDate", dir: "asc" });
const dayOf = (value) => (value ? dayMonth(parseDate(value)) : "");
export const runRows = (nextRuns = []) =>
  nextRuns.map((run) => ({
    ...run,
    day: dayOf(run.date),
    number: run.documentNumber || "Draft invoice",
  }));
export const awaitingRows = (items = []) =>
  items.map((item) => ({
    ...item,
    until: item.validUntil
      ? `valid until ${dayOf(item.validUntil)}`
      : "No expiry",
    to: `/document/${item.documentId}`,
  }));

// Counts of invoices are worded as on the Documents list.
export { invoiceCount } from "../../utils/counts";

// Spans of days are named as on the Documents and Payments lists.
export { dateSpan };

// The line under the overview's title: the period and its days, then the
// days it is compared with, when there were figures before it. Those keep
// their year only when it isn't this period's.
export const periodSubtitle = (period, hasPrevious) => {
  const current = `${findPeriod(period.key).name}, ${dateSpan(period.from, period.to)}`;
  if (!hasPrevious) return current;
  const sameYear =
    parseDate(period.previousTo).getFullYear() ===
    parseDate(period.to).getFullYear();
  return `${current} · compared with ${dateSpan(period.previousFrom, period.previousTo, !sameYear)}`;
};

// A figure against the same figure for the period before, as a percentage
// of the earlier one, rounded to a tenth. An earlier figure of zero leaves
// nothing to compare with.
export const changeFrom = (current, previous) => {
  const now = Number(current);
  const before = Number(previous);
  if (!Number.isFinite(now) || !Number.isFinite(before) || before === 0) {
    return null;
  }
  const ratio = (now - before) / Math.abs(before);
  const percent = Math.round(Math.abs(ratio) * 1000) / 10;
  return {
    direction: percent === 0 ? "flat" : ratio > 0 ? "up" : "down",
    percent,
  };
};
export const percentLabel = (percent) =>
  `${percent.toLocaleString("en", {
    minimumFractionDigits: 1,
    maximumFractionDigits: 1,
  })}%`;

// A small trend line's points in a 100 × 100 box, left to right. The bottom
// is zero, or the lowest value when one is below it; the top leaves a tenth
// of room above the highest. A lone point sits in the middle.
export const trendPoints = (values) => {
  const numbers = values.map(asAmount);
  const high = Math.max(0, ...numbers) * 1.1;
  const low = Math.min(0, ...numbers);
  const last = numbers.length - 1;
  return numbers.map((value, index) => ({
    x: last > 0 ? (index / last) * 100 : 50,
    y: high > low ? ((high - value) / (high - low)) * 100 : 100,
  }));
};

// Columns for a trend that can fall below zero, such as profit: where zero
// is, and each column's top and height, as percentages of the box from its
// top. Columns above zero stand on it; those below hang from it.
export const trendColumns = (values) => {
  const numbers = values.map(asAmount);
  const high = Math.max(0, ...numbers);
  const low = Math.min(0, ...numbers);
  const span = high - low;
  return {
    zero: span ? (high / span) * 100 : 100,
    columns: numbers.map((value) => ({
      value,
      top: span ? ((high - Math.max(value, 0)) / span) * 100 : 100,
      height: span ? (Math.abs(value) / span) * 100 : 0,
    })),
  };
};

// Round steps for an axis, as Reports draws them: 1, 2, 2.5 or 5 times a
// power of ten, in whole amounts.
const niceStep = (raw) => {
  if (!(raw > 1)) return 1;
  const magnitude = 10 ** Math.floor(Math.log10(raw));
  const steps = magnitude >= 10 ? [1, 2, 2.5, 5, 10] : [1, 2, 5, 10];
  return steps.find((nice) => raw <= nice * magnitude) * magnitude;
};

// The cash flow chart's scale, in about four round steps: money in above
// zero and money out below it, each side at least one step, so neither
// side ever vanishes. `at` places a value as a percentage of the height
// from the top. There is no scale when nothing came in or went out.
export const cashFlowScale = (trend) => {
  const high = Math.max(0, ...trend.map((bucket) => asAmount(bucket.moneyIn)));
  const low = Math.max(0, ...trend.map((bucket) => asAmount(bucket.moneyOut)));
  if (!high && !low) return null;
  const step = niceStep((high + low) / 4);
  const top = Math.max(1, Math.ceil(high / step)) * step;
  const bottom = Math.max(1, Math.ceil(low / step)) * step;
  const ticks = [];
  for (let tick = top; tick >= -bottom; tick -= step) ticks.push(tick);
  return {
    ticks,
    span: top + bottom,
    at: (value) => ((top - asAmount(value)) / (top + bottom)) * 100,
  };
};

// An amount rounded to whole units, for labels with little room.
export const wholeAmount = (value, currency) =>
  formatAmount(Math.round(asAmount(value)), currency).replace(/\.\d+$/, "");

// An axis figure in whole amounts, shortened (1.5K, or 1.5L in rupees) once
// the axis reaches a hundred thousand, with a true minus sign.
export const axisLabel = (value, ticks, currency) =>
  (Math.max(...ticks.map(Math.abs)) >= 100000
    ? compactAmount(value, currency)
    : wholeAmount(value, currency)
  ).replace("-", "−");

// A run of days as the overview names it: "Sep 5", "Jul 7–13",
// "Jul 28–Aug 3", or with years when it crosses into a new one.
export const dayRange = (from, to) => {
  const start = parseDate(from);
  const end = parseDate(to);
  if (from === to) return dayMonth(start);
  if (start.getFullYear() !== end.getFullYear()) {
    return `${dayMonthYear(start)} – ${dayMonthYear(end)}`;
  }
  return start.getMonth() === end.getMonth()
    ? `${dayMonth(start)}–${end.getDate()}`
    : `${dayMonth(start)}–${dayMonth(end)}`;
};

// A part of the period under the chart: its first day, or its month.
export const bucketLabel = (bucket, step) =>
  parseDate(bucket.from).toLocaleDateString(
    "en",
    step === "month" ? { month: "short" } : { month: "short", day: "numeric" },
  );

// The days a part of the period covers, as its hover card names them. A
// whole month is named with its year; the month so far by its days.
export const bucketRange = (bucket, step) => {
  if (step !== "month") return dayRange(bucket.from, bucket.to);
  const start = parseDate(bucket.from);
  const end = parseDate(bucket.to);
  const lastDay = new Date(end.getFullYear(), end.getMonth() + 1, 0);
  return start.getDate() === 1 && end.getDate() === lastDay.getDate()
    ? start.toLocaleDateString("en", { month: "long", year: "numeric" })
    : `${dayRange(bucket.from, bucket.to)}, ${end.getFullYear()}`;
};

// How often to name the chart's columns so the names don't crowd: each one
// when there is room for it, otherwise every second, third and so on,
// counted back from the latest so that one is always named.
export const labelStep = (count, width, room) =>
  Math.max(1, Math.ceil(count / Math.max(1, Math.floor(width / room))));
export const showsLabel = (index, count, step) =>
  (count - 1 - index) % step === 0;

// What needs attention, in the overview's words: each kind's label above
// the document's number, and what the footer calls it when there is none
// of it, or when the list leaves some of it out.
const ATTENTION = {
  paymentReport: {
    label: "To confirm",
    count: "paymentReports",
    none: "payment reports to confirm",
    more: ["payment report", "payment reports"],
  },
  overdue: {
    label: "Overdue",
    count: "overdue",
    none: "overdue invoices",
    more: ["overdue invoice", "overdue invoices"],
  },
  dueSoon: {
    label: "Due soon",
    count: "dueSoon",
    none: "invoices due this week",
    more: ["due this week", "due this week"],
  },
  quotation: {
    label: "Quotation",
    count: "quotations",
    none: "quotations expiring this week",
    more: ["quotation", "quotations"],
  },
  recurring: {
    label: "Recurring",
    count: "recurring",
    none: "recurring invoices due this week",
    more: ["recurring invoice", "recurring invoices"],
  },
  draft: {
    label: "To issue",
    count: "drafts",
    none: "drafts to issue",
    more: ["draft", "drafts"],
  },
};
// The kinds as the list orders them, most urgent first; the kinds there is
// none of are named in an order that reads better.
const ATTENTION_ORDER = [
  "paymentReport",
  "overdue",
  "dueSoon",
  "quotation",
  "recurring",
  "draft",
];
const NONE_ORDER = [
  "paymentReport",
  "overdue",
  "dueSoon",
  "quotation",
  "draft",
  "recurring",
];
const dayCount = (days) => `${days} ${days === 1 ? "day" : "days"}`;
const soon = (days, today, tomorrow, later) =>
  days === 0 ? today : days === 1 ? tomorrow : later;
const listed = (phrases, last) =>
  phrases.length > 1
    ? `${phrases.slice(0, -1).join(", ")} ${last} ${phrases.at(-1)}`
    : phrases[0];

// One item's row: its label, its state, the day that matters, and where it
// leads. Without a number of days, only the day is given. A recurring
// schedule opens the recurring list; the rest open their document. The
// client's name opens the client when the item is linked to one.
export const attentionRow = (item) => {
  const date = item.date ? dayMonth(parseDate(item.date)) : "";
  const days = item.days == null ? NaN : Number(item.days);
  const known = Number.isFinite(days);
  const [state, when] = {
    paymentReport: ["Payment reported", date && `Paid on ${date}`],
    overdue: [known ? `${dayCount(days)} late` : "", date && `Due ${date}`],
    dueSoon: [
      known
        ? soon(days, "Due today", "Due tomorrow", `Due in ${days} days`)
        : "",
      date && `Due ${date}`,
    ],
    quotation: ["Awaiting an answer", date && `Valid until ${date}`],
    recurring: [
      known
        ? soon(days, "Runs today", "Runs tomorrow", `Runs in ${days} days`)
        : "",
      date && `Next run ${date}`,
    ],
    draft: ["Draft", date && `Created ${date}`],
  }[item.kind] || ["", date];
  return {
    label: ATTENTION[item.kind]?.label || "",
    state,
    when,
    to:
      item.kind === "recurring" ? "/recurring" : `/document/${item.documentId}`,
    clientTo:
      item.clientId && item.client ? `/single-customer/${item.clientId}` : null,
  };
};

// The list's footer: the kinds there is none of, then what the list leaves
// out, since it shows only the most urgent few, and that a client's name
// opens the client, when one does.
export const attentionFooter = (counts = {}, items = []) => {
  const none = NONE_ORDER.filter((kind) => !counts[ATTENTION[kind].count]).map(
    (kind) => ATTENTION[kind].none,
  );
  const more = ATTENTION_ORDER.flatMap((kind) => {
    const {
      count,
      more: [one, many],
    } = ATTENTION[kind];
    const shown = items.filter((item) => item.kind === kind).length;
    const left = (Number(counts[count]) || 0) - shown;
    return left > 0
      ? [
          `${left.toLocaleString("en")} ${shown ? "more " : ""}${left === 1 ? one : many}`,
        ]
      : [];
  });
  const sentences = [
    none.length && `No ${listed(none, "or")}.`,
    more.length && `Also ${listed(more, "and")}.`,
    items.some((item) => attentionRow(item).clientTo) &&
      "A client’s name opens the client.",
  ].filter(Boolean);
  return sentences.length ? sentences.join(" ") : null;
};

// Stored status values, in the words the app shows for them.
export const STATUS_LABELS = {
  Paid: "Paid",
  "Not Fully Paid": "Partially paid",
  "Not Paid": "Unpaid",
};
