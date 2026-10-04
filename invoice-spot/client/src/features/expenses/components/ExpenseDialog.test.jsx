import { expect, it, vi } from "vitest";
import { createRoot } from "react-dom/client";
import { act } from "react-dom/test-utils";
import ExpenseDialog from "./ExpenseDialog";
import { dateInput } from "../../../components/workspace/Workspace";

// The dialog saves through these hooks, so it needs no store or server; the
// browser's modal is left out.
const idle = () => [vi.fn(), { isLoading: false }];
vi.mock("../expensesApiSlice", () => ({
  useCreateExpenseMutation: () => idle(),
  useGetExpenseCategoriesQuery: () => ({}),
  useUpdateExpenseMutation: () => idle(),
}));
vi.mock("../../settings/settingsApiSlice", () => ({
  useGetSettingsQuery: () => ({
    data: { settings: { defaultCurrency: "USD", baseCurrency: "USD" } },
  }),
}));
vi.mock("../../../components/workspace/Dialog", () => ({
  Dialog: ({ children }) => children,
}));

window.IS_REACT_ACT_ENVIRONMENT = true;

// A new expense and a changed one share the form.
it("dates an expense no later than today", () => {
  const container = document.createElement("div");
  const root = createRoot(container);
  act(() => {
    root.render(<ExpenseDialog open onClose={() => {}} />);
  });
  expect(container.querySelector('input[type="date"]').max).toBe(dateInput());
  act(() => root.unmount());
});

// An expense in another currency than the reporting one.
it("takes a rate with up to the 10 decimals the server keeps", () => {
  const container = document.createElement("div");
  const root = createRoot(container);
  act(() => {
    root.render(
      <ExpenseDialog
        open
        expense={{
          _id: "e1",
          date: "2026-01-15",
          category: "Travel",
          total: 10,
          currency: "EUR",
        }}
        onClose={() => {}}
      />,
    );
  });
  const label = [...container.querySelectorAll("label")].find((element) =>
    element.textContent.startsWith("Exchange rate"),
  );
  const rate = container.querySelector(`[id="${label.htmlFor}"]`);
  rate.value = "0.012048192771084338";
  expect(rate.validity.stepMismatch).toBe(true);
  rate.value = "0.0120481928";
  expect(rate.validity.valid).toBe(true);
  act(() => root.unmount());
});
