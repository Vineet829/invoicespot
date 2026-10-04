import { expect, it, vi } from "vitest";
import { createRoot } from "react-dom/client";
import { act } from "react-dom/test-utils";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import DocCreateEditForm from "./DocCreateEditForm";
import { useGetSingleDocQuery } from "../documentsApiSlice";
import { useGetSettingsQuery } from "../../settings/settingsApiSlice";
import { dateInput } from "../../../components/workspace/Workspace";

// The editor gets everything from these hooks, so it needs no store or
// server; the signed-in shell around it is left out.
const idle = () => [vi.fn(), { isLoading: false }];
vi.mock("../documentsApiSlice", () => ({
  useCreateDocMutation: () => idle(),
  useGetSingleDocQuery: vi.fn(),
  useGetTaxRatesQuery: () => ({}),
  useIssueDocMutation: () => idle(),
  useUpdateDocMutation: () => idle(),
}));
vi.mock("../../workspace/workspaceApiSlice", () => ({
  useGetWorkspaceCustomersQuery: () => ({ data: { myCustomers: [] } }),
}));
vi.mock("../../settings/settingsApiSlice", () => ({
  useGetSettingsQuery: vi.fn(),
}));
vi.mock("../../business/businessApiSlice", () => ({
  useGetBusinessQuery: () => ({ data: { business: { taxId: "" } } }),
}));
vi.mock("../../gst/gstApiSlice", () => ({
  useGetGstStatesQuery: () => ({ data: { states: [] } }),
}));
vi.mock("../../business/permissions", () => ({
  useBusiness: () => ({ reason: () => undefined }),
}));
vi.mock("../../items/itemsApiSlice", () => ({
  useCreateItemMutation: () => idle(),
  useGetItemsQuery: () => ({}),
}));
vi.mock("../../customers/components/NewClientDialog", () => ({
  default: () => null,
}));
vi.mock("../../dashboard/components/DashboardShell", () => ({
  default: ({ children }) => children,
}));

window.IS_REACT_ACT_ENVIRONMENT = true;

const inRegime = (taxRegime) =>
  useGetSettingsQuery.mockReturnValue({
    data: {
      settings: {
        defaultCurrency: "USD",
        baseCurrency: "INR",
        paymentTermsDays: 15,
        defaultTaxRate: 0,
        taxLabel: "Tax",
        taxRegime,
      },
    },
  });

const render = (doc) => {
  useGetSingleDocQuery.mockReturnValue(doc ? { data: { document: doc } } : {});
  const container = document.createElement("div");
  act(() => {
    createRoot(container).render(
      <MemoryRouter initialEntries={[doc ? "/edit/d1" : "/create"]}>
        <Routes>
          <Route path="/create" element={<DocCreateEditForm />} />
          <Route path="/edit/:id" element={<DocCreateEditForm />} />
        </Routes>
      </MemoryRouter>,
    );
  });
  return container;
};

// A field by the start of its label, and what it says under it.
const field = (container, label) => {
  const element = [...container.querySelectorAll("label")].find((candidate) =>
    candidate.textContent.startsWith(label),
  );
  const input = [...container.querySelectorAll("input")].find(
    (candidate) => candidate.id === element.htmlFor,
  );
  const hint = [...container.querySelectorAll("p")].find(
    (candidate) => candidate.id === `${input.id}-hint`,
  );
  return { input, hint: hint?.textContent };
};

// A day picked in a date field, as the browser sends it.
const pick = (input, day) =>
  act(() => {
    Object.getOwnPropertyDescriptor(
      HTMLInputElement.prototype,
      "value",
    ).set.call(input, day);
    input.dispatchEvent(new Event("input", { bubbles: true }));
  });

// Whether the browser would let the form be sent with this value.
const takes = (input, value) => {
  input.value = value;
  return input.validity.valid;
};

it("takes a due date no earlier than the issue date, which is today unless chosen", () => {
  inRegime("STANDARD");
  const container = render();
  const { input: due } = field(container, "Due date");
  expect(due.min).toBe(dateInput());
  pick(field(container, "Issue date").input, "2026-01-15");
  expect(due.min).toBe("2026-01-15");
  pick(due, "2026-01-10");
  expect(due.validity.rangeUnderflow).toBe(true);
});

it("keeps a draft's due date saved before its issue date until either date changes", () => {
  inRegime("STANDARD");
  const container = render({
    _id: "d1",
    state: "draft",
    documentType: "Invoice",
    currency: "USD",
    issueDate: "2026-01-15",
    dueDate: "2026-01-10",
    customer: { name: "Beta LLC" },
    customerId: "c1",
    billingItems: [{ itemName: "Work", quantity: 1, unitPrice: 100 }],
  });
  const { input: due } = field(container, "Due date");
  expect(due.validity.valid).toBe(true);
  pick(due, "2026-01-12");
  expect(due.validity.rangeUnderflow).toBe(true);
});

it("refuses a line with more decimals than the server keeps", () => {
  inRegime("STANDARD");
  const container = render();
  for (const label of [
    "Quantity",
    "Unit price",
    "Discount (%)",
    "Tax %",
    "Tax rate for new lines",
  ]) {
    const { input } = field(container, label);
    expect(takes(input, "1.23456"), label).toBe(false);
    expect(takes(input, "1.2345"), label).toBe(true);
    expect(takes(input, "12.50000"), label).toBe(true);
  }
  const { input: rate } = field(container, "Exchange rate");
  expect(takes(rate, "0.012048192771084338")).toBe(false);
  expect(takes(rate, "0.0120481928")).toBe(true);
});

it("says a GST invoice needs a rate even when the draft kept one to another currency", () => {
  inRegime("GST_INDIA");
  const { hint } = field(
    render({
      _id: "d1",
      state: "draft",
      documentType: "Invoice",
      currency: "EUR",
      exchangeRate: "1.08",
      exchangeBase: "USD",
      customer: { name: "Beta LLC" },
      customerId: "c1",
      billingItems: [{ itemName: "Work", quantity: 1, unitPrice: 100 }],
    }),
    "Exchange rate",
  );
  expect(hint).toMatch(/^Needed/);
});
