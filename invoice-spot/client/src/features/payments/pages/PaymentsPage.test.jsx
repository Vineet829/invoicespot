import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { createRoot } from "react-dom/client";
import { act } from "react-dom/test-utils";
import fixture from "./PaymentsPage.fixture.json";

// The page as the app draws it, through its own store and RTK Query, with
// only fetch faked. The answers follow the server's shapes for the demo
// account on Oct 4, 2026: the fixture has the payments list, and the
// summary of the last 90 days, the same 44,562.29 as the overview's
// Received. The signed-in shell is left out.
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

const { payments } = fixture;

const summary = {
  success: true,
  currency: "USD",
  currencies: ["USD"],
  outstanding: {
    total: 24285.79,
    overdue: 6326.03,
    notDue: 17959.76,
    invoices: 10,
    overdueInvoices: 3,
  },
  receivedLast30Days: { total: 22763.97, payments: 7 },
};

// The days the server applies for the last 90 days.
const ninetyDays = (address) =>
  address.searchParams.get("period") === "90d"
    ? { from: "2026-07-07", to: "2026-10-04" }
    : {};

// Answers each request by its path; `answers` replaces any of them with a
// body or a Response, a promise of either, or a function of the request's
// address giving one.
// Receipts alone are 2 of the 18.
const serve = (answers = {}) =>
  vi.stubGlobal(
    "fetch",
    vi.fn(async (request) => {
      const url = new URL(request.url);
      requests.push(`${url.pathname}${url.search}`);
      const answer = {
        "/payments": (address) =>
          address.searchParams.get("method") === "receipt"
            ? {
                ...payments,
                ...ninetyDays(address),
                total: 2,
                numberOfPages: 1,
                payments: [payments.payments[1]],
                totals: [
                  {
                    currency: "USD",
                    payments: 2,
                    received: 12011.28,
                    receipts: 2,
                  },
                ],
              }
            : { ...payments, ...ninetyDays(address) },
        "/document/summary": summary,
        "/payments/summary": fixture.summary,
        "/businesses": { businesses: [] },
        ...answers,
      }[url.pathname.replace("/api/v1", "")];
      const body = await (typeof answer === "function" ? answer(url) : answer);
      if (body instanceof Response) return body;
      return body ? json(body) : json({ message: "Not found" }, 404);
    }),
  );

// The list's own table, below the summary's.
const listTable = (page) =>
  [...page.querySelectorAll("table")].find(
    (table) => table.querySelector("caption").textContent === "Payments",
  );
const listed = (page) => vi.waitFor(() => expect(listTable(page)).toBeTruthy());

const open = async (path, answers) => {
  serve(answers);
  const { Provider } = await import("react-redux");
  const { MemoryRouter, useLocation } = await import("react-router-dom");
  const { store } = await import("../../../app/store");
  const { default: PaymentsPage } = await import("./PaymentsPage");
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
          <PaymentsPage />
          <Address />
        </MemoryRouter>
      </Provider>,
    );
  });
  await act(() => listed(container));
  return container;
};

// A sortable column's header holds a button with its name.
const headings = (page) =>
  [...listTable(page).querySelectorAll("thead th")].map(
    (header) =>
      header.querySelector("button")?.textContent ?? header.textContent,
  );
const rows = (page) =>
  [...listTable(page).querySelectorAll("tbody tr")].map((row) =>
    [...row.children].map((cell) => cell.textContent),
  );
// The styled dropdown with this label: its trigger, which carries the value
// chosen.
const select = (page, label) =>
  [...page.querySelectorAll("label")]
    .find((element) => element.textContent.startsWith(label))
    ?.querySelector('[role="combobox"]');
// What the dropdown offers, opening it to look and closing it again.
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
// Opens the dropdown and picks the option with this value.
const choose = async (combobox, value) => {
  await act(async () => combobox.click());
  await act(async () =>
    document.querySelector(`[role="option"][data-value="${value}"]`).click(),
  );
};

it("lists payments, receipts and refunds as the Received figure opens them", async () => {
  const page = await open("/payments?period=90d");
  expect(requests).toContain(
    "/api/v1/payments?page=1&size=10&sort=date&dir=desc&period=90d",
  );
  expect(headings(page)).toEqual([
    "#",
    "Date paid",
    "Customer",
    "Document",
    "Method",
    "Note",
    "Amount",
    "View",
  ]);
  expect(rows(page)).toEqual([
    [
      "1",
      "Oct 4, 2026",
      "Harbor & Pine Architects",
      "INV-1038",
      "Bank Transfer",
      "Deposit",
      "USD262.76",
      "View",
    ],
    [
      "2",
      "Oct 2, 2026",
      "Quill & Co. Publishing",
      "REC-0304",
      "On a receipt",
      "—",
      "USD9,598.88",
      "View",
    ],
    [
      "3",
      "Sep 20, 2026",
      "Tidewater Logistics",
      "INV-1019",
      "Refund · Credit Card",
      "REF-0042",
      "USD-150.00",
      "View",
    ],
  ]);
  const links = [
    ...listTable(page).querySelectorAll("tbody tr")[1].querySelectorAll("a"),
  ];
  expect(links.map((link) => link.getAttribute("href"))).toEqual([
    "/document/doc-304",
    "/document/doc-304",
  ]);
  expect(links[1].getAttribute("aria-label")).toBe("View REC-0304");
  expect(page.querySelector('[role="status"]').textContent).toBe("1–3 of 18");
});

const line = (page) =>
  [...page.querySelectorAll("button")]
    .find((button) => button.textContent === "Clear filters")
    ?.parentElement.querySelector("p").textContent;

it("says how many payments the filters found and what came in, with receipts counted apart", async () => {
  const page = await open("/payments?period=90d");
  expect(line(page)).toBe(
    "18 payments from Jul 7 – Oct 4, 2026, ·Received 44,562.29, ·2 of them on receipts",
  );
  // The totals count the receipts, so nothing else is asked.
  expect(
    requests.filter((request) => request.startsWith("/api/v1/payments?")),
  ).toEqual(["/api/v1/payments?page=1&size=10&sort=date&dir=desc&period=90d"]);
  const clear = [...page.querySelectorAll("button")].find(
    (button) => button.textContent === "Clear filters",
  );
  await act(async () => clear.click());
  expect(address).toBe("");
  expect(page.textContent).not.toContain("Clear filters");
  // Clear filters is gone, so focus moves to the search box.
  expect(document.activeElement).toBe(
    page.querySelector('input[type="search"]'),
  );
});

// At 5 in the morning of Jan 1, 2027 in Kolkata it is still Dec 31, 2026 in
// Los Angeles, where the business is: this year is still 2026 there. The
// line above the list names the days the server took.
it("lists this year's payments up to the business's today", async () => {
  const zone = process.env.TZ;
  process.env.TZ = "Asia/Kolkata";
  vi.useFakeTimers({ toFake: ["Date"] });
  vi.setSystemTime(new Date("2027-01-01T05:00:00+05:30"));
  try {
    const page = await open("/payments?period=this-year", {
      "/businesses": {
        businesses: [
          {
            _id: "b-1",
            name: "Studio Demo",
            active: true,
            timeZone: "America/Los_Angeles",
          },
        ],
      },
      "/payments": (url) => ({
        ...payments,
        from: url.searchParams.get("from"),
        to: url.searchParams.get("to"),
      }),
    });
    await act(() =>
      vi.waitFor(() =>
        expect(line(page)).toMatch(/^18 payments from Jan 1 – Dec 31, 2026,/),
      ),
    );
    expect(
      requests
        .filter((request) => request.startsWith("/api/v1/payments?"))
        .at(-1),
    ).toBe(
      "/api/v1/payments?page=1&size=10&sort=date&dir=desc&from=2026-01-01&to=2026-12-31",
    );
  } finally {
    vi.useRealTimers();
    if (zone === undefined) delete process.env.TZ;
    else process.env.TZ = zone;
  }
});

it("says only what the next method's answer counts", async () => {
  const page = await open("/payments?period=90d", {
    "/payments": (url) =>
      url.searchParams.get("method") === "Bank Transfer"
        ? {
            ...payments,
            ...ninetyDays(url),
            total: 1,
            numberOfPages: 1,
            payments: [payments.payments[0]],
            totals: [
              { currency: "USD", payments: 1, received: 262.76, receipts: 0 },
            ],
          }
        : { ...payments, ...ninetyDays(url) },
  });
  expect(line(page)).toContain("2 of them on receipts");
  await choose(select(page, "Payment method"), "Bank Transfer");
  await act(() =>
    vi.waitFor(() =>
      expect(line(page)).toBe(
        "1 payment from Jul 7 – Oct 4, 2026, ·Received 262.76",
      ),
    ),
  );
});

it("filters by method, date, and sorts as the menus offer", async () => {
  const page = await open("/payments");
  expect(await options(select(page, "Payment method"))).toEqual([
    "All methods",
    "Bank Transfer",
    "Credit Card",
    "Mobile Money",
    "PayPal",
    "Others",
    "On a receipt",
  ]);
  expect(await options(select(page, "Sort payments"))).toEqual([
    "Newest first",
    "Oldest first",
    "Highest amount",
    "Lowest amount",
    "Customer A–Z",
  ]);
  expect((await options(select(page, "Date paid")))[1]).toBe("Last 30 days");
  await choose(select(page, "Payment method"), "receipt");
  expect(address).toBe("?method=receipt");
  await act(() =>
    vi.waitFor(() =>
      expect(requests).toContain(
        "/api/v1/payments?page=1&size=10&sort=date&dir=desc&method=receipt",
      ),
    ),
  );
  // Sorting by a column's header turns it around on the next click.
  const amount = [...page.querySelectorAll("thead button")].find(
    (button) => button.textContent === "Amount",
  );
  await act(async () => amount.click());
  expect(address).toBe("?method=receipt&sort=amount&dir=desc");
});

it("sits beside All documents, Receivables, Sales and Recurring", async () => {
  const page = await open("/payments");
  const tabs = [
    ...page.querySelectorAll('nav[aria-label="Documents views"] a'),
  ];
  expect(
    tabs.map((tab) => [
      tab.textContent,
      tab.getAttribute("href"),
      tab.getAttribute("aria-current"),
    ]),
  ).toEqual([
    ["All documents", "/documents", null],
    ["Receivables", "/receivables", null],
    ["Sales", "/sales", null],
    ["Payments", "/payments", "page"],
    ["Recurring", "/recurring", null],
  ]);
  expect(page.querySelector("h1").textContent).toBe("Documents.");
});

it("shows the money bar, with the last 30 days' payments as the part shown", async () => {
  const page = await open("/payments?period=30d");
  const bar = () =>
    page.querySelector('[aria-label="What is owed and what came in"]');
  await act(() => vi.waitFor(() => expect(bar()).not.toBeNull()));
  expect(
    bar()
      .querySelector('a[href="/payments?period=30d"]')
      .getAttribute("aria-current"),
  ).toBe("true");
  expect(bar().textContent).toContain("22,763.97");
});

it.each(["/payments?period=90d", "/payments?period=30d&method=receipt"])(
  "doesn't ring the last 30 days' payments for %s",
  async (path) => {
    const page = await open(path);
    const bar = () =>
      page.querySelector('[aria-label="What is owed and what came in"]');
    await act(() => vi.waitFor(() => expect(bar()).not.toBeNull()));
    expect(
      [...bar().querySelectorAll("a")].map((link) =>
        link.getAttribute("aria-current"),
      ),
    ).toEqual([null, null, null]);
  },
);

// The summary above the list, and its tables.
const summed = (page) =>
  [...page.querySelectorAll("section")].find(
    (section) => section.querySelector("h2")?.textContent === "What came in",
  );
const summedUp = (page) =>
  act(() =>
    vi.waitFor(() => expect(summed(page)?.querySelector("table")).toBeTruthy()),
  );
const breakdown = (page, title) =>
  [...summed(page).querySelectorAll("h3")].find(
    (heading) => heading.textContent === title,
  ).parentElement;
const cells = (element) =>
  [...element.querySelectorAll("tbody tr")].map((row) =>
    [...row.children].map((cell) => cell.textContent),
  );
const summaries = () =>
  requests.filter((request) => request.startsWith("/api/v1/payments/summary"));

it("sums up what came in over the list's period by method and by who paid most, against the period before", async () => {
  const page = await open("/payments?period=90d");
  await summedUp(page);
  expect(summaries()).toEqual(["/api/v1/payments/summary?period=90d"]);
  const summary = summed(page);
  // The same total as the overview's Received for the last 90 days.
  expect(summary.textContent).toContain(
    "Received44,562.29from 18 payments, Jul 7 – Oct 4, 2026",
  );
  const compared = [...summary.querySelectorAll("p")].find((line) =>
    line.textContent.includes("vs "),
  );
  expect(compared.textContent).toBe(
    "down 25.6%vs 59,864.82 in Apr 8 – Jul 6, 2026",
  );
  expect(compared.querySelector(".text-change-down")).not.toBeNull();
  // Every method, receipts included, largest first.
  const methods = breakdown(page, "Received by method");
  expect(cells(methods)).toEqual([
    ["Bank Transfer", "6", "17,342.70"],
    ["On a receipt", "2", "12,011.28"],
    ["Credit Card", "5", "8,960.11"],
    ["PayPal", "3", "4,248.20"],
    ["Mobile Money", "2", "2,000.00"],
  ]);
  // The clients who paid the most, each linked one opening the client.
  const payers = breakdown(page, "Who paid most");
  expect(cells(payers)).toEqual([
    ["Quill & Co. Publishing", "3", "11,490.77"],
    ["Kestrel Outdoor", "2", "10,317.45"],
    ["Atlas Robotics", "2", "4,331.10"],
    ["Verdant Farms", "2", "3,278.96"],
    ["Bluefin Analytics", "1", "2,500.00"],
  ]);
  expect(
    [...payers.querySelectorAll("a")].map((link) => [
      link.textContent,
      link.getAttribute("href"),
    ]),
  ).toEqual([
    [
      "Quill & Co. Publishing",
      "/single-customer/00000000-0000-0000-0000-00000000c107",
    ],
    [
      "Kestrel Outdoor",
      "/single-customer/00000000-0000-0000-0000-00000000c109",
    ],
    ["Atlas Robotics", "/single-customer/00000000-0000-0000-0000-00000000c101"],
    [
      "Bluefin Analytics",
      "/single-customer/00000000-0000-0000-0000-00000000c103",
    ],
  ]);
  // It sits between the money bar and the list.
  const sections = [...page.querySelectorAll("section")];
  expect(sections.indexOf(summary)).toBe(1);
  expect(sections[2].contains(listTable(page))).toBe(true);
});

it("follows the list's period and currency, but not its method", async () => {
  const page = await open("/payments?period=90d&currency=EUR");
  await summedUp(page);
  expect(summaries()).toEqual([
    "/api/v1/payments/summary?period=90d&currency=EUR",
  ]);
  await choose(select(page, "Payment method"), "Bank Transfer");
  expect(address).toBe("?method=Bank+Transfer&period=90d&currency=EUR");
  await act(() =>
    vi.waitFor(() =>
      expect(requests).toContain(
        "/api/v1/payments?page=1&size=10&sort=date&dir=desc&method=Bank+Transfer&period=90d&currency=EUR",
      ),
    ),
  );
  expect(summaries()).toHaveLength(1);
  await choose(select(page, "Date paid"), "30d");
  await act(() =>
    vi.waitFor(() =>
      expect(summaries()).toContain(
        "/api/v1/payments/summary?period=30d&currency=EUR",
      ),
    ),
  );
});

it("sums up every date until a period is chosen, with nothing to compare with", async () => {
  const page = await open("/payments", {
    "/payments/summary": {
      ...fixture.summary,
      period: {
        key: "",
        from: null,
        to: null,
        previousFrom: null,
        previousTo: null,
      },
      previousTotal: null,
    },
  });
  await summedUp(page);
  expect(summaries()).toEqual(["/api/v1/payments/summary"]);
  expect(summed(page).textContent).toContain(
    "Received44,562.29from 18 payments",
  );
  expect(summed(page).textContent).not.toContain("vs ");
  expect(summed(page).textContent).not.toContain("Jul 7");
});

it("names each amount's currency when the business bills in several", async () => {
  const page = await open("/payments?period=90d", {
    "/payments/summary": { ...fixture.summary, currencies: ["USD", "EUR"] },
  });
  await summedUp(page);
  expect(summed(page).textContent).toContain("ReceivedUSD44,562.29");
  expect(summed(page).textContent).toContain("vs USD 59,864.82 in");
  expect(cells(breakdown(page, "Received by method"))[0]).toEqual([
    "Bank Transfer",
    "6",
    "USD17,342.70",
  ]);
});

it("says when nothing came in over the period", async () => {
  const page = await open("/payments?period=30d", {
    "/payments/summary": {
      ...fixture.summary,
      total: 0,
      previousTotal: 0,
      payments: 0,
      methods: [],
      payers: [],
    },
  });
  await act(() =>
    vi.waitFor(() =>
      expect(summed(page)?.textContent).toContain(
        "Nothing came in over this period.",
      ),
    ),
  );
  expect(summed(page).textContent).toContain("Received0.00from 0 payments");
  expect(summed(page).querySelector("table")).toBeNull();
});

it("holds the summary's place while it loads", async () => {
  let answer;
  const page = await open("/payments?period=90d", {
    "/payments/summary": new Promise((resolve) => {
      answer = resolve;
    }),
  });
  // Blocks between the money bar and the list, and no figures yet.
  const sections = [...page.querySelectorAll("section")];
  const held = sections[0].nextElementSibling;
  expect(held.getAttribute("aria-hidden")).toBe("true");
  expect(held.querySelector(".animate-ov-pulse")).not.toBeNull();
  expect(summed(page)).toBeUndefined();
  await act(async () => answer(fixture.summary));
  await summedUp(page);
  expect(page.querySelector(".animate-ov-pulse")).toBeNull();
});

it("says when the summary can't be loaded, with a way to try again, and keeps the list", async () => {
  let fails = true;
  const page = await open("/payments?period=90d", {
    "/payments/summary": () =>
      fails ? json({ message: "Try later" }, 500) : fixture.summary,
  });
  await act(() =>
    vi.waitFor(() =>
      expect(page.querySelector('[role="alert"]')).not.toBeNull(),
    ),
  );
  const alert = page.querySelector('[role="alert"]');
  expect(alert.textContent).toBe("We couldn’t sum up these payments.Try again");
  expect(rows(page)).toHaveLength(3);
  fails = false;
  await act(async () => alert.querySelector("button").click());
  await summedUp(page);
  expect(summed(page).textContent).toContain("44,562.29");
});
