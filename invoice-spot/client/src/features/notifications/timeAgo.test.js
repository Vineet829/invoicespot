import { expect, it } from "vitest";
import { timeAgo } from "./timeAgo";

const now = new Date("2026-09-30T12:00:00");

it.each([
  ["2026-09-30T11:59:40", "Just now"],
  ["2026-09-30T11:15:00", "45 min ago"],
  ["2026-09-30T02:00:00", "10 h ago"],
  ["2026-09-29T09:00:00", "Yesterday"],
  ["2026-09-25T12:00:00", "5 days ago"],
  ["2026-09-03T12:00:00", "Sep 3"],
  ["2025-12-24T12:00:00", "Dec 24, 2025"],
  ["not a date", ""],
])("%s reads as %s", (value, words) => {
  expect(timeAgo(value, now)).toBe(words);
});
