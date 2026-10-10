import { expect, it, vi } from "vitest";
import { createRoot } from "react-dom/client";
import { act } from "react-dom/test-utils";
import PaymentsLedger from "./PaymentsLedger";
import { saveFile } from "../useDocumentPdf";
import { dateInput } from "../../../components/workspace/Workspace";

// The ledger changes payments and gets their receipts through these hooks,
// and saves a receipt with saveFile, so it needs no store or server; the
// browser's modal and the receipt email are left out.
const idle = () => [vi.fn(), { isLoading: false }];
vi.mock("../documentsApiSlice", () => ({
  useDeletePaymentMutation: () => idle(),
  useGetReceiptPdfMutation: () => [
    () => ({ unwrap: () => Promise.resolve("blob:receipt") }),
  ],
  useUpdatePaymentMutation: () => idle(),
}));
vi.mock("../useDocumentPdf", () => ({ saveFile: vi.fn() }));
vi.mock("../../../components/workspace/Dialog", () => ({
  Dialog: ({ children }) => children,
}));
vi.mock("./EmailReceiptDialog", () => ({ default: () => null }));

window.IS_REACT_ACT_ENVIRONMENT = true;

it("moves a payment to no later than today", () => {
  const container = document.createElement("div");
  const root = createRoot(container);
  act(() => {
    root.render(
      <PaymentsLedger
        document={{
          _id: "1",
          documentNumber: "INV-0001",
          currency: "USD",
          ledger: { balanceDue: 60 },
          paymentRecords: [
            {
              _id: "p1",
              datePaid: "2026-01-15",
              amountPaid: 40,
              tdsAmount: 0,
              paymentMethod: "Cash",
            },
          ],
        }}
      />,
    );
  });
  act(() => {
    container
      .querySelector('button[aria-label^="Edit the payment"]')
      .dispatchEvent(new MouseEvent("click", { bubbles: true }));
  });
  expect(container.querySelector('input[type="date"]').max).toBe(dateInput());
  act(() => root.unmount());
});

// Two payments, the later-dated one recorded first, so each receipt's number
// isn't its place in the list, which runs by date.
const invoice = {
  _id: "invoice",
  documentNumber: "INV-0001",
  currency: "USD",
  ledger: { balanceDue: "30.00" },
  paymentRecords: [
    {
      _id: "early",
      datePaid: "2026-09-10",
      amountPaid: 40,
      tdsAmount: 0,
      paymentMethod: "Cash",
      receiptNumber: 2,
    },
    {
      _id: "late",
      datePaid: "2026-09-20",
      amountPaid: 30,
      tdsAmount: 0,
      paymentMethod: "Cash",
      receiptNumber: 1,
    },
  ],
};

it("names a downloaded receipt by its number, as the receipt itself is", async () => {
  const container = document.createElement("div");
  const root = createRoot(container);
  act(() => {
    root.render(<PaymentsLedger document={invoice} />);
  });
  const [first] = container.querySelectorAll(
    'button[aria-label^="Download the receipt"]',
  );
  await act(async () => {
    first.click();
  });
  expect(saveFile).toHaveBeenCalledWith(
    "blob:receipt",
    "Receipt-INV-0001-P2.pdf",
  );
  act(() => root.unmount());
});
