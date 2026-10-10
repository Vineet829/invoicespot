import { describe, expect, it } from "vitest";
import { itemListSearch, matchingItems, readItemList } from "./itemFilters";

const items = [
  { _id: "i-1", name: "Motion graphics (per 15 seconds)", unit: null },
  { _id: "i-2", name: "Consulting", unit: "hour" },
  { _id: "i-3", name: "Logo and identity system" },
  { _id: "i-4", name: "Front-end build", unit: "page" },
];

describe("the Items list's address", () => {
  it("reads the search, and nothing without one", () => {
    expect(readItemList(new URLSearchParams("q=Consulting"))).toEqual({
      q: "Consulting",
    });
    expect(readItemList(new URLSearchParams("q=Motion+graphics"))).toEqual({
      q: "Motion graphics",
    });
    expect(readItemList(new URLSearchParams(""))).toEqual({ q: "" });
  });

  it("keeps the search, and leaves an empty one out", () => {
    expect(
      itemListSearch({ q: "Motion graphics (per 15 seconds)" }).toString(),
    ).toBe("q=Motion+graphics+%28per+15+seconds%29");
    expect(itemListSearch({ q: "" }).toString()).toBe("");
  });

  it("reads back what it writes", () => {
    const q = "Rock & roll: 50% off";
    expect(readItemList(itemListSearch({ q }))).toEqual({ q });
  });
});

describe("searching items", () => {
  const names = (found) => found.map((item) => item.name);

  it("finds an item by any part of its name, in any case", () => {
    expect(names(matchingItems(items, "motion GRAPHICS"))).toEqual([
      "Motion graphics (per 15 seconds)",
    ]);
    expect(
      names(matchingItems(items, "Motion graphics (per 15 seconds)")),
    ).toEqual(["Motion graphics (per 15 seconds)"]);
    expect(names(matchingItems(items, "system"))).toEqual([
      "Logo and identity system",
    ]);
  });

  it("finds an item by its unit", () => {
    expect(names(matchingItems(items, "hour"))).toEqual(["Consulting"]);
    expect(names(matchingItems(items, "Page"))).toEqual(["Front-end build"]);
  });

  it("finds nothing that doesn't match, and every item without a search", () => {
    expect(matchingItems(items, "photography")).toEqual([]);
    expect(matchingItems(items, "")).toBe(items);
    expect(matchingItems(items, "   ")).toBe(items);
    expect(names(matchingItems(items, "  consulting "))).toEqual([
      "Consulting",
    ]);
  });
});
