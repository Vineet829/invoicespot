import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { createRoot } from "react-dom/client";
import { act } from "react-dom/test-utils";
import fixture from "./ItemSalesPage.fixture.json";

// Sales by item and an item's own sales page, as the app draws them,
// through its own store and RTK Query, with only fetch faked. The fixture
// is the demo account's last 90 days to Oct 4, 2026: every item sold,
// adding up to the overview's income of 64,194.00, and the sales of its top
// item. The signed-in shell is left out.
vi.mock("../../dashboard/components/DashboardShell", () => ({
  default: ({ children }) => children,
}));

window.IS_REACT_ACT_ENVIRONMENT = true;

let requests;
let address;
let root;
let container;
beforeEach(() => {
  vi.resetModules();
  vi.stubEnv("VITE_API_URL", "http://localhost:3001/api/v1");
  requests = [];
});
afterEach(() => {
  act(() => root.unmount());
  container.remove();
  vi.unstubAllGlobals();
  vi.unstubAllEnvs();
});

const json = (body, status = 200) =>
  new Response(JSON.stringify(body), {
    status,
    headers: { "content-type": "application/json" },
  });

// Answers each request by its path; `answers` replaces any of them with a
// body or a Response, a promise of either, or a function of the request's
// address giving one.
const serve = (answers = {}) =>
  vi.stubGlobal(
    "fetch",
    vi.fn(async (request) => {
      const url = new URL(request.url);
      requests.push(`${url.pathname}${url.search}`);
      const answer = {
        "/item/sales": fixture.sales,
        "/item/sales/detail": fixture.detail,
        "/businesses": { businesses: [] },
        ...answers,
      }[url.pathname.replace("/api/v1", "")];
      const body = await (typeof answer === "function" ? answer(url) : answer);
      if (body instanceof Response) return body;
      return body ? json(body) : json({ message: "Not found" }, 404);
    }),
  );

const listed = (page) =>
  vi.waitFor(() => expect(page.querySelector("tbody")).not.toBeNull());

const PAGES = {
  list: () => import("./ItemSalesPage"),
  item: () => import("./ItemSalesDetailPage"),
};
const open = async (path, answers, ready = listed) => {
  serve(answers);
  const { Provider } = await import("react-redux");
  const { MemoryRouter, useLocation } = await import("react-router-dom");
  const { store } = await import("../../../app/store");
  const { default: Page } =
    await PAGES[path.startsWith("/items/sales/item") ? "item" : "list"]();
  const Address = () => {
    address = useLocation().search;
    return null;
  };
  container = document.body.appendChild(document.createElement("div"));
  root = createRoot(container);
  await act(async () => {
    root.render(
      <Provider store={store}>
        <MemoryRouter initialEntries={[path]}>
          <Page />
          <Address />
        </MemoryRouter>
      </Provider>,
    );
  });
  await act(() => ready(container));
  return container;
};

const click = (element) =>
  act(async () => {
    element.dispatchEvent(
      new MouseEvent("click", { bubbles: true, cancelable: true }),
    );
  });
const asked = (path) =>
  requests.filter((request) => request.startsWith(`/api/v1${path}?`));
const table = (element) =>
  [...element.querySelectorAll("tbody tr")].map((row) =>
    [...row.children].map((cell) => cell.textContent),
  );
const headings = (element) =>
  [...element.querySelectorAll("thead th")].map((th) => th.textContent);
const links = (element) =>
  [...element.querySelectorAll("tbody a")].map((link) => [
    link.textContent,
    link.getAttribute("href"),
  ]);
// The styled dropdown with this label: its trigger, which carries the value
// chosen.
const select = (page, label) =>
  [...page.querySelectorAll("label")]
    .find((element) => element.textContent.startsWith(label))
    ?.querySelector('[role="combobox"]');
const options = async (combobox) => {
  await act(async () => combobox.click());
  const words = [...document.querySelectorAll('[role="option"]')].map(
    (option) => option.firstChild.textContent,
  );
  await act(async () =>
    combobox.dispatchEvent(
      new KeyboardEvent("keydown", { key: "Escape", bubbles: true }),
    ),
  );
  return words;
};
const choose = async (combobox, value) => {
  await act(async () => combobox.click());
  await act(async () =>
    document.querySelector(`[role="option"][data-value="${value}"]`).click(),
  );
};
const C = "00000000-0000-0000-0000-00000000";
const MOTION = "/items/sales/item?name=Motion+graphics+%28per+15+seconds%29";

it("lists what each item sold in the period, its share of income and the totals, each opening its sales", async () => {
  const page = await open("/items/sales?period=90d");
  expect(document.title).toBe("Sales by item — Invoice Spot");
  expect(page.querySelector("h1").textContent).toBe("Items.");
  expect(
    [...page.querySelectorAll('nav[aria-label="Items views"] a')].map((tab) => [
      tab.textContent,
      tab.getAttribute("aria-current"),
    ]),
  ).toEqual([
    ["All items", null],
    ["Sales by item", "page"],
  ]);
  expect(asked("/item/sales")).toEqual([
    "/api/v1/item/sales?period=90d&page=1&size=10",
  ]);
  expect(page.textContent).toContain(
    "What each item sold in Jul 7 – Oct 4, 2026, before tax, less what credit notes took back.",
  );
  expect(select(page, "Sold").textContent).toContain("Last 90 days");
  expect(headings(page)).toEqual(["Item", "Sold", "Amount", "Share of income"]);
  // The overview's top five lead, then the rest, then the lines saved
  // without a name, and the totals: everything sold is the income.
  expect(table(page)).toEqual([
    ["Motion graphics (per 15 seconds)", "28", "18,200.00", "28%"],
    ["Logo and identity system", "3", "9,280.00", "15%"],
    ["Consulting (hourly)", "52", "7,140.00", "11%"],
    ["Front-end build (per page)", "17", "6,460.00", "10%"],
    ["Brand strategy workshop", "3", "5,400.00", "8%"],
    ["Website maintenance (monthly)", "6", "5,100.00", "8%"],
    ["Photography (half day)", "4", "4,800.00", "8%"],
    ["Copywriting (per page)", "22", "3,960.00", "6%"],
    ["Social media kit", "2", "2,400.00", "4%"],
    ["Other lines", "5", "1,454.00", "2%"],
    ["All 10 items", "", "64,194.00", "100%"],
  ]);
  // The shares add up to the totals' 100%.
  expect(
    table(page)
      .slice(0, -1)
      .reduce((sum, row) => sum + parseInt(row[3], 10), 0),
  ).toBe(100);
  // Each item opens its own sales for the same period, the lines saved
  // without a name too; the totals open nothing.
  expect(links(page)).toHaveLength(10);
  expect(links(page)[9]).toEqual([
    "Other lines",
    "/items/sales/item?name=Other+lines&period=90d",
  ]);
  expect(links(page)[0]).toEqual([
    "Motion graphics (per 15 seconds)",
    `${MOTION}&period=90d`,
  ]);
  expect(links(page)[2]).toEqual([
    "Consulting (hourly)",
    "/items/sales/item?name=Consulting+%28hourly%29&period=90d",
  ]);
  expect(page.querySelector('[role="status"]').textContent).toBe("1–10 of 10");
});

it("keeps the period, the currency and the page in the address, and the item pages follow them", async () => {
  const page = await open("/items/sales?period=90d", {
    "/item/sales": (url) => ({
      ...fixture.sales,
      currencies: ["USD", "EUR"],
      currency: url.searchParams.get("currency") || "USD",
      totalItems: 12,
      numberOfPages: 2,
    }),
  });
  // Several currencies: one at a time, the amounts naming theirs.
  expect(table(page)[0][2]).toBe("USD18,200.00");
  expect(await options(select(page, "Currency"))).toEqual(["USD", "EUR"]);
  await choose(select(page, "Currency"), "EUR");
  expect(address).toBe("?period=90d&currency=EUR");
  await act(() =>
    vi.waitFor(() =>
      expect(asked("/item/sales")).toContain(
        "/api/v1/item/sales?period=90d&currency=EUR&page=1&size=10",
      ),
    ),
  );
  expect(links(page)[0][1]).toBe(`${MOTION}&period=90d&currency=EUR`);
  await click(page.querySelector('button[aria-label="Next page"]'));
  expect(address).toBe("?period=90d&currency=EUR&page=2");
  await choose(select(page, "Sold"), "last-month");
  expect(address).toBe("?period=last-month&currency=EUR");
  await act(() =>
    vi.waitFor(() =>
      expect(asked("/item/sales")).toContain(
        "/api/v1/item/sales?period=last-month&currency=EUR&page=1&size=10",
      ),
    ),
  );
});

it("says when nothing was sold in the period", async () => {
  const page = await open(
    "/items/sales?period=30d",
    {
      "/item/sales": {
        ...fixture.sales,
        total: 0,
        totalItems: 0,
        numberOfPages: 0,
        items: [],
      },
    },
    (element) =>
      vi.waitFor(() =>
        expect(element.textContent).toContain(
          "Nothing was sold in this period.",
        ),
      ),
  );
  expect(page.textContent).toContain(
    "Items show up here once they are on an invoice or receipt you issue, with what each brought in.",
  );
  expect(page.querySelector("table")).toBeNull();
  expect(select(page, "Sold")).toBeTruthy();
});

it("holds the table's place while it loads, and says when it can't be loaded", async () => {
  let answer;
  const page = await open(
    "/items/sales?period=90d",
    {
      "/item/sales": new Promise((resolve) => {
        answer = resolve;
      }),
    },
    () => Promise.resolve(),
  );
  expect(page.querySelector('[role="status"]').textContent).toBe(
    "Loading workspace data…",
  );
  await act(async () => answer(json({ message: "Try later." }, 500)));
  await act(() =>
    vi.waitFor(() =>
      expect(page.querySelector('[role="alert"]')).not.toBeNull(),
    ),
  );
  expect(page.querySelector('[role="alert"]').textContent).toContain(
    "We couldn’t load this page.Try later.",
  );
  expect(page.querySelector("table")).toBeNull();
});

it("shows what an item brought in over the period, who bought it and each line, each line opening its document", async () => {
  const page = await open(`${MOTION}&period=90d`);
  expect(document.title).toBe(
    "Motion graphics (per 15 seconds) sales — Invoice Spot",
  );
  expect(page.querySelector("h1").textContent).toBe("Item sales.");
  expect(asked("/item/sales/detail")).toEqual([
    "/api/v1/item/sales/detail?name=Motion+graphics+%28per+15+seconds%29&period=90d",
  ]);
  const [figures, linesPanel] = page.querySelectorAll("section");
  expect(figures.querySelector("h2").textContent).toBe(
    "Motion graphics (per 15 seconds)",
  );
  expect(figures.textContent).toContain(
    "Sold Jul 7 – Oct 4, 2026, before tax.",
  );
  expect(figures.textContent).toContain(
    "Brought in18,200.0028 sold, 28% of income, on 6 documents",
  );
  // Who bought it, largest first, each opening the client.
  const buyers = figures.querySelector("table");
  expect(headings(buyers)).toEqual(["Client", "Sold", "Amount"]);
  expect(table(buyers)).toEqual([
    ["Quill & Co. Publishing", "12", "7,800.00"],
    ["Kestrel Outdoor", "8", "5,200.00"],
    ["Atlas Robotics", "4", "2,600.00"],
    ["Oak Street Bakery", "4", "2,600.00"],
  ]);
  expect(links(buyers)[0]).toEqual([
    "Quill & Co. Publishing",
    `/single-customer/${C}c107`,
  ]);
  // Its lines, newest first, each opening its document.
  expect(linesPanel.querySelector("h2").textContent).toBe("Lines");
  expect(linesPanel.textContent).toContain(
    "Each line it was sold on, newest first.",
  );
  expect(headings(linesPanel)).toEqual([
    "Document",
    "Type",
    "Issued",
    "Client",
    "Sold",
    "Amount",
  ]);
  expect(table(linesPanel)).toEqual([
    [
      "REC-0304",
      "Receipt",
      "Oct 2, 2026",
      "Quill & Co. Publishing",
      "8",
      "5,200.00",
    ],
    [
      "INV-1035",
      "Invoice",
      "Oct 1, 2026",
      "Oak Street Bakery",
      "4",
      "2,600.00",
    ],
    ["INV-1034", "Invoice", "Sep 28, 2026", "Atlas Robotics", "4", "2,600.00"],
    ["REC-0303", "Receipt", "Sep 15, 2026", "Kestrel Outdoor", "4", "2,600.00"],
    [
      "INV-1022",
      "Invoice",
      "Aug 18, 2026",
      "Quill & Co. Publishing",
      "4",
      "2,600.00",
    ],
    ["INV-1017", "Invoice", "Jul 20, 2026", "Kestrel Outdoor", "4", "2,600.00"],
  ]);
  expect(links(linesPanel)[0]).toEqual([
    "REC-0304",
    "/document/00000000-0000-0000-0000-000000000304",
  ]);
});

it("says how many lines it leaves out", async () => {
  const page = await open(`${MOTION}&period=12m`, {
    "/item/sales/detail": { ...fixture.detail, linesTotal: 63 },
  });
  expect(page.querySelectorAll("section")[1].textContent).toContain(
    "The latest 6 of 63 lines, newest first.",
  );
});

it("keeps the item, the period and the currency in the address", async () => {
  const page = await open(`${MOTION}&period=90d`, {
    "/item/sales/detail": (url) => ({
      ...fixture.detail,
      currencies: ["USD", "EUR"],
      currency: url.searchParams.get("currency") || "USD",
    }),
  });
  await choose(select(page, "Sold"), "this-year");
  expect(address).toBe(
    "?name=Motion+graphics+%28per+15+seconds%29&period=this-year",
  );
  await choose(select(page, "Currency"), "EUR");
  expect(address).toBe(
    "?name=Motion+graphics+%28per+15+seconds%29&period=this-year&currency=EUR",
  );
  await act(() =>
    vi.waitFor(() =>
      expect(asked("/item/sales/detail")).toContain(
        "/api/v1/item/sales/detail?name=Motion+graphics+%28per+15+seconds%29&period=this-year&currency=EUR",
      ),
    ),
  );
});

it("says when the item sold nothing in the period", async () => {
  const page = await open(
    `${MOTION}&period=30d`,
    {
      "/item/sales/detail": {
        ...fixture.detail,
        quantity: 0,
        amount: 0,
        share: 0,
        documents: 0,
        clients: [],
        lines: [],
        linesTotal: 0,
      },
    },
    (element) =>
      vi.waitFor(() =>
        expect(element.textContent).toContain("Nothing sold in this period."),
      ),
  );
  expect(page.textContent).toContain("No one bought it in this period.");
  expect(page.textContent).toContain(
    "Brought in0.000 sold, 0% of income, on 0 documents",
  );
  expect(page.querySelector("table")).toBeNull();
});

it("says when the item's sales can't be loaded, with a way to try again", async () => {
  let fails = true;
  const page = await open(
    `${MOTION}&period=90d`,
    {
      "/item/sales/detail": () =>
        fails ? json({ message: "Try later." }, 500) : fixture.detail,
    },
    (element) =>
      vi.waitFor(() =>
        expect(element.querySelector('[role="alert"]')).not.toBeNull(),
      ),
  );
  const alert = page.querySelector('[role="alert"]');
  expect(alert.textContent).toContain("We couldn’t load this page.");
  fails = false;
  await click(
    [...alert.querySelectorAll("button")].find(
      (button) => button.textContent === "Try again",
    ),
  );
  await act(() => listed(page));
  expect(page.textContent).toContain("Brought in18,200.00");
});

it("lists the lines saved without an item name as other lines", async () => {
  const page = await open("/items/sales/item?name=Other+lines&period=30d", {
    "/item/sales/detail": {
      ...fixture.detail,
      item: "Other lines",
      quantity: 5,
      amount: 1454,
      share: 0.0227,
      documents: 3,
      clients: [
        {
          clientId: `${C}c107`,
          client: "Quill & Co. Publishing",
          quantity: 5,
          amount: 1454,
        },
      ],
      lines: [fixture.detail.lines[0]],
      linesTotal: 1,
    },
  });
  expect(asked("/item/sales/detail")).toEqual([
    "/api/v1/item/sales/detail?name=Other+lines&period=30d",
  ]);
  const figures = page.querySelector("section");
  expect(figures.querySelector("h2").textContent).toBe("Other lines");
  expect(figures.textContent).toContain(
    "Sold Jul 7 – Oct 4, 2026, before tax, on lines saved without an item name.",
  );
  expect(figures.textContent).toContain(
    "Brought in1,454.005 sold, 2% of income, on 3 documents",
  );
});

it("holds the item's place while its sales load", async () => {
  let answer;
  const page = await open(
    `${MOTION}&period=90d`,
    {
      "/item/sales/detail": new Promise((resolve) => {
        answer = resolve;
      }),
    },
    () => Promise.resolve(),
  );
  expect(page.querySelector('[role="status"]').textContent).toBe(
    "Loading workspace data…",
  );
  expect(page.querySelector("section")).toBeNull();
  expect(page.textContent).not.toContain("Brought in");
  await act(async () => answer(fixture.detail));
  await act(() => listed(page));
  expect(page.querySelector('[role="status"]')).toBeNull();
  expect(page.textContent).toContain("Brought in18,200.00");
});

it("asks for nothing without an item, and points to Sales by item", async () => {
  const page = await open("/items/sales/item?period=90d", {}, (element) =>
    vi.waitFor(() =>
      expect(element.textContent).toContain("No item was chosen."),
    ),
  );
  expect(asked("/item/sales/detail")).toEqual([]);
  expect(page.querySelector('a[href="/items/sales"]').textContent).toBe(
    "Sales by item",
  );
});
