import { beforeEach, expect, it, vi } from "vitest";
import { createRoot } from "react-dom/client";
import { act } from "react-dom/test-utils";
import { MemoryRouter } from "react-router-dom";
import ReportsPage from "./ReportsPage";
import {
  useGetProfitReportQuery,
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

const render = () => {
  const container = document.createElement("div");
  act(() => {
    createRoot(container).render(
      <MemoryRouter>
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
