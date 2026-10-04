import { afterEach, expect, it, vi } from "vitest";
import { createRoot } from "react-dom/client";
import { act } from "react-dom/test-utils";
import EditDetailsDialog from "./EditDetailsDialog";

// The dialog saves through this hook, so it needs no store or server; the
// browser's modal is left out.
vi.mock("../documentsApiSlice", () => ({
  useUpdateDocMutation: () => [vi.fn(), { isLoading: false }],
}));
vi.mock("../../../components/workspace/Dialog", () => ({
  Dialog: ({ children }) => children,
}));

window.IS_REACT_ACT_ENVIRONMENT = true;

let root;
afterEach(() => act(() => root.unmount()));

// The due date field of an issued invoice dated 15 January.
const dueField = (dueDate) => {
  const container = document.createElement("div");
  root = createRoot(container);
  act(() => {
    root.render(
      <EditDetailsDialog
        open
        onClose={() => {}}
        document={{
          _id: "1",
          documentType: "Invoice",
          documentNumber: "INV-0001",
          issueDate: "2026-01-15",
          dueDate,
        }}
      />,
    );
  });
  return container.querySelector('input[type="date"]');
};

// A day picked in the field, as the browser sends it.
const pick = (input, day) =>
  act(() => {
    Object.getOwnPropertyDescriptor(
      HTMLInputElement.prototype,
      "value",
    ).set.call(input, day);
    input.dispatchEvent(new Event("input", { bubbles: true }));
  });

it("moves a due date no earlier than the day the invoice is dated", () => {
  const field = dueField("2026-02-14");
  pick(field, "2026-01-10");
  expect(field.min).toBe("2026-01-15");
  expect(field.validity.rangeUnderflow).toBe(true);
  pick(field, "2026-01-15");
  expect(field.validity.valid).toBe(true);
});

it("keeps a due date saved before the issue date, so the notes still save", () => {
  const field = dueField("2026-01-10");
  expect(field.validity.valid).toBe(true);
});
