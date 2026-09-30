import { expect, it } from "vitest";
import { afterOnePeriod, frequencyLabel } from "./frequencies";

it.each([
  ["2026-09-30", "WEEKLY", "2026-10-07"],
  ["2026-09-30", "MONTHLY", "2026-10-30"],
  ["2026-01-31", "MONTHLY", "2026-02-28"],
  ["2026-11-30", "QUARTERLY", "2027-02-28"],
  ["2028-02-29", "YEARLY", "2029-02-28"],
  ["2026-12-15", "MONTHLY", "2027-01-15"],
])("one period after %s, %s, is %s", (day, frequency, next) => {
  expect(afterOnePeriod(day, frequency)).toBe(next);
});

it("names a frequency as the server spells it", () => {
  expect(frequencyLabel("QUARTERLY")).toBe("Quarterly");
  expect(frequencyLabel("SOMETIMES")).toBe("SOMETIMES");
});
