import { afterEach, expect, it, vi } from "vitest";
import { createRoot } from "react-dom/client";
import { act } from "react-dom/test-utils";
import RefundsPanel from "./RefundsPanel";
import { dateInput } from "../../../components/workspace/Workspace";

// The panel records refunds through these hooks, and reads the business it
// works in from the last, so it needs no store or server; the browser's
// modal is left out.
const idle = () => [vi.fn(), { isLoading: false }];
vi.mock("../documentsApiSlice", () => ({
  useCreateRefundMutation: () => idle(),
  useDeleteRefundMutation: () => idle(),
  useUpdateRefundMutation: () => idle(),
}));
vi.mock("../../../components/workspace/Dialog", () => ({
  Dialog: ({ children }) => children,
}));
const active = vi.hoisted(() => ({ business: undefined }));
vi.mock("../../business/permissions", () => ({
  useBusiness: () => ({ business: active.business }),
}));

window.IS_REACT_ACT_ENVIRONMENT = true;

afterEach(() => {
  vi.useRealTimers();
  active.business = undefined;
});

// The date fields as they start, and the latest day each takes: a new
// refund's, then a changed one's once its Edit is pressed.
const dateFields = () => {
  const container = document.createElement("div");
  const root = createRoot(container);
  act(() => {
    root.render(
      <RefundsPanel
        document={{
          _id: "1",
          currency: "USD",
          ledger: { refundDue: 30 },
          refunds: [
            {
              _id: "r1",
              dateRefunded: "2026-09-01",
              amount: 10,
              paymentMethod: "Cash",
            },
          ],
        }}
      />,
    );
  });
  act(() => {
    container
      .querySelector('button[aria-label^="Edit the refund"]')
      .dispatchEvent(new MouseEvent("click", { bubbles: true }));
  });
  const fields = [...container.querySelectorAll('input[type="date"]')].map(
    ({ value, max }) => ({ value, max }),
  );
  act(() => root.unmount());
  return fields;
};

// A new refund and a changed one share their fields.
it("dates a new refund today, and no refund later", () => {
  expect(dateFields()).toEqual([
    { value: dateInput(), max: dateInput() },
    { value: "2026-09-01", max: dateInput() },
  ]);
});

// At 5 in the morning of Oct 8 in Kolkata it is still Oct 7 in Los Angeles,
// where the business is.
it("dates refunds by the business's calendar, not the viewer's", () => {
  const zone = process.env.TZ;
  process.env.TZ = "Asia/Kolkata";
  try {
    vi.useFakeTimers({ toFake: ["Date"] });
    vi.setSystemTime(new Date("2026-10-08T05:00:00+05:30"));
    active.business = { _id: "b-1", timeZone: "America/Los_Angeles" };
    expect(dateFields()).toEqual([
      { value: "2026-10-07", max: "2026-10-07" },
      { value: "2026-09-01", max: "2026-10-07" },
    ]);
  } finally {
    if (zone === undefined) delete process.env.TZ;
    else process.env.TZ = zone;
  }
});
