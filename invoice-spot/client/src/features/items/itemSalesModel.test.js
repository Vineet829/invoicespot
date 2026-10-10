import { describe, expect, it } from "vitest";
import {
  itemDetailQuery,
  itemDetailSearch,
  itemSalesLink,
  itemSalesQuery,
  itemSalesSearch,
  itemName,
  readItemDetail,
  readItemSales,
  shareLabel,
  shareLabels,
  soldCount,
} from "./itemSalesModel";
import fixture from "./pages/ItemSalesPage.fixture.json";

const read = (search) => readItemSales(new URLSearchParams(search));
const readDetail = (search) => readItemDetail(new URLSearchParams(search));

describe("sales by item in the URL", () => {
  it("opens at every date, ten to a page", () => {
    expect(read("")).toEqual({ period: "", currency: "", page: 1, size: 10 });
    expect(itemSalesSearch(read("")).toString()).toBe("");
  });

  it("reads and writes the period, the currency and the page", () => {
    const list = read("size=25&page=2&currency=EUR&period=12m");
    expect(list).toEqual({
      period: "12m",
      currency: "EUR",
      page: 2,
      size: 25,
    });
    expect(itemSalesSearch(list).toString()).toBe(
      "period=12m&currency=EUR&page=2&size=25",
    );
    expect(read("period=ytd&currency=eur")).toEqual({
      period: "",
      currency: "",
      page: 1,
      size: 10,
    });
  });

  it("asks for the period by name and the currency when chosen", () => {
    expect(itemSalesQuery(read("period=this-year&page=3"))).toEqual({
      period: "this-year",
      page: 3,
      size: 10,
    });
    expect(itemSalesQuery(read("currency=EUR"))).toEqual({
      currency: "EUR",
      page: 1,
      size: 10,
    });
  });
});

describe("an item's sales page", () => {
  it("reads the item's name, the period and the currency", () => {
    expect(
      readDetail("name=+Consulting+%28hourly%29+&period=90d&currency=EUR"),
    ).toEqual({ name: "Consulting (hourly)", period: "90d", currency: "EUR" });
    expect(readDetail("period=soon")).toEqual({
      name: "",
      period: "",
      currency: "",
    });
  });

  it("opens from a name, for a period and a currency when one was chosen", () => {
    expect(itemSalesLink("Consulting (hourly)", "90d")).toBe(
      "/items/sales/item?name=Consulting+%28hourly%29&period=90d",
    );
    expect(itemSalesLink("Logo & identity", "this-year", "EUR")).toBe(
      "/items/sales/item?name=Logo+%26+identity&period=this-year&currency=EUR",
    );
    expect(itemSalesLink("Copywriting", "")).toBe(
      "/items/sales/item?name=Copywriting",
    );
    expect(
      itemDetailSearch(readDetail("currency=EUR&name=Copywriting")).toString(),
    ).toBe("name=Copywriting&currency=EUR");
  });

  it("asks for the item by name, with the period and currency when given", () => {
    expect(itemDetailQuery(readDetail("name=Copywriting&period=30d"))).toEqual({
      name: "Copywriting",
      period: "30d",
    });
    expect(itemDetailQuery(readDetail("name=Copywriting"))).toEqual({
      name: "Copywriting",
    });
  });
});

describe("the figures", () => {
  it("names an item as its lines spell it, and lines saved without one as other lines", () => {
    expect(itemName(" Copywriting ")).toBe("Copywriting");
    expect(itemName("Other lines")).toBe("Other lines");
    expect(itemName(" ")).toBe("Other lines");
    expect(itemName(null)).toBe("Other lines");
  });

  it("gives an item's share of income as a whole percentage, and a tiny one as under 1%", () => {
    expect(shareLabel(0.2835)).toBe("28%");
    expect(shareLabel(1)).toBe("100%");
    expect(shareLabel(0.004)).toBe("<1%");
    expect(shareLabel(0)).toBe("0%");
    expect(shareLabel(-0.1)).toBe("0%");
  });

  // Each share rounded on its own, the demo's ten items came to 98%.
  it("rounds the shares of every item so they add up to 100%", () => {
    const labels = shareLabels(fixture.sales.items, fixture.sales.total);
    expect(labels).toEqual([
      "28%",
      "15%",
      "11%",
      "10%",
      "8%",
      "8%",
      "8%",
      "6%",
      "4%",
      "2%",
    ]);
    expect(labels.reduce((sum, label) => sum + parseInt(label, 10), 0)).toBe(
      100,
    );
    // Three equal thirds: the point left over goes to the first.
    const thirds = [{ amount: 100 }, { amount: 100 }, { amount: 100 }];
    expect(shareLabels(thirds, 300)).toEqual(["34%", "33%", "33%"]);
  });

  it("counts the items on other pages as one share, so a page's shares stay whole", () => {
    // Two items of 1,000.00: the other 334.00 rounds up, these down.
    expect(shareLabels([{ amount: 333 }, { amount: 333 }], 1000)).toEqual([
      "33%",
      "33%",
    ]);
  });

  it("shows a share that rounds to nothing as under 1%, and an item that sold nothing as 0%", () => {
    expect(
      shareLabels([{ amount: 9990 }, { amount: 10 }, { amount: 0 }], 10000),
    ).toEqual(["100%", "<1%", "0%"]);
  });

  it("rounds each share on its own when a credit note leaves an item below nothing", () => {
    expect(
      shareLabels(
        [
          { amount: 1200, share: 1.2 },
          { amount: -200, share: -0.2 },
        ],
        1000,
      ),
    ).toEqual(["120%", "0%"]);
    expect(shareLabels([{ amount: 0, share: 0 }], 0)).toEqual(["0%"]);
  });

  it("counts what was sold to two places at most", () => {
    expect(soldCount(28)).toBe("28");
    expect(soldCount(2.5)).toBe("2.5");
    expect(soldCount(1234.567)).toBe("1,234.57");
    expect(soldCount(null)).toBe("0");
  });
});
