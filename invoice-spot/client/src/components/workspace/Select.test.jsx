import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { StrictMode, useState } from "react";
import { createRoot } from "react-dom/client";
import { act } from "react-dom/test-utils";
import { Dialog } from "./Dialog";
import { Select } from "./Select";

window.IS_REACT_ACT_ENVIRONMENT = true;

// jsdom has no modal dialogs; these stand in for the browser's.
HTMLDialogElement.prototype.showModal = function showModal() {
  this.open = true;
};
HTMLDialogElement.prototype.close = function close() {
  this.open = false;
};

let root;
let container;
beforeEach(() => {
  container = document.body.appendChild(document.createElement("div"));
  root = createRoot(container);
});
afterEach(() => {
  act(() => root.unmount());
  container.remove();
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
  window.innerWidth = 1024;
  window.innerHeight = 768;
});

const SORTS = [
  { value: "new", label: "Newest first" },
  { value: "old", label: "Oldest first" },
  { value: "due", label: "Due soonest" },
  { value: "late", label: "Due latest" },
  { value: "high", label: "Highest amount" },
  { value: "low", label: "Lowest amount" },
];
const ZONES = Array.from({ length: 20 }, (_, index) => ({
  value: `Zone/${index}`,
  label: `Zone ${index}`,
  meta: `UTC+${index}:00`,
}));
ZONES[7] = { value: "Asia/Kolkata", label: "Asia/Kolkata", meta: "UTC+5:30" };

// The control with its own value, as a form holds it.
const Harness = ({ initial = "new", onChange, ...props }) => {
  const [value, setValue] = useState(initial);
  return (
    <Select
      aria-label="Sort"
      size="toolbar"
      value={value}
      onChange={(next) => {
        setValue(next);
        onChange?.(next);
      }}
      options={SORTS}
      {...props}
    />
  );
};
const render = (element) => act(async () => root.render(element));
// Each shows a fresh control, whatever was rendered before.
let shown = 0;
const show = (props) => render(<Harness key={(shown += 1)} {...props} />);

const trigger = () => container.querySelector('[role="combobox"]');
const listbox = () => document.querySelector('[role="listbox"]');
const options = () => [...document.querySelectorAll('[role="option"]')];
const searchBox = () => document.querySelector('input[role="combobox"]');
// An option's accessible name: the words its aria-labelledby points at.
const nameOf = (option) =>
  option
    .getAttribute("aria-labelledby")
    .split(" ")
    .map((id) => document.getElementById(id).textContent)
    .join(" ");
const labels = () =>
  options().map((option) => option.firstChild.firstChild.textContent);
const activeLabel = (element = trigger()) =>
  document.getElementById(element.getAttribute("aria-activedescendant"))
    ?.firstChild.firstChild.textContent;
const click = (element) => act(async () => element.click());
const press = (element, key, init = {}) =>
  act(async () =>
    element.dispatchEvent(
      new KeyboardEvent("keydown", {
        key,
        bubbles: true,
        cancelable: true,
        ...init,
      }),
    ),
  );
const type = (element, text) =>
  act(async () => {
    const setter = Object.getOwnPropertyDescriptor(
      HTMLInputElement.prototype,
      "value",
    ).set;
    setter.call(element, text);
    element.dispatchEvent(new Event("input", { bubbles: true }));
  });

it("opens on a click, lists the options with the chosen one ticked, and closes on the next click", async () => {
  await show();
  const button = trigger();
  expect(button.getAttribute("aria-haspopup")).toBe("listbox");
  expect(button.getAttribute("aria-expanded")).toBe("false");
  expect(button.textContent).toContain("Newest first");
  expect(button.value).toBe("new");
  expect(listbox()).toBeNull();
  await click(button);
  expect(button.getAttribute("aria-expanded")).toBe("true");
  expect(button.getAttribute("aria-controls")).toBe(listbox().id);
  expect(labels()).toEqual(SORTS.map((option) => option.label));
  const [chosen, next] = options();
  expect(chosen.getAttribute("aria-selected")).toBe("true");
  expect(chosen.querySelector("svg")).not.toBeNull();
  expect(chosen.dataset.value).toBe("new");
  expect(next.getAttribute("aria-selected")).toBe("false");
  expect(next.querySelector("svg")).toBeNull();
  expect(activeLabel()).toBe("Newest first");
  // The menu is drawn at the app's root, in the workspace's styles, and
  // lifts into place.
  expect(listbox().closest(".dashboard-app").parentElement).toBe(document.body);
  expect(listbox().parentElement.className).toContain("animate-ov-lift");
  await click(button);
  expect(button.getAttribute("aria-expanded")).toBe("false");
  expect(listbox()).toBeNull();
});

it("chooses an option with the mouse, reports its value and gives focus back to the trigger", async () => {
  const onChange = vi.fn();
  await show({ onChange });
  await click(trigger());
  await act(async () =>
    options()[2].dispatchEvent(new MouseEvent("mousemove", { bubbles: true })),
  );
  expect(activeLabel()).toBe("Due soonest");
  await click(options()[2]);
  expect(onChange).toHaveBeenCalledWith("due");
  expect(listbox()).toBeNull();
  expect(trigger().textContent).toContain("Due soonest");
  expect(trigger().value).toBe("due");
  expect(document.activeElement).toBe(trigger());
  // Choosing the option already chosen reports nothing.
  await click(trigger());
  await click(options()[2]);
  expect(onChange).toHaveBeenCalledTimes(1);
});

it("opens on Enter, Space, the arrows, Home and End, with the arrows also moving", async () => {
  await show({ initial: "due" });
  const button = trigger();
  for (const [key, expected] of [
    ["Enter", "Due soonest"],
    [" ", "Due soonest"],
    ["ArrowDown", "Due latest"],
    ["ArrowUp", "Oldest first"],
    ["Home", "Newest first"],
    ["End", "Lowest amount"],
  ]) {
    await press(button, key);
    expect(button.getAttribute("aria-expanded")).toBe("true");
    expect(activeLabel()).toBe(expected);
    await press(button, "Escape");
    expect(listbox()).toBeNull();
  }
});

it("moves with the arrows, Home, End, PageUp and PageDown, skipping disabled options, and Enter chooses", async () => {
  const onChange = vi.fn();
  const sorts = SORTS.map((option) =>
    option.value === "late" ? { ...option, disabled: true } : option,
  );
  await show({ onChange, options: sorts, initial: "old" });
  const button = trigger();
  await press(button, "Enter");
  expect(activeLabel()).toBe("Oldest first");
  await press(button, "ArrowDown");
  expect(activeLabel()).toBe("Due soonest");
  await press(button, "ArrowDown");
  expect(activeLabel()).toBe("Highest amount");
  await press(button, "ArrowUp");
  expect(activeLabel()).toBe("Due soonest");
  await press(button, "End");
  expect(activeLabel()).toBe("Lowest amount");
  await press(button, "ArrowDown");
  expect(activeLabel()).toBe("Lowest amount");
  await press(button, "Home");
  expect(activeLabel()).toBe("Newest first");
  await press(button, "ArrowUp");
  expect(activeLabel()).toBe("Newest first");
  await press(button, "PageDown");
  expect(activeLabel()).toBe("Lowest amount");
  await press(button, "PageUp");
  expect(activeLabel()).toBe("Newest first");
  await press(button, "ArrowDown");
  await press(button, "ArrowDown");
  await press(button, "Enter");
  expect(onChange).toHaveBeenCalledWith("due");
  expect(listbox()).toBeNull();
  // A disabled option can't be chosen with the mouse either.
  await click(button);
  expect(options()[3].getAttribute("aria-disabled")).toBe("true");
  await click(options()[3]);
  expect(listbox()).not.toBeNull();
  expect(onChange).toHaveBeenCalledTimes(1);
});

it("keeps the old value on Esc and lets Tab move on, closing either way", async () => {
  const onChange = vi.fn();
  await render(
    <>
      <Harness onChange={onChange} />
      <button type="button">After</button>
    </>,
  );
  const button = trigger();
  await press(button, "ArrowDown");
  expect(activeLabel()).toBe("Oldest first");
  await press(button, "Escape");
  expect(listbox()).toBeNull();
  expect(onChange).not.toHaveBeenCalled();
  expect(button.value).toBe("new");
  expect(document.activeElement).toBe(button);
  await press(button, "ArrowDown");
  const tab = new KeyboardEvent("keydown", {
    key: "Tab",
    bubbles: true,
    cancelable: true,
  });
  await act(async () => button.dispatchEvent(tab));
  expect(tab.defaultPrevented).toBe(false);
  expect(listbox()).toBeNull();
  expect(onChange).not.toHaveBeenCalled();
});

it("closes on a click outside without taking focus, and does not open when disabled", async () => {
  await render(
    <>
      <Harness />
      <button type="button">Elsewhere</button>
    </>,
  );
  const elsewhere = container.querySelectorAll("button")[1];
  await click(trigger());
  expect(listbox()).not.toBeNull();
  await act(async () => {
    elsewhere.focus();
    elsewhere.dispatchEvent(new MouseEvent("mousedown", { bubbles: true }));
  });
  expect(listbox()).toBeNull();
  expect(document.activeElement).toBe(elsewhere);
  await show({ disabled: true });
  await click(trigger());
  await press(trigger(), "Enter");
  expect(listbox()).toBeNull();
});

it("jumps to the option starting with the letters typed within half a second", async () => {
  let now = 1000;
  vi.spyOn(Date, "now").mockImplementation(() => now);
  await show();
  const button = trigger();
  // Typing with the menu closed opens it at the match.
  await press(button, "o");
  expect(listbox()).not.toBeNull();
  expect(activeLabel()).toBe("Oldest first");
  // The letter that opened the menu is still in the word for half a
  // second; after that, a new word starts.
  now += 600;
  await press(button, "d");
  expect(activeLabel()).toBe("Due soonest");
  now += 100;
  // The same letter again goes on to the next option with it.
  await press(button, "d");
  expect(activeLabel()).toBe("Due latest");
  now += 600;
  await press(button, "l");
  expect(activeLabel()).toBe("Lowest amount");
  now += 100;
  await press(button, "o");
  expect(activeLabel()).toBe("Lowest amount");
  now += 600;
  await press(button, "h");
  expect(activeLabel()).toBe("Highest amount");
  // The trigger's value only changes on Enter.
  expect(button.value).toBe("new");
  await press(button, "Enter");
  expect(button.value).toBe("high");
});

it("keeps the letters typed on the closed trigger together", async () => {
  let now = 1000;
  vi.spyOn(Date, "now").mockImplementation(() => now);
  await show({ options: [...SORTS, { value: "30d", label: "Last 30 days" }] });
  const button = trigger();
  await press(button, "l");
  expect(activeLabel()).toBe("Lowest amount");
  now += 100;
  await press(button, "a");
  expect(activeLabel()).toBe("Last 30 days");
});

it("moves ten at a time with PageDown and PageUp, stopping at the ends", async () => {
  await show({ options: ZONES, searchable: false, initial: "Zone/3" });
  const button = trigger();
  await press(button, "Enter");
  expect(activeLabel()).toBe("Zone 3");
  await press(button, "PageDown");
  expect(activeLabel()).toBe("Zone 13");
  await press(button, "PageDown");
  expect(activeLabel()).toBe("Zone 19");
  await press(button, "PageUp");
  expect(activeLabel()).toBe("Zone 9");
  await press(button, "PageUp");
  expect(activeLabel()).toBe("Zone 0");
});

it("searches a long list on label, description and meta, counts the matches, and says when nothing matches", async () => {
  const onChange = vi.fn();
  await show({ initial: "Zone/3", options: ZONES, onChange });
  const button = trigger();
  await click(button);
  const search = searchBox();
  expect(document.activeElement).toBe(search);
  expect(button.getAttribute("aria-expanded")).toBe("true");
  // While the search has focus it is the combobox, with the active option.
  expect(search.getAttribute("aria-expanded")).toBe("true");
  expect(search.getAttribute("aria-haspopup")).toBe("listbox");
  expect(search.getAttribute("aria-controls")).toBe(listbox().id);
  expect(button.hasAttribute("aria-activedescendant")).toBe(false);
  expect(labels()).toHaveLength(20);
  expect(activeLabel(search)).toBe("Zone 3");
  const footer = document.querySelector('[role="status"]');
  expect(footer.textContent).toBe("20 options");
  await type(search, "kol");
  expect(labels()).toEqual(["Asia/Kolkata"]);
  expect(options()[0].textContent).toContain("UTC+5:30");
  expect(nameOf(options()[0])).toBe("Asia/Kolkata UTC+5:30");
  expect(footer.textContent).toBe("1 of 20 match");
  expect(activeLabel(search)).toBe("Asia/Kolkata");
  await type(search, "+1");
  expect(labels()).toEqual([
    "Zone 1",
    "Zone 10",
    "Zone 11",
    "Zone 12",
    "Zone 13",
    "Zone 14",
    "Zone 15",
    "Zone 16",
    "Zone 17",
    "Zone 18",
    "Zone 19",
  ]);
  expect(footer.textContent).toBe("11 of 20 match");
  await type(search, "zzz");
  expect(options()).toEqual([]);
  expect(listbox().textContent).toBe("Nothing matches");
  expect(footer.textContent).toBe("0 of 20 match");
  await press(search, "Enter");
  expect(onChange).not.toHaveBeenCalled();
  await type(search, "zone 1");
  await press(search, "ArrowDown");
  expect(activeLabel(search)).toBe("Zone 10");
  await press(search, "Enter");
  expect(onChange).toHaveBeenCalledWith("Zone/10");
  expect(listbox()).toBeNull();
  expect(document.activeElement).toBe(button);
  // A letter typed on the closed trigger opens the search with it, and
  // its first match active, so Enter chooses it.
  await press(button, "k");
  expect(searchBox().value).toBe("k");
  expect(labels()).toEqual(["Asia/Kolkata"]);
  expect(activeLabel(searchBox())).toBe("Asia/Kolkata");
  await press(searchBox(), "Enter");
  expect(onChange).toHaveBeenLastCalledWith("Asia/Kolkata");
});

it("closes on Tab from the search and lets focus go on from the trigger", async () => {
  await show({ options: ZONES, initial: "Zone/3" });
  await click(trigger());
  expect(document.activeElement).toBe(searchBox());
  const tab = new KeyboardEvent("keydown", {
    key: "Tab",
    bubbles: true,
    cancelable: true,
  });
  await act(async () => searchBox().dispatchEvent(tab));
  expect(tab.defaultPrevented).toBe(false);
  expect(listbox()).toBeNull();
  expect(document.activeElement).toBe(trigger());
});

it("can be told to search a short list or not to search a long one", async () => {
  await show({ searchable: true });
  await click(trigger());
  expect(searchBox()).not.toBeNull();
  await show({ options: ZONES, searchable: false, initial: "Zone/1" });
  await click(trigger());
  expect(searchBox()).toBeNull();
  expect(document.querySelector('[role="status"]')).toBeNull();
});

it("scrolls the chosen option into view when it opens and the active one as it moves", async () => {
  const scrolled = [];
  Element.prototype.scrollIntoView = vi.fn(function scrollIntoView(where) {
    scrolled.push([this.dataset.value, where.block]);
  });
  await show({ initial: "high" });
  await press(trigger(), "Enter");
  expect(scrolled).toEqual([["high", "nearest"]]);
  await press(trigger(), "ArrowDown");
  expect(scrolled.at(-1)).toEqual(["low", "nearest"]);
  delete Element.prototype.scrollIntoView;
});

it("hangs under the trigger as wide as it, and flips above when there is more room there", async () => {
  // The menu measures 200 by 300; the trigger's box is set per case.
  vi.spyOn(Element.prototype, "getBoundingClientRect").mockReturnValue({
    width: 199.6,
    height: 299.5,
  });
  await show();
  const button = trigger();
  let rect = { top: 100, bottom: 144, left: 30, width: 170, height: 44 };
  button.getBoundingClientRect = () => rect;
  await click(button);
  let menu = listbox().parentElement;
  expect(menu.style.top).toBe("148px");
  expect(menu.style.left).toBe("30px");
  expect(menu.style.width).toBe("200px");
  expect(menu.style.getPropertyValue("--lift")).toBe("4px");
  await press(button, "Escape");
  rect = { top: 700, bottom: 744, left: 900, width: 260, height: 44 };
  await click(button);
  menu = listbox().parentElement;
  expect(menu.style.top).toBe("396px");
  expect(menu.style.width).toBe("260px");
  // Kept clear of the window's right edge.
  expect(menu.style.left).toBe("756px");
  expect(menu.style.getPropertyValue("--lift")).toBe("-4px");
  // It follows the trigger as the page scrolls.
  rect = { top: 600, bottom: 644, left: 900, width: 260, height: 44 };
  await act(async () => window.dispatchEvent(new Event("scroll")));
  expect(menu.style.top).toBe("296px");
});

it("keeps a hidden input with the value when it has a name, and the form's required check", async () => {
  await render(
    <form>
      <Harness name="sort" />
    </form>,
  );
  const hidden = container.querySelector('input[name="sort"]');
  expect(hidden.type).toBe("hidden");
  expect(hidden.value).toBe("new");
  await click(trigger());
  await click(options()[1]);
  expect(hidden.value).toBe("old");
  await render(
    <form>
      <Harness key="required" name="sort" initial="" required />
    </form>,
  );
  const button = trigger();
  expect(button.getAttribute("aria-required")).toBe("true");
  expect(button.textContent).toContain("Choose…");
  expect(container.querySelector("form").checkValidity()).toBe(false);
  expect(button.getAttribute("aria-invalid")).toBeNull();
  const checked = container.querySelector('input[name="sort"]');
  // The browser's message focuses this input, so it is named like the
  // control and not hidden from a screen reader.
  expect(checked.getAttribute("aria-label")).toBe("Sort");
  expect(checked.hasAttribute("aria-hidden")).toBe(false);
  expect(checked.tabIndex).toBe(-1);
  await act(async () =>
    checked.dispatchEvent(new Event("invalid", { bubbles: false })),
  );
  expect(button.getAttribute("aria-invalid")).toBe("true");
  await click(button);
  await click(options()[0]);
  expect(button.getAttribute("aria-invalid")).toBeNull();
  expect(container.querySelector("form").checkValidity()).toBe(true);
  await render(
    <>
      <span id="sort-label">Sort by</span>
      <Select
        aria-labelledby="sort-label"
        required
        value=""
        onChange={() => {}}
        options={SORTS}
      />
    </>,
  );
  const named = container.querySelector("input[required]");
  expect(named.getAttribute("aria-labelledby")).toBe("sort-label");
  expect(named.hasAttribute("aria-label")).toBe(false);
});

it("sends a key pressed on the required input on to the trigger, which opens", async () => {
  await render(
    <form>
      <Harness name="sort" initial="" required />
    </form>,
  );
  const checked = container.querySelector('input[name="sort"]');
  await act(async () => checked.focus());
  expect(document.activeElement).toBe(checked);
  await press(checked, "Enter");
  expect(document.activeElement).toBe(trigger());
  expect(listbox()).not.toBeNull();
  expect(activeLabel()).toBe("Newest first");
});

it("draws its menu inside the open dialog it sits in, and a press on the backdrop closes the menu alone", async () => {
  await render(
    <dialog open>
      <Harness />
    </dialog>,
  );
  await click(trigger());
  expect(listbox().closest("dialog")).toBe(container.querySelector("dialog"));
  const onClose = vi.fn();
  await render(
    <Dialog open title="Record a payment" onClose={onClose}>
      <Harness key="in-dialog" />
    </Dialog>,
  );
  const dialog = container.querySelector("dialog");
  await click(trigger());
  expect(listbox().closest("dialog")).toBe(dialog);
  await act(async () => {
    dialog.dispatchEvent(new MouseEvent("mousedown", { bubbles: true }));
    dialog.dispatchEvent(new MouseEvent("click", { bubbles: true }));
  });
  expect(listbox()).toBeNull();
  expect(onClose).not.toHaveBeenCalled();
  // With the menu closed, the backdrop closes the dialog as before.
  await act(async () =>
    dialog.dispatchEvent(new MouseEvent("click", { bubbles: true })),
  );
  expect(onClose).toHaveBeenCalledTimes(1);
});

it("ends closed when its label is clicked while it is open", async () => {
  await render(
    <label>
      <span>Sort documents</span>
      <Harness aria-label={undefined} />
    </label>,
  );
  const words = container.querySelector("label > span");
  await click(trigger());
  expect(listbox()).not.toBeNull();
  // The press lands on the label, then the label passes a click to the
  // trigger.
  await act(async () =>
    words.dispatchEvent(new MouseEvent("mousedown", { bubbles: true })),
  );
  expect(listbox()).not.toBeNull();
  await click(words);
  expect(listbox()).toBeNull();
  expect(trigger().getAttribute("aria-expanded")).toBe("false");
});

it("works the same under StrictMode", async () => {
  const onChange = vi.fn();
  await render(
    <StrictMode>
      <Harness onChange={onChange} />
    </StrictMode>,
  );
  await click(trigger());
  expect(document.querySelectorAll('[role="listbox"]')).toHaveLength(1);
  await click(options()[1]);
  expect(onChange).toHaveBeenCalledWith("old");
  expect(listbox()).toBeNull();
  expect(document.activeElement).toBe(trigger());
});

it("opens as a sheet on a coarse pointer, titled by its label, and gives focus back when done", async () => {
  vi.stubGlobal(
    "matchMedia",
    vi.fn((query) => ({ matches: query === "(pointer: coarse)" })),
  );
  const onChange = vi.fn();
  await render(
    <label>
      <span>Sort documents</span>
      <Harness aria-label={undefined} onChange={onChange} />
    </label>,
  );
  const button = trigger();
  await click(button);
  const sheet = document.querySelector("dialog");
  expect(sheet.open).toBe(true);
  expect(sheet.getAttribute("aria-label")).toBe("Sort documents");
  expect(sheet.textContent).toContain("Done");
  expect(button.getAttribute("aria-expanded")).toBe("true");
  expect(button.hasAttribute("aria-activedescendant")).toBe(false);
  const list = listbox();
  expect(document.activeElement).toBe(list);
  expect(options()[0].className).toContain("min-h-11");
  await press(list, "ArrowDown");
  expect(activeLabel(list)).toBe("Oldest first");
  await press(list, "Enter");
  expect(onChange).toHaveBeenCalledWith("old");
  expect(document.querySelector("dialog")).toBeNull();
  expect(document.activeElement).toBe(button);
  await click(button);
  await click(
    [...document.querySelectorAll("dialog button")].find(
      (element) => element.textContent === "Done",
    ),
  );
  expect(document.querySelector("dialog")).toBeNull();
  expect(onChange).toHaveBeenCalledTimes(1);
  expect(document.activeElement).toBe(button);
  // A narrow window gets the sheet too, with the search at the top.
  vi.unstubAllGlobals();
  window.innerWidth = 390;
  await show({ options: ZONES, initial: "Zone/1" });
  await click(trigger());
  expect(document.querySelector("dialog input[role=combobox]")).toBe(
    document.activeElement,
  );
  await press(document.activeElement, "Escape");
  expect(document.querySelector("dialog")).toBeNull();
});

it("appears without the lift under reduced motion", async () => {
  vi.stubGlobal(
    "matchMedia",
    vi.fn((query) => ({
      matches: query === "(prefers-reduced-motion: reduce)",
    })),
  );
  await show();
  await click(trigger());
  expect(listbox().parentElement.className).not.toContain("animate-");
});

it("takes option and optgroup children, with group headings, and shows a muted placeholder", async () => {
  await render(
    <Select aria-label="Period" value="" onChange={() => {}}>
      <option value="">All dates</option>
      <option>Last 30 days</option>
      <optgroup label="Calendar">
        <option value="this-month">This month</option>
        <option value="last-month" disabled>
          Last month
        </option>
      </optgroup>
    </Select>,
  );
  await click(trigger());
  expect(labels()).toEqual([
    "All dates",
    "Last 30 days",
    "This month",
    "Last month",
  ]);
  expect(options().map((option) => option.dataset.value)).toEqual([
    "",
    "Last 30 days",
    "this-month",
    "last-month",
  ]);
  const heading = document.querySelector('[role="presentation"]');
  expect(heading.textContent).toBe("Calendar");
  expect(heading.nextElementSibling.dataset.value).toBe("this-month");
  expect(options()[3].getAttribute("aria-disabled")).toBe("true");
  await render(
    <Select
      aria-label="Method"
      value=""
      placeholder="Choose a method"
      onChange={() => {}}
    >
      <option>Cash</option>
    </Select>,
  );
  const shown = trigger().firstChild.firstChild;
  expect(shown.textContent).toBe("Choose a method");
  expect(shown.className).toContain("text-spot-muted");
});

it("describes options inside the menu and shows their meta at the right", async () => {
  await render(
    <Select
      aria-label="Template"
      value="MODERN"
      onChange={() => {}}
      options={[
        {
          value: "CLASSIC",
          label: "Classic",
          description: "The layout so far.",
        },
        { value: "MODERN", label: "Modern", description: "A tinted band." },
      ]}
    />,
  );
  await click(trigger());
  const [classic, modern] = options();
  expect(classic.className).toContain("min-h-[52px]");
  const note = document.getElementById(
    classic.getAttribute("aria-describedby"),
  );
  expect(classic.contains(note)).toBe(true);
  expect(note.textContent).toBe("The layout so far.");
  expect(nameOf(classic)).toBe("Classic");
  expect(
    document
      .getElementById(classic.getAttribute("aria-labelledby"))
      .closest('[role="option"]'),
  ).toBe(classic);
  expect(modern.getAttribute("aria-selected")).toBe("true");
  expect(modern.className).toContain("font-medium");
});

it("is as wide as its longest option in a toolbar unless told to fit the chosen one", async () => {
  await show();
  const sizer = trigger().querySelector('[aria-hidden="true"].invisible');
  expect(sizer.textContent).toBe(SORTS.map((option) => option.label).join(""));
  await show({ fit: "content" });
  expect(trigger().querySelector('[aria-hidden="true"].invisible')).toBeNull();
  await show({ size: "form" });
  expect(trigger().querySelector('[aria-hidden="true"].invisible')).toBeNull();
  expect(trigger().parentElement.className).toContain("block w-full");
});
