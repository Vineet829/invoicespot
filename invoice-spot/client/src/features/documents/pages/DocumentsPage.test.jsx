import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { createRoot } from "react-dom/client";
import { act } from "react-dom/test-utils";

// The page as the app draws it, through its own store and RTK Query, with
// only fetch faked. The answers follow the server's shapes for the demo
// account on Oct 4, 2026. The signed-in shell is left out.
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

const invoice = (number, fields) => ({
  _id: `id-${number}`,
  documentType: "Invoice",
  documentNumber: number,
  state: "issued",
  status: "Not Paid",
  overdue: false,
  customer: { name: "Atlas Robotics" },
  currency: "USD",
  ...fields,
});

const documents = {
  success: true,
  totalDocuments: 2,
  numberOfPages: 1,
  page: 1,
  size: 10,
  issuedFrom: null,
  issuedTo: null,
  myDocuments: [
    invoice("INV-1034", {
      status: "Not Fully Paid",
      total: 4397.25,
      balanceDue: 2550.4,
      dueDate: "2026-10-12",
    }),
    {
      ...invoice("REC-0304", { documentType: "Receipt", status: "Paid" }),
      customer: { name: "Quill & Co. Publishing" },
      total: 9598.88,
      balanceDue: null,
      dueDate: null,
    },
  ],
  totals: [
    {
      currency: "USD",
      documents: 2,
      billed: 13996.13,
      balanceDue: 2550.4,
      overdue: 0,
      overdueDocuments: 0,
    },
  ],
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

// Answers each request by its path; `answers` replaces any of them with a
// body or a Response, a promise of either, or a function of the request's
// address giving one.
const serve = (answers) =>
  vi.stubGlobal(
    "fetch",
    vi.fn(async (request) => {
      const url = new URL(request.url);
      requests.push(`${url.pathname}${url.search}`);
      const answer = {
        "/document/all": documents,
        "/document/summary": summary,
        "/payment-claims": { claims: [] },
        "/businesses": { businesses: [] },
        ...answers,
      }[url.pathname.replace("/api/v1", "")];
      const body = await (typeof answer === "function" ? answer(url) : answer);
      if (body instanceof Response) return body;
      return body ? json(body) : json({ message: "Not found" }, 404);
    }),
  );

const listed = (page) =>
  vi.waitFor(() => expect(page.querySelector("table")).not.toBeNull());

const open = async (path, answers = {}, ready = listed) => {
  serve(answers);
  const { Provider } = await import("react-redux");
  const { MemoryRouter, useLocation } = await import("react-router-dom");
  const { store } = await import("../../../app/store");
  const { default: DocumentsPage } = await import("./DocumentsPage");
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
          <DocumentsPage />
          <Address />
        </MemoryRouter>
      </Provider>,
    );
  });
  await act(() => ready(container));
  return container;
};

const listRequests = () =>
  requests.filter((request) => request.includes("/document/all"));
const select = (page, label) =>
  [...page.querySelectorAll("label")]
    .find((element) => element.textContent.startsWith(label))
    ?.querySelector("select");
const choose = (element, value) =>
  act(async () => {
    element.value = value;
    element.dispatchEvent(new Event("change", { bubbles: true }));
  });

it("opens the invoices with money owed, soonest due first, as a link asks", async () => {
  const page = await open("/documents?status=outstanding&sort=dueDate&dir=asc");
  expect(listRequests()).toEqual([
    "/api/v1/document/all?page=1&size=10&sort=dueDate&dir=asc&outstanding=true",
  ]);
  const chips = [...page.querySelectorAll('input[name="document-status"]')];
  expect(chips.map((chip) => chip.parentElement.textContent)).toEqual([
    "All",
    "Outstanding",
    "Unpaid",
    "Partially paid",
    "Paid",
    "Overdue",
    "Drafts",
    "Void",
  ]);
  expect(chips[1].checked).toBe(true);
  expect(select(page, "Sort documents").value).toBe("dueDate:asc");
});

it("asks for what was billed in the overview's last 90 days by name", async () => {
  const page = await open("/documents?type=billed&period=90d");
  expect(listRequests()).toEqual([
    "/api/v1/document/all?page=1&size=10&sort=createdAt&dir=desc&billed=true&period=90d",
  ]);
  expect(select(page, "Document type").value).toBe("billed");
  expect(select(page, "Document type").selectedOptions[0].textContent).toBe(
    "Billed: invoices, receipts and credit notes",
  );
  expect(select(page, "Issued").selectedOptions[0].textContent).toBe(
    "Last 90 days",
  );
});

it("shows a week of due dates as a filter that can be removed", async () => {
  const page = await open(
    "/documents?status=outstanding&due=next-week&sort=dueDate&dir=asc",
  );
  expect(listRequests()[0]).toBe(
    "/api/v1/document/all?page=1&size=10&sort=dueDate&dir=asc&outstanding=true&due=next-week",
  );
  const remove = page.querySelector(
    'button[aria-label="Remove Due next week"]',
  );
  expect(remove.parentElement.textContent).toBe("Due next week");
  await act(async () => remove.click());
  await act(() => vi.waitFor(() => expect(listRequests()).toHaveLength(2)));
  expect(address).toBe("?status=outstanding&sort=dueDate&dir=asc");
  expect(listRequests()[1]).not.toContain("due=");
  expect(page.textContent).not.toContain("Due next week");
});

const moneyBar = (page) =>
  page.querySelector('[aria-label="What is owed and what came in"]');

it("offers no currency when the business bills in only one", async () => {
  const page = await open("/documents");
  await act(() => vi.waitFor(() => expect(moneyBar(page)).not.toBeNull()));
  expect(requests).toContain("/api/v1/document/summary");
  expect(select(page, "Currency")).toBeUndefined();
});

it("offers each currency when the business bills in more than one", async () => {
  const page = await open("/documents", {
    "/document/summary": (url) => ({
      ...summary,
      currency: url.searchParams.get("currency") || "USD",
      currencies: ["USD", "EUR"],
    }),
  });
  await act(() =>
    vi.waitFor(() => expect(select(page, "Currency")).toBeDefined()),
  );
  const currency = select(page, "Currency");
  expect([...currency.options].map((option) => option.textContent)).toEqual([
    "All currencies",
    "USD",
    "EUR",
  ]);
  await choose(currency, "EUR");
  await act(() => vi.waitFor(() => expect(listRequests()).toHaveLength(2)));
  expect(address).toBe("?currency=EUR");
  expect(listRequests()[1]).toBe(
    "/api/v1/document/all?page=1&size=10&sort=createdAt&dir=desc&currency=EUR",
  );
  expect(requests).toContain("/api/v1/document/summary?currency=EUR");
});

const radio = (page, label) =>
  [...page.querySelectorAll('input[name="document-status"]')].find(
    (input) => input.parentElement.textContent === label,
  );

it("drops the week of due dates when the status leaves Outstanding", async () => {
  const page = await open(
    "/documents?status=outstanding&due=later&sort=dueDate&dir=asc",
  );
  await act(async () => radio(page, "All").click());
  expect(address).toBe("?sort=dueDate&dir=asc");
  expect(page.textContent).not.toContain("Due later");
});

it("moves focus to the status chosen once the week of due dates is removed", async () => {
  const page = await open(
    "/documents?status=outstanding&due=this-week&sort=dueDate&dir=asc",
  );
  const remove = page.querySelector(
    'button[aria-label="Remove Due this week"]',
  );
  remove.focus();
  await act(async () => remove.click());
  expect(document.activeElement).toBe(radio(page, "Outstanding"));
  expect(document.activeElement.checked).toBe(true);
});

it("downloads the list as it is filtered", async () => {
  // jsdom has no object URLs, and a download would leave the page.
  const { createObjectURL, revokeObjectURL } = URL;
  URL.createObjectURL = vi.fn(() => "blob:documents");
  URL.revokeObjectURL = vi.fn();
  const click = vi
    .spyOn(HTMLAnchorElement.prototype, "click")
    .mockImplementation(() => {});
  try {
    const page = await open(
      "/documents?q=atlas&status=outstanding&due=week-after&type=billed&period=90d&currency=EUR",
      {
        "/document/csv": () =>
          new Response("Number\n", {
            headers: { "content-type": "text/csv" },
          }),
      },
    );
    await act(async () =>
      page
        .querySelector('button[aria-label="Download these documents as CSV"]')
        .click(),
    );
    await act(() => vi.waitFor(() => expect(click).toHaveBeenCalled()));
    expect(
      requests.filter((request) => request.includes("/document/csv")),
    ).toEqual([
      "/api/v1/document/csv?sort=createdAt&dir=desc&q=atlas&outstanding=true&due=week-after&billed=true&period=90d&currency=EUR",
    ]);
  } finally {
    click.mockRestore();
    Object.assign(URL, { createObjectURL, revokeObjectURL });
  }
});

it("keeps showing a currency the address names when the currencies can't be loaded", async () => {
  const page = await open("/documents?currency=EUR", {
    "/document/summary": () => json({ message: "Try later" }, 500),
  });
  const currency = select(page, "Currency");
  expect(currency.value).toBe("EUR");
  expect([...currency.options].map((option) => option.textContent)).toEqual([
    "All currencies",
    "EUR",
  ]);
});

it("clears every filter, then moves focus to All", async () => {
  const page = await open(
    "/documents?q=atlas&status=outstanding&due=week-after&type=billed&period=90d&currency=EUR&sort=total&dir=asc",
    {
      // Nothing matches the filters; without them there are documents.
      "/document/all": (url) =>
        url.searchParams.get("q")
          ? { ...documents, totalDocuments: 0, myDocuments: [] }
          : documents,
    },
    (page) =>
      vi.waitFor(() =>
        expect(page.textContent).toContain("No documents match."),
      ),
  );
  const clear = [...page.querySelectorAll("button")].find(
    (button) => button.textContent === "Clear filters",
  );
  await act(async () => clear.click());
  // The order stays: it is not a filter.
  expect(address).toBe("?sort=total&dir=asc");
  await act(() =>
    vi.waitFor(() => expect(document.activeElement).toBe(radio(page, "All"))),
  );
  expect(listRequests().at(-1)).toBe(
    "/api/v1/document/all?page=1&size=10&sort=total&dir=asc",
  );
});

// A sortable column's header holds a button with its name.
const headings = (page) =>
  [...page.querySelectorAll("thead th")].map(
    (header) =>
      header.querySelector("button")?.textContent ?? header.textContent,
  );
const cells = (page, label) => {
  const column = headings(page).indexOf(label);
  return [...page.querySelectorAll("tbody tr")].map(
    (row) => row.children[column].textContent,
  );
};

it("shows each invoice's balance due after its amount, and a dash where nothing is owed", async () => {
  const page = await open("/documents");
  expect(headings(page)).toEqual([
    "#",
    "Document no.",
    "Type",
    "Customer",
    "Amount",
    "Balance due",
    "Due date",
    "Status",
    "View",
    "Delete",
  ]);
  expect(cells(page, "Balance due")).toEqual(["USD2,550.40", "—"]);
  // The whole list is shown, so there is nothing to clear.
  expect(page.textContent).not.toContain("Clear filters");
});

it("says what the filtered list holds and adds up to, and clears every filter", async () => {
  const page = await open(
    "/documents?q=atlas&status=outstanding&due=this-week&type=billed&period=90d&currency=USD&sort=dueDate&dir=asc",
    {
      "/document/all": {
        ...documents,
        totalDocuments: 1,
        issuedFrom: "2026-07-07",
        issuedTo: "2026-10-04",
        myDocuments: [documents.myDocuments[0]],
        totals: [
          {
            currency: "USD",
            documents: 1,
            billed: 4397.25,
            balanceDue: 2550.4,
            overdue: 0,
            overdueDocuments: 0,
          },
        ],
      },
    },
  );
  const line = [...page.querySelectorAll("button")].find(
    (button) => button.textContent === "Clear filters",
  ).parentElement;
  expect(line.querySelector("p").textContent).toBe(
    "1 invoice with money owed, issued Jul 7 – Oct 4, 2026, ·Balance due 2,550.40",
  );
  expect(
    [...line.querySelectorAll("strong")].map((figure) => figure.textContent),
  ).toEqual(["1 invoice", "2,550.40"]);
  await act(async () => [...line.querySelectorAll("button")].at(-1).click());
  // Every filter goes; the sort stays, as it is an order, not a filter.
  expect(address).toBe("?sort=dueDate&dir=asc");
  await act(() =>
    vi.waitFor(() =>
      expect(listRequests().at(-1)).toBe(
        "/api/v1/document/all?page=1&size=10&sort=dueDate&dir=asc",
      ),
    ),
  );
  expect(page.textContent).not.toContain("Clear filters");
  expect(document.activeElement).toBe(radio(page, "All"));
});

it("keeps the last answer's words and figures, dimmed, until the next filters' arrive", async () => {
  let pending;
  const page = await open("/documents?status=unpaid", {
    "/document/all": (url) =>
      url.searchParams.get("outstanding")
        ? new Promise((resolve) => (pending = resolve))
        : documents,
  });
  const line = () =>
    [...page.querySelectorAll("button")].find(
      (button) => button.textContent === "Clear filters",
    ).parentElement;
  expect(line().querySelector("p").textContent).toBe(
    "2 documents, ·Billed 13,996.13, ·Balance due 2,550.40",
  );
  await act(async () => radio(page, "Outstanding").click());
  await act(() => vi.waitFor(() => expect(pending).toBeDefined()));
  expect(line().querySelector("p").textContent).toBe(
    "2 documents, ·Billed 13,996.13, ·Balance due 2,550.40",
  );
  expect(line().className).toContain("opacity-60");
  await act(async () =>
    pending(
      json({
        ...documents,
        totalDocuments: 1,
        myDocuments: [documents.myDocuments[0]],
        totals: [
          {
            currency: "USD",
            documents: 1,
            billed: 4397.25,
            balanceDue: 2550.4,
            overdue: 0,
            overdueDocuments: 0,
          },
        ],
      }),
    ),
  );
  await act(() =>
    vi.waitFor(() =>
      expect(line().querySelector("p").textContent).toBe(
        "1 invoice with money owed, ·Balance due 2,550.40",
      ),
    ),
  );
  expect(line().className).not.toContain("opacity-60");
});

it("shows what is owed and what came in above the list, ringing the part the list shows", async () => {
  const page = await open("/documents?status=outstanding&sort=dueDate&dir=asc");
  await act(() => vi.waitFor(() => expect(moneyBar(page)).not.toBeNull()));
  // The bar comes before the documents.
  expect(
    moneyBar(page).compareDocumentPosition(page.querySelector("table")) &
      Node.DOCUMENT_POSITION_FOLLOWING,
  ).toBeTruthy();
  expect(
    [...moneyBar(page).querySelectorAll("a")].map((link) => [
      link.getAttribute("href"),
      link.getAttribute("aria-current"),
    ]),
  ).toEqual([
    ["/documents?status=outstanding&sort=dueDate&dir=asc", "true"],
    ["/documents?status=overdue", null],
    ["/payments?period=30d", null],
  ]);
  expect(moneyBar(page).textContent).toContain("24,285.79");
});

it("rings only the part of the money bar whose list is exactly the one shown", async () => {
  const current = (page) =>
    [...moneyBar(page).querySelectorAll("a")].map((link) =>
      link.getAttribute("aria-current"),
    );
  const overdue = await open("/documents?status=overdue");
  await act(() => vi.waitFor(() => expect(moneyBar(overdue)).not.toBeNull()));
  expect(current(overdue)).toEqual([null, "true", null]);
  expect(moneyBar(overdue).querySelectorAll("a")[1].className).toContain(
    "ring-2",
  );
});

it("rings nothing when the list is narrowed further than a part opens", async () => {
  const page = await open(
    "/documents?status=outstanding&q=atlas&sort=dueDate&dir=asc",
  );
  await act(() => vi.waitFor(() => expect(moneyBar(page)).not.toBeNull()));
  expect(
    [...moneyBar(page).querySelectorAll("a")].map((link) =>
      link.getAttribute("aria-current"),
    ),
  ).toEqual([null, null, null]);
});
