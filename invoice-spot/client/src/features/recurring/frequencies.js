export const FREQUENCIES = [
  { value: "WEEKLY", label: "Weekly", every: "week" },
  { value: "MONTHLY", label: "Monthly", every: "month" },
  { value: "QUARTERLY", label: "Quarterly", every: "quarter" },
  { value: "YEARLY", label: "Yearly", every: "year" },
];

export const frequencyLabel = (value) =>
  FREQUENCIES.find((frequency) => frequency.value === value)?.label || value;

const pad = (number) => String(number).padStart(2, "0");
const isoDay = (date) =>
  `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;

// One period after a day, as YYYY-MM-DD: the natural date for the next
// invoice. A month end stays a month end, so Jan 31 is followed by Feb 28.
export const afterOnePeriod = (day, frequency) => {
  const [year, month, date] = day.split("-").map(Number);
  if (frequency === "WEEKLY") {
    return isoDay(new Date(year, month - 1, date + 7));
  }
  const months = { MONTHLY: 1, QUARTERLY: 3, YEARLY: 12 }[frequency] || 1;
  const lastDay = new Date(year, month - 1 + months + 1, 0).getDate();
  return isoDay(new Date(year, month - 1 + months, Math.min(date, lastDay)));
};
