import { describe, expect, it } from "vitest";
import {
  expenseFilterQuery,
  expenseForm,
  expenseListSearch,
  expensePayload,
  expenseQuery,
  expenseResults,
  isFiltered,
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
      currency: "",
      page: 1,
      size: 10,
    });
    expect(isFiltered(read("page=2&size=25"))).toBe(false);
  });

  it("keeps a search, category, period, currency and page, and writes only what differs", () => {
    const search =
      "q=uber&category=Travel&period=last-month&currency=EUR&page=2&size=25";
    expect(expenseListSearch(read(search)).toString()).toBe(search);
    expect(expenseListSearch(read("")).toString()).toBe("");
    expect(read("period=someday&currency=eur&page=zero&size=7")).toMatchObject({
      period: "",
      currency: "",
      page: 1,
      size: 10,
    });
    for (const filter of [
      "q=uber",
      "category=Travel",
      "period=90d",
      "currency=EUR",
    ]) {
      expect(isFiltered(read(filter))).toBe(true);
    }
  });

  it("reads what the overview's Spending links ask for", () => {
    const search =
      "category=Software+and+subscriptions&period=90d&currency=EUR";
    expect(read(search)).toMatchObject({
      category: "Software and subscriptions",
      period: "90d",
      currency: "EUR",
    });
    expect(expenseListSearch(read(search)).toString()).toBe(search);
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

  // The menu's last 30 and 90 days and 12 months are the overview's, whose
  // days the server works out in the business's time zone.
  it.each([
    ["", {}],
    ["30d", { period: "30d" }],
    ["90d", { period: "90d" }],
    ["12m", { period: "12m" }],
    ["this-month", { from: "2026-10-01", to: "2026-10-31" }],
    ["last-month", { from: "2026-09-01", to: "2026-09-30" }],
    ["this-quarter", { from: "2026-10-01", to: "2026-12-31" }],
    ["this-year", { from: "2026-01-01", to: "2026-12-31" }],
    ["last-year", { from: "2025-01-01", to: "2025-12-31" }],
  ])("sends the period %j as %j", (period, sent) => {
    const today = new Date(2026, 9, 4, 12);
    expect(expenseFilterQuery(read(`period=${period}`), today)).toEqual(sent);
    expect(
      expenseQuery(read(`category=Travel&period=${period}&page=2`), today),
    ).toEqual({ page: 2, size: 10, category: "Travel", ...sent });
  });

  it("passes on a currency as it is", () => {
    expect(
      expenseQuery(read("q=figma&period=12m&currency=EUR&size=25")),
    ).toEqual({
      page: 1,
      size: 25,
      q: "figma",
      period: "12m",
      currency: "EUR",
    });
  });
});

describe("the line above a filtered list", () => {
  const words = (parts) =>
    parts.map(({ before = "", strong = "", after = "" }) =>
      [before, strong, after].join(""),
    );

  it("says how many expenses were paid in the days the server took, and what they cost", () => {
    const parts = expenseResults({
      totalExpenses: 2,
      from: "2026-07-07",
      to: "2026-10-04",
      totals: [{ currency: "USD", expenses: 2, spent: 3600 }],
    });
    expect(words(parts)).toEqual([
      "2 expenses from Jul 7 – Oct 4, 2026",
      "Spent 3,600.00",
    ]);
    expect(parts.map((part) => part.strong)).toEqual([
      "2 expenses",
      "3,600.00",
    ]);
  });

  it("names no days when the server gives none, and counts one as one", () => {
    expect(
      words(
        expenseResults({
          totalExpenses: 1,
          from: null,
          to: null,
          totals: [{ currency: "USD", expenses: 1, spent: 412.6 }],
        }),
      ),
    ).toEqual(["1 expense", "Spent 412.60"]);
    expect(words(expenseResults({ totalExpenses: 0, totals: [] }))).toEqual([
      "0 expenses",
    ]);
  });

  it("names each currency when the list holds several, or the business bills in more than one", () => {
    const answer = {
      totalExpenses: 5,
      from: "2026-01-01",
      to: "2026-12-31",
      totals: [
        { currency: "EUR", expenses: 2, spent: 980 },
        { currency: "USD", expenses: 3, spent: 1240 },
      ],
    };
    expect(words(expenseResults(answer))).toEqual([
      "5 expenses from Jan 1 – Dec 31, 2026",
      "Spent 980.00 EUR, 1,240.00 USD",
    ]);
    expect(
      words(
        expenseResults(
          { ...answer, totalExpenses: 3, totals: answer.totals.slice(1) },
          ["USD", "EUR"],
        ),
      )[1],
    ).toBe("Spent 1,240.00 USD");
  });

  it("groups rupees in lakhs", () => {
    expect(
      words(
        expenseResults({
          totalExpenses: 4,
          totals: [{ currency: "INR", expenses: 4, spent: 125000 }],
        }),
      )[1],
    ).toBe("Spent 1,25,000.00");
  });

  // An expense can be paid in a currency the business doesn't bill in.
  it("names a currency that isn't the business's main one, even alone", () => {
    const paidInDollars = {
      totalExpenses: 1,
      totals: [{ currency: "USD", expenses: 1, spent: 120 }],
    };
    expect(words(expenseResults(paidInDollars, ["INR"]))[1]).toBe(
      "Spent 120.00 USD",
    );
    expect(
      words(
        expenseResults(
          {
            totalExpenses: 4,
            totals: [{ currency: "INR", expenses: 4, spent: 125000 }],
          },
          ["INR"],
        ),
      )[1],
    ).toBe("Spent 1,25,000.00");
    // Until the business's currencies are known, one currency goes unnamed.
    expect(words(expenseResults(paidInDollars))[1]).toBe("Spent 120.00");
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
