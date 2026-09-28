const asAmount = (value) =>
  Number.isFinite(Number(value)) ? Number(value) : 0;

// Preserve the original dashboard's totals and stored status categories.
// Payments are displayed as recorded; this view never creates or updates data.
export const getDashboardSnapshot = (documents, now = new Date()) => {
  const expectedIncome = documents.reduce(
    (sum, document) => sum + asAmount(document.total),
    0,
  );
  const cashReceived = documents.reduce(
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
        (new Date(b.datePaid).getTime() || 0) -
        (new Date(a.datePaid).getTime() || 0),
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
    overdue: documents.filter(
      (document) => document.dueDate <= now.toISOString(),
    ).length,
    payments,
  };
};

export const formatMoney = (value) =>
  new Intl.NumberFormat("en", {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(asAmount(value));

export const formatDate = (value) => {
  const date = value ? new Date(value) : null;
  return date && Number.isFinite(date.getTime())
    ? date.toLocaleDateString("en", {
        day: "numeric",
        month: "short",
        year: "numeric",
      })
    : "—";
};
