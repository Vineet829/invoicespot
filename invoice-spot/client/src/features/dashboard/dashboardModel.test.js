import { describe, expect, it } from "vitest";
import {
  formatDate,
  formatMoney,
  getDashboardSnapshot,
  getFinanceBarRatios,
  getPaymentDocuments,
  getStatusRingRatio,
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

describe("finance bar comparisons", () => {
  it("compares each existing amount with expected income", () => {
    expect(
      getFinanceBarRatios({
        expectedIncome: 100,
        cashReceived: 25,
        cashPending: 75,
      }),
    ).toEqual({ expected: 1, received: 0.25, pending: 0.75 });
  });

  it.each([
    [100, 0, 100, { expected: 1, received: 0, pending: 1 }],
    [100, 100, 0, { expected: 1, received: 1, pending: 0 }],
    [0, 0, 0, null],
    [-100, 0, -100, null],
    [100, 110, -10, null],
    [100, -10, 110, null],
    [100, 101, 0, null],
    [100, 0, 101, null],
    [Infinity, 0, 0, null],
    [100, NaN, 100, null],
    [100, 0, undefined, null],
  ])(
    "handles expected %s, received %s, pending %s",
    (expectedIncome, cashReceived, cashPending, result) => {
      expect(
        getFinanceBarRatios({ expectedIncome, cashReceived, cashPending }),
      ).toEqual(result);
    },
  );
});

describe("status ring comparisons", () => {
  it.each([
    [4, 12, 1 / 3],
    [3, 12, 0.25],
    [5, 12, 5 / 12],
    [12, 12, 1],
    [0, 12, 0],
    [0, 0, 0],
    [1, 0, 0],
    [-1, 12, 0],
    [13, 12, 0],
    [NaN, 12, 0],
    [1, Infinity, 0],
    [undefined, 12, 0],
  ])("draws count %s against total %s as %s", (count, total, ratio) => {
    expect(getStatusRingRatio(count, total)).toBe(ratio);
  });
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

it("finds the document behind every payment the history shows", () => {
  const invoice = {
    _id: "invoice",
    documentNumber: "INV-1",
    paymentRecords: [
      { amountPaid: 10, datePaid: "2026-09-01" },
      { amountPaid: 20, datePaid: "2026-09-02" },
    ],
  };
  const receipt = {
    _id: "receipt",
    documentNumber: "REC-1",
    paymentRecords: [{ amountPaid: 5, datePaid: "2026-09-03" }],
  };
  const documents = [invoice, receipt, { _id: "quote" }];
  const byPayment = getPaymentDocuments(documents);
  // Every payment id in the history resolves to its own document.
  const ids = getDashboardSnapshot(documents, now).payments.map(
    (payment) => payment.id,
  );
  expect(ids.map((id) => byPayment.get(id)?.documentNumber)).toEqual([
    "REC-1",
    "INV-1",
    "INV-1",
  ]);
  expect(byPayment.size).toBe(3);
});
