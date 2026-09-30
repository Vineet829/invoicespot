import { parseDate } from "../../utils/dates";
const asAmount = (value) =>
  Number.isFinite(Number(value)) ? Number(value) : 0;

// Income comes from invoices and receipts; a quotation is an offer, not money
// owed. Overdue is the server's flag, which leaves settled documents out.
// Payments are displayed as recorded; this view never creates or updates data.
export const getDashboardSnapshot = (documents) => {
  const billed = documents.filter(
    (document) => document.documentType !== "Quotation",
  );
  const expectedIncome = billed.reduce(
    (sum, document) => sum + asAmount(document.total),
    0,
  );
  const cashReceived = billed.reduce(
    (sum, document) => sum + asAmount(document.totalAmountReceived),
    0,
  );
  const payments = documents
    .flatMap((document) =>
      (document.paymentRecords || []).map((payment, index) => ({
        ...payment,
        id: `${document._id}-${index}`,
      })),
    )
    .sort(
      (a, b) =>
        (parseDate(b.datePaid).getTime() || 0) -
        (parseDate(a.datePaid).getTime() || 0),
    );

  return {
    expectedIncome,
    cashReceived,
    cashPending: expectedIncome - cashReceived,
    paid: documents.filter((document) => document.status === "Paid").length,
    partial: documents.filter(
      (document) => document.status === "Not Fully Paid",
    ).length,
    unpaid: documents.filter((document) => document.status === "Not Paid")
      .length,
    overdue: documents.filter((document) => document.overdue).length,
    payments,
  };
};

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

// The document each recorded payment belongs to, keyed like the payment ids
// in getDashboardSnapshot. Payment hover details read the document from here.
export const getPaymentDocuments = (documents) =>
  new Map(
    documents.flatMap((document) =>
      (document.paymentRecords || []).map((_payment, index) => [
        `${document._id}-${index}`,
        document,
      ]),
    ),
  );

// Stored status values, in the words the overview uses for them.
export const STATUS_LABELS = {
  Paid: "Paid",
  "Not Fully Paid": "Partially paid",
  "Not Paid": "Unpaid",
};
