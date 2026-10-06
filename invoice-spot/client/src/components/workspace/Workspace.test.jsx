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

// A date field draws the calendar, named by its label and the day it
// shows, described by its hint, and reports a day as a change event.
it("draws a date field as the calendar and reports days as changes", async () => {
  window.IS_REACT_ACT_ENVIRONMENT = true;
  const { createRoot } = await import("react-dom/client");
  const { act } = await import("react-dom/test-utils");
  const { Field } = await import("./Workspace");
  const container = document.body.appendChild(document.createElement("div"));
  const root = createRoot(container);
  const changes = [];
  await act(async () =>
    root.render(
      <Field
        label="Due date"
        type="date"
        name="dueDate"
        value="2026-10-15"
        required
        hint="When it falls due."
        onChange={(event) => changes.push(event.target)}
      />,
    ),
  );
  const label = container.querySelector("label");
  const button = container.querySelector('[role="combobox"]');
  expect(label.htmlFor).toBe(button.id);
  expect(button.getAttribute("aria-haspopup")).toBe("dialog");
  expect(button.getAttribute("aria-labelledby")).toBe(
    `${label.id} ${button.id}-value`,
  );
  expect(document.getElementById(`${button.id}-value`).textContent).toBe(
    "Thu, Oct 15, 2026",
  );
  expect(
    document.getElementById(button.getAttribute("aria-describedby"))
      .textContent,
  ).toBe("When it falls due.");
  const input = container.querySelector('input[type="date"]');
  expect([input.name, input.value, input.required]).toEqual([
    "dueDate",
    "2026-10-15",
    true,
  ]);
  await act(async () => button.click());
  await act(async () =>
    document.querySelector('[data-day="2026-10-20"]').click(),
  );
  expect(changes).toEqual([{ value: "2026-10-20", name: "dueDate" }]);
  act(() => root.unmount());
  container.remove();
});

// A month field draws the calendar's months, and reports a month as a
// change event.
it("draws a month field as the calendar's months and reports months as changes", async () => {
  window.IS_REACT_ACT_ENVIRONMENT = true;
  const { createRoot } = await import("react-dom/client");
  const { act } = await import("react-dom/test-utils");
  const { Field } = await import("./Workspace");
  const container = document.body.appendChild(document.createElement("div"));
  const root = createRoot(container);
  const changes = [];
  await act(async () =>
    root.render(
      <Field
        label="From"
        type="month"
        name="from"
        value="2026-09"
        max="2026-10"
        required
        onChange={(event) => changes.push(event.target)}
      />,
    ),
  );
  const button = container.querySelector('[role="combobox"]');
  expect(document.getElementById(`${button.id}-value`).textContent).toBe(
    "September 2026",
  );
  expect(container.querySelector('input[type="month"]').max).toBe("2026-10");
  await act(async () => button.click());
  expect(
    document.querySelector('[aria-label="Choose a month"]'),
  ).not.toBeNull();
  await act(async () => document.querySelector('[data-day="2026-08"]').click());
  expect(changes).toEqual([{ value: "2026-08", name: "from" }]);
  act(() => root.unmount());
  container.remove();
});
