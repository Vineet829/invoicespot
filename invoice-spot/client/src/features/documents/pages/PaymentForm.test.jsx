import { expect, it, vi } from "vitest";
import { createRoot } from "react-dom/client";
import { act } from "react-dom/test-utils";
import PaymentForm from "./PaymentForm";
import { dateInput } from "../../../components/workspace/Workspace";

// The form records through this hook, so it needs no store or server.
vi.mock("../documentsApiSlice", () => ({
  useCreatePaymentMutation: () => [vi.fn(), { isLoading: false }],
}));

window.IS_REACT_ACT_ENVIRONMENT = true;

it("dates a payment no later than today", () => {
  const container = document.createElement("div");
  const root = createRoot(container);
  act(() => {
    root.render(
      <PaymentForm
        document={{ _id: "1", currency: "USD", ledger: { balanceDue: 100 } }}
      />,
    );
  });
  expect(container.querySelector('input[type="date"]').max).toBe(dateInput());
  act(() => root.unmount());
});
