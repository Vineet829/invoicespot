import { readSortAndPage, writeSortAndPage } from "../../utils/listState";
import { countOf } from "../../utils/counts";
import { dateSpan } from "../../utils/dates";
import { PERIODS, currencyOf, periodQuery } from "../documents/documentFilters";
import { amountsOf } from "../documents/listTotals";
import { cents } from "../documents/taxWithheld";

// The Expenses list's search, category, period, currency and page, as kept
// in the URL. It has the Documents list's periods and currencies. Expenses
// are always listed newest first, so only the page and its size come from
// the shared list helpers.
const NEWEST_FIRST = { date: { first: "desc" } };

export const readExpenseList = (params) => {
  const { page, size } = readSortAndPage(params, NEWEST_FIRST, "date");
  const period = params.get("period");
  return {
    q: params.get("q") || "",
    category: params.get("category") || "",
    period: PERIODS.some((option) => option.value === period) ? period : "",
    currency: currencyOf(params.get("currency")),
    page,
    size,
  };
};

export const expenseListSearch = ({
  q,
  category,
  period,
  currency,
  page,
  size,
}) => {
  const params = new URLSearchParams();
  if (q) params.set("q", q);
  if (category) params.set("category", category);
  if (period) params.set("period", period);
  if (currency) params.set("currency", currency);
  return writeSortAndPage(
    params,
    { sort: "date", dir: "desc", page, size },
    NEWEST_FIRST,
    "date",
  );
};

export const isFiltered = ({ q, category, period, currency }) =>
  Boolean(q || category || period || currency);

// The list's filters as the server reads them, with the overview's periods
// by name and the others as dates; an export of the list sends these
// without a page.
export const expenseFilterQuery = (
  { q, category, period, currency },
  today = new Date(),
) => ({
  ...(q && { q }),
  ...(category && { category }),
  ...periodQuery(period, today),
  ...(currency && { currency }),
});

export const expenseQuery = (list, today) => ({
  page: list.page,
  size: list.size,
  ...expenseFilterQuery(list, today),
});

// The line above a filtered list: how many expenses it holds, from the days
// the server applied, and what they cost, as Profit counts it, from the
// totals the server sends with each page.
// Amounts name their currency when the business bills in more than one, and
// whenever one isn't its main currency, the first of `currencies`: an
// expense can be paid in any currency.
export const expenseResults = (data, currencies = []) => {
  const totals = data?.totals || [];
  const [main] = currencies;
  const several =
    currencies.length > 1 ||
    totals.length > 1 ||
    totals.some((entry) => main && entry.currency !== main);
  const spent = amountsOf(totals, "spent", { several, all: true });
  return [
    {
      strong: countOf(data?.totalExpenses ?? 0, "expense", "expenses"),
      after:
        data?.from && data?.to ? ` from ${dateSpan(data.from, data.to)}` : "",
    },
    spent && { before: "Spent ", strong: spent },
  ].filter(Boolean);
};

// The dialog's fields for an expense, or for a new one paid today in the
// business's currency. People type what they paid and the tax in it; the
// server keeps the amount before tax. A rate to a currency the business no
// longer reports in is left out, so saving removes it.
export const expenseForm = (expense, { today, currency, base }) => ({
  date: expense?.date || today,
  category: expense?.category || "",
  vendor: expense?.vendor || "",
  total: expense ? String(expense.total) : "",
  tax: Number(expense?.taxAmount) > 0 ? String(expense.taxAmount) : "",
  taxReclaimable: Boolean(expense?.taxReclaimable),
  currency: expense?.currency || currency,
  exchangeRate:
    expense?.exchangeRate && expense.exchangeBase === base
      ? expense.exchangeRate
      : "",
  paymentMethod: expense?.paymentMethod || "",
  reference: expense?.reference || "",
  notes: expense?.notes || "",
});

// The tax has to leave something paid for.
export const taxError = ({ total, tax }) =>
  total !== "" && cents(tax) > 0 && cents(tax) >= cents(total)
    ? "The tax has to be less than the total paid."
    : null;

export const expensePayload = (values, base) => ({
  date: values.date,
  category: values.category.trim(),
  vendor: values.vendor,
  amount: cents(cents(values.total) - cents(values.tax)),
  taxAmount: cents(values.tax),
  taxReclaimable: values.taxReclaimable,
  currency: values.currency,
  exchangeRate: values.currency === base ? "" : values.exchangeRate,
  paymentMethod: values.paymentMethod,
  reference: values.reference,
  notes: values.notes,
});
