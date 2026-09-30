import { describe, expect, it } from "vitest";
import {
  SORT_OPTIONS,
  documentQuery,
  isFiltered,
  listSearch,
  periodRange,
  readListState,
} from "./documentFilters";

const read = (search) => readListState(new URLSearchParams(search));

describe("list state in the URL", () => {
  it("starts with every document, newest first, ten to a page", () => {
    expect(read("")).toEqual({
      q: "",
      status: "",
      type: "",
      period: "",
      sort: "createdAt",
      dir: "desc",
      page: 1,
      size: 10,
    });
  });

  it("reads a search, filters, sort and page", () => {
    expect(
      read(
        "q=north&status=overdue&type=invoice&period=this-year&sort=total&dir=asc&page=3&size=25",
      ),
    ).toEqual({
      q: "north",
      status: "overdue",
      type: "invoice",
      period: "this-year",
      sort: "total",
      dir: "asc",
      page: 3,
      size: 25,
    });
  });

  it("falls back to the defaults for unknown or malformed values", () => {
    expect(
      read(
        "status=Maybe&type=memo&period=forever&sort=password&dir=up&page=-2&size=7&extra=1",
      ),
    ).toEqual(read(""));
    expect(read("sort=status").sort).toBe("createdAt");
  });

  it("writes only what differs from the defaults", () => {
    expect(listSearch(read("")).toString()).toBe("");
    const search = "q=a+b&status=paid&sort=createdAt&dir=asc&page=2&size=50";
    expect(listSearch(read(search)).toString()).toBe(search);
    expect(isFiltered(read("sort=total&page=2"))).toBe(false);
    expect(isFiltered(read("type=receipt"))).toBe(true);
    expect(isFiltered(read("period=last-month"))).toBe(true);
  });
});

describe("the server query", () => {
  it("sends the stored status of a payment filter", () => {
    expect(documentQuery(read("status=partial"))).toEqual({
      page: 1,
      size: 10,
      sort: "createdAt",
      dir: "desc",
      status: "Not Fully Paid",
    });
  });

  it("asks for overdue documents rather than a status", () => {
    const query = documentQuery(read("status=overdue&type=invoice&q=inv"));
    expect(query).toMatchObject({ overdue: "true", type: "Invoice", q: "inv" });
    expect(query).not.toHaveProperty("status");
  });
});

describe("issue date periods", () => {
  const today = new Date(2026, 8, 30, 23, 30);

  it.each([
    ["this-month", "2026-09-01", "2026-09-30"],
    ["last-month", "2026-08-01", "2026-08-31"],
    ["this-quarter", "2026-07-01", "2026-09-30"],
    ["this-year", "2026-01-01", "2026-12-31"],
    ["last-year", "2025-01-01", "2025-12-31"],
  ])("%s runs from %s to %s", (period, from, to) => {
    expect(periodRange(period, today)).toEqual({ from, to });
  });

  it("works across the turn of the year", () => {
    expect(periodRange("last-month", new Date(2027, 0, 15))).toEqual({
      from: "2026-12-01",
      to: "2026-12-31",
    });
    expect(periodRange("this-quarter", new Date(2027, 0, 15))).toEqual({
      from: "2027-01-01",
      to: "2027-03-31",
    });
  });

  it("sends a period as from and to dates, and no dates for all of them", () => {
    expect(documentQuery(read("period=this-month"), today)).toMatchObject({
      from: "2026-09-01",
      to: "2026-09-30",
    });
    expect(documentQuery(read(""), today)).not.toHaveProperty("from");
  });
});

describe("the sort menu", () => {
  it("offers both directions of every field, newest first at the top", () => {
    expect(SORT_OPTIONS).toHaveLength(10);
    expect(SORT_OPTIONS[0]).toEqual({
      value: "createdAt:desc",
      label: "Newest first",
    });
  });
});
