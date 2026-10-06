import { describe, expect, it } from "vitest";
import {
  AGE_BANDS,
  DUE_BANDS,
  MODES,
  bandsOf,
  daysUntil,
  dueLabel,
  lateLabel,
  readReceivables,
  receivablesQuery,
  receivablesSearch,
} from "./receivablesModel";

const read = (search) => readReceivables(new URLSearchParams(search));
const OCT_4 = new Date(2026, 9, 4, 12);

describe("the receivables view in the URL", () => {
  it("opens at what is coming in, on the first page of ten", () => {
    expect(read("")).toEqual({
      mode: "coming-in",
      currency: "",
      page: 1,
      size: 10,
    });
    expect(receivablesSearch(read("")).toString()).toBe("");
  });

  it("reads and writes the mode, the currency and the page, in that order", () => {
    const list = read("page=2&currency=EUR&size=25&mode=overdue");
    expect(list).toEqual({
      mode: "overdue",
      currency: "EUR",
      page: 2,
      size: 25,
    });
    expect(receivablesSearch(list).toString()).toBe(
      "mode=overdue&currency=EUR&page=2&size=25",
    );
  });

  it("falls back to the defaults for unknown or malformed values", () => {
    expect(read("mode=late&currency=euro&page=-1&size=7")).toEqual({
      mode: "coming-in",
      currency: "",
      page: 1,
      size: 10,
    });
  });

  it("offers the modes as the pill pair names them", () => {
    expect(MODES.map((mode) => [mode.value, mode.label])).toEqual([
      ["coming-in", "Coming in"],
      ["overdue", "Overdue"],
    ]);
  });
});

describe("the invoices behind each figure", () => {
  it("asks for the invoices owed and not due yet, soonest due first", () => {
    expect(
      receivablesQuery({ mode: "coming-in", page: 1, size: 10 }, "USD"),
    ).toEqual({
      page: 1,
      size: 10,
      sort: "dueDate",
      dir: "asc",
      outstanding: "true",
      due: "current",
      currency: "USD",
    });
  });

  it("asks for the overdue invoices still owed, most late first", () => {
    expect(
      receivablesQuery({ mode: "overdue", page: 3, size: 25 }, "EUR"),
    ).toEqual({
      page: 3,
      size: 25,
      sort: "dueDate",
      dir: "asc",
      overdue: "true",
      outstanding: "true",
      currency: "EUR",
    });
  });

  it("leaves the currency out when there is none", () => {
    expect(
      receivablesQuery({ mode: "overdue", page: 1, size: 10 }),
    ).not.toHaveProperty("currency");
  });
});

describe("how late or how soon", () => {
  it("counts whole days from today by the reader's calendar", () => {
    expect(daysUntil("2026-10-06", OCT_4)).toBe(2);
    expect(daysUntil("2026-10-04", OCT_4)).toBe(0);
    expect(daysUntil("2026-08-11", OCT_4)).toBe(-54);
  });

  it("names an overdue invoice's days late, at least one", () => {
    expect(lateLabel("2026-08-11", OCT_4)).toBe("54 days late");
    expect(lateLabel("2026-10-03", OCT_4)).toBe("1 day late");
    // The server's clock can be a day ahead of the reader's.
    expect(lateLabel("2026-10-04", OCT_4)).toBe("1 day late");
  });

  it("names when an invoice not yet due falls due", () => {
    expect(dueLabel("2026-10-04", OCT_4)).toBe("Due today");
    expect(dueLabel("2026-10-05", OCT_4)).toBe("Due tomorrow");
    expect(dueLabel("2026-11-16", OCT_4)).toBe("Due in 43 days");
    expect(dueLabel("2026-10-03", OCT_4)).toBe("Due today");
    expect(dueLabel(null, OCT_4)).toBe("No due date");
  });
});

describe("the bands", () => {
  it("names the overdue bands in order, each in a deeper rust", () => {
    expect(
      AGE_BANDS.map(({ key, label, color }) => [key, label, color]),
    ).toEqual([
      ["days1to15", "1–15 days", "bg-owed-age-1"],
      ["days16to30", "16–30 days", "bg-owed-age-2"],
      ["days31to45", "31–45 days", "bg-owed-age-3"],
      ["over45", "Over 45 days", "bg-owed-age-4"],
    ]);
  });

  it("names the bands not due yet in order, each a paler lavender", () => {
    expect(
      DUE_BANDS.map(({ key, label, color }) => [key, label, color]),
    ).toEqual([
      ["dueIn0to15", "Due in 0–15 days", "bg-owed-due"],
      ["dueIn16to30", "Due in 16–30 days", "bg-owed-due/70"],
      ["dueIn31to45", "Due in 31–45 days", "bg-owed-due/45"],
      ["dueAfter45", "Due after 45 days", "bg-owed-due/25"],
    ]);
  });

  it("reads a group's bands by key", () => {
    const late = { key: "over45", amount: 5, invoices: 2 };
    expect(bandsOf({ bands: [late] })).toEqual({ over45: late });
    expect(bandsOf({})).toEqual({});
    expect(bandsOf()).toEqual({});
  });
});
