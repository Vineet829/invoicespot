import { describe, expect, it } from "vitest";
import {
  TYPE_NAMES,
  billedQuery,
  readSales,
  salesQuery,
  salesSearch,
} from "./salesModel";

const read = (search) => readSales(new URLSearchParams(search));
const OCT_4 = new Date(2026, 9, 4, 12);

describe("the sales view in the URL", () => {
  it("opens at every date, on the first page of ten", () => {
    expect(read("")).toEqual({ period: "", currency: "", page: 1, size: 10 });
    expect(salesSearch(read("")).toString()).toBe("");
  });

  it("reads and writes the period, the currency and the page, in that order", () => {
    const list = read("size=25&currency=EUR&page=3&period=this-year");
    expect(list).toEqual({
      period: "this-year",
      currency: "EUR",
      page: 3,
      size: 25,
    });
    expect(salesSearch(list).toString()).toBe(
      "period=this-year&currency=EUR&page=3&size=25",
    );
  });

  it("takes every period the Documents list has, and the overview's links send", () => {
    for (const period of [
      "30d",
      "90d",
      "12m",
      "this-month",
      "last-month",
      "this-quarter",
      "this-year",
      "last-year",
    ]) {
      expect(read(`period=${period}`).period).toBe(period);
    }
    expect(read("period=ytd&currency=usd").period).toBe("");
    expect(read("currency=usd").currency).toBe("");
  });
});

describe("what the view asks for", () => {
  it("asks for the summary by the period's name, and the currency when chosen", () => {
    expect(salesQuery(read("period=this-year"))).toEqual({
      period: "this-year",
    });
    expect(salesQuery(read("period=90d&currency=EUR"))).toEqual({
      period: "90d",
      currency: "EUR",
    });
    expect(salesQuery(read(""))).toEqual({});
  });

  it("lists what was billed in the period and the total's currency, newest first", () => {
    expect(billedQuery(read("period=90d"), "USD", OCT_4)).toEqual({
      page: 1,
      size: 10,
      sort: "createdAt",
      dir: "desc",
      billed: "true",
      period: "90d",
      currency: "USD",
    });
    // The Documents list sends the calendar's periods as days, this year's
    // up to today.
    expect(billedQuery(read("period=this-year&page=2"), "EUR", OCT_4)).toEqual({
      page: 2,
      size: 10,
      sort: "createdAt",
      dir: "desc",
      billed: "true",
      from: "2026-01-01",
      to: "2026-10-04",
      currency: "EUR",
    });
    expect(billedQuery(read(""), undefined, OCT_4)).toEqual({
      page: 1,
      size: 10,
      sort: "createdAt",
      dir: "desc",
      billed: "true",
    });
  });

  it("names each type of document billed", () => {
    expect(TYPE_NAMES).toEqual({
      Invoice: "Invoices",
      Receipt: "Receipts",
      "Credit Note": "Credit notes",
    });
  });
});
