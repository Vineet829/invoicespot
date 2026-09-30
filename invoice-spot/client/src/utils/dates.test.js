import { expect, it } from "vitest";
import { parseDate } from "./dates";

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
