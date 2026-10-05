import { afterAll, beforeAll, expect, it } from "vitest";
import { dateInput, dateLabel } from "./Workspace";

// West of UTC, where a day read as UTC midnight falls on the day before.
const zone = process.env.TZ;
beforeAll(() => {
  process.env.TZ = "America/Los_Angeles";
});
afterAll(() => {
  if (zone === undefined) delete process.env.TZ;
  else process.env.TZ = zone;
});

it("reads a due date sent as a day as that day, in any time zone", () => {
  expect(dateInput("2026-10-15")).toBe("2026-10-15");
  expect(dateLabel("2026-10-15")).toBe("Oct 15, 2026");
});

it("gives the local day of a moment", () => {
  expect(dateInput(new Date(2026, 9, 15, 23, 30).getTime())).toBe("2026-10-15");
});

// A select field draws the styled dropdown, labelled and described like
// any other field, and reports a choice as a change event.
it("draws a select field as the styled dropdown and reports choices as changes", async () => {
  window.IS_REACT_ACT_ENVIRONMENT = true;
  const { createRoot } = await import("react-dom/client");
  const { act } = await import("react-dom/test-utils");
  const { Field } = await import("./Workspace");
  const container = document.body.appendChild(document.createElement("div"));
  const root = createRoot(container);
  const changes = [];
  const draw = (error) =>
    act(async () =>
      root.render(
        <Field
          label="Role"
          as="select"
          name="role"
          value="MEMBER"
          required
          hint="Who can do what."
          error={error}
          onChange={(event) => changes.push(event.target)}
        >
          <option value="ADMIN">Admin</option>
          <option value="MEMBER">Member</option>
        </Field>,
      ),
    );
  await draw();
  expect(container.querySelector("select")).toBeNull();
  const label = container.querySelector("label");
  const button = container.querySelector('[role="combobox"]');
  expect(label.htmlFor).toBe(button.id);
  expect(label.textContent).toBe("Role *");
  expect(button.textContent).toContain("Member");
  expect(button.value).toBe("MEMBER");
  expect(container.querySelector('input[name="role"]').value).toBe("MEMBER");
  expect(
    document.getElementById(button.getAttribute("aria-describedby"))
      .textContent,
  ).toBe("Who can do what.");
  expect(button.getAttribute("aria-invalid")).toBeNull();
  await act(async () => button.click());
  await act(async () =>
    document.querySelector('[role="option"][data-value="ADMIN"]').click(),
  );
  expect(changes).toEqual([{ value: "ADMIN", name: "role" }]);
  await draw("Choose a role.");
  expect(button.getAttribute("aria-invalid")).toBe("true");
  expect(
    document.getElementById(button.getAttribute("aria-describedby"))
      .textContent,
  ).toBe("Choose a role.");
  act(() => root.unmount());
  container.remove();
});
