// A plain YYYY-MM-DD is a calendar day, so it is read in local time; the
// browser would otherwise read it as UTC midnight, a day early in the west.
export const parseDate = (value) => {
  const day = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value || "");
  return day ? new Date(day[1], day[2] - 1, day[3]) : new Date(value);
};

// A day as "Oct 4", or with its year as "Oct 4, 2026".
export const dayMonth = (date) =>
  date.toLocaleDateString("en", { month: "short", day: "numeric" });
export const dayMonthYear = (date) =>
  date.toLocaleDateString("en", {
    month: "short",
    day: "numeric",
    year: "numeric",
  });

// From one day to another, as "Jul 7 – Oct 4, 2026": the year once at the
// end when both days share it, or after each day when they don't.
// `withYear` false leaves a shared year off.
export const dateSpan = (from, to, withYear = true) => {
  const start = parseDate(from);
  const end = parseDate(to);
  if (start.getFullYear() !== end.getFullYear()) {
    return `${dayMonthYear(start)} – ${dayMonthYear(end)}`;
  }
  return `${dayMonth(start)} – ${withYear ? dayMonthYear(end) : dayMonth(end)}`;
};
