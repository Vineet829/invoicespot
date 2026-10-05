import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { createRoot } from "react-dom/client";
import { act } from "react-dom/test-utils";

// The page as the app draws it, through its own store and RTK Query, with
// only fetch faked. The answers follow the server's shapes for the demo
// account on Oct 4, 2026; the signed-in shell is left out.
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

const payments = {
  success: true,
  total: 18,
  numberOfPages: 2,
  page: 1,
  size: 10,
  from: null,
  to: null,
  payments: [
    {
      kind: "payment",
      id: "p-1",
      date: "2026-10-04",
      client: "Harbor & Pine Architects",
      document: { id: "doc-1038", number: "INV-1038", type: "Invoice" },
      method: "Bank Transfer",
      note: "Deposit",
      amount: 262.76,
      currency: "USD",
    },
    {
      kind: "receipt",
      id: "doc-304",
      date: "2026-10-02",
      client: "Quill & Co. Publishing",
      document: { id: "doc-304", number: "REC-0304", type: "Receipt" },
      method: null,
      note: null,
      amount: 9598.88,
      currency: "USD",
    },
    {
      kind: "refund",
      id: "r-7",
      date: "2026-09-20",
      client: "Tidewater Logistics",
      document: { id: "doc-1019", number: "INV-1019", type: "Invoice" },
      method: "Credit Card",
      note: "REF-0042",
      amount: -150,
      currency: "USD",
    },
  ],
  totals: [{ currency: "USD", payments: 18, received: 44562.29, receipts: 2 }],
  methods: ["Bank Transfer", "Credit Card", "Mobile Money", "PayPal", "Others"],
};

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
// body, a promise of one, or a function of the request's address giving one.
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
        "/businesses": { businesses: [] },
        ...answers,
      }[url.pathname.replace("/api/v1", "")];
      const body = await (typeof answer === "function" ? answer(url) : answer);
      return body ? json(body) : json({ message: "Not found" }, 404);
    }),
  );

const listed = (page) =>
  vi.waitFor(() => expect(page.querySelector("table")).not.toBeNull());

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
  [...page.querySelectorAll("thead th")].map(
    (header) =>
      header.querySelector("button")?.textContent ?? header.textContent,
  );
const rows = (page) =>
  [...page.querySelectorAll("tbody tr")].map((row) =>
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
  const links = [...page.querySelectorAll("tbody tr")[1].querySelectorAll("a")];
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
  expect(requests.filter((request) => request.includes("/payments"))).toEqual([
    "/api/v1/payments?page=1&size=10&sort=date&dir=desc&period=90d",
  ]);
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

it("sits beside All documents and Recurring", async () => {
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
