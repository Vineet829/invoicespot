import { expect, it } from "vitest";
import { reminderSummary } from "./reminders";

const choices = {
  remindersEnabled: true,
  reminderDaysBefore: "3",
  reminderOnDue: true,
  reminderEveryDays: "7",
  reminderMax: "3",
};

it("describes each step that sends a reminder, and the limit", () => {
  expect(reminderSummary(choices)).toBe(
    "Reminders go out 3 days before the due date, on the due date and every 7 days after it, at most 3 times per invoice.",
  );
  expect(
    reminderSummary({
      ...choices,
      reminderDaysBefore: "1",
      reminderOnDue: false,
      reminderEveryDays: "0",
      reminderMax: "1",
    }),
  ).toBe(
    "Reminders go out 1 day before the due date, at most 1 time per invoice.",
  );
});

it("says when reminders are off or nothing would be sent", () => {
  expect(reminderSummary({ ...choices, remindersEnabled: false })).toBe(
    "Automatic reminders are off.",
  );
  expect(
    reminderSummary({
      ...choices,
      reminderDaysBefore: "0",
      reminderOnDue: false,
      reminderEveryDays: "0",
    }),
  ).toBe("No reminder will go out with these choices.");
});
