import { afterEach, expect, it, vi } from "vitest";
import { createRoot } from "react-dom/client";
import { act } from "react-dom/test-utils";
import ScheduleDialog from "./ScheduleDialog";

// The dialog saves through these hooks, and reads the business it works in
// from the last, so it needs no store or server; the browser's modal is
// left out.
const idle = () => [vi.fn(), { isLoading: false }];
vi.mock("../recurringApiSlice", () => ({
  useCreateScheduleMutation: () => idle(),
  useUpdateScheduleMutation: () => idle(),
}));
vi.mock("../../../components/workspace/Dialog", () => ({
  Dialog: ({ open, children }) => (open ? children : null),
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

// The next invoice's day as the dialog starts it, and the earliest it takes.
const nextInvoice = (schedule) => {
  const container = document.createElement("div");
  const root = createRoot(container);
  act(() => {
    root.render(
      <ScheduleDialog
        open
        onClose={() => {}}
        documentId="d1"
        documentNumber="INV-1014"
        schedule={schedule}
      />,
    );
  });
  const { value, min } = container.querySelector('input[type="date"]');
  act(() => root.unmount());
  return { value, min };
};

it("starts a new schedule a month after today, and none before today", () => {
  vi.useFakeTimers({ toFake: ["Date"] });
  vi.setSystemTime(new Date(2026, 9, 8, 12));
  expect(nextInvoice()).toEqual({ value: "2026-11-08", min: "2026-10-08" });
});

// The server refuses a first invoice before the business's today. At 5 in
// the morning of Oct 8 in Kolkata it is still Oct 7 in Los Angeles.
it("counts from the business's today, not the viewer's", () => {
  const zone = process.env.TZ;
  process.env.TZ = "Asia/Kolkata";
  try {
    vi.useFakeTimers({ toFake: ["Date"] });
    vi.setSystemTime(new Date("2026-10-08T05:00:00+05:30"));
    active.business = { _id: "b-1", timeZone: "America/Los_Angeles" };
    expect(nextInvoice()).toEqual({ value: "2026-11-07", min: "2026-10-07" });
    // A schedule due to run today keeps its next invoice.
    expect(
      nextInvoice({
        _id: "s1",
        frequency: "MONTHLY",
        nextRun: "2026-10-07",
        issueAutomatically: true,
        emailAutomatically: false,
      }),
    ).toEqual({ value: "2026-10-07", min: "2026-10-07" });
  } finally {
    if (zone === undefined) delete process.env.TZ;
    else process.env.TZ = zone;
  }
});
