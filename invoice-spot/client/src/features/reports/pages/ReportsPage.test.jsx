import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { createRoot } from "react-dom/client";
import { act } from "react-dom/test-utils";
import { MemoryRouter } from "react-router-dom";
import ReportsPage from "./ReportsPage";
import {
  useGetProfitReportQuery,
  useGetReportCsvMutation,
  useGetReportQuery,
  useGetTaxReportQuery,
  useGetTdsReportQuery,
} from "../reportsApiSlice";

// The page gets its reports from these hooks, so it needs no store or
// server; the signed-in shell around it is left out.
vi.mock("../reportsApiSlice", () => ({
  useGetReportQuery: vi.fn(),
  useGetTaxReportQuery: vi.fn(),
  useGetTdsReportQuery: vi.fn(),
  useGetProfitReportQuery: vi.fn(),
  useGetReportCsvMutation: vi.fn(),
}));
vi.mock("../../settings/settingsApiSlice", () => ({
  useGetSettingsQuery: () => ({}),
}));
vi.mock("../../dashboard/components/DashboardShell", () => ({
  default: ({ children }) => children,
}));

window.IS_REACT_ACT_ENVIRONMENT = true;

const loaded = (data) => ({
  data,
  isLoading: false,
  isFetching: false,
  isError: false,
});

// A period with no invoices, receipts or payments, and nothing unpaid.
beforeEach(() => {
  useGetReportQuery.mockReturnValue(
    loaded({
      period: "12m",
      from: "2025-11-01",
      to: "2026-10-02",
      today: "2026-10-02",
      years: [],
      view: "currency",
      baseCurrency: "INR",
      currencies: [],
      notConverted: [],
    }),
  );
  useGetTaxReportQuery.mockReturnValue(loaded({ currencies: [] }));
  useGetTdsReportQuery.mockReturnValue(loaded({ currencies: [] }));
});

let root;
let container;
afterEach(() => {
  act(() => root.unmount());
  container.remove();
  delete Element.prototype.scrollIntoView;
});

// In the document, so a section can take focus.
const render = (path = "/reports") => {
  container = document.body.appendChild(document.createElement("div"));
  root = createRoot(container);
  act(() => {
    root.render(
      <MemoryRouter initialEntries={[path]}>
        <ReportsPage />
      </MemoryRouter>,
    );
  });
  return container;
};

const headings = (container) =>
  [...container.querySelectorAll("h2")].map((heading) => heading.textContent);

it("shows the profit and loss of a period with expenses but no invoices", () => {
  useGetProfitReportQuery.mockReturnValue(
    loaded({
      period: "12m",
      currencies: [
        {
          currency: "INR",
          income: 0,
          expenses: 1500,
          profit: -1500,
          months: [
            { month: "2026-09", income: 0, expenses: 1500, profit: -1500 },
          ],
          categories: [{ category: "Rent", expenses: 1500 }],
        },
      ],
    }),
  );
  const page = render();
  expect(headings(page)).toEqual([
    "Nothing to report for this period.",
    "Profit and loss",
  ]);
  expect(page.textContent).toContain("a loss of INR 1,500.00");
});

it("shows only that there is nothing to report when there are no expenses either", () => {
  useGetProfitReportQuery.mockReturnValue(
    loaded({ period: "12m", currencies: [] }),
  );
  expect(headings(render())).toEqual(["Nothing to report for this period."]);
});

// A period with one invoice, part paid, in one currency.
const withFigures = () => {
  useGetReportCsvMutation.mockReturnValue([vi.fn(), { isLoading: false }]);
  useGetReportQuery.mockReturnValue(
    loaded({
      period: "12m",
      from: "2025-11-01",
      to: "2026-10-02",
      today: "2026-10-02",
      years: [],
      view: "currency",
      baseCurrency: "USD",
      notConverted: [],
      currencies: [
        {
          currency: "USD",
          billed: 1000,
          received: 600,
          outstanding: 400,
          overdue: 0,
          months: [{ month: "2026-09", billed: 1000, received: 600 }],
          ageing: [
            {
              key: "notDue",
              label: "Not due yet",
              invoices: 1,
              outstanding: 400,
            },
          ],
          methods: [{ method: "Bank Transfer", payments: 1, amount: 600 }],
          clients: [
            {
              customerId: "c1",
              name: "Northwind",
              documents: 1,
              billed: 1000,
              paid: 600,
              outstanding: 400,
            },
          ],
          otherClients: null,
        },
      ],
    }),
  );
  useGetProfitReportQuery.mockReturnValue(
    loaded({
      period: "12m",
      currencies: [
        {
          currency: "USD",
          income: 900,
          expenses: 0,
          profit: 900,
          months: [{ month: "2026-09", income: 900, expenses: 0, profit: 900 }],
          categories: [],
        },
      ],
    }),
  );
};

it("opens at the section the overview links to, with focus there", () => {
  withFigures();
  const scrolled = [];
  Element.prototype.scrollIntoView = vi.fn(function () {
    scrolled.push(this.id);
  });
  const page = render("/reports#ageing");
  expect(
    ["income", "profit", "ageing", "clients"].map(
      (id) => page.querySelector(`#${id}`)?.querySelector("h2").textContent,
    ),
  ).toEqual([
    "Income by month",
    "Profit and loss",
    "Unpaid invoices by age",
    "Top clients",
  ]);
  expect(scrolled).toEqual(["ageing"]);
  expect(document.activeElement.id).toBe("ageing");
});

it.each(["/reports#%", "/reports#nowhere", "/reports"])(
  "stays at the top for %s",
  (path) => {
    withFigures();
    Element.prototype.scrollIntoView = vi.fn();
    const page = render(path);
    expect(page.querySelector("#income")).not.toBeNull();
    expect(Element.prototype.scrollIntoView).not.toHaveBeenCalled();
  },
);

it("reports on the overview's last 90 days when its profit opens the page", () => {
  withFigures();
  const scrolled = [];
  Element.prototype.scrollIntoView = vi.fn(function () {
    scrolled.push(this.id);
  });
  const page = render("/reports?period=90d#profit");
  expect(useGetReportQuery).toHaveBeenLastCalledWith({
    period: "90d",
    view: undefined,
  });
  expect(useGetProfitReportQuery).toHaveBeenLastCalledWith("90d");
  const period = [...page.querySelectorAll("label")]
    .find((label) => label.textContent.startsWith("Period"))
    .querySelector("select");
  expect(period.value).toBe("90d");
  expect(
    [...period.options].slice(0, 3).map((option) => option.textContent),
  ).toEqual(["Last 30 days", "Last 90 days", "Last 3 months"]);
  expect(scrolled).toEqual(["profit"]);
});
