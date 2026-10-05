import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { createRoot } from "react-dom/client";
import { act } from "react-dom/test-utils";
import overview from "./DashboardPage.fixture.json";

// The page as the app draws it, through its own store and RTK Query, with
// only fetch faked. The fixture is the demo account's overview of the last
// 90 days, as the server sends it. The signed-in shell is left out.
vi.mock("../components/DashboardShell", () => ({
  default: ({ children }) => children,
}));

window.IS_REACT_ACT_ENVIRONMENT = true;

let requests;
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
  localStorage.clear();
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
  "EXPORT_ARCHIVE",
  "MANAGE_BUSINESS",
  "MANAGE_TEAM",
  "VIEW_TEAM_ACTIVITY",
  "OWN_BUSINESS",
]);
const viewer = business("VIEWER", ["VIEW_RECORDS", "EXPORT_LISTS"]);

// Answers every overview request with `answer`, or with what it gives for
// the request's address: a body, a Response, or a promise of either. The
// business the session works in is `session`'s.
const serve = (answer, session) =>
  vi.stubGlobal(
    "fetch",
    vi.fn(async (request) => {
      const url = new URL(request.url);
      if (url.pathname.endsWith("/businesses")) return json(session);
      requests.push(`${url.pathname}${url.search}`);
      const body = await (typeof answer === "function" ? answer(url) : answer);
      return body instanceof Response ? body : json(body);
    }),
  );

const loaded = (page) =>
  vi.waitFor(() =>
    expect(page.querySelector("#document-status-title")).not.toBeNull(),
  );

const open = async (
  path,
  answer = overview,
  ready = loaded,
  session = owner,
) => {
  serve(answer, session);
  const { Provider } = await import("react-redux");
  const { MemoryRouter } = await import("react-router-dom");
  const { store } = await import("../../../app/store");
  const { default: DashboardPage } = await import("./DashboardPage");
  // In the document, so controls can take focus.
  container = document.body.appendChild(document.createElement("div"));
  root = createRoot(container);
  await act(async () => {
    root.render(
      <Provider store={store}>
        <MemoryRouter initialEntries={[path]}>
          <DashboardPage />
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
const pill = (page, label) =>
  [...page.querySelectorAll('nav[aria-label="Period"] a')].find(
    (link) => link.textContent === label,
  );
const content = (page) =>
  page.querySelector("[aria-busy], .transition-opacity");
const setupStrip = (page) =>
  page.querySelector('[aria-labelledby="setup-title"]');
// The titles of the panels in each row.
const layout = (page) =>
  [...content(page).children].map((row) =>
    [...row.querySelectorAll("h2")].map((heading) => heading.textContent),
  );
const tiles = (page) =>
  [...page.querySelectorAll('[aria-labelledby="figures-title"] a')].map(
    (link) => [link.getAttribute("href"), link.getAttribute("aria-label")],
  );
const pills = (page) =>
  [...page.querySelectorAll('nav[aria-label="Period"] a')].map((link) => [
    link.textContent,
    link.getAttribute("href"),
    link.getAttribute("aria-current"),
  ]);
const withTotals = (totals) => ({
  ...overview,
  totals: { ...overview.totals, ...totals },
});
const lastThirtyDays = {
  ...overview,
  period: {
    key: "30d",
    from: "2026-09-05",
    to: "2026-10-04",
    previousFrom: "2026-08-06",
    previousTo: "2026-09-04",
    step: "day",
  },
};
// Twelve calendar months to Oct 4, 2026, the last of them month to date.
const day = (date) =>
  `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
const twelveMonths = {
  ...overview,
  period: {
    key: "12m",
    from: "2025-11-01",
    to: "2026-10-04",
    previousFrom: "2024-11-01",
    previousTo: "2025-10-31",
    step: "month",
  },
  trend: Array.from({ length: 12 }, (_, index) => ({
    from: day(new Date(2025, 10 + index, 1)),
    to: day(
      index === 11 ? new Date(2026, 9, 4) : new Date(2025, 11 + index, 0),
    ),
    billed: 1000 * (index + 1),
    received: 800 * (index + 1),
    income: 900 * (index + 1),
    expenses: 100 * index,
    moneyIn: 800 * (index + 1),
    moneyOut: 100 * index,
  })),
};

it("draws the last 90 days from the server's figures, against the 90 days before", async () => {
  const page = await open("/dashboard");
  expect(requests).toEqual(["/api/v1/report/overview?period=90d"]);
  expect(page.querySelector("h1").textContent).toBe("Business overview.");
  expect(page.textContent).toContain(
    "Last 90 days, Jul 7 – Oct 4, 2026 · compared with Apr 8 – Jul 6",
  );
  expect(pills(page)).toEqual([
    ["30 days", "/dashboard?period=30d", null],
    ["90 days", "/dashboard", "true"],
    ["12 months", "/dashboard?period=12m", null],
    ["This year", "/dashboard?period=ytd", null],
  ]);
  expect(tiles(page)).toEqual([
    [
      "/documents?type=billed&period=90d",
      "Billed in the last 90 days: USD 68,848.08, up 29.1% vs previous 90 days. View the invoices, receipts and credit notes",
    ],
    [
      "/payments?period=90d",
      "Received in the last 90 days: USD 44,562.29, down 25.6% vs previous 90 days. View the payments",
    ],
    [
      "/reports?period=90d#profit",
      "Profit in the last 90 days: USD 64,194.00, up 29.1% vs previous 90 days. View the profit and loss report",
    ],
    [
      "/documents?status=outstanding&sort=dueDate&dir=asc",
      "Outstanding now: USD 24,285.79 on 10 invoices, USD 6,326.03 of it overdue on 3 invoices. View the invoices owed, soonest due first",
    ],
  ]);
  for (const text of [
    "68,848.08",
    "44,562.29",
    "64,194.00",
    "24,285.79",
    "29.1%",
    "25.6%",
    "vs previous 90 days",
    "10 invoices · 3 overdue",
    "Not due 17,959.76",
    "Overdue 6,326.03",
    "48 documents",
  ]) {
    expect(page.textContent).toContain(text);
  }
  // One currency, so there is nothing to switch.
  expect(page.textContent).not.toContain("Currency");
  // The panels in their rows, in reading order: what needs attention beside
  // the top clients, Spending, the top items and what is coming up side by
  // side, then the latest payments beside the document status.
  expect(layout(page)).toEqual([
    ["Figures in the last 90 days"],
    ["Cash flow", "Invoices owed to you"],
    ["Needs your attention", "Top clients"],
    ["Spending", "Top items", "Coming up"],
    ["Latest payments", "Document status"],
  ]);
  // Above them, under the header, what is left to set up.
  await act(() => vi.waitFor(() => expect(setupStrip(page)).not.toBeNull()));
  expect(
    [...page.querySelectorAll("h2")].map((heading) => heading.textContent),
  ).toEqual(["Finish setting up", ...layout(page).flat()]);
});

it("charts money in and out, with the same figures in a table and on hover or focus", async () => {
  const page = await open("/dashboard");
  const chart = page.querySelector('[aria-labelledby="cash-flow-title"]');
  expect(
    [...chart.querySelectorAll("dl > div")].map((total) => total.textContent),
  ).toEqual(["In44,562.29", "Out0.00", "Net44,562.29"]);
  expect(chart.textContent).toContain("No expenses recorded in this period");
  for (const tick of ["20,000", "15,000", "10,000", "5,000", "−5,000"]) {
    expect(chart.textContent).toContain(tick);
  }

  const table = chart.querySelector("table");
  expect(table.querySelector("caption").textContent).toBe(
    "Cash flow each week, USD",
  );
  const rows = [...table.querySelectorAll("tbody tr")].map((row) =>
    [...row.children].map((cell) => cell.textContent),
  );
  expect(rows).toHaveLength(13);
  expect(rows[0]).toEqual(["Jul 7–13", "0.00", "0.00", "0.00"]);
  expect(rows[3]).toEqual(["Jul 28–Aug 3", "6,002.14", "0.00", "6,002.14"]);
  expect(rows[12]).toEqual(["Sep 29–Oct 4", "17,256.86", "0.00", "17,256.86"]);

  // One column at a time is in the tab order, the latest to begin with.
  const columns = [...chart.querySelectorAll('[role="img"]')];
  expect(columns).toHaveLength(13);
  expect(columns.map((column) => column.tabIndex)).toEqual([
    ...Array(12).fill(-1),
    0,
  ]);
  expect(columns[12].getAttribute("aria-label")).toBe(
    "Sep 29–Oct 4: money in 17,256.86, money out 0.00, net 17,256.86",
  );
  const card = "Money in17,256.86Money out0.00Net17,256.86";
  expect(chart.textContent).not.toContain(card);
  act(() => columns[12].focus());
  expect(chart.textContent).toContain(`Sep 29–Oct 4${card}`);
  act(() => {
    columns[12].dispatchEvent(
      new KeyboardEvent("keydown", { key: "ArrowLeft", bubbles: true }),
    );
  });
  expect(document.activeElement).toBe(columns[11]);
  expect(columns[11].tabIndex).toBe(0);
  expect(chart.textContent).toContain(
    "Sep 22–28Money in0.00Money out0.00Net0.00",
  );
  const key = (column, name) =>
    act(() => {
      column.dispatchEvent(
        new KeyboardEvent("keydown", { key: name, bubbles: true }),
      );
    });
  key(columns[11], "Home");
  expect(document.activeElement).toBe(columns[0]);
  expect(chart.textContent).toContain("Jul 7–13Money in0.00");
  key(columns[0], "ArrowRight");
  expect(document.activeElement).toBe(columns[1]);
  expect(columns.map((column) => column.tabIndex).indexOf(0)).toBe(1);
  expect(chart.textContent).toContain("Jul 14–20Money in300.30");
  key(columns[1], "End");
  expect(document.activeElement).toBe(columns[12]);
  // Escape hides the card; focus stays where it was.
  key(columns[12], "Escape");
  expect(chart.textContent).not.toContain(card);
  expect(document.activeElement).toBe(columns[12]);
  act(() => columns[12].blur());
  expect(chart.textContent).not.toContain("Sep 29–Oct 4Money in");

  act(() => {
    columns[2].dispatchEvent(new MouseEvent("mouseover", { bubbles: true }));
  });
  expect(chart.textContent).toContain(
    "Jul 21–27Money in4,332.90Money out0.00Net4,332.90",
  );
});

it("charts money going out below the line", async () => {
  const page = await open("/dashboard", {
    ...overview,
    trend: overview.trend.map((bucket, index) =>
      index === 12 ? { ...bucket, moneyOut: 50000 } : bucket,
    ),
  });
  const chart = page.querySelector('[aria-labelledby="cash-flow-title"]');
  expect(
    [...chart.querySelectorAll("dl > div")].map((total) => total.textContent),
  ).toEqual(["In44,562.29", "Out50,000.00", "Net-5,437.71"]);
  expect(chart.textContent).not.toContain("No expenses recorded");
  for (const tick of ["20,000", "−20,000", "−40,000", "−60,000"]) {
    expect(chart.textContent).toContain(tick);
  }
  expect([...chart.querySelectorAll("tbody tr")].at(-1).textContent).toBe(
    "Sep 29–Oct 417,256.8650,000.00-32,743.14",
  );
});

it("says when nothing came in or went out", async () => {
  const page = await open("/dashboard", {
    ...overview,
    trend: overview.trend.map((bucket) => ({
      ...bucket,
      moneyIn: 0,
      moneyOut: 0,
    })),
  });
  const chart = page.querySelector('[aria-labelledby="cash-flow-title"]');
  expect(chart.textContent).toContain(
    "Nothing came in or went out in this period.",
  );
  expect(chart.textContent).not.toContain("No expenses recorded");
  expect(chart.querySelectorAll('[role="img"]')).toHaveLength(0);
});

it("leaves every comparison out when nothing came before the period", async () => {
  const page = await open("/dashboard", withTotals({ hasPrevious: false }));
  expect(page.textContent).toContain("Last 90 days, Jul 7 – Oct 4, 2026");
  expect(page.textContent).not.toContain("compared with");
  expect(page.textContent).not.toContain("vs previous");
  // The top items' shares of income compare nothing with before.
  const rest = page.cloneNode(true);
  rest.querySelector('[aria-labelledby="top-items-title"]').remove();
  expect(rest.textContent).not.toMatch(/\d%/);
  expect(tiles(page)[0][1]).toBe(
    "Billed in the last 90 days: USD 68,848.08. View the invoices, receipts and credit notes",
  );
});

it("compares only figures that were not zero before", async () => {
  const page = await open(
    "/dashboard",
    withTotals({ billed: { current: 68848.08, previous: 0 } }),
  );
  expect(tiles(page)[0][1]).toBe(
    "Billed in the last 90 days: USD 68,848.08. View the invoices, receipts and credit notes",
  );
  expect(tiles(page)[1][1]).toContain("down 25.6% vs previous 90 days");
  expect(page.textContent.match(/vs previous 90 days/g)).toHaveLength(2);
});

it("shows a loss as a fall against the period before", async () => {
  const page = await open(
    "/dashboard",
    withTotals({ profit: { current: -1500, previous: 2000 } }),
  );
  expect(tiles(page)[2][1]).toBe(
    "Profit in the last 90 days: USD -1,500.00, down 175.0% vs previous 90 days. View the profit and loss report",
  );
  expect(page.textContent).toContain("-1,500.00");
  expect(page.textContent).toContain("175.0%");
});

it("keeps the chosen period in the address and asks for it", async () => {
  const page = await open("/dashboard?period=12m", (url) =>
    url.searchParams.get("period") === "12m" ? twelveMonths : overview,
  );
  expect(requests).toEqual(["/api/v1/report/overview?period=12m"]);
  expect(page.textContent).toContain(
    "Last 12 months, Nov 1, 2025 – Oct 4, 2026 · compared with Nov 1, 2024 – Oct 31, 2025",
  );
  expect(tiles(page)[0]).toEqual([
    "/documents?type=billed&period=12m",
    expect.stringContaining("Billed in the last 12 months: USD 68,848.08"),
  ]);
  // The last 12 months is Reports' own default.
  expect(tiles(page)[2][0]).toBe("/reports#profit");
  const months = page.querySelector('[aria-labelledby="cash-flow-title"]');
  expect(months.querySelector("caption").textContent).toBe(
    "Cash flow each month, USD",
  );
  expect(
    [...months.querySelectorAll("tbody th")].map((month) => month.textContent),
  ).toEqual([
    "November 2025",
    "December 2025",
    "January 2026",
    "February 2026",
    "March 2026",
    "April 2026",
    "May 2026",
    "June 2026",
    "July 2026",
    "August 2026",
    "September 2026",
    "Oct 1–4, 2026",
  ]);
  expect(pills(page)).toEqual([
    ["30 days", "/dashboard?period=30d", null],
    ["90 days", "/dashboard", null],
    ["12 months", "/dashboard?period=12m", "true"],
    ["This year", "/dashboard?period=ytd", null],
  ]);

  await click(pill(page, "90 days"));
  await act(() =>
    vi.waitFor(() =>
      expect(page.textContent).toContain("compared with Apr 8 – Jul 6"),
    ),
  );
  expect(requests).toEqual([
    "/api/v1/report/overview?period=12m",
    "/api/v1/report/overview?period=90d",
  ]);
  expect(pills(page).map((pill) => pill[2])).toEqual([
    null,
    "true",
    null,
    null,
  ]);
});

it("switches currency when there is more than one, keeping the period", async () => {
  const page = await open("/dashboard?period=30d", (url) => ({
    ...overview,
    currencies: ["USD", "EUR"],
    currency: url.searchParams.get("currency") || "USD",
    period: { ...overview.period, key: url.searchParams.get("period") },
  }));
  const select = page.querySelector('label [role="combobox"]');
  expect(select.closest("label").textContent).toContain("Currency");
  await act(async () => select.click());
  const offered = [...document.querySelectorAll('[role="option"]')];
  expect(offered.map((option) => option.dataset.value)).toEqual(["USD", "EUR"]);
  await act(async () => offered[1].click());
  await act(() => vi.waitFor(() => expect(select.value).toBe("EUR")));
  expect(requests).toEqual([
    "/api/v1/report/overview?period=30d",
    "/api/v1/report/overview?currency=EUR&period=30d",
  ]);
  expect(pills(page)[0][1]).toBe("/dashboard?period=30d&currency=EUR");
});

it("asks for the last 90 days when the address names another period", async () => {
  const page = await open("/dashboard?period=7d");
  expect(requests).toEqual(["/api/v1/report/overview?period=90d"]);
  expect(pill(page, "90 days").getAttribute("aria-current")).toBe("true");
});

it("keeps the last figures, dimmed, until the chosen period's arrive, and says so when they can't", async () => {
  let thirtyDays;
  const page = await open("/dashboard", (url) =>
    url.searchParams.get("period") === "30d" ? thirtyDays() : overview,
  );
  const ninetyDays =
    "Last 90 days, Jul 7 – Oct 4, 2026 · compared with Apr 8 – Jul 6";
  expect(content(page).getAttribute("aria-busy")).toBeNull();

  // The answer is on its way.
  let answer;
  thirtyDays = () => new Promise((resolve) => (answer = resolve));
  await click(pill(page, "30 days"));
  await act(() => vi.waitFor(() => expect(answer).toBeDefined()));
  expect(pill(page, "30 days").getAttribute("aria-current")).toBe("true");
  expect(content(page).getAttribute("aria-busy")).toBe("true");
  expect(content(page).className).toContain("opacity-60");
  expect(page.textContent).toContain(ninetyDays);
  expect(page.querySelector('[role="alert"]')).toBeNull();

  // It fails: the 90 days stay, dimmed and named, with a way to try again.
  await act(async () => answer(json({ message: "Try later" }, 500)));
  await act(() =>
    vi.waitFor(() =>
      expect(page.querySelector('[role="alert"]')).not.toBeNull(),
    ),
  );
  expect(page.querySelector('[role="alert"]').textContent).toContain(
    "We couldn’t load the last 30 days.Showing the last 90 days.",
  );
  expect(content(page).getAttribute("aria-busy")).toBe("true");
  expect(page.textContent).toContain(ninetyDays);
  expect(tiles(page)[0][1]).toContain("Billed in the last 90 days");

  // Trying again brings the 30 days.
  thirtyDays = () => lastThirtyDays;
  await click(
    [...page.querySelectorAll("button")].find(
      (button) => button.textContent === "Try again",
    ),
  );
  await act(() =>
    vi.waitFor(() =>
      expect(page.textContent).toContain(
        "Last 30 days, Sep 5 – Oct 4, 2026 · compared with Aug 6 – Sep 4",
      ),
    ),
  );
  expect(page.querySelector('[role="alert"]')).toBeNull();
  expect(content(page).getAttribute("aria-busy")).toBeNull();
  expect(tiles(page)[0][1]).toContain("Billed in the last 30 days");
});

it("says it couldn't load the overview when the answer has none of its figures", async () => {
  const page = await open(
    "/dashboard",
    { ...overview, period: undefined, totals: undefined },
    (page) =>
      vi.waitFor(() =>
        expect(page.querySelector('[role="alert"]')).not.toBeNull(),
      ),
  );
  expect(page.querySelector('[role="alert"]').textContent).toContain(
    "We couldn’t load your overview.",
  );
  expect(page.querySelector("#figures-title")).toBeNull();
});

it("keeps the period when the first currency is picked again", async () => {
  const page = await open("/dashboard?period=30d&currency=EUR", (url) => ({
    ...lastThirtyDays,
    currencies: ["USD", "EUR"],
    currency: url.searchParams.get("currency") || "USD",
  }));
  const select = page.querySelector('label [role="combobox"]');
  expect(select.value).toBe("EUR");
  await act(async () => select.click());
  await act(async () =>
    document.querySelector('[role="option"][data-value="USD"]').click(),
  );
  await act(() => vi.waitFor(() => expect(select.value).toBe("USD")));
  expect(requests).toEqual([
    "/api/v1/report/overview?currency=EUR&period=30d",
    "/api/v1/report/overview?period=30d",
  ]);
  expect(pill(page, "30 days").getAttribute("href")).toBe(
    "/dashboard?period=30d",
  );
});

it("opens each figure's records for the same period and the chosen currency", async () => {
  const page = await open("/dashboard?period=ytd&currency=EUR", (url) => ({
    ...overview,
    currencies: ["USD", "EUR"],
    currency: url.searchParams.get("currency") || "USD",
    period: {
      key: "ytd",
      from: "2026-01-01",
      to: "2026-10-04",
      previousFrom: "2025-01-01",
      previousTo: "2025-10-04",
      step: "month",
    },
  }));
  expect(tiles(page).map(([href]) => href)).toEqual([
    "/documents?type=billed&period=this-year&currency=EUR",
    "/payments?period=this-year&currency=EUR",
    "/reports?period=ytd&currency=EUR#profit",
    "/documents?status=outstanding&currency=EUR&sort=dueDate&dir=asc",
  ]);
  const owed = page.querySelector('[aria-labelledby="owed-title"]');
  expect(
    [...owed.querySelectorAll("a")].map((link) => link.getAttribute("href")),
  ).toEqual([
    "/reports?currency=EUR#ageing",
    "/documents?status=overdue&currency=EUR",
    "/documents?status=outstanding&due=this-week&currency=EUR&sort=dueDate&dir=asc",
    "/documents?status=outstanding&due=next-week&currency=EUR&sort=dueDate&dir=asc",
    "/documents?status=outstanding&due=week-after&currency=EUR&sort=dueDate&dir=asc",
  ]);
  const attention = page.querySelector('[aria-labelledby="attention-title"]');
  expect(attention.querySelector("a").getAttribute("href")).toBe(
    "/documents?status=overdue&currency=EUR",
  );
  // The document status counts every currency, so it keeps to none.
  const status = page.querySelector(
    '[aria-labelledby="document-status-title"]',
  );
  expect(
    [...status.querySelectorAll("a")].map((link) => link.getAttribute("href")),
  ).toEqual([
    "/documents?status=paid",
    "/documents?status=partial",
    "/documents?status=unpaid",
    "/documents?status=overdue",
  ]);
});

it("opens the last 30 days' records, and Reports at the same days", async () => {
  const page = await open("/dashboard?period=30d", lastThirtyDays);
  expect(tiles(page).map(([href]) => href)).toEqual([
    "/documents?type=billed&period=30d",
    "/payments?period=30d",
    "/reports?period=30d#profit",
    "/documents?status=outstanding&sort=dueDate&dir=asc",
  ]);
});

it("shows what is owed by due date and what needs attention, each opening its list", async () => {
  const page = await open("/dashboard");
  const owed = page.querySelector('[aria-labelledby="owed-title"]');
  expect(owed.textContent).toContain("24,285.79across 10 invoices");
  expect(
    [...owed.querySelectorAll("a")].map((link) => [
      link.getAttribute("href"),
      link.getAttribute("aria-label") || link.textContent,
    ]),
  ).toEqual([
    ["/reports#ageing", "View report"],
    [
      "/documents?status=overdue",
      "Overdue: 6,326.03 on 3 invoices. Open the overdue invoices",
    ],
    [
      "/documents?status=outstanding&due=this-week&sort=dueDate&dir=asc",
      "This week, due Oct 4–10: 2,664.09 on 1 invoice. Open these invoices",
    ],
    [
      "/documents?status=outstanding&due=next-week&sort=dueDate&dir=asc",
      "Next week, due Oct 11–17: 13,353.37 on 4 invoices. Open these invoices",
    ],
    [
      "/documents?status=outstanding&due=week-after&sort=dueDate&dir=asc",
      "Week after, due Oct 18–24: 1,942.30 on 2 invoices. Open these invoices",
    ],
  ]);
  for (const text of [
    "6,326",
    "Past due · 3",
    "Oct 4–10 · 1",
    "Oct 11–17 · 4",
    "Oct 18–24 · 2",
    "Grouped by due date. Each column opens its invoices.",
  ]) {
    expect(owed.textContent).toContain(text);
  }
  // Nothing is due later, so that column is left out.
  expect(owed.textContent).not.toContain("Later");

  const attention = page.querySelector('[aria-labelledby="attention-title"]');
  expect(
    [...attention.querySelectorAll("ol > li")].map((item) => item.textContent),
  ).toEqual([
    "OverdueINV-1026Sable Fitness54 days lateDue Aug 113,391.67",
    "OverdueINV-1030Quill & Co. Publishing21 days lateDue Sep 131,969.11",
    "OverdueINV-1031Verdant Farms10 days lateDue Sep 24965.25",
    "Due soonINV-1032Bluefin AnalyticsDue in 2 daysDue Oct 62,664.09",
    "QuotationQUO-0206Kestrel OutdoorAwaiting an answerValid until Oct 91,201.20",
  ]);
  expect(
    [...attention.querySelectorAll("a")].map((link) => [
      link.getAttribute("href"),
      link.getAttribute("aria-label") || link.textContent,
    ]),
  ).toEqual([
    ["/documents?status=overdue", "Overdue invoices"],
    [
      "/document/00000000-0000-0000-0000-000000001026",
      "Overdue INV-1026, Sable Fitness, 54 days late, Due Aug 11, 3,391.67",
    ],
    [
      "/single-customer/00000000-0000-0000-0000-00000000c105",
      "Sable Fitness. View the client",
    ],
    [
      "/document/00000000-0000-0000-0000-000000001030",
      "Overdue INV-1030, Quill & Co. Publishing, 21 days late, Due Sep 13, 1,969.11",
    ],
    [
      "/single-customer/00000000-0000-0000-0000-00000000c107",
      "Quill & Co. Publishing. View the client",
    ],
    [
      "/document/00000000-0000-0000-0000-000000001031",
      "Overdue INV-1031, Verdant Farms, 10 days late, Due Sep 24, 965.25",
    ],
    [
      "/document/00000000-0000-0000-0000-000000001032",
      "Due soon INV-1032, Bluefin Analytics, Due in 2 days, Due Oct 6, 2,664.09",
    ],
    [
      "/single-customer/00000000-0000-0000-0000-00000000c103",
      "Bluefin Analytics. View the client",
    ],
    [
      "/document/00000000-0000-0000-0000-000000000206",
      "Quotation QUO-0206, Kestrel Outdoor, Awaiting an answer, Valid until Oct 9, 1,201.20",
    ],
    [
      "/single-customer/00000000-0000-0000-0000-00000000c109",
      "Kestrel Outdoor. View the client",
    ],
  ]);
  expect(attention.textContent).toContain(
    "No payment reports to confirm, drafts to issue or recurring invoices due this week.",
  );

  const status = page.querySelector(
    '[aria-labelledby="document-status-title"]',
  );
  expect(
    [...status.querySelectorAll("a")].map((link) => [
      link.getAttribute("href"),
      link.getAttribute("aria-label"),
    ]),
  ).toEqual([
    ["/documents?status=paid", "Paid: 32 documents. Open them"],
    ["/documents?status=partial", "Partially paid: 6 documents. Open them"],
    ["/documents?status=unpaid", "Unpaid: 10 documents. Open them"],
    [
      "/documents?status=overdue",
      "Overdue: 3 unpaid invoices past their due date. Open them",
    ],
  ]);
});

it("opens a status only when it has documents", async () => {
  const page = await open("/dashboard", {
    ...overview,
    partial: 0,
    overdue: 0,
  });
  const status = page.querySelector(
    '[aria-labelledby="document-status-title"]',
  );
  expect(
    [...status.querySelectorAll("a")].map((link) => link.getAttribute("href")),
  ).toEqual(["/documents?status=paid", "/documents?status=unpaid"]);
  expect(status.textContent).toContain("Partially paid");
  expect(status.textContent).toContain("Unpaid invoices past their due date");
});

it("shows a later column when something is due then, and says when nothing needs attention", async () => {
  const page = await open("/dashboard", {
    ...overview,
    owedByWeek: overview.owedByWeek.map((week) =>
      week.key === "later" ? { ...week, amount: 500, invoices: 1 } : week,
    ),
    attention: {
      items: [],
      counts: {
        paymentReports: 0,
        overdue: 0,
        dueSoon: 0,
        quotations: 0,
        recurring: 0,
        drafts: 0,
      },
    },
  });
  const owed = page.querySelector('[aria-labelledby="owed-title"]');
  expect(owed.textContent).toContain("LaterFrom Oct 25 · 1");
  const later = [...owed.querySelectorAll("a")].at(-1);
  expect(later.getAttribute("href")).toBe(
    "/documents?status=outstanding&due=later&sort=dueDate&dir=asc",
  );
  expect(later.getAttribute("aria-label")).toBe(
    "Later, due from Oct 25 or with no due date: 500.00 on 1 invoice. Open these invoices",
  );
  const attention = page.querySelector('[aria-labelledby="attention-title"]');
  expect(attention.textContent).toBe(
    "Needs your attentionNothing needs your attention right now.",
  );
  expect(attention.querySelector("a")).toBeNull();
});

it("says when nothing is owed", async () => {
  const page = await open("/dashboard", {
    ...overview,
    owedNow: {
      total: 0,
      notDue: 0,
      overdue: 0,
      invoices: 0,
      overdueInvoices: 0,
    },
    owedByWeek: overview.owedByWeek.map((week) => ({
      ...week,
      amount: 0,
      invoices: 0,
    })),
  });
  const owed = page.querySelector('[aria-labelledby="owed-title"]');
  expect(owed.textContent).toContain("0.00No unpaid invoices");
  expect(owed.textContent).toContain("Nothing is owed to you right now.");
  expect(owed.querySelector('a[href^="/documents"]')).toBeNull();
  expect(tiles(page)[3][1]).toBe(
    "Outstanding now: USD 0.00, no unpaid invoices. View the invoices owed, soonest due first",
  );
});

it("names what the attention list leaves out", async () => {
  const late = overview.attention.items[0];
  const page = await open("/dashboard", {
    ...overview,
    attention: {
      items: Array.from({ length: 6 }, (_, index) => ({
        ...late,
        documentId: `${late.documentId}-${index}`,
      })),
      counts: {
        paymentReports: 0,
        overdue: 9,
        dueSoon: 4,
        quotations: 2,
        recurring: 1,
        drafts: 7,
      },
    },
  });
  const attention = page.querySelector('[aria-labelledby="attention-title"]');
  expect(attention.querySelectorAll("ol > li")).toHaveLength(6);
  expect(attention.textContent).toContain(
    "No payment reports to confirm. Also 3 more overdue invoices, 4 due this week, 2 quotations, 1 recurring invoice and 7 drafts.",
  );
});

it("opens the ageing report from the owed panel in the chosen currency, whatever the period", async () => {
  const page = await open("/dashboard?period=ytd&currency=EUR", (url) => ({
    ...overview,
    currencies: ["USD", "EUR"],
    currency: url.searchParams.get("currency") || "USD",
    period: { ...overview.period, key: "ytd" },
  }));
  const owed = page.querySelector('[aria-labelledby="owed-title"]');
  expect(owed.querySelector("a").getAttribute("href")).toBe(
    "/reports?currency=EUR#ageing",
  );
});

// What was spent in the last 90 days: the five largest categories, then
// three more added up.
const spent = {
  total: 7610.15,
  categories: [
    { category: "Contractors", amount: 3600, expenses: 2 },
    { category: "Software and subscriptions", amount: 1240, expenses: 4 },
    { category: "Marketing", amount: 980, expenses: 2 },
    { category: "Travel", amount: 865.4, expenses: 3 },
    { category: "Office", amount: 412.75, expenses: 5 },
  ],
  others: { amount: 512, expenses: 6, categories: 3 },
};
const spendingPanel = (page) =>
  page.querySelector('[aria-labelledby="spending-title"]');
const links = (element) =>
  [...element.querySelectorAll("a")].map((link) => [
    link.getAttribute("href"),
    link.getAttribute("aria-label") || link.textContent,
  ]);

it("lists what was spent in the period by category, each opening its expenses", async () => {
  const page = await open("/dashboard", { ...overview, spending: spent });
  const spending = spendingPanel(page);
  expect(spending.querySelector("p").textContent).toBe(
    "7,610.15spent in these 90 days",
  );
  expect(links(spending)).toEqual([
    ["/expenses?period=90d&currency=USD", "All expenses"],
    [
      "/expenses?category=Contractors&period=90d&currency=USD",
      "Contractors: 3,600.00 on 2 expenses, 47% of spending. Open these expenses",
    ],
    [
      "/expenses?category=Software+and+subscriptions&period=90d&currency=USD",
      "Software and subscriptions: 1,240.00 on 4 expenses, 16% of spending. Open these expenses",
    ],
    [
      "/expenses?category=Marketing&period=90d&currency=USD",
      "Marketing: 980.00 on 2 expenses, 13% of spending. Open these expenses",
    ],
    [
      "/expenses?category=Travel&period=90d&currency=USD",
      "Travel: 865.40 on 3 expenses, 11% of spending. Open these expenses",
    ],
    [
      "/expenses?category=Office&period=90d&currency=USD",
      "Office: 412.75 on 5 expenses, 5% of spending. Open these expenses",
    ],
  ]);
  const rows = [...spending.querySelectorAll("ol > li")];
  expect(rows.map((row) => row.textContent)).toEqual([
    "Contractors · 23,600.0047%",
    "Software and subscriptions · 41,240.0016%",
    "Marketing · 2980.0013%",
    "Travel · 3865.4011%",
    "Office · 5412.755%",
    "3 other categories · 6512.007%",
  ]);
  // Each bar is against the largest row.
  expect(
    rows.map((row) =>
      Math.round(parseFloat(row.querySelector("[style]").style.width)),
    ),
  ).toEqual([100, 34, 27, 24, 11, 14]);
  // The rest of the categories open nothing, and are read as one figure.
  const others = rows[5].firstElementChild;
  expect(others.tagName).toBe("DIV");
  expect(others.getAttribute("role")).toBe("img");
  expect(others.getAttribute("aria-label")).toBe(
    "3 other categories: 512.00 on 6 expenses, 7% of spending",
  );
  expect(spending.lastElementChild.textContent).toBe(
    "Each category opens its expenses for these 90 days.",
  );
});

it("opens the expenses of the period shown, in the chosen currency", async () => {
  const page = await open("/dashboard?period=ytd&currency=EUR", (url) => ({
    ...overview,
    currencies: ["USD", "EUR"],
    currency: url.searchParams.get("currency") || "USD",
    period: {
      key: "ytd",
      from: "2026-01-01",
      to: "2026-10-04",
      previousFrom: "2025-01-01",
      previousTo: "2025-10-04",
      step: "month",
    },
    spending: spent,
  }));
  const spending = spendingPanel(page);
  expect(links(spending).map(([href]) => href)).toEqual([
    "/expenses?period=this-year&currency=EUR",
    "/expenses?category=Contractors&period=this-year&currency=EUR",
    "/expenses?category=Software+and+subscriptions&period=this-year&currency=EUR",
    "/expenses?category=Marketing&period=this-year&currency=EUR",
    "/expenses?category=Travel&period=this-year&currency=EUR",
    "/expenses?category=Office&period=this-year&currency=EUR",
  ]);
  expect(spending.querySelector("p").textContent).toBe(
    "7,610.15spent this year",
  );
  expect(spending.lastElementChild.textContent).toBe(
    "Each category opens its expenses for this year.",
  );
});

it.each([
  ["30d", "in these 30 days", "30d"],
  ["90d", "in these 90 days", "90d"],
  ["12m", "in these 12 months", "12m"],
  ["ytd", "this year", "this-year"],
])(
  "says when nothing was spent over %s, with a way to record an expense",
  async (key, words, list) => {
    const page = await open(`/dashboard?period=${key}`, {
      ...overview,
      period: { ...overview.period, key },
    });
    const spending = spendingPanel(page);
    expect(spending.textContent).toBe(
      `SpendingAll expenses0.00spent ${words}No expenses recorded ${words}.Record an expenseExpenses by category, each opening its expenses.`,
    );
    expect(links(spending)).toEqual([
      [`/expenses?period=${list}&currency=USD`, "All expenses"],
      ["/expenses?new=1&currency=USD", "Record an expense"],
    ]);
  },
);

it("shows a viewer the way to record an expense disabled, with the reason", async () => {
  const page = await open("/dashboard", overview, loaded, viewer);
  const spending = spendingPanel(page);
  await act(() =>
    vi.waitFor(() => expect(spending.querySelector("button")).not.toBeNull()),
  );
  const record = spending.querySelector("button");
  expect(record.textContent).toBe("Record an expense");
  expect(record.disabled).toBe(true);
  expect(record.title).toBe("Viewers can't change anything in this business");
  expect(
    document.getElementById(record.getAttribute("aria-describedby"))
      .textContent,
  ).toBe("Viewers can't change anything in this business");
  expect(links(spending)).toEqual([
    ["/expenses?period=90d&currency=USD", "All expenses"],
  ]);
});

it("keeps each panel's place in its row while the overview loads", async () => {
  let respond;
  const page = await open(
    "/dashboard",
    () => new Promise((resolve) => (respond = resolve)),
    () => vi.waitFor(() => expect(respond).toBeDefined()),
  );
  const loading = page.querySelector(
    '[aria-label="Loading your business overview"]',
  );
  // After the words for screen readers: the four tiles, the cash flow and
  // what is owed, what needs attention and the top clients, Spending, the
  // top items and what is coming up, then the latest payments and the
  // document status.
  expect(
    [...loading.children].slice(1).map((row) => row.children.length),
  ).toEqual([4, 2, 2, 3, 2]);
  expect(spendingPanel(page)).toBeNull();
  await act(async () => respond({ ...overview, spending: spent }));
  await act(() => loaded(page));
  expect(
    page.querySelector('[aria-label="Loading your business overview"]'),
  ).toBeNull();
  expect(spendingPanel(page).querySelector("p").textContent).toBe(
    "7,610.15spent in these 90 days",
  );
});

it("keeps the last period's spending, dimmed, when the chosen period's can't be loaded", async () => {
  const page = await open("/dashboard", (url) =>
    url.searchParams.get("period") === "30d"
      ? json({ message: "Try later" }, 500)
      : { ...overview, spending: spent },
  );
  await click(pill(page, "30 days"));
  await act(() =>
    vi.waitFor(() =>
      expect(page.querySelector('[role="alert"]')).not.toBeNull(),
    ),
  );
  expect(page.querySelector('[role="alert"]').textContent).toContain(
    "We couldn’t load the last 30 days.Showing the last 90 days.",
  );
  const spending = spendingPanel(page);
  expect(spending.closest("[aria-busy]").getAttribute("aria-busy")).toBe(
    "true",
  );
  expect(spending.querySelector("p").textContent).toBe(
    "7,610.15spent in these 90 days",
  );
  expect(links(spending)[1][0]).toBe(
    "/expenses?category=Contractors&period=90d&currency=USD",
  );
});

it("leaves Spending out when the server sends no spending", async () => {
  const { spending, ...older } = overview;
  const page = await open("/dashboard", older);
  expect(spendingPanel(page)).toBeNull();
  expect(layout(page)).toEqual([
    ["Figures in the last 90 days"],
    ["Cash flow", "Invoices owed to you"],
    ["Needs your attention", "Top clients"],
    ["Top items", "Coming up"],
    ["Latest payments", "Document status"],
  ]);
});

it.each([
  ["30d", "30d"],
  ["90d", "90d"],
  ["12m", "12m"],
  ["ytd", "this-year"],
])(
  "keeps every Spending link over %s to the currency its figures count, though the address names none",
  async (key, list) => {
    const page = await open(`/dashboard?period=${key}`, {
      ...overview,
      period: { ...overview.period, key },
      spending: spent,
    });
    expect(links(spendingPanel(page)).map(([href]) => href)).toEqual([
      `/expenses?period=${list}&currency=USD`,
      ...spent.categories.map(
        ({ category }) =>
          `/expenses?${new URLSearchParams({ category, period: list, currency: "USD" })}`,
      ),
    ]);
    // The other figures keep to no currency while none was chosen.
    expect(tiles(page)[1][0]).toBe(`/payments?period=${list}`);
  },
);

it("keeps a category named others apart from the row that adds up the rest", async () => {
  const warnings = vi.spyOn(console, "error").mockImplementation(() => {});
  try {
    const page = await open("/dashboard", {
      ...overview,
      spending: {
        ...spent,
        categories: [
          ...spent.categories.slice(0, 4),
          { category: "others", amount: 412.75, expenses: 5 },
        ],
      },
    });
    expect(
      [...spendingPanel(page).querySelectorAll("ol > li")]
        .slice(-2)
        .map((row) => row.textContent),
    ).toEqual(["others · 5412.755%", "3 other categories · 6512.007%"]);
    expect(warnings.mock.calls.flat().join(" ")).not.toContain("same key");
  } finally {
    warnings.mockRestore();
  }
});

const shortcuts = (page) => page.querySelector('nav[aria-label="Shortcuts"]');
const REFUSAL = "Viewers can't change anything in this business";

it("offers shortcuts to a new invoice, expense or client under the subtitle", async () => {
  const page = await open("/dashboard");
  const nav = shortcuts(page);
  expect(nav.previousElementSibling.textContent).toBe(
    "Last 90 days, Jul 7 – Oct 4, 2026 · compared with Apr 8 – Jul 6",
  );
  expect(
    [...nav.querySelectorAll("a")].map((link) => [
      link.textContent,
      link.getAttribute("href"),
    ]),
  ).toEqual([
    ["New invoice", "/create-doc"],
    ["Record an expense", "/expenses?new=1"],
    ["Add a client", "/create-customer"],
  ]);
  expect(nav.querySelector("button")).toBeNull();
});

it("shows a viewer the shortcuts disabled, with the reason given once", async () => {
  const page = await open("/dashboard", overview, loaded, viewer);
  const nav = shortcuts(page);
  await act(() =>
    vi.waitFor(() => expect(nav.querySelectorAll("button")).toHaveLength(3)),
  );
  const keys = [...nav.querySelectorAll("button")];
  expect(keys.map((key) => [key.textContent, key.disabled, key.title])).toEqual(
    [
      ["New invoice", true, REFUSAL],
      ["Record an expense", true, REFUSAL],
      ["Add a client", true, REFUSAL],
    ],
  );
  // One note gives the reason, and each shortcut points to it.
  const notes = new Set(
    keys.map((key) => key.getAttribute("aria-describedby")),
  );
  expect(notes.size).toBe(1);
  expect(document.getElementById([...notes][0]).textContent).toBe(REFUSAL);
  expect(
    [...page.querySelectorAll("header span")].filter(
      (span) => span.textContent === REFUSAL,
    ),
  ).toHaveLength(1);
  expect(nav.querySelector("a")).toBeNull();
});

it("opens a client from the attention list in a link of its own beside the document's", async () => {
  const page = await open("/dashboard");
  const rows = [
    ...page.querySelectorAll('[aria-labelledby="attention-title"] ol > li'),
  ];
  // With a client: the document and the client, each its own link, neither
  // inside the other, both reached with Tab in reading order.
  const links = [...rows[0].querySelectorAll("a")];
  expect(links.map((link) => link.getAttribute("href"))).toEqual([
    "/document/00000000-0000-0000-0000-000000001026",
    "/single-customer/00000000-0000-0000-0000-00000000c105",
  ]);
  expect(links[1].textContent).toBe("Sable Fitness");
  for (const link of links) {
    expect(link.querySelector("a")).toBeNull();
    expect(link.parentElement.closest("a")).toBeNull();
    expect(link.tabIndex).toBe(0);
  }
  expect(
    links[0].compareDocumentPosition(links[1]) &
      Node.DOCUMENT_POSITION_FOLLOWING,
  ).toBeTruthy();
  // Without one, the name is only words, and the row opens its document.
  const unlinked = [...rows[2].querySelectorAll("a")];
  expect(unlinked.map((link) => link.getAttribute("href"))).toEqual([
    "/document/00000000-0000-0000-0000-000000001031",
  ]);
  expect(rows[2].querySelector('[class*="grid-area:client"]').tagName).toBe(
    "SPAN",
  );
  expect(rows[2].textContent).toContain("Verdant Farms");
  // Each row is read once: the document's link reads it whole, so the words
  // beside it are hidden from screen readers.
  for (const row of rows) {
    const read = row.cloneNode(true);
    read
      .querySelectorAll('a, [aria-hidden="true"]')
      .forEach((node) => node.remove());
    expect(read.textContent).toBe("");
  }
  expect(
    page.querySelector('[aria-labelledby="attention-title"]').lastElementChild
      .textContent,
  ).toBe(
    "No payment reports to confirm, drafts to issue or recurring invoices due this week. A client’s name opens the client.",
  );
});

it("says nothing of clients when no row in the attention list links one", async () => {
  const page = await open("/dashboard", {
    ...overview,
    attention: {
      ...overview.attention,
      items: overview.attention.items.map((item) => ({
        ...item,
        clientId: null,
      })),
    },
  });
  const attention = page.querySelector('[aria-labelledby="attention-title"]');
  expect(
    [...attention.querySelectorAll("ol a")].map((link) =>
      link.getAttribute("href"),
    ),
  ).toEqual([
    "/document/00000000-0000-0000-0000-000000001026",
    "/document/00000000-0000-0000-0000-000000001030",
    "/document/00000000-0000-0000-0000-000000001031",
    "/document/00000000-0000-0000-0000-000000001032",
    "/document/00000000-0000-0000-0000-000000000206",
  ]);
  expect(attention.lastElementChild.textContent).toBe(
    "No payment reports to confirm, drafts to issue or recurring invoices due this week.",
  );
});

it("hides the cash flow's table for screen readers inside a box that clips it", async () => {
  const page = await open("/dashboard");
  const table = page.querySelector('[aria-labelledby="cash-flow-title"] table');
  // A table is never narrower than its widest row, so the box is hidden,
  // not the table, which would still widen the page on a small phone.
  expect(table.parentElement.className).toBe("sr-only");
  expect(table.className).toBe("");
  expect(table.querySelector("caption").textContent).toBe(
    "Cash flow each week, USD",
  );
  expect(table.querySelectorAll("tbody tr")).toHaveLength(13);
});

const topClients = (page) =>
  page.querySelector('[aria-labelledby="top-clients-title"]');
const C = "00000000-0000-0000-0000-00000000c";

it("lists the clients billed the most beside what needs attention, each linked name opening the client", async () => {
  const page = await open("/dashboard");
  const panel = topClients(page);
  // In the row the document status had, beside what needs attention.
  expect(
    [...panel.parentElement.children].map((section) =>
      section.getAttribute("aria-labelledby"),
    ),
  ).toEqual(["attention-title", "top-clients-title"]);
  expect(panel.querySelector("p").textContent).toBe(
    "Billed in these 90 days, largest first",
  );
  expect(links(panel)).toEqual([
    ["/customers", "All clients"],
    [
      `/single-customer/${C}107`,
      "Quill & Co. Publishing: 13,459.88 billed on 2 documents, owes 1,969.11. View the client",
    ],
    [
      `/single-customer/${C}109`,
      "Kestrel Outdoor: 10,317.45 billed on 2 documents, owes nothing. View the client",
    ],
    [
      `/single-customer/${C}110`,
      "Oak Street Bakery: 8,363.36 billed on 3 documents, owes 3,217.50. View the client",
    ],
    [
      `/single-customer/${C}101`,
      "Atlas Robotics: 5,298.15 billed on 2 documents, owes 2,550.40. View the client",
    ],
  ]);
  const rows = [...panel.querySelectorAll("ol > li")];
  expect(rows.map((row) => row.textContent)).toEqual([
    "Quill & Co. PublishingOwes 1,969.11 · 2 documents13,459.88",
    "Kestrel OutdoorOwes nothing · 2 documents10,317.45",
    "Verdant FarmsOwes 5,311.77 · 2 documents8,590.73",
    "Oak Street BakeryOwes 3,217.50 · 3 documents8,363.36",
    "Atlas RoboticsOwes 2,550.40 · 2 documents5,298.15",
  ]);
  // Each bar is against the largest.
  expect(
    rows.map((row) =>
      Math.round(parseFloat(row.querySelector("[style]").style.width)),
    ),
  ).toEqual([100, 77, 64, 62, 39]);
  // The count of documents keeps to one line.
  expect(
    rows.map((row) => row.querySelector(".whitespace-nowrap").textContent),
  ).toEqual([
    "· 2 documents",
    "· 2 documents",
    "· 2 documents",
    "· 3 documents",
    "· 2 documents",
  ]);
  // What a client owes reads in the usual ink, since it needn't be overdue;
  // owing nothing is muted.
  expect(
    rows.map((row) =>
      row.querySelector(".text-spot-ink") ? "owes" : "settled",
    ),
  ).toEqual(["owes", "settled", "owes", "owes", "owes"]);
  expect(panel.querySelector(".text-overdue-strong")).toBeNull();
  // Documents saved without a linked client: the name is only words, and
  // the row is read as one figure.
  const unlinked = rows[2].firstElementChild;
  expect(unlinked.querySelector("a")).toBeNull();
  expect(unlinked.getAttribute("role")).toBe("img");
  expect(unlinked.getAttribute("aria-label")).toBe(
    "Verdant Farms: 8,590.73 billed on 2 documents, owes 5,311.77",
  );
  // A linked row is read once: its name reads the whole row.
  for (const row of [rows[0], rows[1], rows[3], rows[4]]) {
    const read = row.cloneNode(true);
    read
      .querySelectorAll('a, [aria-hidden="true"]')
      .forEach((node) => node.remove());
    expect(read.textContent).toBe("");
  }
  expect(panel.lastElementChild.textContent).toBe(
    "A name opens the client: their documents, balance and statement.",
  );
});

it("opens every client the same way in any currency and period", async () => {
  const page = await open("/dashboard?period=ytd&currency=EUR", (url) => ({
    ...overview,
    currencies: ["USD", "EUR"],
    currency: url.searchParams.get("currency") || "USD",
    period: { ...overview.period, key: "ytd" },
  }));
  const panel = topClients(page);
  expect(panel.querySelector("p").textContent).toBe(
    "Billed this year, largest first",
  );
  expect(links(panel).map(([href]) => href)).toEqual([
    "/customers",
    `/single-customer/${C}107`,
    `/single-customer/${C}109`,
    `/single-customer/${C}110`,
    `/single-customer/${C}101`,
  ]);
});

it("says nothing of names opening clients when no client is linked", async () => {
  const page = await open("/dashboard", {
    ...overview,
    topClients: overview.topClients.map((client) => ({
      ...client,
      clientId: null,
    })),
  });
  const panel = topClients(page);
  expect(links(panel)).toEqual([["/customers", "All clients"]]);
  expect(panel.querySelectorAll('ol > li > [role="img"]')).toHaveLength(5);
  expect(panel.lastElementChild.tagName).toBe("OL");
});

it.each([
  ["30d", "in these 30 days"],
  ["90d", "in these 90 days"],
  ["12m", "in these 12 months"],
  ["ytd", "this year"],
])("says when no one was billed over %s", async (key, words) => {
  const page = await open(`/dashboard?period=${key}`, {
    ...overview,
    period: { ...overview.period, key },
    topClients: [],
  });
  const panel = topClients(page);
  expect(panel.textContent).toBe(
    `Top clientsAll clientsNo one billed ${words}.`,
  );
  expect(links(panel)).toEqual([["/customers", "All clients"]]);
});

it("leaves what needs attention its whole row when the server sends no top clients", async () => {
  const { topClients: left, ...older } = overview;
  const page = await open("/dashboard", older);
  expect(topClients(page)).toBeNull();
  expect(layout(page)).toEqual([
    ["Figures in the last 90 days"],
    ["Cash flow", "Invoices owed to you"],
    ["Needs your attention"],
    ["Spending", "Top items", "Coming up"],
    ["Latest payments", "Document status"],
  ]);
  expect(
    page.querySelector('[aria-labelledby="attention-title"]').parentElement
      .className,
  ).toBe("contents");
});

it("keeps the document status, beside the latest payments, in the last row", async () => {
  const page = await open("/dashboard");
  const status = page.querySelector(
    '[aria-labelledby="document-status-title"]',
  );
  const last = content(page).lastElementChild;
  expect(status.parentElement).toBe(last);
  expect(
    [...last.children].map((section) =>
      section.getAttribute("aria-labelledby"),
    ),
  ).toEqual(["payments-title", "document-status-title"]);
  expect(status.textContent).toContain("48 documents");
  expect(
    [...status.querySelectorAll("a")].map((link) => link.getAttribute("href")),
  ).toEqual([
    "/documents?status=paid",
    "/documents?status=partial",
    "/documents?status=unpaid",
    "/documents?status=overdue",
  ]);
});

it("keeps the last period's top clients, dimmed, when the chosen period's can't be loaded", async () => {
  const page = await open("/dashboard", (url) =>
    url.searchParams.get("period") === "30d"
      ? json({ message: "Try later" }, 500)
      : overview,
  );
  await click(pill(page, "30 days"));
  await act(() =>
    vi.waitFor(() =>
      expect(page.querySelector('[role="alert"]')).not.toBeNull(),
    ),
  );
  const panel = topClients(page);
  expect(panel.closest("[aria-busy]").getAttribute("aria-busy")).toBe("true");
  expect(panel.querySelector("p").textContent).toBe(
    "Billed in these 90 days, largest first",
  );
  expect(panel.querySelectorAll("ol > li")).toHaveLength(5);
});

const topItems = (page) =>
  page.querySelector('[aria-labelledby="top-items-title"]');

it("lists the items that brought in the most beside Spending, each opening Items searched for it", async () => {
  const page = await open("/dashboard");
  const panel = topItems(page);
  expect(
    [...panel.parentElement.children].map((section) =>
      section.getAttribute("aria-labelledby"),
    ),
  ).toEqual(["spending-title", "top-items-title", "coming-up-title"]);
  expect(panel.querySelector("p").textContent).toBe(
    "What you sold in these 90 days, before tax",
  );
  expect(links(panel)).toEqual([
    ["/items", "All items"],
    [
      "/items?q=Motion+graphics+%28per+15+seconds%29",
      "Motion graphics (per 15 seconds): 18,200.00, 28 sold, 28% of income. Search Items for it",
    ],
    [
      "/items?q=Logo+and+identity+system",
      "Logo and identity system: 9,280.00, 3 sold, 14% of income. Search Items for it",
    ],
    [
      "/items?q=Consulting+%28hourly%29",
      "Consulting (hourly): 7,140.00, 52 sold, 11% of income. Search Items for it",
    ],
    [
      "/items?q=Front-end+build+%28per+page%29",
      "Front-end build (per page): 6,460.00, 17 sold, 10% of income. Search Items for it",
    ],
    [
      "/items?q=Brand+strategy+workshop",
      "Brand strategy workshop: 5,400.00, 3 sold, 8% of income. Search Items for it",
    ],
  ]);
  const rows = [...panel.querySelectorAll("ol > li")];
  expect(rows.map((row) => row.textContent)).toEqual([
    "Motion graphics (per 15 seconds)28 sold · 28% of income18,200.00",
    "Logo and identity system3 sold · 14% of income9,280.00",
    "Consulting (hourly)52 sold · 11% of income7,140.00",
    "Front-end build (per page)17 sold · 10% of income6,460.00",
    "Brand strategy workshop3 sold · 8% of income5,400.00",
  ]);
  // Each bar is against the largest.
  expect(
    rows.map((row) =>
      Math.round(parseFloat(row.querySelector("[style]").style.width)),
    ),
  ).toEqual([100, 51, 39, 35, 30]);
  expect(panel.lastElementChild.textContent).toBe(
    "Everything sold adds up to the income behind Profit. Each row searches Items for it.",
  );
});

it("adds up lines without a name as other lines, which open nothing", async () => {
  const page = await open("/dashboard", {
    ...overview,
    topItems: [
      ...overview.topItems.slice(0, 3),
      { item: null, quantity: 4, amount: 650.5, documents: 2 },
      { item: "Stickers", quantity: 40, amount: 12, documents: 1 },
    ],
  });
  const rows = [...topItems(page).querySelectorAll("ol > li")];
  expect(rows.map((row) => row.textContent)).toEqual([
    "Motion graphics (per 15 seconds)28 sold · 28% of income18,200.00",
    "Logo and identity system3 sold · 14% of income9,280.00",
    "Consulting (hourly)52 sold · 11% of income7,140.00",
    "Other lines4 sold · 1% of income650.50",
    "Stickers40 sold · <1% of income12.00",
  ]);
  const other = rows[3].firstElementChild;
  expect(other.tagName).toBe("DIV");
  expect(other.getAttribute("role")).toBe("img");
  expect(other.getAttribute("aria-label")).toBe(
    "Other lines: 650.50, 4 sold, 1% of income",
  );
  expect(links(topItems(page)).map(([href]) => href)).toEqual([
    "/items",
    "/items?q=Motion+graphics+%28per+15+seconds%29",
    "/items?q=Logo+and+identity+system",
    "/items?q=Consulting+%28hourly%29",
    "/items?q=Stickers",
  ]);
});

it("leaves the shares out when there was no income to share", async () => {
  const page = await open("/dashboard", {
    ...overview,
    topItems: [{ item: "Refunded work", quantity: 2, amount: 0, documents: 2 }],
    itemsTotal: 0,
  });
  const panel = topItems(page);
  expect(panel.querySelector("ol > li").textContent).toBe(
    "Refunded work2 sold0.00",
  );
  expect(links(panel)[1]).toEqual([
    "/items?q=Refunded+work",
    "Refunded work: 0.00, 2 sold. Search Items for it",
  ]);
});

it.each([
  ["30d", "in these 30 days"],
  ["90d", "in these 90 days"],
  ["12m", "in these 12 months"],
  ["ytd", "this year"],
])("says when nothing was sold over %s", async (key, words) => {
  const page = await open(`/dashboard?period=${key}`, {
    ...overview,
    period: { ...overview.period, key },
    topItems: [],
    itemsTotal: 0,
  });
  const panel = topItems(page);
  expect(panel.textContent).toBe(`Top itemsAll itemsNothing sold ${words}.`);
  expect(links(panel)).toEqual([["/items", "All items"]]);
});

it("leaves the rest of its row when the server sends no top items", async () => {
  const { topItems: left, itemsTotal, ...older } = overview;
  const page = await open("/dashboard", older);
  expect(topItems(page)).toBeNull();
  expect(layout(page)[3]).toEqual(["Spending", "Coming up"]);
});

const comingUp = (page) =>
  page.querySelector('[aria-labelledby="coming-up-title"]');
// A detail after a quotation's number starts with its dot, kept on the line
// of the word after it.
const DOT = " ·\u00a0";
const withComingUp = (changes) => ({
  ...overview,
  comingUp: { ...overview.comingUp, ...changes },
});

it("shows what is coming up beside the top items: the next recurring invoices and the quotations waiting for an answer", async () => {
  const page = await open("/dashboard");
  const panel = comingUp(page);
  expect(
    [...panel.parentElement.children].map((section) =>
      section.getAttribute("aria-labelledby"),
    ),
  ).toEqual(["spending-title", "top-items-title", "coming-up-title"]);
  expect(
    [...panel.querySelectorAll("h3")].map((heading) => heading.textContent),
  ).toEqual(["Recurring invoices", "Quotations"]);
  expect(panel.textContent).toContain("Next 90 days: 7 invoices · 10,650.00");
  expect(links(panel)).toEqual([
    ["/recurring", "Recurring"],
    [
      "/recurring",
      "Oct 12: Harbor & Pine Architects, INV-1012, 450.00. Open the recurring invoices",
    ],
    [
      "/recurring",
      "Oct 20: Bluefin Analytics, INV-1020, 2,500.00. Open the recurring invoices",
    ],
    [
      "/recurring",
      "Nov 3: Oak Street Bakery, INV-1027, 1,800.00. Open the recurring invoices",
    ],
    [
      "/recurring",
      "Nov 12: Harbor & Pine Architects, INV-1012, 450.00. Open the recurring invoices",
    ],
    [
      "/recurring",
      "Nov 20: Bluefin Analytics, INV-1020, 2,500.00. Open the recurring invoices",
    ],
    ["/documents?type=quotation", "All quotations"],
    [
      "/document/00000000-0000-0000-0000-000000000206",
      "QUO-0206, Kestrel Outdoor, valid until Oct 9, 1,201.20. Open the quotation",
    ],
    [
      "/documents?type=quotation&sort=dueDate&dir=asc",
      "Expired without an answer: 5 quotations, 25,343.18. Open the quotations, sorted by their last valid day",
    ],
  ]);
  const [runs, awaiting] = panel.querySelectorAll("ol");
  expect([...runs.children].map((run) => run.textContent)).toEqual([
    "Oct 12Harbor & Pine ArchitectsINV-1012450.00",
    "Oct 20Bluefin AnalyticsINV-10202,500.00",
    "Nov 3Oak Street BakeryINV-10271,800.00",
    "Nov 12Harbor & Pine ArchitectsINV-1012450.00",
    "Nov 20Bluefin AnalyticsINV-10202,500.00",
  ]);
  expect(awaiting.previousElementSibling.textContent).toBe(
    "Awaiting an answer · 1",
  );
  expect(awaiting.textContent).toBe(
    `QUO-0206${DOT}Kestrel Outdoor${DOT}valid until Oct 91,201.20`,
  );
  // The day keeps to one line; a long client's name can wrap.
  expect(
    [...awaiting.querySelectorAll("a > span:first-child > span")].map(
      (detail) => detail.className,
    ),
  ).toEqual(["whitespace-nowrap", "", "whitespace-nowrap"]);
  expect(panel.lastElementChild.lastElementChild.textContent).toBe(
    "Expired without an answer · 5Worth following up, or letting go25,343.18",
  );
});

it("keeps the quotations' lists to the chosen currency", async () => {
  const page = await open("/dashboard?currency=EUR", (url) => ({
    ...overview,
    currencies: ["USD", "EUR"],
    currency: url.searchParams.get("currency") || "USD",
  }));
  expect(
    links(comingUp(page))
      .map(([href]) => href)
      .filter((href) => !href.startsWith("/recurring")),
  ).toEqual([
    "/documents?type=quotation&currency=EUR",
    "/document/00000000-0000-0000-0000-000000000206",
    "/documents?type=quotation&currency=EUR&sort=dueDate&dir=asc",
  ]);
});

const noSchedules = {
  active: 0,
  next90Days: { invoices: 0, amount: 0 },
  nextRuns: [],
};

it("offers to set up a recurring invoice while there is none", async () => {
  const page = await open(
    "/dashboard",
    withComingUp({ recurring: noSchedules }),
  );
  const panel = comingUp(page);
  expect(panel.textContent).toContain(
    "Recurring invoicesRecurringNone yet. Bill a client every week, month or year without lifting a finger.Set one upQuotations",
  );
  expect(panel.textContent).not.toContain("Next 90 days");
  expect(links(panel).slice(0, 3)).toEqual([
    ["/recurring", "Recurring"],
    ["/recurring", "Set one up"],
    ["/documents?type=quotation", "All quotations"],
  ]);
});

it("shows a viewer the way to set up a recurring invoice disabled, with the reason", async () => {
  const page = await open(
    "/dashboard",
    withComingUp({ recurring: noSchedules }),
    loaded,
    viewer,
  );
  const panel = comingUp(page);
  await act(() =>
    vi.waitFor(() => expect(panel.querySelector("button")).not.toBeNull()),
  );
  const setUp = panel.querySelector("button");
  expect(setUp.textContent).toBe("Set one up");
  expect(setUp.disabled).toBe(true);
  expect(setUp.title).toBe(REFUSAL);
  expect(
    document.getElementById(setUp.getAttribute("aria-describedby")).textContent,
  ).toBe(REFUSAL);
  expect(links(panel)[0]).toEqual(["/recurring", "Recurring"]);
  expect(links(panel)[1]).toEqual([
    "/documents?type=quotation",
    "All quotations",
  ]);
});

it("says when no recurring invoice runs in the next 90 days", async () => {
  const page = await open(
    "/dashboard",
    withComingUp({ recurring: { ...noSchedules, active: 2 } }),
  );
  const panel = comingUp(page);
  expect(panel.textContent).toContain(
    "Recurring invoicesRecurringNothing runs in the next 90 days.Quotations",
  );
  expect(panel.textContent).not.toContain("Set one up");
});

it("names a schedule's draft template and a run without a client", async () => {
  const [run] = overview.comingUp.recurring.nextRuns;
  const page = await open(
    "/dashboard",
    withComingUp({
      recurring: {
        active: 1,
        next90Days: { invoices: 1, amount: 450 },
        nextRuns: [{ ...run, documentNumber: null, client: null }],
      },
    }),
  );
  const panel = comingUp(page);
  expect(panel.textContent).toContain("Next 90 days: 1 invoice · 450.00");
  expect(panel.querySelector("ol").textContent).toBe(
    "Oct 12No clientDraft invoice450.00",
  );
  expect(links(panel)[1]).toEqual([
    "/recurring",
    "Oct 12: No client, Draft invoice, 450.00. Open the recurring invoices",
  ]);
});

it("says when no quotation is waiting for an answer", async () => {
  const page = await open(
    "/dashboard",
    withComingUp({
      quotations: {
        awaiting: { count: 0, amount: 0, items: [] },
        expired: { count: 0, amount: 0 },
      },
    }),
  );
  const panel = comingUp(page);
  expect(panel.lastElementChild.textContent).toBe(
    "QuotationsAll quotationsNo quotations are waiting for an answer.",
  );
  expect(links(panel).at(-1)).toEqual([
    "/documents?type=quotation",
    "All quotations",
  ]);
});

it("names the quotations waiting for an answer that it leaves out, and leaves out the expired ones when there are none", async () => {
  const [quote] = overview.comingUp.quotations.awaiting.items;
  const page = await open(
    "/dashboard",
    withComingUp({
      quotations: {
        awaiting: {
          count: 5,
          amount: 6006,
          items: ["206", "207", "208"].map((number, index) => ({
            ...quote,
            documentId: `00000000-0000-0000-0000-000000000${number}`,
            documentNumber: `QUO-0${number}`,
            validUntil: `2026-10-${String(9 + index * 5).padStart(2, "0")}`,
          })),
        },
        expired: { count: 0, amount: 0 },
      },
    }),
  );
  const panel = comingUp(page);
  const awaiting = panel.querySelectorAll("ol")[1];
  expect([...awaiting.children].map((item) => item.textContent)).toEqual([
    `QUO-0206${DOT}Kestrel Outdoor${DOT}valid until Oct 91,201.20`,
    `QUO-0207${DOT}Kestrel Outdoor${DOT}valid until Oct 141,201.20`,
    `QUO-0208${DOT}Kestrel Outdoor${DOT}valid until Oct 191,201.20`,
  ]);
  expect(awaiting.previousElementSibling.textContent).toBe(
    "Awaiting an answer · 5",
  );
  expect(awaiting.nextElementSibling.textContent).toBe(
    "Also 2 more quotations",
  );
  expect(panel.textContent).not.toContain("Expired");
  expect(
    links(panel)
      .map(([href]) => href)
      .slice(-3),
  ).toEqual([
    "/document/00000000-0000-0000-0000-000000000206",
    "/document/00000000-0000-0000-0000-000000000207",
    "/document/00000000-0000-0000-0000-000000000208",
  ]);
});

it("leaves the rest of its row when the server sends nothing coming up", async () => {
  const { comingUp: left, ...older } = overview;
  const page = await open("/dashboard", older);
  expect(comingUp(page)).toBeNull();
  expect(layout(page)[3]).toEqual(["Spending", "Top items"]);
});

it("names a quotation with no last valid day as having no expiry", async () => {
  const [quote] = overview.comingUp.quotations.awaiting.items;
  const page = await open(
    "/dashboard",
    withComingUp({
      quotations: {
        ...overview.comingUp.quotations,
        awaiting: {
          count: 1,
          amount: 1201.2,
          items: [{ ...quote, validUntil: null, days: null }],
        },
      },
    }),
  );
  const panel = comingUp(page);
  expect(panel.querySelectorAll("ol")[1].textContent).toBe(
    `QUO-0206${DOT}Kestrel Outdoor${DOT}No expiry1,201.20`,
  );
  expect(links(panel)).toContainEqual([
    "/document/00000000-0000-0000-0000-000000000206",
    "QUO-0206, Kestrel Outdoor, No expiry, 1,201.20. Open the quotation",
  ]);
});

// Waits for the business the session works in, which decides who sees the
// setup steps.
const businessLoaded = async () => {
  const { store } = await import("../../../app/store");
  await act(() =>
    vi.waitFor(() =>
      expect(
        Object.values(store.getState().api.queries).some(
          (query) =>
            query.endpointName === "getBusinesses" &&
            query.status === "fulfilled",
        ),
      ).toBe(true),
    ),
  );
};
const stripShown = (page) =>
  act(() => vi.waitFor(() => expect(setupStrip(page)).not.toBeNull()));
const steps = (strip) =>
  [...strip.querySelectorAll("li a")].map((link) => [
    link.getAttribute("href"),
    link.textContent,
  ]);
const marks = (strip) =>
  [...strip.querySelectorAll("li a > span:first-child")].map((mark) =>
    mark.querySelector("svg") ? "ticked" : mark.textContent,
  );
const setupWith = (done) => ({
  ...overview,
  setup: {
    steps: [
      "businessDetails",
      "logo",
      "paymentDetails",
      "firstInvoice",
      "team",
    ].map((key) => ({ key, done: done.includes(key) })),
  },
});

it("shows owners and admins what is left to set up under the header, each step opening its page", async () => {
  const page = await open("/dashboard");
  await stripShown(page);
  const strip = setupStrip(page);
  expect(page.querySelector("header").nextElementSibling).toBe(strip);
  expect(strip.querySelector("h2").textContent).toBe("Finish setting up");
  expect(strip.querySelector("p").textContent).toBe(
    "1 of 5 done · each opens its setting",
  );
  expect(strip.querySelector("[style]").style.width).toBe("20%");
  expect(steps(strip)).toEqual([
    ["/settings/business", "1Business detailsAddress and tax ID"],
    ["/settings/business", "2LogoOn your documents"],
    ["/settings/payments", "3How clients pay youBank, UPI or a link"],
    ["/create-doc", "First invoiceDone"],
    ["/settings/team", "5Your teamInvite people to help"],
  ]);
  // A done step is ticked; the rest keep their number.
  expect(marks(strip)).toEqual(["1", "2", "3", "ticked", "5"]);
  const hide = strip.querySelector("button");
  expect(hide.textContent).toBe("Hide");
  expect(hide.getAttribute("aria-label")).toBe("Hide the setup steps");
  // It stays as it is while another period loads.
  expect(strip.closest("[aria-busy]")).toBeNull();
});

it("ticks every step the server says is done, and counts them", async () => {
  const page = await open(
    "/dashboard",
    setupWith(["businessDetails", "logo", "firstInvoice"]),
  );
  await stripShown(page);
  const strip = setupStrip(page);
  expect(strip.querySelector("p").textContent).toBe(
    "3 of 5 done · each opens its setting",
  );
  expect(strip.querySelector("[style]").style.width).toBe("60%");
  expect(marks(strip)).toEqual(["ticked", "ticked", "3", "ticked", "5"]);
  expect(steps(strip).map(([, text]) => text)).toEqual([
    "Business detailsDone",
    "LogoDone",
    "3How clients pay youBank, UPI or a link",
    "First invoiceDone",
    "5Your teamInvite people to help",
  ]);
});

it("shows no setup steps once every step is done", async () => {
  const page = await open(
    "/dashboard",
    setupWith([
      "businessDetails",
      "logo",
      "paymentDetails",
      "firstInvoice",
      "team",
    ]),
  );
  await businessLoaded();
  expect(setupStrip(page)).toBeNull();
});

it("hides the steps in this business when asked, leaving focus in their place", async () => {
  const page = await open("/dashboard");
  await stripShown(page);
  await click(setupStrip(page).querySelector("button"));
  expect(setupStrip(page)).toBeNull();
  expect(page.textContent).not.toContain("Finish setting up");
  expect(localStorage.getItem("setupHidden:b-1")).toBe("1");
  // A note for screen readers takes the strip's place and its focus.
  const note = page.querySelector("header").nextElementSibling;
  expect(document.activeElement).toBe(note);
  expect(note.className).toBe("sr-only");
  expect(note.textContent).toBe("The setup steps are hidden.");
});

it("keeps the steps hidden in the business they were hidden in, and only there", async () => {
  localStorage.setItem("setupHidden:b-1", "1");
  let page = await open("/dashboard");
  await businessLoaded();
  expect(setupStrip(page)).toBeNull();
  expect(page.querySelector("header").nextElementSibling.tagName).not.toBe("P");
  act(() => root.unmount());
  container.remove();

  vi.resetModules();
  page = await open("/dashboard", overview, loaded, {
    businesses: [{ ...owner.businesses[0], _id: "b-2", name: "Second Studio" }],
  });
  await stripShown(page);
  expect(steps(setupStrip(page))).toHaveLength(5);
});

it("shows the steps when storage can't be read, and hides them until the page is left when it can't be written", async () => {
  const read = vi.spyOn(Storage.prototype, "getItem").mockImplementation(() => {
    throw new Error("Storage is off");
  });
  const write = vi
    .spyOn(Storage.prototype, "setItem")
    .mockImplementation(() => {
      throw new Error("Storage is off");
    });
  try {
    const page = await open("/dashboard");
    await stripShown(page);
    await click(setupStrip(page).querySelector("button"));
    expect(write).toHaveBeenCalledWith("setupHidden:b-1", "1");
    expect(setupStrip(page)).toBeNull();
    expect(document.activeElement.textContent).toBe(
      "The setup steps are hidden.",
    );
  } finally {
    read.mockRestore();
    write.mockRestore();
  }
});

it("shows a viewer no setup steps", async () => {
  const page = await open("/dashboard", overview, loaded, viewer);
  await businessLoaded();
  expect(setupStrip(page)).toBeNull();
  expect(page.textContent).not.toContain("Finish setting up");
});

it("shows no setup steps when the server sends none, as it does to those who can't change the business's details", async () => {
  const page = await open("/dashboard", { ...overview, setup: null });
  await businessLoaded();
  expect(setupStrip(page)).toBeNull();
  const { setup, ...older } = overview;
  act(() => root.unmount());
  container.remove();
  vi.resetModules();
  const again = await open("/dashboard", older);
  await businessLoaded();
  expect(setupStrip(again)).toBeNull();
});

it("asks for the overview again once a setup step is done on another page", async () => {
  const page = await open("/dashboard");
  await stripShown(page);
  const overviews = () =>
    requests.filter((request) => request.includes("/report/overview"));
  expect(overviews()).toHaveLength(1);
  // How clients pay you, saved on the payment details page.
  const { store } = await import("../../../app/store");
  const { settingsApiSlice } = await import("../../settings/settingsApiSlice");
  await act(async () => {
    await store.dispatch(
      settingsApiSlice.endpoints.updatePaymentDetails.initiate({
        bankAccount: "12345678",
      }),
    );
  });
  await act(() => vi.waitFor(() => expect(overviews()).toHaveLength(2)));
  expect(setupStrip(page)).not.toBeNull();
});
