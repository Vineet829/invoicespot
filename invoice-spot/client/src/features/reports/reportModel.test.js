import { describe, expect, it } from "vitest";
import {
  axisTicks,
  compactAmount,
  monthName,
  monthYear,
  periodOptions,
  share,
} from "./reportModel";

describe("reportModel", () => {
  it("offers the recent periods, then each earlier year", () => {
    expect(periodOptions([2025, 2024]).map((option) => option.value)).toEqual([
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
