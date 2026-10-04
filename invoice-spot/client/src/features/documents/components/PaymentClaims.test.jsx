import { expect, it, vi } from "vitest";
import { createRoot } from "react-dom/client";
import { act } from "react-dom/test-utils";
import PaymentClaims from "./PaymentClaims";
import { dateInput } from "../../../components/workspace/Workspace";

// The panel gets the reports and answers them through these hooks, so it
// needs no store or server; the browser's modal is left out.
const idle = () => [vi.fn(), { isLoading: false }];
vi.mock("../documentsApiSlice", () => ({
  useConfirmPaymentClaimMutation: () => idle(),
  useDismissPaymentClaimMutation: () => idle(),
  useGetPaymentClaimsQuery: () => ({
    data: {
      claims: [
        {
          id: "c1",
          status: "PENDING",
          amount: 40,
          currency: "USD",
          method: "UPI",
          methodLabel: "UPI",
          paidOn: "2026-01-15",
          reportedAt: "2026-01-15T10:00:00Z",
        },
      ],
    },
  }),
}));
vi.mock("../../../components/workspace/Dialog", () => ({
  Dialog: ({ children }) => children,
}));

window.IS_REACT_ACT_ENVIRONMENT = true;

it("records a reported payment dated no later than today", () => {
  const container = document.createElement("div");
  const root = createRoot(container);
  act(() => {
    root.render(<PaymentClaims document={{ _id: "1", currency: "USD" }} />);
  });
  const confirm = [...container.querySelectorAll("button")].find((button) =>
    button.textContent.includes("Confirm"),
  );
  act(() => {
    confirm.dispatchEvent(new MouseEvent("click", { bubbles: true }));
  });
  expect(container.querySelector('input[type="date"]').max).toBe(dateInput());
  act(() => root.unmount());
});
