import {
  formatDate,
  formatMoney,
  getDashboardSnapshot,
} from "./dashboardModel";

const now = new Date("2026-09-28T12:00:00.000Z");

it("preserves the original totals, backend status categories, and due-date count", () => {
  const documents = [
    {
      _id: "invoice",
      documentType: "Invoice",
      total: 100,
      totalAmountReceived: 20,
      status: "Not Fully Paid",
      dueDate: "2026-09-27T00:00:00.000Z",
      paymentRecords: [{ amountPaid: 35 }],
    },
    {
      _id: "paid",
      documentType: "Invoice",
      total: 200,
      totalAmountReceived: 200,
      status: "Paid",
      dueDate: "2026-09-25T00:00:00.000Z",
    },
    {
      _id: "quote",
      documentType: "Quotation",
      total: 900,
      status: "Not Paid",
      dueDate: "2026-10-10T00:00:00.000Z",
    },
    {
      _id: "receipt",
      documentType: "Receipt",
      total: 60,
      status: "Paid",
      dueDate: "2026-09-29T00:00:00.000Z",
    },
  ];
  const summary = getDashboardSnapshot(documents, now);
  expect(summary).toMatchObject({
    expectedIncome: 1260,
    cashReceived: 220,
    cashPending: 1040,
    paid: 2,
    partial: 1,
    unpaid: 1,
    overdue: 2,
  });
  // The UI uses stored totals and statuses, not new payment or invoice rules.
  expect(summary.payments).toHaveLength(1);
  expect(summary.payments[0].amountPaid).toBe(35);
});

it("keeps every original payment-history field and sorts recorded payments newest first", () => {
  const older = {
    paidBy: "First client",
    datePaid: "2026-08-01",
    amountPaid: 24.5,
    paymentMethod: "Cash",
    additionalInfo: "Deposit",
  };
  const newer = {
    paidBy: "Second client",
    datePaid: "2026-09-20",
    amountPaid: 10,
    paymentMethod: "Bank Transfer",
    additionalInfo: "Balance",
  };
  const documents = [
    { _id: "one", paymentRecords: [older] },
    { _id: "two", paymentRecords: [newer], status: "Paid", total: 10 },
    { _id: "three", status: "Paid", total: 999 },
  ];
  const snapshot = getDashboardSnapshot(documents, now);
  expect(snapshot.payments).toEqual([
    { ...newer, id: "two-0" },
    { ...older, id: "one-0" },
  ]);
  expect(documents[0].paymentRecords).toEqual([older]);
});

it("shows empty data safely without creating payment records or inferred statuses", () => {
  expect(getDashboardSnapshot([], now)).toEqual({
    expectedIncome: 0,
    cashReceived: 0,
    cashPending: 0,
    paid: 0,
    partial: 0,
    unpaid: 0,
    overdue: 0,
    payments: [],
  });
  expect(getDashboardSnapshot([{ _id: "empty" }], now).payments).toEqual([]);
  expect(formatMoney(undefined)).toBe("0.00");
  expect(formatMoney(1234.5)).toBe("1,234.50");
  expect(formatDate("invalid")).toBe("—");
});
