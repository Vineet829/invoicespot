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
  // The days that need attention name their year from the day the fixture
  // was taken, when it isn't that day's year.
  vi.useFakeTimers({ toFake: ["Date"] });
  vi.setSystemTime(new Date(2026, 9, 4, 12));
  requests = [];
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
  vi.waitFor(() => expect(page.querySelector("#figures-title")).not.toBeNull());

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
// The titles of the panels in each row.
const layout = (page) =>
  [...content(page).children].map((row) =>
    [...row.querySelectorAll("h2")].map((heading) => heading.textContent),
  );
const tiles = (page) =>
  [...page.querySelectorAll('[aria-labelledby="figures-title"] a')].map(
    (link) => [link.getAttribute("href"), link.getAttribute("aria-label")],
  );
const receivables = (page) =>
  page.querySelector('[aria-labelledby="receivables-title"]');
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
      "/sales?period=90d",
      "Billed in the last 90 days: USD 68,848.08, including USD 4,654.08 tax, up 29.1% vs previous 90 days. View the sales",
    ],
    [
      "/payments?period=90d",
      "Received in the last 90 days: USD 44,562.29, down 25.6% vs previous 90 days. View the payments",
    ],
    [
      "/reports?period=90d#profit",
      "Profit in the last 90 days: USD 64,194.00, up 29.1% vs previous 90 days. View the profit and loss report",
    ],
  ]);
  for (const text of [
    "68,848.08",
    "includes 4,654.08 tax",
    "44,562.29",
    "64,194.00",
    "24,285.79",
    "29.1%",
    "25.6%",
    "vs previous 90 days",
  ]) {
    expect(page.textContent).toContain(text);
  }
  // One currency, so there is nothing to switch.
  expect(page.textContent).not.toContain("Currency");
  // The panels in their rows, in reading order, right under the header: the
  // period's figures, what is owed today beside what needs attention, the
  // cash flow across the width, then the top clients, the top items and
  // what is coming up. Nothing else on the page has a title.
  expect(page.querySelector("header").nextElementSibling).toBe(content(page));
  expect(layout(page)).toEqual([
    ["Figures in the last 90 days"],
    ["Total receivables", "Needs your attention"],
    ["Cash flow"],
    ["Top clients", "Top items", "Coming up"],
  ]);
  expect(
    [...page.querySelectorAll("h2")].map((heading) => heading.textContent),
  ).toEqual(layout(page).flat());
  // From 1200px the row under the figures shares their three columns, what
  // is owed in one and what needs attention across the other two, and from
  // 1360px each list takes one, so every row's edges line up.
  const [figures, owed, , lists] = content(page).children;
  expect(owed.className).toContain("min-[1200px]:grid-cols-3");
  expect(owed.className).toContain("min-[1200px]:[&>:last-child]:col-span-2");
  expect(lists.className).toContain("min-[1360px]:grid-cols-3");
  // Every row keeps the page's spacing, the same across as down.
  for (const row of [content(page), figures.lastElementChild, owed, lists])
    expect(row.className).toMatch(/(^| )gap-6 compact:gap-5 /);
  // Every card and panel has every page's padding and corners.
  for (const surface of content(page).querySelectorAll("section, article, a"))
    if (/rounded-\[(22|30)px\]/.test(surface.className)) {
      expect(surface.className).toContain("rounded-[30px]");
      expect(surface.className).toContain("p-6");
      expect(surface.className).toContain("compact:p-[18px]");
    }
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
    "Billed in the last 90 days: USD 68,848.08, including USD 4,654.08 tax. View the sales",
  );
});

it("compares only figures that were not zero before", async () => {
  const page = await open(
    "/dashboard",
    withTotals({ billed: { current: 68848.08, previous: 0 } }),
  );
  expect(tiles(page)[0][1]).toBe(
    "Billed in the last 90 days: USD 68,848.08, including USD 4,654.08 tax. View the sales",
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
    "/sales?period=12m",
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

it("opens each figure's view for the same period and the chosen currency", async () => {
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
    "/sales?period=this-year&currency=EUR",
    "/payments?period=this-year&currency=EUR",
    "/reports?period=ytd&currency=EUR#profit",
  ]);
  // What is owed is as of today, so its links name no period.
  expect(links(receivables(page)).map(([href]) => href)).toEqual([
    "/receivables?currency=EUR",
    "/receivables?mode=overdue&currency=EUR",
  ]);
  // What needs attention opens only its invoices, in any currency.
  const attention = page.querySelector('[aria-labelledby="attention-title"]');
  expect(
    [...attention.querySelectorAll("a")].every((link) =>
      link.getAttribute("href").startsWith("/document/"),
    ),
  ).toBe(true);
});

it("opens the views for the last 30 days, and Reports at the same days", async () => {
  const page = await open("/dashboard?period=30d", lastThirtyDays);
  expect(tiles(page).map(([href]) => href)).toEqual([
    "/sales?period=30d",
    "/payments?period=30d",
    "/reports?period=30d#profit",
  ]);
  expect(links(receivables(page)).map(([href]) => href)).toEqual([
    "/receivables",
    "/receivables?mode=overdue",
  ]);
});

it("shows billed, received and profit as large cards, billed with the tax it includes", async () => {
  const page = await open("/dashboard");
  const cards = [
    ...page.querySelectorAll('[aria-labelledby="figures-title"] a'),
  ];
  // As large as the Reports page's cards, each in its tone.
  expect(cards.map((card) => card.className.match(/bg-card-\w+/)[0])).toEqual([
    "bg-card-blue",
    "bg-card-lilac",
    "bg-card-sage",
  ]);
  for (const card of cards) {
    expect(card.className).toContain("[container-name:finance-card]");
    expect(card.querySelector(".font-display").className).toContain("min(56px");
  }
  // Only Billed has a line under its figure.
  expect(cards.map((card) => card.textContent.includes("includes"))).toEqual([
    true,
    false,
    false,
  ]);
  expect(cards[0].textContent).toContain("68,848.08includes 4,654.08 tax");
  // Billed opens the Sales view, Received the Payments list with its
  // summary, and Profit the profit and loss report.
  expect(cards.map((card) => card.getAttribute("href"))).toEqual([
    "/sales?period=90d",
    "/payments?period=90d",
    "/reports?period=90d#profit",
  ]);
});

it("leaves the tax out when the server sends none", async () => {
  const { tax, previousTax, ...older } = overview.totals;
  const page = await open("/dashboard", { ...overview, totals: older });
  expect(page.textContent).not.toContain("includes");
  expect(tiles(page)[0][1]).toBe(
    "Billed in the last 90 days: USD 68,848.08, up 29.1% vs previous 90 days. View the sales",
  );
});

it("shows the invoices that need attention, each opening its invoice and nothing else", async () => {
  const page = await open("/dashboard");
  const attention = page.querySelector('[aria-labelledby="attention-title"]');
  // Overdue and due soon, each kind named once above its rows; the
  // quotation is Coming up's.
  expect(
    [...attention.querySelectorAll("h3")].map((heading) => [
      heading.textContent,
      heading.nextElementSibling.querySelectorAll("li").length,
    ]),
  ).toEqual([
    ["Overdue", 3],
    ["Due soon", 1],
  ]);
  const rows = [...attention.querySelectorAll("ol > li")];
  expect(rows.map((item) => item.textContent)).toEqual([
    "Sable FitnessINV-1026 · Due Aug 11 · 54 days late3,391.67",
    "Quill & Co. PublishingINV-1030 · Due Sep 13 · 21 days late1,969.11",
    "Verdant FarmsINV-1031 · Due Sep 24 · 10 days late965.25",
    "Bluefin AnalyticsINV-1032 · Due Oct 6 · Due in 2 days2,664.09",
  ]);
  // An overdue row's bar is as long as it is late beside the most overdue
  // one; the invoice due soon has none.
  expect(
    rows.map((item) => item.querySelector("[style]")?.style.width ?? null),
  ).toEqual(["100%", `${(21 / 54) * 100}%`, `${(10 / 54) * 100}%`, null]);
  // Each row opens its invoice; client names are words, and no link opens
  // the overdue invoices, as the Overdue figure above does.
  expect(
    [...attention.querySelectorAll("a")].map((link) => [
      link.getAttribute("href"),
      link.getAttribute("aria-label"),
    ]),
  ).toEqual([
    [
      "/document/00000000-0000-0000-0000-000000001026",
      "Overdue INV-1026, Sable Fitness, 54 days late, Due Aug 11, 3,391.67",
    ],
    [
      "/document/00000000-0000-0000-0000-000000001030",
      "Overdue INV-1030, Quill & Co. Publishing, 21 days late, Due Sep 13, 1,969.11",
    ],
    [
      "/document/00000000-0000-0000-0000-000000001031",
      "Overdue INV-1031, Verdant Farms, 10 days late, Due Sep 24, 965.25",
    ],
    [
      "/document/00000000-0000-0000-0000-000000001032",
      "Due soon INV-1032, Bluefin Analytics, Due in 2 days, Due Oct 6, 2,664.09",
    ],
  ]);
  expect(attention.textContent).not.toContain("Overdue invoices");
  expect(attention.lastElementChild.textContent).toBe(
    "No payment reports to confirm or drafts to issue.",
  );
});

// At 5 in the morning of Jan 1, 2027 in Kolkata it is still Dec 31, 2026 in
// Los Angeles, where the business is: a day of 2026 is this year's there,
// and one of 2025 names its year, so it doesn't read as a day to come.
it("names the year of a day that isn't in the business's current year", async () => {
  const zone = process.env.TZ;
  process.env.TZ = "Asia/Kolkata";
  try {
    vi.setSystemTime(new Date("2027-01-01T05:00:00+05:30"));
    const [late] = overview.attention.items;
    const page = await open(
      "/dashboard",
      {
        ...overview,
        attention: {
          items: [
            { ...late, date: "2025-12-14", days: 382 },
            {
              ...late,
              documentId: "d-2",
              documentNumber: "INV-1101",
              date: "2026-12-14",
              days: 17,
            },
          ],
          counts: { paymentReports: 0, overdue: 2, dueSoon: 0, drafts: 0 },
        },
      },
      loaded,
      {
        businesses: owner.businesses.map((entry) => ({
          ...entry,
          timeZone: "America/Los_Angeles",
        })),
      },
    );
    const rows = () =>
      [
        ...page.querySelectorAll('[aria-labelledby="attention-title"] ol > li'),
      ].map((item) => item.textContent);
    await act(() =>
      vi.waitFor(() =>
        expect(rows()).toEqual([
          "Sable FitnessINV-1026 · Due Dec 14, 2025 · 382 days late3,391.67",
          "Sable FitnessINV-1101 · Due Dec 14 · 17 days late3,391.67",
        ]),
      ),
    );
  } finally {
    if (zone === undefined) delete process.env.TZ;
    else process.env.TZ = zone;
  }
});

it("opens a document listed twice from its first row only", async () => {
  const [late] = overview.attention.items;
  const page = await open("/dashboard", {
    ...overview,
    attention: {
      items: [
        {
          ...late,
          kind: "paymentReport",
          date: "2026-10-03",
          days: null,
        },
        late,
      ],
      counts: { paymentReports: 1, overdue: 1, dueSoon: 0, drafts: 0 },
    },
  });
  const [reported, overdue] = page.querySelectorAll(
    '[aria-labelledby="attention-title"] ol > li',
  );
  expect(reported.querySelector("a").getAttribute("href")).toBe(
    "/document/00000000-0000-0000-0000-000000001026",
  );
  // The second row is still read, whole, but opens nothing.
  expect(overdue.querySelector("a")).toBeNull();
  const row = overdue.firstElementChild;
  expect(row.getAttribute("role")).toBe("img");
  expect(row.getAttribute("aria-label")).toBe(
    "Overdue INV-1026, Sable Fitness, 54 days late, Due Aug 11, 3,391.67",
  );
  expect(row.className).not.toContain("hover:");
});

it("leaves out the quotations and recurring invoices an older server lists", async () => {
  const [late] = overview.attention.items;
  const page = await open("/dashboard", {
    ...overview,
    attention: {
      items: [
        late,
        { ...late, kind: "quotation", documentId: "q-1" },
        { ...late, kind: "recurring", documentId: "t-1" },
      ],
      counts: {
        paymentReports: 0,
        overdue: 1,
        dueSoon: 0,
        quotations: 1,
        recurring: 1,
        drafts: 0,
      },
    },
  });
  const attention = page.querySelector('[aria-labelledby="attention-title"]');
  expect(attention.querySelectorAll("ol > li")).toHaveLength(1);
  expect(attention.lastElementChild.textContent).toBe(
    "No payment reports to confirm, invoices due this week or drafts to issue.",
  );
});

it("says when nothing needs attention", async () => {
  const page = await open("/dashboard", {
    ...overview,
    attention: {
      items: [],
      counts: { paymentReports: 0, overdue: 0, dueSoon: 0, drafts: 0 },
    },
  });
  const attention = page.querySelector('[aria-labelledby="attention-title"]');
  expect(attention.textContent).toBe(
    "Needs your attentionNothing needs your attention right now.",
  );
  expect(attention.querySelector("a")).toBeNull();
});

// The receivables card's bar, each part's colour and the amount it grows by,
// and the overdue bands in the panel named by its heading.
const bar = (card) =>
  [...card.querySelector(".h-4").children].map((part) => [
    part.className.match(/bg-owed-[\w-]+/)[0],
    parseFloat(part.style.flexGrow),
  ]);
const legend = (card) =>
  [
    ...card.querySelectorAll('ul[aria-labelledby="receivables-bands"] > li'),
  ].map((band) => band.textContent);

it("shows what is owed today beside what needs attention, split into current and overdue by days late", async () => {
  const page = await open("/dashboard");
  const card = receivables(page);
  // Under the period's figures, first in its row, beside what needs
  // attention.
  expect(card.parentElement).toBe(content(page).children[1]);
  expect(card.nextElementSibling.getAttribute("aria-labelledby")).toBe(
    "attention-title",
  );
  expect(card.querySelector("h2").textContent).toBe("Total receivables");
  expect(card.textContent).toContain("Total receivablesas of today");
  expect(card.textContent).toContain("24,285.79across 10 outstanding invoices");
  // Not due yet, then each overdue band in a rust that deepens with age; a
  // band with nothing in it is left out of the bar.
  expect(bar(card)).toEqual([
    ["bg-owed-due", 17959.76],
    ["bg-owed-age-1", 965.25],
    ["bg-owed-age-2", 1969.11],
    ["bg-owed-age-4", 3391.67],
  ]);
  expect(card.querySelector(".h-4").getAttribute("aria-hidden")).toBe("true");
  // The overdue bands under their heading, each with its amount and count,
  // the empty one too.
  expect(card.querySelector("#receivables-bands").textContent).toBe(
    "Overdue by days late",
  );
  expect(legend(card)).toEqual([
    "1–15 days965.251 invoice late: USD 965.25 on 1 invoice",
    "16–30 days1,969.111 invoice late: USD 1,969.11 on 1 invoice",
    "31–45 days0.000 invoices late: USD 0.00 on 0 invoices",
    "Over 45 days3,391.671 invoice late: USD 3,391.67 on 1 invoice",
  ]);
  expect(
    [...card.querySelectorAll("li .size-2")].map(
      (swatch) => swatch.className.match(/bg-owed-[\w-]+/)[0],
    ),
  ).toEqual([
    "bg-owed-age-1",
    "bg-owed-age-2",
    "bg-owed-age-3",
    "bg-owed-age-4",
  ]);
  // Current opens what is coming in on the Receivables view, and Overdue
  // what is overdue there. There is no link to the age table any more.
  expect(links(card)).toEqual([
    [
      "/receivables",
      "Current, not due yet: USD 17,959.76 on 7 invoices. View what is coming in",
    ],
    [
      "/receivables?mode=overdue",
      "Overdue: USD 6,326.03 on 3 invoices. View the overdue invoices",
    ],
  ]);
  expect(card.textContent).not.toContain("Aging details");
  const [current, late] = card.querySelectorAll("a");
  expect(current.textContent).toContain("Current");
  expect(current.textContent).toContain("17,959.76");
  expect(current.lastElementChild.textContent).toBe("7 invoices");
  expect(late.textContent).toContain("Overdue");
  expect(late.textContent).toContain("6,326.03");
  expect(late.lastElementChild.textContent).toBe("3 invoices");
});

// Every link's address but the period tabs', which show the overview again
// rather than open a view.
const opened = (page) =>
  [...page.querySelectorAll("a")]
    .filter((link) => !link.closest('nav[aria-label="Period"]'))
    .map((link) => link.getAttribute("href"));
// The addresses more than one link opens.
const repeated = (hrefs) => [
  ...new Set(hrefs.filter((href, index) => hrefs.indexOf(href) !== index)),
];

it("opens a view or record of its own from every link", async () => {
  const page = await open("/dashboard");
  const hrefs = opened(page);
  expect(hrefs).toContain("/receivables");
  expect(hrefs).toContain("/receivables?mode=overdue");
  expect(repeated(hrefs)).toEqual([]);
});

// One invoice part paid: money is still owed on it, so it is outstanding
// rather than unpaid.
it("names the invoices with money still owed on them as outstanding", async () => {
  const page = await open("/dashboard", {
    ...overview,
    owedNow: {
      total: 500,
      notDue: 500,
      overdue: 0,
      invoices: 1,
      overdueInvoices: 0,
      bands: [{ key: "current", amount: 500, invoices: 1 }],
    },
  });
  expect(receivables(page).textContent).toContain(
    "500.00across 1 outstanding invoice",
  );
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
      bands: ["current", "days1to15", "days16to30", "days31to45", "over45"].map(
        (key) => ({ key, amount: 0, invoices: 0 }),
      ),
    },
  });
  const card = receivables(page);
  expect(card.textContent).toContain("0.00No unpaid invoices");
  // The bar is an empty track.
  expect(bar(card)).toEqual([]);
  expect(card.querySelector(".h-4").className).toContain("bg-white/50");
  expect(legend(card)).toEqual([
    "1–15 days0.000 invoices late: USD 0.00 on 0 invoices",
    "16–30 days0.000 invoices late: USD 0.00 on 0 invoices",
    "31–45 days0.000 invoices late: USD 0.00 on 0 invoices",
    "Over 45 days0.000 invoices late: USD 0.00 on 0 invoices",
  ]);
  expect(links(card).map(([, label]) => label)).toEqual([
    "Current, not due yet: USD 0.00 on 0 invoices. View what is coming in",
    "Overdue: USD 0.00 on 0 invoices. View the overdue invoices",
  ]);
});

it("splits what is owed only into current and overdue when the server sends no bands", async () => {
  const { bands, ...older } = overview.owedNow;
  const page = await open("/dashboard", { ...overview, owedNow: older });
  const card = receivables(page);
  expect(bar(card)).toEqual([
    ["bg-owed-due", 17959.76],
    ["bg-owed-overdue", 6326.03],
  ]);
  expect(legend(card)).toEqual([]);
  expect(links(card)).toHaveLength(2);
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
      counts: { paymentReports: 0, overdue: 9, dueSoon: 4, drafts: 7 },
    },
  });
  const attention = page.querySelector('[aria-labelledby="attention-title"]');
  expect(attention.querySelectorAll("ol > li")).toHaveLength(6);
  expect(attention.textContent).toContain(
    "No payment reports to confirm. Also 3 more overdue invoices, 4 due this week and 7 drafts.",
  );
});

const links = (element) =>
  [...element.querySelectorAll("a")].map((link) => [
    link.getAttribute("href"),
    link.getAttribute("aria-label") || link.textContent,
  ]);

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
  // After the words for screen readers: the three cards, what is owed
  // beside what needs attention, the cash flow across the width, then the
  // three lists.
  expect(
    [...loading.children].slice(1).map((row) => row.children.length),
  ).toEqual([3, 2, 1, 3]);
  expect(loading.children[2].firstElementChild.className).toContain(
    "bg-card-peach",
  );
  // The three cards as large as they will be, each in its tone.
  expect(
    [...loading.children[1].children].map((card) => [
      card.className.match(/bg-card-\w+/)[0],
      card.className.includes("[container-name:finance-card]"),
    ]),
  ).toEqual([
    ["bg-card-blue", true],
    ["bg-card-lilac", true],
    ["bg-card-sage", true],
  ]);
  expect(receivables(page)).toBeNull();
  await act(async () => respond(overview));
  await act(() => loaded(page));
  expect(
    page.querySelector('[aria-label="Loading your business overview"]'),
  ).toBeNull();
  expect(layout(page).map((row) => row.length)).toEqual([1, 2, 1, 3]);
  expect(receivables(page).textContent).toContain(
    "24,285.79across 10 outstanding invoices",
  );
});

it("shows no Spending, even when the server still sends it", async () => {
  const page = await open("/dashboard", {
    ...overview,
    spending: {
      total: 7610.15,
      categories: [{ category: "Contractors", amount: 3600, expenses: 2 }],
      others: null,
    },
  });
  expect(page.querySelector('[aria-labelledby="spending-title"]')).toBeNull();
  expect(page.textContent).not.toContain("Spending");
  expect(page.textContent).not.toContain("Contractors");
  expect(layout(page)).toEqual([
    ["Figures in the last 90 days"],
    ["Total receivables", "Needs your attention"],
    ["Cash flow"],
    ["Top clients", "Top items", "Coming up"],
  ]);
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

it("names each row's client in words, the row reading once as its invoice's link", async () => {
  const page = await open("/dashboard");
  const rows = [
    ...page.querySelectorAll('[aria-labelledby="attention-title"] ol > li'),
  ];
  for (const row of rows) {
    expect(row.querySelectorAll("a")).toHaveLength(1);
    // The client's name is the row's link to its document, which reads the
    // row whole, so the words beside it are hidden from screen readers
    // rather than read twice.
    const read = row.cloneNode(true);
    read
      .querySelectorAll('a, [aria-hidden="true"]')
      .forEach((node) => node.remove());
    expect(read.textContent).toBe("");
  }
  expect(rows.map((row) => row.querySelector("a").textContent)).toEqual([
    "Sable Fitness",
    "Quill & Co. Publishing",
    "Verdant Farms",
    "Bluefin Analytics",
  ]);
  expect(
    page.querySelector(
      '[aria-labelledby="attention-title"] a[href^="/single-customer/"]',
    ),
  ).toBeNull();
});

it.each([
  ["30d", "the last 30 days", "30d", ""],
  ["90d", "the last 90 days", "90d", ""],
  ["ytd", "this year", "this-year", "EUR"],
])(
  "opens the expenses of the period shown over %s from the money out, in the overview's currency",
  async (key, words, list, chosen) => {
    const page = await open(
      `/dashboard?period=${key}${chosen ? `&currency=${chosen}` : ""}`,
      (url) => ({
        ...overview,
        currencies: ["USD", "EUR"],
        currency: url.searchParams.get("currency") || "USD",
        period: { ...overview.period, key },
      }),
    );
    const flow = page.querySelector('[aria-labelledby="cash-flow-title"]');
    const out = [...flow.querySelectorAll("dl > div")].find(
      (figure) => figure.querySelector("dt").textContent === "Out",
    );
    // Only the money out opens anything, and always in the currency its
    // figure counts, even when the address names none.
    expect(
      [...flow.querySelectorAll("a")].map((link) => [
        link.getAttribute("href"),
        link.getAttribute("aria-label"),
      ]),
    ).toEqual([
      [
        `/expenses?period=${list}&currency=${chosen || "USD"}`,
        `Money out ${key === "ytd" ? words : `in ${words}`}: ${chosen || "USD"} 0.00. View the expenses`,
      ],
    ]);
    expect(out.querySelector("dd a").textContent).toBe("0.00");
  },
);

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

it("lists the clients billed the most first of the lists, each linked name opening the client", async () => {
  const page = await open("/dashboard");
  const panel = topClients(page);
  // First of the lists under the cash flow, in a row of their own.
  expect(
    [...panel.parentElement.children].map((section) =>
      section.getAttribute("aria-labelledby"),
    ),
  ).toEqual(["top-clients-title", "top-items-title", "coming-up-title"]);
  expect(panel.querySelector("p").textContent).toBe(
    "Billed in these 90 days, largest first",
  );
  // Sales by customer opens every client's sales, for the same days.
  expect(links(panel)).toEqual([
    ["/customers/sales?period=90d", "Sales by customer"],
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

it("opens every client the same way in any currency and period, and sales by customer for them", async () => {
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
    "/customers/sales?period=this-year&currency=EUR",
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
  expect(links(panel)).toEqual([
    ["/customers/sales?period=90d", "Sales by customer"],
  ]);
  expect(panel.querySelectorAll('ol > li > [role="img"]')).toHaveLength(5);
  expect(panel.lastElementChild.tagName).toBe("OL");
});

it.each([
  ["30d", "in these 30 days", "/customers/sales?period=30d"],
  ["90d", "in these 90 days", "/customers/sales?period=90d"],
  ["12m", "in these 12 months", "/customers/sales?period=12m"],
  ["ytd", "this year", "/customers/sales?period=this-year"],
])("says when no one was billed over %s", async (key, words, sales) => {
  const page = await open(`/dashboard?period=${key}`, {
    ...overview,
    period: { ...overview.period, key },
    topClients: [],
  });
  const panel = topClients(page);
  expect(panel.textContent).toBe(
    `Top clientsSales by customerNo one billed ${words}.`,
  );
  expect(links(panel)).toEqual([[sales, "Sales by customer"]]);
});

it("leaves the top items and what is coming up the lists' row when the server sends no top clients", async () => {
  const { topClients: left, ...older } = overview;
  const page = await open("/dashboard", older);
  expect(topClients(page)).toBeNull();
  expect(layout(page)).toEqual([
    ["Figures in the last 90 days"],
    ["Total receivables", "Needs your attention"],
    ["Cash flow"],
    ["Top items", "Coming up"],
  ]);
  // The two share the row equally.
  const lists = content(page).lastElementChild;
  expect(lists.className).toContain("min-[1200px]:grid-cols-2");
  expect(lists.className).not.toContain("grid-cols-3");
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

it("lists the items that brought in the most beside what is coming up, each opening its sales for the same days", async () => {
  const page = await open("/dashboard");
  const panel = topItems(page);
  expect(
    [...panel.parentElement.children].map((section) =>
      section.getAttribute("aria-labelledby"),
    ),
  ).toEqual(["top-clients-title", "top-items-title", "coming-up-title"]);
  expect(panel.querySelector("p").textContent).toBe(
    "What you sold in these 90 days, before tax",
  );
  // Sales by item opens every item's sales, and each row its own, for the
  // same days.
  expect(links(panel)).toEqual([
    ["/items/sales?period=90d", "Sales by item"],
    [
      "/items/sales/item?name=Motion+graphics+%28per+15+seconds%29&period=90d",
      "Motion graphics (per 15 seconds): 18,200.00, 28 sold, 28% of income. View its sales",
    ],
    [
      "/items/sales/item?name=Logo+and+identity+system&period=90d",
      "Logo and identity system: 9,280.00, 3 sold, 14% of income. View its sales",
    ],
    [
      "/items/sales/item?name=Consulting+%28hourly%29&period=90d",
      "Consulting (hourly): 7,140.00, 52 sold, 11% of income. View its sales",
    ],
    [
      "/items/sales/item?name=Front-end+build+%28per+page%29&period=90d",
      "Front-end build (per page): 6,460.00, 17 sold, 10% of income. View its sales",
    ],
    [
      "/items/sales/item?name=Brand+strategy+workshop&period=90d",
      "Brand strategy workshop: 5,400.00, 3 sold, 8% of income. View its sales",
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
    "Everything sold adds up to the income behind Profit. Each row opens what it sold.",
  );
});

it("adds up lines without a name as other lines, which open their own page too", async () => {
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
  expect(rows[3].firstElementChild.getAttribute("aria-label")).toBe(
    "Other lines: 650.50, 4 sold, 1% of income. View its sales",
  );
  expect(links(topItems(page)).map(([href]) => href)).toEqual([
    "/items/sales?period=90d",
    "/items/sales/item?name=Motion+graphics+%28per+15+seconds%29&period=90d",
    "/items/sales/item?name=Logo+and+identity+system&period=90d",
    "/items/sales/item?name=Consulting+%28hourly%29&period=90d",
    "/items/sales/item?name=Other+lines&period=90d",
    "/items/sales/item?name=Stickers&period=90d",
  ]);
});

it("opens the items' sales for the year to date in the chosen currency", async () => {
  const page = await open("/dashboard?period=ytd&currency=EUR", (url) => ({
    ...overview,
    currencies: ["USD", "EUR"],
    currency: url.searchParams.get("currency") || "USD",
    period: { ...overview.period, key: "ytd" },
  }));
  expect(
    links(topItems(page))
      .map(([href]) => href)
      .slice(0, 2),
  ).toEqual([
    "/items/sales?period=this-year&currency=EUR",
    "/items/sales/item?name=Motion+graphics+%28per+15+seconds%29&period=this-year&currency=EUR",
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
    "/items/sales/item?name=Refunded+work&period=90d",
    "Refunded work: 0.00, 2 sold. View its sales",
  ]);
});

it.each([
  ["30d", "in these 30 days", "30d"],
  ["90d", "in these 90 days", "90d"],
  ["12m", "in these 12 months", "12m"],
  ["ytd", "this year", "this-year"],
])("says when nothing was sold over %s", async (key, words, list) => {
  const page = await open(`/dashboard?period=${key}`, {
    ...overview,
    period: { ...overview.period, key },
    topItems: [],
    itemsTotal: 0,
  });
  const panel = topItems(page);
  expect(panel.textContent).toBe(
    `Top itemsSales by itemNothing sold ${words}.`,
  );
  expect(links(panel)).toEqual([
    [`/items/sales?period=${list}`, "Sales by item"],
  ]);
});

it("leaves the rest of its row when the server sends no top items", async () => {
  const { topItems: left, itemsTotal, ...older } = overview;
  const page = await open("/dashboard", older);
  expect(topItems(page)).toBeNull();
  expect(layout(page)[3]).toEqual(["Top clients", "Coming up"]);
});

const comingUp = (page) =>
  page.querySelector('[aria-labelledby="coming-up-title"]');
const withComingUp = (changes) => ({
  ...overview,
  comingUp: { ...overview.comingUp, ...changes },
});

it("shows what is coming up beside the top items: each schedule's next invoice, the quotations waiting for an answer and the latest that expired", async () => {
  const page = await open("/dashboard");
  const panel = comingUp(page);
  expect(
    [...panel.parentElement.children].map((section) =>
      section.getAttribute("aria-labelledby"),
    ),
  ).toEqual(["top-clients-title", "top-items-title", "coming-up-title"]);
  expect(
    [...panel.querySelectorAll("h3")].map((heading) => heading.textContent),
  ).toEqual(["Recurring invoices", "Quotations"]);
  expect(panel.textContent).toContain("Next 90 days: 7 invoices · 10,650.00");
  // Each schedule once, at its next run, opening the invoice it repeats,
  // whose page shows its schedule.
  expect(links(panel)).toEqual([
    ["/recurring", "Recurring"],
    [
      "/document/00000000-0000-0000-0000-000000001012",
      "Oct 12: Harbor & Pine Architects, INV-1012, 450.00. Open the invoice it repeats",
    ],
    [
      "/document/00000000-0000-0000-0000-000000001020",
      "Oct 20: Bluefin Analytics, INV-1020, 2,500.00. Open the invoice it repeats",
    ],
    [
      "/document/00000000-0000-0000-0000-000000001027",
      "Nov 3: Oak Street Bakery, INV-1027, 1,800.00. Open the invoice it repeats",
    ],
    ["/documents?type=quotation", "All quotations"],
    [
      "/document/00000000-0000-0000-0000-000000000206",
      "QUO-0206, Kestrel Outdoor, valid until Oct 9, 1,201.20. Open the quotation",
    ],
    [
      "/document/00000000-0000-0000-0000-000000000205",
      "QUO-0205, Atlas Robotics, valid until Aug 24, 5,362.50. Open the quotation",
    ],
    [
      "/document/00000000-0000-0000-0000-000000000204",
      "QUO-0204, Lumen Health, valid until Jul 2, 8,558.55. Open the quotation",
    ],
    [
      "/document/00000000-0000-0000-0000-000000000203",
      "QUO-0203, Tidewater Logistics, valid until May 15, 321.75. Open the quotation",
    ],
  ]);
  const [runs, awaiting, expired] = panel.querySelectorAll("ol");
  expect([...runs.children].map((run) => run.textContent)).toEqual([
    "Oct 12Harbor & Pine ArchitectsINV-1012450.00",
    "Oct 20Bluefin AnalyticsINV-10202,500.00",
    "Nov 3Oak Street BakeryINV-10271,800.00",
  ]);
  expect(awaiting.previousElementSibling.textContent).toBe(
    "Awaiting an answer · 1",
  );
  // Laid out as a recurring invoice: the client with the amount on its
  // line, then the number and last valid day across the row under them.
  expect(awaiting.textContent).toBe(
    "Kestrel Outdoor1,201.20QUO-0206 · valid until Oct 9",
  );
  const details = awaiting.querySelector("a").lastElementChild;
  expect(details.className).toContain("col-span-2");
  expect(awaiting.querySelector("a").className).toContain("items-baseline");
  // The latest three that expired, most recent first, then how many more.
  expect(expired.previousElementSibling.textContent).toBe(
    "Expired without an answer · 5",
  );
  expect([...expired.children].map((quote) => quote.textContent)).toEqual([
    "Atlas Robotics5,362.50QUO-0205 · valid until Aug 24",
    "Lumen Health8,558.55QUO-0204 · valid until Jul 2",
    "Tidewater Logistics321.75QUO-0203 · valid until May 15",
  ]);
  expect(expired.nextElementSibling.textContent).toBe("and 2 more");
  expect(panel.lastElementChild.lastElementChild).toBe(expired.parentElement);
});

it("keeps the quotations' list to the chosen currency", async () => {
  const page = await open("/dashboard?currency=EUR", (url) => ({
    ...overview,
    currencies: ["USD", "EUR"],
    currency: url.searchParams.get("currency") || "USD",
  }));
  expect(
    links(comingUp(page))
      .map(([href]) => href)
      .filter((href) => !/^\/(recurring|document\/.*10\d\d$)/.test(href)),
  ).toEqual([
    "/documents?type=quotation&currency=EUR",
    "/document/00000000-0000-0000-0000-000000000206",
    "/document/00000000-0000-0000-0000-000000000205",
    "/document/00000000-0000-0000-0000-000000000204",
    "/document/00000000-0000-0000-0000-000000000203",
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
  // Setting one up opens the recurring list, so it is the one link there.
  expect(panel.textContent).toContain(
    "Recurring invoicesNone yet. Bill a client every week, month or year without lifting a finger.Set one upQuotations",
  );
  expect(panel.textContent).not.toContain("Next 90 days");
  expect(links(panel).slice(0, 2)).toEqual([
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
    "/document/00000000-0000-0000-0000-000000001012",
    "Oct 12: No client, Draft invoice, 450.00. Open the invoice it repeats",
  ]);
});

it("lists each schedule once, at its next run, whatever order the runs come in", async () => {
  const [harbor, bluefin] = overview.comingUp.recurring.nextRuns;
  const page = await open(
    "/dashboard",
    withComingUp({
      recurring: {
        active: 2,
        next90Days: { invoices: 4, amount: 5900 },
        nextRuns: [
          { ...bluefin, date: "2026-11-20" },
          harbor,
          bluefin,
          { ...harbor, date: "2026-11-12" },
        ],
      },
    }),
  );
  expect(
    [...comingUp(page).querySelector("ol").children].map(
      (run) => run.textContent,
    ),
  ).toEqual([
    "Oct 12Harbor & Pine ArchitectsINV-1012450.00",
    "Oct 20Bluefin AnalyticsINV-10202,500.00",
  ]);
});

it("doesn't open an invoice that needs attention a second time from Coming up", async () => {
  const [harbor, bluefin] = overview.comingUp.recurring.nextRuns;
  const page = await open("/dashboard", {
    ...overview,
    attention: {
      ...overview.attention,
      items: [
        {
          kind: "overdue",
          documentId: harbor.templateId,
          documentNumber: "INV-1012",
          client: "Harbor & Pine Architects",
          clientId: null,
          amount: 450,
          date: "2026-09-20",
          days: 14,
        },
      ],
    },
    comingUp: {
      ...overview.comingUp,
      recurring: {
        ...overview.comingUp.recurring,
        nextRuns: [harbor, bluefin],
      },
    },
  });
  // The schedule is still listed, and its invoice opens from what needs
  // attention.
  const [first, second] = comingUp(page).querySelector("ol").children;
  expect(first.textContent).toBe(
    "Oct 12Harbor & Pine ArchitectsINV-1012450.00",
  );
  expect(first.querySelector("a")).toBeNull();
  expect(second.querySelector("a").getAttribute("href")).toBe(
    "/document/00000000-0000-0000-0000-000000001020",
  );
  expect(
    opened(page).filter(
      (href) => href === "/document/00000000-0000-0000-0000-000000001012",
    ),
  ).toHaveLength(1);
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
    "Kestrel Outdoor1,201.20QUO-0206 · valid until Oct 9",
    "Kestrel Outdoor1,201.20QUO-0207 · valid until Oct 14",
    "Kestrel Outdoor1,201.20QUO-0208 · valid until Oct 19",
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
  expect(layout(page)[3]).toEqual(["Top clients", "Top items"]);
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
    "Kestrel Outdoor1,201.20QUO-0206 · No expiry",
  );
  expect(links(panel)).toContainEqual([
    "/document/00000000-0000-0000-0000-000000000206",
    "QUO-0206, Kestrel Outdoor, No expiry, 1,201.20. Open the quotation",
  ]);
});

it("says no more expired quotations when it lists them all", async () => {
  const { awaiting, expired } = overview.comingUp.quotations;
  const page = await open(
    "/dashboard",
    withComingUp({
      quotations: {
        awaiting,
        expired: {
          count: 2,
          amount: 13921.05,
          items: expired.items.slice(0, 2),
        },
      },
    }),
  );
  const listed = comingUp(page).querySelectorAll("ol")[2];
  expect(listed.previousElementSibling.textContent).toBe(
    "Expired without an answer · 2",
  );
  expect(listed.children).toHaveLength(2);
  expect(listed.nextElementSibling).toBeNull();
  expect(comingUp(page).textContent).not.toContain("more");
});

it("counts the expired quotations without listing them when the server sends none", async () => {
  const { awaiting, expired } = overview.comingUp.quotations;
  const page = await open(
    "/dashboard",
    withComingUp({
      quotations: {
        awaiting,
        expired: { count: expired.count, amount: expired.amount },
      },
    }),
  );
  const panel = comingUp(page);
  const listed = panel.querySelectorAll("ol")[2];
  expect(listed.previousElementSibling.textContent).toBe(
    "Expired without an answer · 5",
  );
  expect(listed.children).toHaveLength(0);
  expect(panel.textContent).not.toContain("more");
});

it("sets every row's text on one baseline: list figures on their names' line, and the header's two rows of buttons alike", async () => {
  const page = await open("/dashboard");
  // A list's figure, and a date, sit on the line of the name beside them.
  for (const title of [
    "top-clients-title",
    "top-items-title",
    "attention-title",
    "coming-up-title",
  ]) {
    const panel = page.querySelector(`[aria-labelledby="${title}"]`);
    const rows = [
      ...panel.querySelectorAll("li > a, li > div, ol > li > a"),
    ].filter((row) => row.className.includes("grid"));
    expect(rows.length).toBeGreaterThan(0);
    for (const row of rows) {
      expect(row.className).toContain("items-baseline");
      expect(row.className).not.toContain("items-center");
    }
  }
  // The period's buttons sit in a frame as tall as a shortcut key, with the
  // keys' text size, so both rows of buttons share their edges and baseline.
  const periods = page.querySelector('nav[aria-label="Period"]');
  expect(periods.className).toContain("p-[3px]");
  for (const pill of periods.querySelectorAll("a")) {
    expect(pill.className).toContain("min-h-8");
    expect(pill.className).toContain("text-[13.5px]");
  }
  // A card's name sits on a panel title's baseline: its pill is 6px taller
  // than its text above and below.
  const receivables = page.querySelector("#receivables-title");
  expect(receivables.className).toContain("py-1.5");
  // The cash flow's legend sits on its title's baseline, each swatch centred
  // beside its word.
  const legend = page.querySelector('[aria-labelledby="cash-flow-title"] ul');
  expect(legend.className).toContain("items-baseline");
  for (const item of legend.children)
    expect(item.className).toContain("items-baseline");
});
