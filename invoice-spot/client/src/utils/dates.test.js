import { expect, it } from "vitest";
import { dayMonthFrom, parseDate } from "./dates";

it("reads a plain date as that calendar day in local time", () => {
  const date = parseDate("2026-09-30");
  expect([date.getFullYear(), date.getMonth(), date.getDate()]).toEqual([
    2026, 8, 30,
  ]);
  expect(date.getHours()).toBe(0);
});

it("reads timestamps as the instant they name", () => {
  expect(parseDate("2026-09-30T06:30:00.000Z").toISOString()).toBe(
    "2026-09-30T06:30:00.000Z",
  );
});

it("names a day's year only when it isn't today's", () => {
  const today = new Date(2026, 9, 8);
  expect(dayMonthFrom(parseDate("2026-12-14"), today)).toBe("Dec 14");
  expect(dayMonthFrom(parseDate("2025-12-14"), today)).toBe("Dec 14, 2025");
  expect(dayMonthFrom(parseDate("2027-01-02"), today)).toBe("Jan 2, 2027");
});
