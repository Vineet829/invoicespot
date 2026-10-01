import { afterAll, beforeAll, expect, it } from "vitest";
import { dateInput, dateLabel } from "./Workspace";

// West of UTC, where a day read as UTC midnight falls on the day before.
const zone = process.env.TZ;
beforeAll(() => {
  process.env.TZ = "America/Los_Angeles";
});
afterAll(() => {
  if (zone === undefined) delete process.env.TZ;
  else process.env.TZ = zone;
});

it("reads a due date sent as a day as that day, in any time zone", () => {
  expect(dateInput("2026-10-15")).toBe("2026-10-15");
  expect(dateLabel("2026-10-15")).toBe("Oct 15, 2026");
});

it("gives the local day of a moment", () => {
  expect(dateInput(new Date(2026, 9, 15, 23, 30).getTime())).toBe("2026-10-15");
});
