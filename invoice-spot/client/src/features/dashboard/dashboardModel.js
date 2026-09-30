import { parseDate } from "../../utils/dates";
const asAmount = (value) =>
  Number.isFinite(Number(value)) ? Number(value) : 0;

// The server's payments as the payment history shows them: each row keyed by
// its id, and the document it was recorded against for the hover card.
export const paymentRows = (payments = []) => ({
  rows: payments.map((payment) => ({ ...payment, id: payment._id })),
  documents: new Map(
    payments.map((payment) => [payment._id, payment.document]),
  ),
});

export const formatMoney = (value) =>
  new Intl.NumberFormat("en", {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(asAmount(value));

export const formatDate = (value) => {
  const date = value ? parseDate(value) : null;
  return date && Number.isFinite(date.getTime())
    ? date.toLocaleDateString("en", {
        day: "numeric",
        month: "short",
        year: "numeric",
      })
    : "—";
};

// Ratios only describe the existing amounts; invalid comparisons have no bars.
export const getFinanceBarRatios = ({
  expectedIncome,
  cashReceived,
  cashPending,
}) => {
  if (
    !Number.isFinite(expectedIncome) ||
    expectedIncome <= 0 ||
    ![cashReceived, cashPending].every(
      (amount) =>
        Number.isFinite(amount) && amount >= 0 && amount <= expectedIncome,
    )
  ) {
    return null;
  }
  return {
    expected: 1,
    received: cashReceived / expectedIncome,
    pending: cashPending / expectedIncome,
  };
};

// An empty or invalid status draws only its track. Overdue is never a ring.
export const getStatusRingRatio = (count, total) =>
  Number.isFinite(total) &&
  total > 0 &&
  Number.isFinite(count) &&
  count >= 0 &&
  count <= total
    ? count / total
    : 0;

// Stored status values, in the words the overview uses for them.
export const STATUS_LABELS = {
  Paid: "Paid",
  "Not Fully Paid": "Partially paid",
  "Not Paid": "Unpaid",
};
