import { describe, expect, it } from "vitest";
import {
  expenseFilterQuery,
  expenseForm,
  expenseListSearch,
  expensePayload,
  expenseQuery,
  readExpenseList,
  taxError,
} from "./expenseModel";

const read = (search) => readExpenseList(new URLSearchParams(search));

describe("the expenses list in the URL", () => {
  it("starts with every expense, ten to a page", () => {
    expect(read("")).toEqual({
      q: "",
      category: "",
      period: "",
      page: 1,
      size: 10,
    });
  });

  it("keeps a search, category, period and page, and writes only what differs", () => {
    const search = "q=uber&category=Travel&period=last-month&page=2&size=25";
    expect(expenseListSearch(read(search)).toString()).toBe(search);
    expect(expenseListSearch(read("")).toString()).toBe("");
    expect(read("period=someday&page=zero&size=7")).toMatchObject({
      period: "",
      page: 1,
      size: 10,
    });
  });

  it("asks the server for the period's first and last day", () => {
    const today = new Date(2026, 9, 1);
    expect(
      expenseQuery(read("category=Travel&period=last-month"), today),
    ).toEqual({
      page: 1,
      size: 10,
      category: "Travel",
      from: "2026-09-01",
      to: "2026-09-30",
    });
    expect(expenseFilterQuery(read("q=uber&page=3"), today)).toEqual({
      q: "uber",
    });
  });
});

describe("an expense in the dialog", () => {
  const defaults = { today: "2026-10-01", currency: "INR", base: "INR" };

  it("starts today, in the business's currency", () => {
    expect(expenseForm(null, defaults)).toEqual({
      date: "2026-10-01",
      category: "",
      vendor: "",
      total: "",
      tax: "",
      taxReclaimable: false,
      currency: "INR",
      exchangeRate: "",
      paymentMethod: "",
      reference: "",
      notes: "",
    });
  });

  it("shows what was paid and the tax in it", () => {
    const expense = {
      date: "2026-09-12",
      category: "Software",
      vendor: "Hostinger",
      amount: 100,
      taxAmount: 18,
      taxReclaimable: true,
      total: 118,
      currency: "USD",
      exchangeRate: "83.25",
      exchangeBase: "INR",
      paymentMethod: "Credit Card",
      reference: "H-1",
      notes: "Yearly plan",
    };
    expect(expenseForm(expense, defaults)).toEqual({
      date: "2026-09-12",
      category: "Software",
      vendor: "Hostinger",
      total: "118",
      tax: "18",
      taxReclaimable: true,
      currency: "USD",
      exchangeRate: "83.25",
      paymentMethod: "Credit Card",
      reference: "H-1",
      notes: "Yearly plan",
    });
    expect(expenseForm({ ...expense, taxAmount: 0 }, defaults).tax).toBe("");
    expect(
      expenseForm({ ...expense, exchangeBase: "EUR" }, defaults).exchangeRate,
    ).toBe("");
  });

  it("sends the amount before tax, to the cent", () => {
    const values = {
      ...expenseForm(null, defaults),
      category: " Travel ",
      total: "10.10",
      tax: "0.10",
      exchangeRate: "1.2",
    };
    expect(expensePayload(values, "INR")).toMatchObject({
      category: "Travel",
      amount: 10,
      taxAmount: 0.1,
      currency: "INR",
      exchangeRate: "",
    });
    expect(
      expensePayload({ ...values, currency: "USD" }, "INR").exchangeRate,
    ).toBe("1.2");
    expect(expensePayload({ ...values, tax: "" }, "INR")).toMatchObject({
      amount: 10.1,
      taxAmount: 0,
    });
  });

  it("refuses tax that leaves nothing paid for", () => {
    expect(taxError({ total: "100", tax: "100" })).toMatch(
      /less than the total/,
    );
    expect(taxError({ total: "100", tax: "18" })).toBeNull();
    expect(taxError({ total: "", tax: "18" })).toBeNull();
  });
});
