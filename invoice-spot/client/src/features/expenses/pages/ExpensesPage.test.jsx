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

// jsdom has no modal dialogs; these stand in for the browser's.
HTMLDialogElement.prototype.showModal = function showModal() {
  this.open = true;
};
HTMLDialogElement.prototype.close = function close() {
  this.open = false;
};

let requests;
let address;
let where;
let navigate;
let root;
let container;
// Each CSV the page saves. jsdom has no object URLs, and a download would
// leave the page.
let saves;
const { createObjectURL, revokeObjectURL } = URL;
beforeEach(() => {
  vi.resetModules();
  vi.stubEnv("VITE_API_URL", "http://localhost:3001/api/v1");
  requests = [];
  URL.createObjectURL = vi.fn(() => "blob:expenses");
  URL.revokeObjectURL = vi.fn();
  saves = vi
    .spyOn(HTMLAnchorElement.prototype, "click")
    .mockImplementation(() => {});
});
afterEach(() => {
  act(() => root.unmount());
  container.remove();
  saves.mockRestore();
  Object.assign(URL, { createObjectURL, revokeObjectURL });
  vi.unstubAllGlobals();
  vi.unstubAllEnvs();
  vi.useRealTimers();
});

const json = (body, status = 200) =>
  new Response(JSON.stringify(body), {
    status,
    headers: { "content-type": "application/json" },
  });

const expense = (id, fields) => ({
  _id: id,
  vendor: null,
  taxAmount: 0,
  taxReclaimable: false,
  currency: "USD",
  paymentMethod: "Credit Card",
  reference: null,
  notes: null,
  ...fields,
  total: fields.amount + (fields.taxAmount || 0),
});

const expenses = {
  success: true,
  totalExpenses: 3,
  numberOfPages: 1,
  from: null,
  to: null,
  expenses: [
    expense("e-3", {
      date: "2026-09-28",
      vendor: "Northwind Design Studio",
      category: "Contractors",
      amount: 1800,
    }),
    expense("e-2", {
      date: "2026-09-15",
      vendor: "Figma",
      category: "Software and subscriptions",
      amount: 45,
      taxAmount: 4.05,
      taxReclaimable: true,
    }),
    expense("e-1", {
      date: "2026-08-30",
      vendor: "Delta Air Lines",
      category: "Travel",
      amount: 412.6,
    }),
  ],
  totals: [{ currency: "USD", expenses: 3, spent: 2257.6 }],
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

// The business the session works in, as its owner, and as a viewer, who
// sees the records and exports lists but changes nothing.
const business = (role, permissions) => ({
  businesses: [
    { _id: "b-1", name: "Studio Demo", role, active: true, permissions },
  ],
});
const owner = business("OWNER", [
  "VIEW_RECORDS",
  "EXPORT_LISTS",
  "EDIT_RECORDS",
  "DELETE_RECORDS",
  "EXPORT_ARCHIVE",
  "MANAGE_BUSINESS",
  "MANAGE_TEAM",
  "VIEW_TEAM_ACTIVITY",
  "OWN_BUSINESS",
]);
const viewer = business("VIEWER", ["VIEW_RECORDS", "EXPORT_LISTS"]);

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
        "/expense": expenses,
        "/expense/categories": {
          categories: ["Contractors", "Software and subscriptions", "Travel"],
        },
        "/settings": {
          settings: { defaultCurrency: "USD", baseCurrency: "USD" },
        },
        "/expense/csv": () =>
          new Response("Date\n", { headers: { "content-type": "text/csv" } }),
        "/document/summary": summary,
        "/businesses": owner,
        ...answers,
      }[url.pathname.replace("/api/v1", "")];
      const body = await (typeof answer === "function" ? answer(url) : answer);
      if (body instanceof Response) return body;
      return body ? json(body) : json({ message: "Not found" }, 404);
    }),
  );

const listed = (page) =>
  vi.waitFor(() => expect(page.querySelector("table")).not.toBeNull());

// Opens `path`, after the addresses in `before` in the history.
const open = async (path, answers, before = []) => {
  serve(answers);
  const { Provider } = await import("react-redux");
  const { MemoryRouter, useLocation, useNavigate } =
    await import("react-router-dom");
  const { store } = await import("../../../app/store");
  const { default: ExpensesPage } = await import("./ExpensesPage");
  const Address = () => {
    const location = useLocation();
    address = location.search;
    where = `${location.pathname}${location.search}`;
    navigate = useNavigate();
    return null;
  };
  container = document.body.appendChild(document.createElement("div"));
  root = createRoot(container);
  await act(async () => {
    root.render(
      <Provider store={store}>
        <MemoryRouter
          initialEntries={[...before, path]}
          initialIndex={before.length}
        >
          <ExpensesPage />
          <Address />
        </MemoryRouter>
      </Provider>,
    );
  });
  await act(() => listed(container));
  return container;
};

const listRequests = () =>
  requests.filter((request) => /\/expense(\?|$)/.test(request));
// The styled dropdown with this label: its trigger, which carries the value
// chosen and shows that option's words.
const select = (page, label) =>
  [...page.querySelectorAll("label")]
    .find((element) => element.textContent.startsWith(label))
    ?.querySelector('[role="combobox"]');
const shown = (combobox) => combobox.firstChild.firstChild.textContent;
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
// Downloads the list as CSV and waits for the file to be saved.
const download = async (page) => {
  const saved = saves.mock.calls.length;
  await act(async () =>
    page
      .querySelector('button[aria-label="Download these expenses as CSV"]')
      .click(),
  );
  await act(() =>
    vi.waitFor(() => expect(saves).toHaveBeenCalledTimes(saved + 1)),
  );
};
const csvRequests = () =>
  requests.filter((request) => request.includes("/expense/csv"));

it("asks for the overview's periods by name and the others as dates, in the list and its CSV", async () => {
  vi.useFakeTimers({ toFake: ["Date"] });
  vi.setSystemTime(new Date(2026, 9, 4, 12));
  const page = await open("/expenses?category=Travel&period=30d");
  expect(listRequests()).toEqual([
    "/api/v1/expense?page=1&size=10&category=Travel&period=30d",
  ]);
  expect(shown(select(page, "Paid"))).toBe("Last 30 days");
  await download(page);

  await choose(select(page, "Paid"), "last-month");
  await act(() => vi.waitFor(() => expect(listRequests()).toHaveLength(2)));
  expect(address).toBe("?category=Travel&period=last-month");
  expect(listRequests()[1]).toBe(
    "/api/v1/expense?page=1&size=10&category=Travel&from=2026-09-01&to=2026-09-30",
  );
  await download(page);
  expect(csvRequests()).toEqual([
    "/api/v1/expense/csv?category=Travel&period=30d",
    "/api/v1/expense/csv?category=Travel&from=2026-09-01&to=2026-09-30",
  ]);
});

it("offers no currency when the business bills in only one", async () => {
  const page = await open("/expenses");
  expect(requests).toContain("/api/v1/document/summary");
  expect(select(page, "Currency")).toBeUndefined();
});

it("offers each currency when the business bills in more than one, and asks for the one chosen, in the list and its CSV", async () => {
  const page = await open("/expenses", {
    "/document/summary": { ...summary, currencies: ["USD", "EUR"] },
  });
  await act(() =>
    vi.waitFor(() => expect(select(page, "Currency")).toBeDefined()),
  );
  const currency = select(page, "Currency");
  expect(await options(currency)).toEqual(["All currencies", "USD", "EUR"]);
  await choose(currency, "EUR");
  await act(() => vi.waitFor(() => expect(listRequests()).toHaveLength(2)));
  expect(address).toBe("?currency=EUR");
  expect(listRequests()[1]).toBe("/api/v1/expense?page=1&size=10&currency=EUR");
  await download(page);
  expect(csvRequests()).toEqual(["/api/v1/expense/csv?currency=EUR"]);
});

// The two contractor payments behind a category on the overview.
const contractors = (url) =>
  url.searchParams.get("category") === "Contractors"
    ? {
        ...expenses,
        totalExpenses: 2,
        from: "2026-07-07",
        to: "2026-10-04",
        expenses: [
          expenses.expenses[0],
          expense("e-0", {
            date: "2026-07-21",
            vendor: "Northwind Design Studio",
            category: "Contractors",
            amount: 1800,
          }),
        ],
        totals: [{ currency: "USD", expenses: 2, spent: 3600 }],
      }
    : expenses;

const line = (page) =>
  [...page.querySelectorAll("button")]
    .find((button) => button.textContent === "Clear filters")
    ?.parentElement.querySelector("p").textContent;

it("says how many expenses a category's link finds over its days and what they cost, then clears every filter, the currency too", async () => {
  const page = await open(
    "/expenses?category=Contractors&period=90d&currency=USD",
    { "/expense": contractors },
  );
  expect(listRequests()).toEqual([
    "/api/v1/expense?page=1&size=10&category=Contractors&period=90d&currency=USD",
  ]);
  expect(line(page)).toBe(
    "2 expenses from Jul 7 – Oct 4, 2026, ·Spent 3,600.00",
  );
  expect(select(page, "Category").value).toBe("Contractors");
  // A currency the address names stays in sight, though the business bills
  // in one.
  expect(select(page, "Currency").value).toBe("USD");
  const clear = [...page.querySelectorAll("button")].find(
    (button) => button.textContent === "Clear filters",
  );
  await act(async () => clear.click());
  expect(address).toBe("");
  await act(() => vi.waitFor(() => expect(listRequests()).toHaveLength(2)));
  expect(listRequests()[1]).toBe("/api/v1/expense?page=1&size=10");
  expect(line(page)).toBeUndefined();
  expect(select(page, "Currency")).toBeUndefined();
  // Clear filters is gone, so focus moves to the search box.
  expect(document.activeElement).toBe(
    page.querySelector('input[type="search"]'),
  );
});

it("says nothing above the list while no filter is set", async () => {
  const page = await open("/expenses");
  expect(page.querySelectorAll("tbody tr")).toHaveLength(3);
  expect(line(page)).toBeUndefined();
});

const dialog = () => document.querySelector("dialog");
const button = (element, text) =>
  [...element.querySelectorAll("button")].find((candidate) =>
    candidate.textContent.includes(text),
  );

it("opens the add dialog from a link once, on its first field, leaving the rest of the address", async () => {
  const page = await open("/expenses?new=1&period=90d", {}, ["/dashboard"]);
  await act(() =>
    vi.waitFor(() => expect(dialog().querySelector("form")).not.toBeNull()),
  );
  expect(dialog().open).toBe(true);
  expect(dialog().querySelector("h2").textContent).toBe("New expense");
  expect(document.activeElement).toBe(
    dialog().querySelector('input[type="date"]'),
  );
  expect(where).toBe("/expenses?period=90d");
  expect(listRequests()).toEqual(["/api/v1/expense?page=1&size=10&period=90d"]);
  await act(async () => button(dialog(), "Cancel").click());
  expect(dialog().open).toBe(false);
  expect(where).toBe("/expenses?period=90d");
  // The link that led here is gone, so focus goes to New expense.
  expect(document.activeElement).toBe(button(page, "New expense"));
  // `new` left the address in place, so Back leaves the page rather than
  // opening the dialog again.
  await act(async () => navigate(-1));
  expect(where).toBe("/dashboard");
  expect(dialog().open).toBe(false);
});

it("waits for the settings the form starts from, then opens on its first field", async () => {
  let answer;
  const page = await open("/expenses?new=1", {
    "/settings": () => new Promise((resolve) => (answer = resolve)),
  });
  await act(() => vi.waitFor(() => expect(answer).toBeDefined()));
  expect(dialog().open).toBe(false);
  expect(where).toBe("/expenses?new=1");
  await act(async () =>
    answer({ settings: { defaultCurrency: "USD", baseCurrency: "USD" } }),
  );
  await act(() => vi.waitFor(() => expect(dialog().open).toBe(true)));
  expect(document.activeElement).toBe(
    dialog().querySelector('input[type="date"]'),
  );
  expect(where).toBe("/expenses");
  expect(page.querySelector("h1").textContent).toBe("Expenses.");
});

it("opens nothing for any other value of new", async () => {
  await open("/expenses?new=0");
  await act(() =>
    vi.waitFor(() =>
      expect(requests).toEqual(
        expect.arrayContaining(["/api/v1/settings", "/api/v1/businesses"]),
      ),
    ),
  );
  await act(() => new Promise((resolve) => setTimeout(resolve, 20)));
  expect(dialog().open).toBe(false);
  expect(dialog().querySelector("form")).toBeNull();
});

it("keeps the add dialog closed for a viewer, who can't add expenses", async () => {
  const page = await open("/expenses?new=1", { "/businesses": viewer });
  await act(() => vi.waitFor(() => expect(address).toBe("")));
  expect(dialog().open).toBe(false);
  expect(dialog().querySelector("form")).toBeNull();
  const add = button(page, "New expense");
  expect(add.disabled).toBe(true);
  expect(add.title).toBe("Viewers can't change anything in this business");
});
