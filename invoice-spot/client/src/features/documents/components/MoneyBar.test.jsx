import { afterEach, expect, it, vi } from "vitest";
import { createRoot } from "react-dom/client";
import { act } from "react-dom/test-utils";
import { MemoryRouter } from "react-router-dom";
import MoneyBar from "./MoneyBar";

window.IS_REACT_ACT_ENVIRONMENT = true;

let root;
let container;
afterEach(() => {
  act(() => root.unmount());
  container.remove();
});

// The demo account's summary on Oct 4, 2026.
const summary = {
  success: true,
  currency: "USD",
  currencies: ["USD"],
  outstanding: {
    total: 24285.79,
    overdue: 6326.03,
    notDue: 17959.76,
    invoices: 10,
    overdueInvoices: 3,
  },
  receivedLast30Days: { total: 22763.97, payments: 7 },
};
// The summary query as RTK Query gives it, once answered.
const answered = (data) => ({
  currentData: data,
  data,
  isFetching: false,
  isError: false,
  refetch: vi.fn(),
});

const render = (props) => {
  container = document.body.appendChild(document.createElement("div"));
  root = createRoot(container);
  act(() => {
    root.render(
      <MemoryRouter>
        <MoneyBar {...props} />
      </MemoryRouter>,
    );
  });
  return container;
};
const again = (props) => {
  act(() => root.unmount());
  container.remove();
  return render(props);
};

const links = (bar) =>
  [...bar.querySelectorAll("a")].map((link) => [
    link.getAttribute("href"),
    link.getAttribute("aria-label"),
    link.getAttribute("aria-current"),
  ]);
const ringed = (bar) =>
  [...bar.querySelectorAll("a")].map((link) =>
    link.className.includes("ring-2"),
  );

it("opens the invoices owed, the overdue ones and the last 30 days' payments", () => {
  const bar = render({ query: answered(summary) });
  expect(links(bar)).toEqual([
    [
      "/documents?status=outstanding&sort=dueDate&dir=asc",
      "Outstanding: USD 24,285.79 on 10 invoices, USD 6,326.03 of it overdue. Open the invoices owed, soonest due first",
      null,
    ],
    [
      "/documents?status=overdue",
      "Overdue: USD 6,326.03 on 3 invoices. Open the overdue invoices",
      null,
    ],
    [
      "/payments?period=30d",
      "Received in the last 30 days: USD 22,763.97 from 7 payments. Open these payments",
      null,
    ],
  ]);
  for (const text of [
    "Outstanding24,285.79on 10 invoices",
    "Overdue6,326.03· 3 invoices",
    "Not due yet17,959.76· 7 invoices",
    "Received in the last 30 days22,763.977 payments",
  ]) {
    expect(bar.textContent).toContain(text);
  }
  // One currency, so the amounts don't name it.
  expect(bar.textContent).not.toContain("USD");
  // The bar is split by what is overdue and what is not due yet.
  expect(
    [...bar.querySelector("a").children].map((part) => part.style.flex),
  ).toEqual(["6326.03 1 0px", "17959.76 1 0px"]);
});

it("keeps the currency in its links and names it when the business has several", () => {
  const bar = render({
    query: answered({
      ...summary,
      currency: "EUR",
      currencies: ["USD", "EUR"],
    }),
  });
  expect(links(bar).map(([href]) => href)).toEqual([
    "/documents?status=outstanding&currency=EUR&sort=dueDate&dir=asc",
    "/documents?status=overdue&currency=EUR",
    "/payments?period=30d&currency=EUR",
  ]);
  expect(bar.textContent).toContain("OutstandingEUR24,285.79");
  expect(bar.textContent).toContain("Received in the last 30 daysEUR22,763.97");
});

it.each([
  ["/documents?status=outstanding&sort=dueDate&dir=asc", 0],
  ["/documents?status=overdue", 1],
  ["/payments?period=30d", 2],
])("rings only the part whose list is the one shown: %s", (shown, index) => {
  const bar = render({
    query: answered(summary),
    shows: (link) => link === shown,
  });
  expect(links(bar).map(([, , current]) => current)).toEqual(
    [0, 1, 2].map((part) => (part === index ? "true" : null)),
  );
  expect(ringed(bar)).toEqual([0, 1, 2].map((part) => part === index));
});

it("says so when nothing is owed or overdue", () => {
  const nothingLate = render({
    query: answered({
      ...summary,
      outstanding: { ...summary.outstanding, overdue: 0, overdueInvoices: 0 },
    }),
  });
  expect(links(nothingLate).map(([href]) => href)).toEqual([
    "/documents?status=outstanding&sort=dueDate&dir=asc",
    "/payments?period=30d",
  ]);
  expect(nothingLate.textContent).toContain("Nothing overdue");

  const nothingOwed = again({
    query: answered({
      ...summary,
      outstanding: {
        total: 0,
        overdue: 0,
        notDue: 0,
        invoices: 0,
        overdueInvoices: 0,
      },
    }),
  });
  expect(links(nothingOwed).map(([href]) => href)).toEqual([
    "/payments?period=30d",
  ]);
  expect(nothingOwed.textContent).toContain(
    "Outstanding0.00No unpaid invoices",
  );
  expect(nothingOwed.textContent).not.toContain("The bar opens");
});

it("keeps its room with empty cards while the figures load", () => {
  const loading = render({
    query: { currentData: undefined, isFetching: true, isError: false },
  });
  const placeholder = loading.firstElementChild;
  expect(placeholder.getAttribute("aria-hidden")).toBe("true");
  expect(placeholder.children).toHaveLength(2);
  expect(loading.querySelectorAll("a")).toHaveLength(0);
  expect(loading.querySelector('[role="alert"]')).toBeNull();
  // A business with nothing issued has no bar, once that is known.
  expect(
    again({ query: answered({ ...summary, currencies: [] }) }).textContent,
  ).toBe("");
});

it("says when the figures couldn't be loaded, and tries again", () => {
  const refetch = vi.fn();
  const failed = render({
    query: {
      currentData: undefined,
      isFetching: false,
      isError: true,
      refetch,
    },
  });
  const note = failed.querySelector('[role="alert"]');
  expect(note.textContent).toBe(
    "We couldn’t load what is owed and what came in.Try again",
  );
  act(() => note.querySelector("button").click());
  expect(refetch).toHaveBeenCalledTimes(1);
  // While it tries again, the cards keep the room.
  const retrying = again({
    query: { currentData: undefined, isFetching: true, isError: true, refetch },
  });
  expect(retrying.querySelector('[role="alert"]')).toBeNull();
  expect(retrying.firstElementChild.getAttribute("aria-hidden")).toBe("true");
});
