import { afterEach, expect, it, vi } from "vitest";
import { createRoot } from "react-dom/client";
import { act } from "react-dom/test-utils";
import PaymentForm from "./PaymentForm";
import { dateInput } from "../../../components/workspace/Workspace";

// The form records through this hook, and reads the business it works in
// from the other, so it needs no store or server.
vi.mock("../documentsApiSlice", () => ({
  useCreatePaymentMutation: () => [vi.fn(), { isLoading: false }],
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

// The date field as the form starts it, and the latest day it takes.
const datePaid = () => {
  const container = document.createElement("div");
  const root = createRoot(container);
  act(() => {
    root.render(
      <PaymentForm
        document={{ _id: "1", currency: "USD", ledger: { balanceDue: 100 } }}
      />,
    );
  });
  const { value, max } = container.querySelector('input[type="date"]');
  act(() => root.unmount());
  return { value, max };
};

it("dates a payment today, and no later", () => {
  expect(datePaid()).toEqual({ value: dateInput(), max: dateInput() });
});

// At 5 in the morning of Oct 8 in Kolkata it is still Oct 7 in Los Angeles,
// where the business is.
it("dates a payment by the business's calendar, not the viewer's", () => {
  const zone = process.env.TZ;
  process.env.TZ = "Asia/Kolkata";
  try {
    vi.useFakeTimers({ toFake: ["Date"] });
    vi.setSystemTime(new Date("2026-10-08T05:00:00+05:30"));
    active.business = { _id: "b-1", timeZone: "America/Los_Angeles" };
    expect(datePaid()).toEqual({ value: "2026-10-07", max: "2026-10-07" });
  } finally {
    if (zone === undefined) delete process.env.TZ;
    else process.env.TZ = zone;
  }
});
