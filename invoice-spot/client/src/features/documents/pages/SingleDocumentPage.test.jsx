import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { createRoot } from "react-dom/client";
import { act } from "react-dom/test-utils";

// The page as the app draws it, through its own store and RTK Query, with
// only fetch faked. The signed-in shell is left out.
vi.mock("../../dashboard/components/DashboardShell", () => ({
  default: ({ children }) => children,
}));

window.IS_REACT_ACT_ENVIRONMENT = true;

let root;
let container;
beforeEach(() => {
  vi.resetModules();
  vi.stubEnv("VITE_API_URL", "http://localhost:3001/api/v1");
});
afterEach(() => {
  act(() => root.unmount());
  container.remove();
  vi.unstubAllGlobals();
  vi.unstubAllEnvs();
});

const json = (body, status = 200) =>
  new Response(JSON.stringify(body), {
    status,
    headers: { "content-type": "application/json" },
  });

// One line at 4,600.00 with 7.25% tax, as the demo's quotations have it.
const priced = {
  state: "issued",
  currency: "USD",
  issueDate: "2026-09-02",
  dueDate: "2026-09-16",
  customer: { name: "Lumen Health" },
  billingItems: [
    {
      itemName: "Brand strategy workshop",
      quantity: 1,
      unitPrice: 4600,
      taxRate: 7.25,
      lineTotal: 4600,
    },
  ],
  subTotal: 4600,
  taxes: [{ rate: 7.25, tax: 333.5 }],
  total: 4933.5,
  paymentRecords: [],
};

// Answers each request by its path, with the document asked for.
const serve = (doc) =>
  vi.stubGlobal(
    "fetch",
    vi.fn(async (request) => {
      const path = new URL(request.url).pathname.replace("/api/v1", "");
      const answer = {
        [`/document/${doc._id}`]: { document: doc },
        [`/document/${doc._id}/activity`]: { events: [] },
        [`/document/${doc._id}/payment-claims`]: { claims: [] },
        [`/document/${doc._id}/recurring`]: { schedule: null },
        "/user/profile": { userProfile: { demo: false } },
        "/business": { business: { name: "Northline Studio" } },
        "/businesses": {
          businesses: [{ _id: "b-1", name: "Northline Studio", active: true }],
        },
      }[path];
      return answer ? json(answer) : json({ message: "Not found" }, 404);
    }),
  );

const open = async (doc) => {
  serve(doc);
  const { Provider } = await import("react-redux");
  const { MemoryRouter, Route, Routes } = await import("react-router-dom");
  const { store } = await import("../../../app/store");
  const { default: SingleDocumentPage } = await import("./SingleDocumentPage");
  container = document.body.appendChild(document.createElement("div"));
  root = createRoot(container);
  await act(async () => {
    root.render(
      <Provider store={store}>
        <MemoryRouter initialEntries={[`/document/${doc._id}`]}>
          <Routes>
            <Route path="/document/:id" element={<SingleDocumentPage />} />
          </Routes>
        </MemoryRouter>
      </Provider>,
    );
  });
  await act(() =>
    vi.waitFor(() => expect(costSummary(container)).toBeTruthy()),
  );
  return container;
};

// The cost summary's rows, as label and amount, and its last, larger row.
const costSummary = (page) =>
  [...page.querySelectorAll("h2")]
    .find((heading) => heading.textContent === "Cost summary")
    ?.closest("section");
const rows = (page) =>
  [...costSummary(page).querySelectorAll("dl > div")].map((row) => [
    row.querySelector("dt").textContent,
    row.querySelector("dd").textContent,
  ]);
const largest = (page) =>
  [...costSummary(page).querySelectorAll("dl > div")]
    .find((row) => row.className.includes("border-t"))
    .querySelector("dt").textContent;

it("ends a quotation's summary at its total, with nothing paid or owed on it", async () => {
  const page = await open({
    ...priced,
    _id: "q1",
    documentType: "Quotation",
    documentNumber: "QUO-0207",
    status: "Not Paid",
    expired: true,
    totalAmountReceived: 0,
    ledger: { balanceDue: null },
  });
  expect(rows(page)).toEqual([
    ["Subtotal", "USD4,600.00"],
    ["Tax (7.25%)", "USD333.50"],
    ["Total", "USD4,933.50"],
  ]);
  expect(largest(page)).toBe("Total");
  expect(costSummary(page).textContent).not.toMatch(/Amount paid|Balance/);
});

it.each([
  [
    "a part-paid invoice",
    {
      documentType: "Invoice",
      documentNumber: "INV-1034",
      status: "Not Fully Paid",
      totalAmountReceived: 1000,
      ledger: { balanceDue: 3933.5 },
    },
    ["Amount paid", "USD1,000.00"],
    ["Balance", "USD3,933.50"],
  ],
  [
    "a receipt",
    {
      documentType: "Receipt",
      documentNumber: "REC-0304",
      status: "Paid",
      totalAmountReceived: 4933.5,
      ledger: { balanceDue: 0 },
    },
    ["Amount paid", "USD4,933.50"],
    ["Balance", "USD0.00"],
  ],
])(
  "calls %s's total Total, as its PDF does, above what was paid and what is left",
  async (_case, fields, paid, left) => {
    const page = await open({ ...priced, _id: "d1", ...fields });
    expect(rows(page)).toEqual([
      ["Subtotal", "USD4,600.00"],
      ["Tax (7.25%)", "USD333.50"],
      ["Total", "USD4,933.50"],
      paid,
      left,
    ]);
    expect(largest(page)).toBe("Balance");
  },
);
