import { afterEach, expect, it } from "vitest";
import { createRoot } from "react-dom/client";
import { act } from "react-dom/test-utils";
import AuthVisual from "./AuthVisual";

window.IS_REACT_ACT_ENVIRONMENT = true;

let root;
let container;
afterEach(() => {
  act(() => root.unmount());
  container.remove();
});

it("shows the app's own address in the picture's address bar", () => {
  container = document.body.appendChild(document.createElement("div"));
  root = createRoot(container);
  act(() =>
    root.render(
      <AuthVisual
        headline={["Invoices", "made simple"]}
        subtext="Bill clients and get paid."
        loaderLabel="Loading"
      />,
    ),
  );
  expect(window.location.host).not.toBe("");
  expect(container.textContent).toContain(`${window.location.host}/dashboard`);
  expect(container.textContent).not.toContain("invoicespot.com");
});
