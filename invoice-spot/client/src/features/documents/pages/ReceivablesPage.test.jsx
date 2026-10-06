import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { createRoot } from "react-dom/client";
import { act } from "react-dom/test-utils";
import fixture from "./ReceivablesPage.fixture.json";

// The view as the app draws it, through its own store and RTK Query, with
// only fetch faked. The fixture is the demo account on Oct 4, 2026: what
// clients owe as the server sums it up, and the invoices behind each
// figure. The signed-in shell is left out.
vi.mock("../../dashboard/components/DashboardShell", () => ({
  default: ({ children }) => children,
}));
const toast = vi.hoisted(() => ({ success: vi.fn(), error: vi.fn() }));
vi.mock("react-toastify", () => ({ toast }));

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
let root;
let container;
beforeEach(() => {
  vi.resetModules();
  vi.stubEnv("VITE_API_URL", "http://localhost:3001/api/v1");
  // Days late and days to go count from the day the fixture was taken.
  vi.useFakeTimers({ toFake: ["Date"] });
  vi.setSystemTime(new Date(2026, 9, 4, 12));
  requests = [];
  toast.success.mockClear();
  toast.error.mockClear();
});
afterEach(() => {
  act(() => root.unmount());
  container.remove();
  vi.useRealTimers();
  vi.unstubAllGlobals();
  vi.unstubAllEnvs();
});

const json = (body, status = 200) =>
  new Response(JSON.stringify(body), {
    status,
    headers: { "content-type": "application/json" },
  });

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
]);
const viewer = business("VIEWER", ["VIEW_RECORDS", "EXPORT_LISTS"]);

const SABLE = "00000000-0000-0000-0000-000000001026";
const draft = {
  success: true,
  to: "accounts@sablefitness.example",
  subject: "A reminder about invoice INV-1026",
  message: "Hello, invoice INV-1026 was due on Aug 11, 2026.",
  note: null,
};

// The invoices each mode asks for: those owed and overdue, or those owed
// and not due yet.
const invoices = (url) =>
  url.searchParams.get("overdue") === "true"
    ? fixture.overdue
    : fixture.comingIn;

// Answers each request by its path; `answers` replaces any of them with a
// body or a Response, a promise of either, or a function of the request
// giving one.
const serve = (answers = {}) =>
  vi.stubGlobal(
    "fetch",
    vi.fn(async (request) => {
      const url = new URL(request.url);
      requests.push(
        `${request.method === "GET" ? "" : `${request.method} `}${url.pathname}${url.search}`,
      );
      const path = url.pathname.replace("/api/v1", "");
      const answer = {
        "/document/receivables": fixture.receivables,
        "/document/all": invoices,
        "/businesses": owner,
        "/user/profile": { userProfile: { demo: false } },
        [`/document/${SABLE}/remind`]: (_url, sent) =>
          sent.method === "POST"
            ? { success: true, message: "Reminder sent successfully." }
            : draft,
        ...answers,
      }[path];
      const body = await (typeof answer === "function"
        ? answer(url, request)
        : answer);
      if (body instanceof Response) return body;
      return body ? json(body) : json({ message: "Not found" }, 404);
    }),
  );

const listed = (page) =>
  vi.waitFor(() => expect(page.querySelector("tbody")).not.toBeNull());

const open = async (path, answers, ready = listed) => {
  serve(answers);
  const { Provider } = await import("react-redux");
  const { MemoryRouter, useLocation } = await import("react-router-dom");
  const { store } = await import("../../../app/store");
  const { default: ReceivablesPage } = await import("./ReceivablesPage");
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
          <ReceivablesPage />
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
const documents = () =>
  requests.filter((request) => request.includes("/document/"));
// The summary, and the list of invoices under it.
const summary = (page) => page.querySelector("section");
const list = (page) => page.querySelectorAll("section")[1];
const table = (element) =>
  [...element.querySelectorAll("tbody tr")].map((row) =>
    [...row.children].map((cell) => cell.textContent),
  );
const headings = (element) =>
  [...element.querySelectorAll("thead th")].map((th) => th.textContent);
const links = (element) =>
  [...element.querySelectorAll("a")].map((link) => [
    link.textContent,
    link.getAttribute("href"),
  ]);
const pill = (page, label) =>
  [...page.querySelectorAll('input[name="receivables-mode"]')].find(
    (input) => input.parentElement.textContent === label,
  );
// The bar's parts: each one's colour and the amount it grows by.
const bar = (page) =>
  [...summary(page).querySelector(".h-3\\.5").children].map((part) => [
    part.className.match(/bg-owed-[\w/-]+/)[0],
    parseFloat(part.style.flexGrow),
  ]);
const legend = (page) =>
  [...summary(page).querySelectorAll("ul > li .sr-only")].map(
    (words) => words.textContent,
  );
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

it("shows what is coming in, as the overview's Current counts it, above those invoices soonest due first", async () => {
  const page = await open("/receivables");
  expect(document.title).toBe("Receivables — Invoice Spot");
  expect(page.querySelector("h1").textContent).toBe("Documents.");
  // The figure's currency is the server's, so the invoices are asked for in
  // it once it has answered.
  expect(documents()).toEqual([
    "/api/v1/document/receivables",
    "/api/v1/document/all?page=1&size=10&sort=dueDate&dir=asc&outstanding=true&due=current&currency=USD",
  ]);
  expect(pill(page, "Coming in").checked).toBe(true);
  expect(summary(page).textContent).toContain(
    "Coming in17,959.76on 7 invoicesOf 24,285.79 owed on 10 invoices, as of today.",
  );
  // Each band as long as its amount; the empty one is left out.
  expect(bar(page)).toEqual([
    ["bg-owed-due", 11194.74],
    ["bg-owed-due/70", 6277.02],
    ["bg-owed-due/45", 488],
  ]);
  expect(legend(page)).toEqual([
    "Due in 0–15 days: USD 11,194.74 on 4 invoices",
    "Due in 16–30 days: USD 6,277.02 on 2 invoices",
    "Due in 31–45 days: USD 488.00 on 1 invoice",
    "Due after 45 days: USD 0.00 on 0 invoices",
  ]);
  // Who owes the most of it, each linked client opening the client.
  const owes = summary(page).querySelector("table");
  expect(headings(owes)).toEqual(["Client", "Invoices", "Owed"]);
  expect(table(owes)).toEqual([
    ["Verdant Farms", "1", "4,346.52"],
    ["Oak Street Bakery", "1", "3,217.50"],
    ["Lumen Health", "1", "2,762.75"],
    ["Bluefin Analytics", "1", "2,664.09"],
    ["Atlas Robotics", "1", "2,550.40"],
  ]);
  expect(links(owes)).toEqual([
    [
      "Oak Street Bakery",
      "/single-customer/00000000-0000-0000-0000-00000000c110",
    ],
    ["Lumen Health", "/single-customer/00000000-0000-0000-0000-00000000c111"],
    [
      "Bluefin Analytics",
      "/single-customer/00000000-0000-0000-0000-00000000c103",
    ],
    ["Atlas Robotics", "/single-customer/00000000-0000-0000-0000-00000000c101"],
  ]);
  // The invoices behind the figure, each saying when it falls due.
  expect(list(page).querySelector("h2").textContent).toBe("Invoices coming in");
  expect(headings(list(page))).toEqual([
    "Invoice",
    "Client",
    "Due date",
    "Due",
    "Balance",
  ]);
  expect(table(list(page))).toEqual([
    [
      "INV-1032",
      "Bluefin Analytics",
      "Oct 6, 2026",
      "Due in 2 days",
      "USD2,664.09",
    ],
    [
      "INV-1034",
      "Atlas Robotics",
      "Oct 12, 2026",
      "Due in 8 days",
      "USD2,550.40",
    ],
    [
      "INV-1035",
      "Oak Street Bakery",
      "Oct 15, 2026",
      "Due in 11 days",
      "USD3,217.50",
    ],
    [
      "INV-1036",
      "Verdant Farms",
      "Oct 24, 2026",
      "Due in 20 days",
      "USD4,346.52",
    ],
    [
      "INV-1037",
      "Juniper Films",
      "Oct 31, 2026",
      "Due in 27 days",
      "USD1,930.50",
    ],
    [
      "INV-1038",
      "Harbor & Pine Architects",
      "Nov 16, 2026",
      "Due in 43 days",
      "USD488.00",
    ],
    ["INV-1039", "Lumen Health", "—", "No due date", "USD2,762.75"],
  ]);
  expect(links(list(page))[0]).toEqual([
    "INV-1032",
    "/document/00000000-0000-0000-0000-000000001032",
  ]);
  expect(list(page).querySelector("button[aria-label^='Send']")).toBeNull();
  expect(page.querySelector('[role="status"]').textContent).toBe("1–7 of 7");
});

it("shows what is overdue, as the overview's Overdue counts it, most late first with a way to remind each client", async () => {
  const page = await open("/receivables?mode=overdue");
  expect(documents()).toEqual([
    "/api/v1/document/receivables",
    "/api/v1/document/all?page=1&size=10&sort=dueDate&dir=asc&overdue=true&outstanding=true&currency=USD",
  ]);
  expect(pill(page, "Overdue").checked).toBe(true);
  expect(summary(page).textContent).toContain(
    "Overdue6,326.03on 3 invoicesOf 24,285.79 owed on 10 invoices, as of today.",
  );
  // The overview's rust ramp, deeper the later.
  expect(bar(page)).toEqual([
    ["bg-owed-age-1", 965.25],
    ["bg-owed-age-2", 1969.11],
    ["bg-owed-age-4", 3391.67],
  ]);
  expect(legend(page)).toEqual([
    "1–15 days late: USD 965.25 on 1 invoice",
    "16–30 days late: USD 1,969.11 on 1 invoice",
    "31–45 days late: USD 0.00 on 0 invoices",
    "Over 45 days late: USD 3,391.67 on 1 invoice",
  ]);
  expect(
    [...summary(page).querySelectorAll("li .rounded-\\[3px\\]")].map(
      (swatch) => swatch.className.match(/bg-owed-[\w-]+/)[0],
    ),
  ).toEqual([
    "bg-owed-age-1",
    "bg-owed-age-2",
    "bg-owed-age-3",
    "bg-owed-age-4",
  ]);
  const owes = summary(page).querySelector("table");
  expect(table(owes)).toEqual([
    ["Sable Fitness", "1", "3,391.67"],
    ["Quill & Co. Publishing", "1", "1,969.11"],
    ["Verdant Farms", "1", "965.25"],
  ]);
  expect(list(page).querySelector("h2").textContent).toBe("Overdue invoices");
  expect(headings(list(page))).toEqual([
    "Invoice",
    "Client",
    "Due date",
    "Late",
    "Balance",
    "Remind",
  ]);
  expect(table(list(page))).toEqual([
    [
      "INV-1026",
      "Sable Fitness",
      "Aug 11, 2026, overdue",
      "54 days late",
      "USD3,391.67",
      "Send reminder",
    ],
    [
      "INV-1030",
      "Quill & Co. Publishing",
      "Sep 13, 2026, overdue",
      "21 days late",
      "USD1,969.11",
      "Send reminder",
    ],
    [
      "INV-1031",
      "Verdant Farms",
      "Sep 24, 2026, overdue",
      "10 days late",
      "USD965.25",
      "Send reminder",
    ],
  ]);
  expect(
    [...list(page).querySelectorAll("tbody button")].map((button) => [
      button.getAttribute("aria-label"),
      button.disabled,
    ]),
  ).toEqual([
    ["Send a reminder for INV-1026", false],
    ["Send a reminder for INV-1030", false],
    ["Send a reminder for INV-1031", false],
  ]);
});

it("switches between coming in and overdue with the pills, keeping the mode in the address", async () => {
  const page = await open("/receivables");
  await click(pill(page, "Overdue"));
  expect(address).toBe("?mode=overdue");
  await act(() =>
    vi.waitFor(() => expect(headings(list(page))).toContain("Late")),
  );
  expect(table(list(page))[0][0]).toBe("INV-1026");
  await click(pill(page, "Coming in"));
  expect(address).toBe("");
  await act(() =>
    vi.waitFor(() => expect(table(list(page))[0][0]).toBe("INV-1032")),
  );
  // The figures were asked for once: the mode only changes the list.
  expect(
    documents().filter((request) => request.includes("/receivables")),
  ).toHaveLength(1);
});

// Twelve overdue invoices, ten to a page; the second page has two.
const twoPages = (url) => ({
  ...fixture.overdue,
  totalDocuments: 12,
  numberOfPages: 2,
  page: Number(url.searchParams.get("page")),
  myDocuments:
    url.searchParams.get("page") === "2"
      ? fixture.overdue.myDocuments.slice(0, 2)
      : fixture.overdue.myDocuments,
});

it("keeps the page in the address, and starts a new mode at its first", async () => {
  const page = await open("/receivables?mode=overdue&page=2", {
    "/document/all": twoPages,
  });
  expect(documents()[1]).toBe(
    "/api/v1/document/all?page=2&size=10&sort=dueDate&dir=asc&overdue=true&outstanding=true&currency=USD",
  );
  expect(page.querySelector('[role="status"]').textContent).toBe("11–12 of 12");
  await click(page.querySelector('button[aria-label="First page"]'));
  expect(address).toBe("?mode=overdue");
  await click(page.querySelector('button[aria-label="Next page"]'));
  expect(address).toBe("?mode=overdue&page=2");
  await click(pill(page, "Coming in"));
  expect(address).toBe("");
});

it("shows the last page for a page past the end", async () => {
  await open("/receivables?mode=overdue&page=5");
  await act(() => vi.waitFor(() => expect(address).toBe("?mode=overdue")));
});

// The business bills in dollars and euros; the server answers in the
// currency asked for, or in dollars.
const inEuros = {
  "/document/receivables": (url) => ({
    ...fixture.receivables,
    currencies: ["USD", "EUR"],
    currency: url.searchParams.get("currency") || "USD",
  }),
};

it("shows one currency at a time, with a menu when the business bills in several", async () => {
  const page = await open("/receivables?mode=overdue", inEuros);
  const currency = select(page, "Currency");
  expect(currency.textContent).toContain("USD");
  expect(await options(currency)).toEqual(["USD", "EUR"]);
  // Amounts name their currency when there are several.
  expect(summary(page).textContent).toContain("USD6,326.03on 3 invoices");
  expect(summary(page).textContent).toContain("Of USD 24,285.79 owed");
  await choose(currency, "EUR");
  expect(address).toBe("?mode=overdue&currency=EUR");
  await act(() =>
    vi.waitFor(() =>
      expect(documents()).toContain(
        "/api/v1/document/all?page=1&size=10&sort=dueDate&dir=asc&overdue=true&outstanding=true&currency=EUR",
      ),
    ),
  );
  expect(documents()).toContain("/api/v1/document/receivables?currency=EUR");
  // The first currency is the default, so it leaves the address.
  await choose(select(page, "Currency"), "USD");
  expect(address).toBe("?mode=overdue");
});

it("offers no currency menu when the business bills in one", async () => {
  const page = await open("/receivables");
  expect(select(page, "Currency")).toBeUndefined();
});

const nothing = (group) => ({
  amount: 0,
  invoices: 0,
  bands: [],
  clients: [],
  ...group,
});
const empty = {
  success: true,
  totalDocuments: 0,
  numberOfPages: 0,
  page: 1,
  size: 10,
  myDocuments: [],
  totals: [],
};

it("says when nothing is overdue", async () => {
  const page = await open(
    "/receivables?mode=overdue",
    {
      "/document/receivables": {
        ...fixture.receivables,
        total: 17959.76,
        invoices: 7,
        overdue: nothing(),
      },
      "/document/all": empty,
    },
    (element) =>
      vi.waitFor(() =>
        expect(element.textContent).toContain("Nothing is overdue."),
      ),
  );
  expect(summary(page).textContent).toContain("Overdue0.00Nothing is overdue");
  // An empty track, and every band at zero.
  expect(bar(page)).toEqual([]);
  expect(legend(page)).toEqual([
    "1–15 days late: USD 0.00 on 0 invoices",
    "16–30 days late: USD 0.00 on 0 invoices",
    "31–45 days late: USD 0.00 on 0 invoices",
    "Over 45 days late: USD 0.00 on 0 invoices",
  ]);
  expect(summary(page).textContent).toContain("No one is late paying.");
  expect(summary(page).querySelector("table")).toBeNull();
  expect(list(page).textContent).toContain(
    "Invoices past their due date show up here, most late first, with a way to remind the client.",
  );
  expect(list(page).querySelector("table")).toBeNull();
});

it("says when nothing is coming in", async () => {
  const page = await open(
    "/receivables",
    {
      "/document/receivables": {
        ...fixture.receivables,
        total: 6326.03,
        invoices: 3,
        current: nothing(),
      },
      "/document/all": empty,
    },
    (element) =>
      vi.waitFor(() =>
        expect(element.textContent).toContain("Nothing is coming in."),
      ),
  );
  expect(summary(page).textContent).toContain(
    "Coming in0.00Nothing is coming in",
  );
  expect(summary(page).textContent).toContain(
    "No one owes anything that isn’t due yet.",
  );
  expect(list(page).textContent).toContain(
    "Unpaid invoices that aren’t due yet show up here, soonest due first.",
  );
});

it("holds the summary's place while it loads, and asks for the invoices once it has", async () => {
  let answer;
  const page = await open(
    "/receivables",
    {
      "/document/receivables": new Promise((resolve) => {
        answer = resolve;
      }),
    },
    () => Promise.resolve(),
  );
  // Blocks where the figures go, and the list's placeholder.
  expect(
    page.querySelector('[aria-hidden="true"] .animate-ov-pulse'),
  ).not.toBeNull();
  expect(page.querySelector('[role="status"]').textContent).toBe(
    "Loading workspace data…",
  );
  expect(page.textContent).not.toContain("17,959.76");
  expect(documents()).toEqual(["/api/v1/document/receivables"]);
  await act(async () => answer(fixture.receivables));
  await act(() => listed(page));
  expect(summary(page).textContent).toContain("17,959.76");
  expect(page.querySelector(".animate-ov-pulse")).toBeNull();
});

it("says when the figures can't be loaded, with a way to try again", async () => {
  let fails = true;
  const page = await open(
    "/receivables",
    {
      "/document/receivables": () =>
        fails
          ? json({ message: "Something went wrong. Please try again." }, 500)
          : fixture.receivables,
    },
    (element) =>
      vi.waitFor(() =>
        expect(element.querySelector('[role="alert"]')).not.toBeNull(),
      ),
  );
  const alert = page.querySelector('[role="alert"]');
  expect(alert.textContent).toContain("We couldn’t load this page.");
  // Without the figures there is no currency to list invoices in.
  expect(documents().some((request) => request.includes("/document/all"))).toBe(
    false,
  );
  expect(page.querySelector("table")).toBeNull();
  fails = false;
  await click(
    [...alert.querySelectorAll("button")].find(
      (button) => button.textContent === "Try again",
    ),
  );
  await act(() => listed(page));
  expect(summary(page).textContent).toContain("17,959.76");
});

it("says when the invoices can't be loaded, and keeps the figures", async () => {
  const page = await open(
    "/receivables",
    {
      "/document/all": () =>
        json({ message: "The list is taking a break." }, 500),
    },
    (element) =>
      vi.waitFor(() =>
        expect(element.querySelector('[role="alert"]')).not.toBeNull(),
      ),
  );
  expect(summary(page).textContent).toContain("17,959.76");
  const alert = list(page).querySelector('[role="alert"]');
  expect(alert.textContent).toContain("We couldn’t load this page.");
  expect(alert.textContent).toContain("The list is taking a break.");
});

const remindButton = (page) =>
  list(page).querySelector('button[aria-label="Send a reminder for INV-1026"]');
// Sends the reminder the dialog offers, as written.
const sendReminder = async (page) => {
  await click(remindButton(page));
  const dialog = document.querySelector("dialog[open]");
  await act(() =>
    vi.waitFor(() => expect(dialog.querySelector("form")).not.toBeNull()),
  );
  expect(dialog.querySelector("h2").textContent).toBe("Send reminder");
  expect(dialog.querySelector('input[type="email"]').value).toBe(draft.to);
  await act(async () => {
    dialog.querySelector("form").requestSubmit();
  });
  return dialog;
};

it("reminds a client about an overdue invoice, with the message the invoice's page shows", async () => {
  const page = await open("/receivables?mode=overdue");
  const dialog = await sendReminder(page);
  await act(() =>
    vi.waitFor(() =>
      expect(toast.success).toHaveBeenCalledWith("Reminder sent successfully."),
    ),
  );
  expect(requests).toContain(`POST /api/v1/document/${SABLE}/remind`);
  expect(toast.error).not.toHaveBeenCalled();
  expect(dialog.open).toBe(false);
});

it("says why a reminder wasn't sent, as the invoice's page does", async () => {
  const page = await open("/receivables?mode=overdue", {
    [`/document/${SABLE}/remind`]: (_url, sent) =>
      sent.method === "POST"
        ? json({ message: "Sable Fitness has no email address." }, 400)
        : draft,
  });
  const dialog = await sendReminder(page);
  await act(() =>
    vi.waitFor(() =>
      expect(toast.error).toHaveBeenCalledWith(
        "Sable Fitness has no email address.",
      ),
    ),
  );
  expect(toast.success).not.toHaveBeenCalled();
  // The dialog stays open, with what was written.
  expect(dialog.open).toBe(true);
});

it("falls back to the invoice page's words when the server gives no reason", async () => {
  const page = await open("/receivables?mode=overdue", {
    [`/document/${SABLE}/remind`]: (_url, sent) =>
      sent.method === "POST" ? new Response(null, { status: 502 }) : draft,
  });
  await sendReminder(page);
  await act(() =>
    vi.waitFor(() =>
      expect(toast.error).toHaveBeenCalledWith(
        "The reminder could not be sent.",
      ),
    ),
  );
});

it("shows a viewer each reminder disabled, with the reason", async () => {
  const page = await open("/receivables?mode=overdue", {
    "/businesses": viewer,
  });
  await act(() =>
    vi.waitFor(() => expect(remindButton(page).disabled).toBe(true)),
  );
  const reasons = [...list(page).querySelectorAll("tbody button")].map(
    (button) => [
      button.disabled,
      button.title,
      document.getElementById(button.getAttribute("aria-describedby"))
        .textContent,
    ],
  );
  expect(reasons).toEqual(
    Array(3).fill([
      true,
      "Viewers can't change anything in this business",
      "Viewers can't change anything in this business",
    ]),
  );
  await click(remindButton(page));
  expect(document.querySelector("dialog[open]")).toBeNull();
});

it("turns reminders off for the shared demo account, as the invoice's page does", async () => {
  const page = await open("/receivables?mode=overdue", {
    "/user/profile": { userProfile: { demo: true } },
  });
  await act(() =>
    vi.waitFor(() => expect(remindButton(page).disabled).toBe(true)),
  );
  expect(remindButton(page).title).toBe(
    "Emailing is turned off for the shared demo account.",
  );
});

it("sits beside All documents, Sales, Payments and Recurring", async () => {
  const page = await open("/receivables");
  expect(
    [...page.querySelectorAll('nav[aria-label="Documents views"] a')].map(
      (tab) => [
        tab.textContent,
        tab.getAttribute("href"),
        tab.getAttribute("aria-current"),
      ],
    ),
  ).toEqual([
    ["All documents", "/documents", null],
    ["Receivables", "/receivables", "page"],
    ["Sales", "/sales", null],
    ["Payments", "/payments", null],
    ["Recurring", "/recurring", null],
  ]);
});
