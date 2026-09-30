import { describe, expect, it } from "vitest";
import {
  formatDate,
  formatMoney,
  getFinanceBarRatios,
  getStatusRingRatio,
  paymentRows,
} from "./dashboardModel";

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

it("keys each payment by its id and finds the document it was recorded against", () => {
  const payments = [
    {
      _id: "p2",
      paidBy: "Second client",
      datePaid: "2026-09-20",
      amountPaid: 10,
      document: { _id: "d2", documentNumber: "INV-2" },
    },
    {
      _id: "p1",
      paidBy: "First client",
      datePaid: "2026-08-01",
      amountPaid: 24.5,
      document: { _id: "d1", documentNumber: "INV-1" },
    },
  ];
  const { rows, documents } = paymentRows(payments);
  expect(rows.map((row) => [row.id, row.paidBy])).toEqual([
    ["p2", "Second client"],
    ["p1", "First client"],
  ]);
  expect(rows.map((row) => documents.get(row.id).documentNumber)).toEqual([
    "INV-2",
    "INV-1",
  ]);
  expect(paymentRows().rows).toEqual([]);
});

it("formats amounts and dates safely", () => {
  expect(formatMoney(undefined)).toBe("0.00");
  expect(formatMoney(1234.5)).toBe("1,234.50");
  expect(formatDate("invalid")).toBe("—");
});
