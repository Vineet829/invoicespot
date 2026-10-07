import { afterEach, beforeEach, expect, it } from "vitest";
import { createRoot } from "react-dom/client";
import { act } from "react-dom/test-utils";
import IncomeChart from "./IncomeChart";

window.IS_REACT_ACT_ENVIRONMENT = true;

let root;
let container;
beforeEach(() => {
  container = document.body.appendChild(document.createElement("div"));
  root = createRoot(container);
});
afterEach(() => {
  act(() => root.unmount());
  container.remove();
});

const draw = (months) =>
  act(async () => root.render(<IncomeChart months={months} currency="USD" />));
const monthsShown = () => [...container.querySelectorAll('[role="img"]')];
const axis = () =>
  [...container.querySelector('[aria-hidden="true"]').children].map(
    (label) => label.textContent,
  );
const heights = (month) =>
  [...month.children].map((column) => parseFloat(column.style.height));
const card = () => container.querySelector(".shadow-float");
const press = (element, key) =>
  act(async () =>
    element.dispatchEvent(
      new KeyboardEvent("keydown", { key, bubbles: true, cancelable: true }),
    ),
  );

it("draws billed and received side by side for each month, each month named in words", async () => {
  await draw([
    { month: "2026-08", billed: 1000, received: 600 },
    { month: "2026-09", billed: 400, received: 900 },
  ]);
  expect(
    container.querySelector('[role="group"]').getAttribute("aria-label"),
  ).toBe("Billed and received by month");
  expect(
    monthsShown().map((month) => month.getAttribute("aria-label")),
  ).toEqual([
    "August 2026: billed 1,000.00, received 600.00",
    "September 2026: billed 400.00, received 900.00",
  ]);
  // The axis runs from zero to a tidy step above the largest figure, in
  // whole amounts.
  expect(axis()).toEqual(["1,000", "750", "500", "250", "0"]);
  // Billed first, in blue, then received, in violet, each as tall as its
  // share of the axis.
  const [august, september] = monthsShown();
  expect(
    [...august.children].map(
      (column) => column.className.match(/bg-card-\w+-bar/)[0],
    ),
  ).toEqual(["bg-card-blue-bar", "bg-card-lilac-bar"]);
  expect(heights(august)).toEqual([100, 60]);
  expect(heights(september)).toEqual([40, 90]);
  // Month names under the columns, the year under the first.
  expect(container.textContent).toContain("Aug2026Sep");
});

it("shows a month's figures on hover or focus while the others fade, and the keys move between months", async () => {
  await draw(
    ["2026-07", "2026-08", "2026-09"].map((month, index) => ({
      month,
      billed: (index + 1) * 100,
      received: index * 50,
    })),
  );
  const [july, august, september] = monthsShown();
  // The latest month is the one stop for Tab.
  expect(monthsShown().map((month) => month.tabIndex)).toEqual([-1, -1, 0]);
  await act(async () =>
    august.dispatchEvent(new MouseEvent("mouseover", { bubbles: true })),
  );
  expect(card().textContent).toBe("August 2026Billed200.00Received50.00");
  expect(card().getAttribute("aria-hidden")).toBe("true");
  expect(august.className).toContain("bg-spot-violet");
  expect(july.firstElementChild.className).toContain("opacity-40");
  expect(august.firstElementChild.className).not.toContain("opacity-40");
  await act(async () =>
    august.parentElement.parentElement.dispatchEvent(
      new MouseEvent("mouseout", {
        bubbles: true,
        relatedTarget: document.body,
      }),
    ),
  );
  expect(card()).toBeNull();
  expect(july.firstElementChild.className).not.toContain("opacity-40");
  await act(async () => september.focus());
  expect(card().textContent).toContain("September 2026");
  await press(september, "ArrowLeft");
  expect(document.activeElement).toBe(august);
  await press(august, "Home");
  expect(document.activeElement).toBe(july);
  await press(july, "ArrowLeft");
  expect(document.activeElement).toBe(july);
  await press(july, "End");
  expect(document.activeElement).toBe(september);
});

it("draws only its zero line when nothing was billed or received", async () => {
  await draw([{ month: "2026-09", billed: 0, received: 0 }]);
  expect(axis()).toEqual(["0"]);
  expect(heights(monthsShown()[0])).toEqual([0, 0]);
});
