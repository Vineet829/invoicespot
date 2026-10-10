import { parseDate } from "../../utils/dates";
import { useBusiness } from "./permissions";

// The day it is in a time zone, as a local midnight that compares with a
// day read by parseDate. With no zone, or one the browser doesn't know, it
// is the browser's own day. The day is read part by part, so the format's
// order doesn't matter.
export const todayIn = (timeZone, now = new Date()) => {
  let parts;
  try {
    parts = new Intl.DateTimeFormat("en-CA", {
      timeZone: timeZone || undefined,
      year: "numeric",
      month: "2-digit",
      day: "2-digit",
    }).formatToParts(now);
  } catch {
    return todayIn(undefined, now);
  }
  const part = (type) => parts.find((entry) => entry.type === type).value;
  return parseDate(`${part("year")}-${part("month")}-${part("day")}`);
};

// Today where the business this tab works in is. The server counts its days,
// such as how late an invoice is, in the business's time zone, so the pages
// count from the same day. It is worked out on each render, so it turns
// over at the business's midnight.
const useBusinessToday = () => todayIn(useBusiness().business?.timeZone);

export default useBusinessToday;
