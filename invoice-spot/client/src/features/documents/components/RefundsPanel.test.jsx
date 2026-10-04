import { expect, it, vi } from "vitest";
import { createRoot } from "react-dom/client";
import { act } from "react-dom/test-utils";
import RefundsPanel from "./RefundsPanel";
import { dateInput } from "../../../components/workspace/Workspace";

// The panel records refunds through these hooks, so it needs no store or
// server; the browser's modal is left out.
const idle = () => [vi.fn(), { isLoading: false }];
vi.mock("../documentsApiSlice", () => ({
  useCreateRefundMutation: () => idle(),
  useDeleteRefundMutation: () => idle(),
  useUpdateRefundMutation: () => idle(),
}));
vi.mock("../../../components/workspace/Dialog", () => ({
  Dialog: ({ children }) => children,
}));

window.IS_REACT_ACT_ENVIRONMENT = true;

// A new refund and a changed one share their fields.
it("dates a refund no later than today", () => {
  const container = document.createElement("div");
  const root = createRoot(container);
  act(() => {
    root.render(
      <RefundsPanel
        document={{ _id: "1", currency: "USD", ledger: { refundDue: 30 } }}
      />,
    );
  });
  expect(container.querySelector('input[type="date"]').max).toBe(dateInput());
  act(() => root.unmount());
});
