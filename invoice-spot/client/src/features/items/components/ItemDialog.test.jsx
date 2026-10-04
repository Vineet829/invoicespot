import { expect, it, vi } from "vitest";
import { createRoot } from "react-dom/client";
import { act } from "react-dom/test-utils";
import ItemDialog from "./ItemDialog";

// The dialog saves through these hooks, so it needs no store or server; the
// browser's modal is left out.
const idle = () => [vi.fn(), { isLoading: false }];
vi.mock("../itemsApiSlice", () => ({
  useCreateItemMutation: () => idle(),
  useUpdateItemMutation: () => idle(),
}));
vi.mock("../../settings/settingsApiSlice", () => ({
  useGetSettingsQuery: () => ({}),
}));
vi.mock("../../../components/workspace/Dialog", () => ({
  Dialog: ({ children }) => children,
}));

window.IS_REACT_ACT_ENVIRONMENT = true;

it("takes a tax rate with up to the 4 decimals the server keeps", () => {
  const container = document.createElement("div");
  const root = createRoot(container);
  act(() => {
    root.render(<ItemDialog open onClose={() => {}} />);
  });
  const label = [...container.querySelectorAll("label")].find((element) =>
    element.textContent.startsWith("Tax rate"),
  );
  const rate = container.querySelector(`[id="${label.htmlFor}"]`);
  rate.value = "1.23456";
  expect(rate.validity.stepMismatch).toBe(true);
  for (const value of ["1.2345", "12.50000"]) {
    rate.value = value;
    expect(rate.validity.valid).toBe(true);
  }
  act(() => root.unmount());
});
