import { describe, expect, it } from "vitest";
import {
  axisTicks,
  compactAmount,
  currentMonth,
  gstFileName,
  margin,
  monthName,
  monthSpan,
  monthYear,
  percentLabel,
  percentOf,
  periodOptions,
  previousMonth,
  profitMonths,
  share,
  taxedSales,
} from "./reportModel";

describe("reportModel", () => {
  it("offers the last 30 and 90 days first, then the recent months and each earlier year", () => {
    expect(periodOptions([2025, 2024]).map((option) => option.value)).toEqual([
      "30d",
      "90d",
      "3m",
      "6m",
      "12m",
      "ytd",
      "2025",
      "2024",
    ]);
    expect(periodOptions().at(-1)).toEqual({
      value: "ytd",
      label: "This year",
    });
    expect(periodOptions().slice(0, 2)).toEqual([
      { value: "30d", label: "Last 30 days" },
      { value: "90d", label: "Last 90 days" },
    ]);
  });

  it("rounds the axis up to four even steps", () => {
    expect(axisTicks(62188.83)).toEqual([0, 20000, 40000, 60000, 80000]);
    expect(axisTicks(100)).toEqual([0, 25, 50, 75, 100]);
    expect(axisTicks(7)).toEqual([0, 2, 4, 6, 8]);
    expect(axisTicks(0)).toEqual([0]);
    expect(axisTicks("not a number")).toEqual([0]);
  });

  it("shortens axis amounts", () => {
    expect(compactAmount(20000)).toBe("20K");
    expect(compactAmount(1500000)).toBe("1.5M");
    expect(compactAmount(0)).toBe("0");
  });

  it("names months without shifting them by time zone", () => {
    expect(monthName("2026-01")).toBe("Jan");
    expect(monthYear("2025-12")).toBe("December 2025");
  });

  it("measures bars against the longest", () => {
    expect(share(50, 200)).toBe(0.25);
    expect(share(0, 200)).toBe(0);
    expect(share(10, 0)).toBe(0);
    expect(share(300, 200)).toBe(1);
  });
});

// The period's first and last days, as the report sends them, and each
// month of it as the report's rows name it.
it.each([
  [
    "the last 30 days, part months at both ends",
    ["2026-09-08", "2026-10-07"],
    [
      ["2026-09", "Sep 8–30, 2026"],
      ["2026-10", "Oct 1–7, 2026"],
    ],
  ],
  [
    "the last 12 months, back to the same day, part months at both ends",
    ["2025-10-09", "2026-10-08"],
    [
      ["2025-10", "Oct 9–31, 2025"],
      ["2025-11", "November 2025"],
      ["2026-02", "February 2026"],
      ["2026-09", "September 2026"],
      ["2026-10", "Oct 1–8, 2026"],
    ],
  ],
  [
    "this year, the month it is in so far",
    ["2026-01-01", "2026-10-08"],
    [
      ["2026-01", "January 2026"],
      ["2026-10", "Oct 1–8, 2026"],
    ],
  ],
  [
    "a year gone by, every month whole",
    ["2025-01-01", "2025-12-31"],
    [
      ["2025-01", "January 2025"],
      ["2025-02", "February 2025"],
      ["2025-12", "December 2025"],
    ],
  ],
  [
    "one day of a month",
    ["2026-09-08", "2026-10-01"],
    [["2026-10", "Oct 1, 2026"]],
  ],
])("names the months of %s", (_case, [from, to], months) => {
  expect(months.map(([month]) => [month, monthSpan(month, from, to)])).toEqual(
    months,
  );
});

it("names a month whole when the report gives no days", () => {
  expect(monthSpan("2026-09")).toBe("September 2026");
});

it("names last month and this one by the reader's calendar, across a new year", () => {
  expect(previousMonth(new Date(2026, 0, 15))).toBe("2025-12");
  expect(previousMonth(new Date(2026, 9, 31))).toBe("2026-09");
  expect(currentMonth(new Date(2026, 9, 1))).toBe("2026-10");
});

it("names the GST helper files as the server does", () => {
  expect(gstFileName("b2b", "2026-09", "2026-09")).toBe(
    "invoice-spot-gstr1-b2b-2026-09.csv",
  );
  expect(gstFileName("gstr3b", "2026-04", "2026-06")).toBe(
    "invoice-spot-gstr3b-2026-04-to-2026-06.csv",
  );
});

it("measures a month's margin against its income, and leaves it out without income", () => {
  expect(margin(300, 1000)).toBe(30);
  expect(margin(-1500, 1000)).toBe(-150);
  expect(margin(1, 3)).toBe(33);
  expect(margin(-500, 0)).toBeNull();
});

it("labels percents whole, a tiny share as under 1%, and nothing as a dash", () => {
  expect(percentLabel(30)).toBe("30%");
  expect(percentLabel(-150)).toBe("-150%");
  expect(percentLabel(1200)).toBe("1,200%");
  expect(percentLabel(0, 0.2)).toBe("<1%");
  expect(percentLabel(0, 0)).toBe("0%");
  expect(percentLabel(null)).toBe("—");
  expect(percentOf(250, 1000)).toBe(25);
  expect(percentOf(5, 0)).toBe(0);
});

it("adds each month's margin and the profit made so far, in cents", () => {
  expect(
    profitMonths([
      { month: "2026-08", income: 1000, expenses: 700, profit: 300 },
      { month: "2026-09", income: 0, expenses: 0.1, profit: -0.1 },
      { month: "2026-10", income: 0.2, expenses: 0, profit: 0.2 },
    ]).map(({ month, margin: kept, soFar }) => [month, kept, soFar]),
  ).toEqual([
    ["2026-08", 30, 300],
    ["2026-09", null, 299.9],
    ["2026-10", 100, 300.1],
  ]);
  expect(profitMonths()).toEqual([]);
});

it("counts only the sales taxed above 0% in what tax was charged on, and the rest apart", () => {
  const entry = {
    documents: 4,
    rates: [
      { rate: 0, documents: 2, taxable: 500 },
      { rate: 5, documents: 1, taxable: 400.1 },
      { rate: 18, documents: 1, taxable: 600.2 },
    ],
  };
  expect(taxedSales(entry)).toEqual({
    taxable: 1000.3,
    documents: 2,
    untaxed: 500,
  });
  // A document taxed at two rates is counted at each, never beyond all of them.
  expect(
    taxedSales({
      documents: 1,
      rates: [
        { rate: 5, documents: 1, taxable: 100 },
        { rate: 18, documents: 1, taxable: 100 },
      ],
    }),
  ).toEqual({ taxable: 200, documents: 1, untaxed: 0 });
  expect(
    taxedSales({
      documents: 2,
      rates: [{ rate: 0, documents: 2, taxable: 80 }],
    }),
  ).toEqual({ taxable: 0, documents: 0, untaxed: 80 });
  expect(taxedSales(undefined)).toEqual({
    taxable: 0,
    documents: 0,
    untaxed: 0,
  });
});
