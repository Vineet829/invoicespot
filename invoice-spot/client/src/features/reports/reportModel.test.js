import { describe, expect, it } from "vitest";
import {
  axisTicks,
  compactAmount,
  currentMonth,
  gstFileName,
  margin,
  monthName,
  monthYear,
  percentLabel,
  percentOf,
  periodOptions,
  previousMonth,
  profitMonths,
  share,
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
