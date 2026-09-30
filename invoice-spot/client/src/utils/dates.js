// A plain YYYY-MM-DD is a calendar day, so it is read in local time; the
// browser would otherwise read it as UTC midnight, a day early in the west.
export const parseDate = (value) => {
  const day = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value || "");
  return day ? new Date(day[1], day[2] - 1, day[3]) : new Date(value);
};
