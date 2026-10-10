import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { createRoot } from "react-dom/client";
import { act } from "react-dom/test-utils";

// The client's page as the app draws it, through its own store and RTK
// Query, with only fetch faked. The signed-in shell is left out.
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

// A client billed 1,500.00, of which 900.00 came in: payments on invoices
// and a receipt.
const ANSWERS = {
  "/customer/c-1": {
    success: true,
    customer: { _id: "c-1", name: "Acme", email: "acme@clients.test" },
  },
  "/customer/c-1/summary": {
    success: true,
    totals: [
      {
        currency: "USD",
        billed: 1500,
        paid: 900,
        outstanding: 600,
        overdue: 0,
      },
    ],
  },
  "/document/all": {
    success: true,
    totalDocuments: 0,
    numberOfPages: 0,
    myDocuments: [],
    totals: [],
  },
  "/user/profile": { success: true, userProfile: { demo: false } },
  "/businesses": { businesses: [] },
};

const open = async () => {
  vi.stubGlobal(
    "fetch",
    vi.fn(async (request) => {
      const path = new URL(request.url).pathname.replace("/api/v1", "");
      const body = ANSWERS[path];
      return body ? json(body) : json({ message: "Not found" }, 404);
    }),
  );
  const { Provider } = await import("react-redux");
  const { MemoryRouter, Route, Routes } = await import("react-router-dom");
  const { store } = await import("../../../app/store");
  const { default: Page } = await import("./SingleCustomerPage.jsx");
  container = document.body.appendChild(document.createElement("div"));
  root = createRoot(container);
  await act(async () => {
    root.render(
      <Provider store={store}>
        <MemoryRouter initialEntries={["/customer/c-1"]}>
          <Routes>
            <Route path="/customer/:custId" element={<Page />} />
          </Routes>
        </MemoryRouter>
      </Provider>,
    );
  });
  await act(() =>
    vi.waitFor(() => expect(container.querySelector("dl dt")).not.toBeNull()),
  );
  return container;
};

// Each figure's label, amount and the note under it.
const figures = (page) =>
  [...page.querySelectorAll("dl > div")]
    .filter((figure) => figure.querySelector("dd")?.childElementCount === 2)
    .map((figure) => [
      figure.querySelector("dt").textContent,
      figure.querySelector("dd").firstElementChild.textContent,
      figure.querySelector("dd").lastElementChild.textContent,
    ]);

it("calls the money that came in from the client Received, as the rest of the app does", async () => {
  const page = await open();
  const received = figures(page).find(([label]) => label === "Received");
  expect(received).toBeDefined();
  expect(received[1]).toContain("900.00");
  expect(received[2]).toBe("Payments and receipts");
  expect(figures(page).map(([label]) => label)).toEqual([
    "Billed",
    "Received",
    "Outstanding",
    "Overdue",
  ]);
  expect(page.textContent).toContain(
    "What this client was billed, what was received from them and what they still owe.",
  );
});
