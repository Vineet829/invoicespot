import { afterEach, expect, it, vi } from "vitest";
import { createRoot } from "react-dom/client";
import { act } from "react-dom/test-utils";
import ErrorBoundary from "./ErrorBoundary";

window.IS_REACT_ACT_ENVIRONMENT = true;

afterEach(() => vi.restoreAllMocks());

const Broken = () => {
  throw new Error("the page broke");
};

it("shows a way back instead of a blank screen when a page fails to draw", () => {
  vi.spyOn(console, "error").mockImplementation(() => {});
  const container = document.createElement("div");
  act(() => {
    createRoot(container).render(
      <ErrorBoundary>
        <Broken />
      </ErrorBoundary>,
    );
  });
  expect(container.textContent).toContain("Something went wrong.");
  expect(
    [...container.querySelectorAll("button")].map(
      (button) => button.textContent,
    ),
  ).toEqual(["Reload the page", "Sign in again"]);
});

it("draws the page as it is when nothing fails", () => {
  const container = document.createElement("div");
  act(() => {
    createRoot(container).render(
      <ErrorBoundary>
        <p>All good</p>
      </ErrorBoundary>,
    );
  });
  expect(container.textContent).toBe("All good");
});
