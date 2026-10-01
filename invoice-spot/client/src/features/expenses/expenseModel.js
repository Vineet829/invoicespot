import { readSortAndPage, writeSortAndPage } from "../../utils/listState";
import { PERIODS, periodRange } from "../documents/documentFilters";
import { cents } from "../documents/taxWithheld";

// The Expenses list's search, category, period and page, as kept in the URL.
// Expenses are always listed newest first, so only the page and its size come
// from the shared list helpers.
const NEWEST_FIRST = { date: { first: "desc" } };

export const readExpenseList = (params) => {
  const { page, size } = readSortAndPage(params, NEWEST_FIRST, "date");
  const period = params.get("period");
  return {
    q: params.get("q") || "",
    category: params.get("category") || "",
    period: PERIODS.some((option) => option.value === period) ? period : "",
    page,
    size,
  };
};

export const expenseListSearch = ({ q, category, period, page, size }) => {
  const params = new URLSearchParams();
  if (q) params.set("q", q);
  if (category) params.set("category", category);
  if (period) params.set("period", period);
  return writeSortAndPage(
    params,
    { sort: "date", dir: "desc", page, size },
    NEWEST_FIRST,
    "date",
  );
};

export const isFiltered = ({ q, category, period }) =>
  Boolean(q || category || period);

// The list's filters as the server reads them; an export of the list sends
// these without a page.
export const expenseFilterQuery = (
  { q, category, period },
  today = new Date(),
) => ({
  ...(q && { q }),
  ...(category && { category }),
  ...periodRange(period, today),
});

export const expenseQuery = (list, today) => ({
  page: list.page,
  size: list.size,
  ...expenseFilterQuery(list, today),
});

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
