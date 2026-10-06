import { describe, expect, it } from "vitest";
import {
  SORT_OPTIONS,
  customerSalesQuery,
  customerSalesSearch,
  readCustomerSales,
} from "./customerSalesModel";

const read = (search) => readCustomerSales(new URLSearchParams(search));

describe("sales by customer in the URL", () => {
  it("opens at every date, the most billed first, ten to a page", () => {
    expect(read("")).toEqual({
      period: "",
      currency: "",
      sort: "billed",
      dir: "desc",
      page: 1,
      size: 10,
    });
    expect(customerSalesSearch(read("")).toString()).toBe("");
  });

  it("reads and writes the period, the currency, the order and the page", () => {
    const list = read("page=2&dir=asc&sort=owed&currency=EUR&period=90d");
    expect(list).toEqual({
      period: "90d",
      currency: "EUR",
      sort: "owed",
      dir: "asc",
      page: 2,
      size: 10,
    });
    expect(customerSalesSearch(list).toString()).toBe(
      "period=90d&currency=EUR&sort=owed&dir=asc&page=2",
    );
  });

  it("falls back to the defaults for unknown or malformed values", () => {
    expect(read("period=ytd&currency=usd&sort=client&size=7")).toEqual({
      period: "",
      currency: "",
      sort: "billed",
      dir: "desc",
      page: 1,
      size: 10,
    });
  });

  it("offers each order the server sorts by", () => {
    expect(SORT_OPTIONS.map((option) => [option.value, option.label])).toEqual([
      ["billed:desc", "Most billed"],
      ["billed:asc", "Least billed"],
      ["paid:desc", "Most paid"],
      ["paid:asc", "Least paid"],
      ["owed:desc", "Most owed"],
      ["owed:asc", "Least owed"],
      ["name:asc", "Name A–Z"],
      ["name:desc", "Name Z–A"],
    ]);
  });
});

it("asks for the period by name, the currency when chosen, and the order and page", () => {
  expect(customerSalesQuery(read("period=this-year&sort=name"))).toEqual({
    period: "this-year",
    sort: "name",
    dir: "asc",
    page: 1,
    size: 10,
  });
  expect(customerSalesQuery(read("currency=EUR&size=25"))).toEqual({
    currency: "EUR",
    sort: "billed",
    dir: "desc",
    page: 1,
    size: 25,
  });
});
