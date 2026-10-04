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
});

const json = (body, status = 200) =>
  new Response(JSON.stringify(body), {
    status,
    headers: { "content-type": "application/json" },
  });

// Answers every overview request with `answer`, or with what it gives for
// the request's address: a body, a Response, or a promise of either.
const serve = (answer) =>
  vi.stubGlobal(
    "fetch",
    vi.fn(async (request) => {
      const url = new URL(request.url);
      requests.push(`${url.pathname}${url.search}`);
      const body = await (typeof answer === "function" ? answer(url) : answer);
      return body instanceof Response ? body : json(body);
    }),
  );

const loaded = (page) =>
  vi.waitFor(() =>
    expect(page.querySelector("#document-status-title")).not.toBeNull(),
  );

const open = async (path, answer = overview, ready = loaded) => {
  serve(answer);
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
const tiles = (page) =>
  [...page.querySelectorAll('a[href^="/reports"][aria-label]')].map((link) => [
    link.getAttribute("href"),
    link.getAttribute("aria-label"),
  ]);
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
      "/reports#income",
      "Billed in the last 90 days: USD 68,848.08, up 29.1% vs previous 90 days. View the income report",
    ],
    [
      "/reports#income",
      "Received in the last 90 days: USD 44,562.29, down 25.6% vs previous 90 days. View the income report",
    ],
    [
      "/reports#profit",
      "Profit in the last 90 days: USD 64,194.00, up 29.1% vs previous 90 days. View the profit and loss report",
    ],
    [
      "/reports#ageing",
      "Outstanding now: USD 24,285.79 on 10 invoices, USD 6,326.03 of it overdue on 3 invoices. View unpaid invoices by age",
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
  expect(
    [...page.querySelectorAll("h2")].map((heading) => heading.textContent),
  ).toEqual([
    "Figures in the last 90 days",
    "Cash flow",
    "Invoices owed to you",
    "Needs your attention",
    "Document status",
    "Latest payments",
  ]);
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
  expect(page.textContent).not.toMatch(/\d%/);
  expect(tiles(page)[0][1]).toBe(
    "Billed in the last 90 days: USD 68,848.08. View the income report",
  );
});

it("compares only figures that were not zero before", async () => {
  const page = await open(
    "/dashboard",
    withTotals({ billed: { current: 68848.08, previous: 0 } }),
  );
  expect(tiles(page)[0][1]).toBe(
    "Billed in the last 90 days: USD 68,848.08. View the income report",
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
    "/reports#income",
    expect.stringContaining("Billed in the last 12 months: USD 68,848.08"),
  ]);
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
  const select = page.querySelector("label select");
  expect(select.closest("label").textContent).toContain("Currency");
  expect([...select.options].map((option) => option.value)).toEqual([
    "USD",
    "EUR",
  ]);
  await act(async () => {
    select.value = "EUR";
    select.dispatchEvent(new Event("change", { bubbles: true }));
  });
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
  const select = page.querySelector("label select");
  expect(select.value).toBe("EUR");
  await act(async () => {
    select.value = "USD";
    select.dispatchEvent(new Event("change", { bubbles: true }));
  });
  await act(() => vi.waitFor(() => expect(select.value).toBe("USD")));
  expect(requests).toEqual([
    "/api/v1/report/overview?currency=EUR&period=30d",
    "/api/v1/report/overview?period=30d",
  ]);
  expect(pill(page, "30 days").getAttribute("href")).toBe(
    "/dashboard?period=30d",
  );
});

it("opens Reports at the same period and in the chosen currency", async () => {
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
    "/reports?period=ytd&currency=EUR#income",
    "/reports?period=ytd&currency=EUR#income",
    "/reports?period=ytd&currency=EUR#profit",
    "/reports?period=ytd&currency=EUR#ageing",
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
  ]);
  expect(
    [...owed.querySelectorAll('[role="img"]')].map((column) =>
      column.getAttribute("aria-label"),
    ),
  ).toEqual([
    "This week, due Oct 4–10: 2,664.09 on 1 invoice",
    "Next week, due Oct 11–17: 13,353.37 on 4 invoices",
    "Week after, due Oct 18–24: 1,942.30 on 2 invoices",
  ]);
  for (const text of [
    "6,326",
    "Past due · 3",
    "Oct 4–10 · 1",
    "Oct 11–17 · 4",
    "Oct 18–24 · 2",
    "Grouped by due date. Overdue opens those invoices.",
  ]) {
    expect(owed.textContent).toContain(text);
  }
  // Nothing is due later, so that column is left out.
  expect(owed.textContent).not.toContain("Later");

  const attention = page.querySelector('[aria-labelledby="attention-title"]');
  expect(
    [...attention.querySelectorAll("a")].map((link) => [
      link.getAttribute("href"),
      link.textContent,
    ]),
  ).toEqual([
    ["/documents?status=overdue", "Overdue invoices"],
    [
      "/document/00000000-0000-0000-0000-000000001026",
      "OverdueINV-1026Sable Fitness54 days lateDue Aug 113,391.67",
    ],
    [
      "/document/00000000-0000-0000-0000-000000001030",
      "OverdueINV-1030Quill & Co. Publishing21 days lateDue Sep 131,969.11",
    ],
    [
      "/document/00000000-0000-0000-0000-000000001031",
      "OverdueINV-1031Verdant Farms10 days lateDue Sep 24965.25",
    ],
    [
      "/document/00000000-0000-0000-0000-000000001032",
      "Due soonINV-1032Bluefin AnalyticsDue in 2 daysDue Oct 62,664.09",
    ],
    [
      "/document/00000000-0000-0000-0000-000000000206",
      "QuotationQUO-0206Kestrel OutdoorAwaiting an answerValid until Oct 91,201.20",
    ],
  ]);
  expect(attention.textContent).toContain(
    "No payment reports to confirm, drafts to issue or recurring invoices due this week.",
  );
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
  expect(
    [...owed.querySelectorAll('[role="img"]')]
      .at(-1)
      .getAttribute("aria-label"),
  ).toBe("Later, due from Oct 25 or with no due date: 500.00 on 1 invoice");
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
    "Outstanding now: USD 0.00, no unpaid invoices. View unpaid invoices by age",
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

it("opens the ageing report from the owed panel at the same period and currency", async () => {
  const page = await open("/dashboard?period=ytd&currency=EUR", (url) => ({
    ...overview,
    currencies: ["USD", "EUR"],
    currency: url.searchParams.get("currency") || "USD",
    period: { ...overview.period, key: "ytd" },
  }));
  const owed = page.querySelector('[aria-labelledby="owed-title"]');
  expect(owed.querySelector("a").getAttribute("href")).toBe(
    "/reports?period=ytd&currency=EUR#ageing",
  );
});
