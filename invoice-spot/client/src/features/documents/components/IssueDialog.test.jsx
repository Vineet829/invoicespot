import { afterEach, expect, it, vi } from "vitest";
import { createRoot } from "react-dom/client";
import { act } from "react-dom/test-utils";
import IssueDialog from "./IssueDialog";
import { useGetSettingsQuery } from "../../settings/settingsApiSlice";

// The dialog reads the business's settings, and the business it works in,
// from these hooks, so it needs no store or server; the browser's modal is
// left out.
vi.mock("../documentsApiSlice", () => ({
  useIssueDocMutation: () => [vi.fn(), { isLoading: false }],
}));
vi.mock("../../settings/settingsApiSlice", () => ({
  useGetSettingsQuery: vi.fn(),
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

const inRegime = (taxRegime) =>
  useGetSettingsQuery.mockReturnValue({
    data: {
      settings: { paymentTermsDays: 15, baseCurrency: "INR", taxRegime },
    },
  });

const render = (draft) => {
  const container = document.createElement("div");
  act(() => {
    createRoot(container).render(
      <IssueDialog
        document={{ _id: "d1", currency: "USD", ...draft }}
        open
        onClose={() => {}}
        onIssued={() => {}}
        canEmail
      />,
    );
  });
  return container;
};

// The exchange rate field and what it says under it, if it is shown.
const rateField = (container) => {
  const label = [...container.querySelectorAll("label")].find((element) =>
    element.textContent.startsWith("Exchange rate"),
  );
  if (!label) return null;
  const input = [...container.querySelectorAll("input")].find(
    (element) => element.id === label.htmlFor,
  );
  const hint = [...container.querySelectorAll("p")].find(
    (element) => element.id === `${input.id}-hint`,
  );
  return { input, hint: hint?.textContent };
};

it("asks for the rate a GST invoice or receipt in another currency is issued with", () => {
  inRegime("GST_INDIA");
  for (const documentType of ["Invoice", "Receipt"]) {
    const { input, hint } = rateField(render({ documentType }));
    expect(input.required).toBe(true);
    expect(hint).toMatch(/^Needed/);
  }
});

it("leaves the rate optional on a quotation, and outside GST mode", () => {
  inRegime("GST_INDIA");
  const quotation = rateField(render({ documentType: "Quotation" }));
  expect(quotation.input.required).toBe(false);
  expect(quotation.hint).toMatch(/^Optional/);
  inRegime("STANDARD");
  const invoice = rateField(render({ documentType: "Invoice" }));
  expect(invoice.input.required).toBe(false);
  expect(invoice.hint).toMatch(/^Optional/);
});

it("doesn't offer to change a credit note's rate, which stays its invoice's", () => {
  inRegime("GST_INDIA");
  expect(
    rateField(
      render({
        documentType: "Credit Note",
        exchangeRate: "83.25",
        exchangeBase: "INR",
      }),
    ),
  ).toBeNull();
});

// The server refuses a document dated after the business's today. At 5 in
// the morning of Oct 8 in Kolkata it is still Oct 7 in Los Angeles.
it("dates a draft on the business's today, due the payment terms after it", () => {
  inRegime("STANDARD");
  const zone = process.env.TZ;
  process.env.TZ = "Asia/Kolkata";
  try {
    vi.useFakeTimers({ toFake: ["Date"] });
    vi.setSystemTime(new Date("2026-10-08T05:00:00+05:30"));
    active.business = { _id: "b-1", timeZone: "America/Los_Angeles" };
    const [issued, due] = render({ documentType: "Invoice" }).querySelectorAll(
      'input[type="date"]',
    );
    expect([issued.value, issued.max]).toEqual(["2026-10-07", "2026-10-07"]);
    expect([due.value, due.min]).toEqual(["2026-10-22", "2026-10-07"]);
  } finally {
    if (zone === undefined) delete process.env.TZ;
    else process.env.TZ = zone;
  }
});

it("takes a rate with up to the 10 decimals the server keeps", () => {
  inRegime("STANDARD");
  const { input } = rateField(render({ documentType: "Invoice" }));
  input.value = "0.012048192771084338";
  expect(input.validity.stepMismatch).toBe(true);
  input.value = "0.0120481928";
  expect(input.validity.valid).toBe(true);
  input.value = "83.2500000000000";
  expect(input.validity.valid).toBe(true);
});
