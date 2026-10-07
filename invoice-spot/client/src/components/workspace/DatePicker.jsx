import { useEffect, useId, useLayoutEffect, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { FiCalendar, FiChevronLeft, FiChevronRight, FiX } from "react-icons/fi";
import {
  calendarCell,
  calendarNav,
  calendarPanel,
  calendarToday,
  selectSheet,
  selectTrigger,
} from "./pastel";

// The calendar sits this far from its field and keeps this much of the
// window's edge clear.
const GAP = 6;
const EDGE = 8;

const media = (query) => {
  try {
    return window.matchMedia(query).matches;
  } catch {
    return false;
  }
};
// A finger or a narrow window gets the sheet instead of the floating
// calendar, as the dropdowns do.
const usesSheet = () => media("(pointer: coarse)") || window.innerWidth < 700;

const pad = (number) => String(number).padStart(2, "0");
// A day travels as the yyyy-mm-dd text a native date field gives, and a
// month as yyyy-mm, as a native month field does.
const dayText = (date) =>
  `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
const monthText = (date) => `${date.getFullYear()}-${pad(date.getMonth() + 1)}`;
const parse = (text, monthly) => {
  const match = monthly
    ? /^(\d{4})-(\d{2})$/.exec(text || "")
    : /^(\d{4})-(\d{2})-(\d{2})$/.exec(text || "");
  if (!match) return null;
  const [, year, month, day = 1] = match.map(Number);
  return new Date(year, month - 1, day);
};
const startOfToday = () => {
  const now = new Date();
  return new Date(now.getFullYear(), now.getMonth(), now.getDate());
};
const addDays = (date, days) =>
  new Date(date.getFullYear(), date.getMonth(), date.getDate() + days);
// The same day in another month, or that month's last day when it is
// shorter.
const addMonths = (date, months) => {
  const last = new Date(
    date.getFullYear(),
    date.getMonth() + months + 1,
    0,
  ).getDate();
  return new Date(
    date.getFullYear(),
    date.getMonth() + months,
    Math.min(date.getDate(), last),
  );
};

const shownDay = new Intl.DateTimeFormat("en-US", {
  weekday: "short",
  month: "short",
  day: "numeric",
  year: "numeric",
});
const shownMonth = new Intl.DateTimeFormat("en-US", {
  month: "long",
  year: "numeric",
});
const spokenDay = new Intl.DateTimeFormat("en-US", { dateStyle: "full" });
const shortMonth = new Intl.DateTimeFormat("en-US", { month: "short" });
const WEEKDAYS = [
  ["Su", "Sunday"],
  ["Mo", "Monday"],
  ["Tu", "Tuesday"],
  ["We", "Wednesday"],
  ["Th", "Thursday"],
  ["Fr", "Friday"],
  ["Sa", "Saturday"],
];

// The weeks a month spans, from the Sunday on or before its first day to
// the Saturday on or after its last, so the calendar is only as tall as
// the month needs.
const weeksOf = (month) => {
  const first = new Date(month.getFullYear(), month.getMonth(), 1);
  const last = new Date(month.getFullYear(), month.getMonth() + 1, 0);
  const days = first.getDay() + last.getDate() + (6 - last.getDay());
  const start = addDays(first, -first.getDay());
  return Array.from({ length: days / 7 }, (_, week) =>
    Array.from({ length: 7 }, (_, day) => addDays(start, week * 7 + day)),
  );
};

// A date field drawn in the app's own style, in place of a native date or
// month input: a button showing the chosen day, or month with
// `type="month"`, that opens a calendar under it, or a sheet on a phone.
// It reports the choice as the text a native field would to `onChange`,
// keeps days outside `min` and `max` out of reach, lets an optional date
// be cleared, and keeps a hidden native field in step so forms check and
// read it as before.
export const DatePicker = ({
  id: providedId,
  type = "date",
  value,
  onChange,
  min,
  max,
  required,
  disabled,
  invalid,
  name,
  labelId,
  placeholder,
  "aria-describedby": describedBy,
  ...rest
}) => {
  const monthly = type === "month";
  const toText = monthly ? monthText : dayText;
  const generatedId = useId();
  const id = providedId || generatedId;
  const chosen = parse(value, monthly);
  const low = parse(min, monthly);
  const high = parse(max, monthly);
  const today = startOfToday();
  const now = monthly
    ? new Date(today.getFullYear(), today.getMonth(), 1)
    : today;
  const [open, setOpen] = useState(false);
  const [sheet, setSheet] = useState(false);
  // The day, or month, that keys move from and the calendar shows.
  const [focused, setFocused] = useState(null);
  const [placement, setPlacement] = useState(null);
  const [missing, setMissing] = useState(false);
  const trigger = useRef(null);
  const panel = useRef(null);
  const sheetRef = useRef(null);
  const wrapper = useRef(null);
  // Set when opening or the keys move the day, so focus goes to it; the
  // arrows in the header keep focus where it is.
  const followFocus = useRef(false);

  const allowed = (date) => (!low || date >= low) && (!high || date <= high);
  const clamp = (date) =>
    low && date < low ? low : high && date > high ? high : date;

  const close = (refocus = true) => {
    setOpen(false);
    setPlacement(null);
    if (refocus) trigger.current?.focus();
  };
  const openCalendar = () => {
    followFocus.current = true;
    setFocused(chosen || clamp(now));
    setSheet(usesSheet());
    setOpen(true);
  };
  const choose = (date) => {
    if (!allowed(date)) return;
    setMissing(false);
    onChange?.(toText(date));
    close();
  };
  // Keys move from the focused day: a day or a week either way, the start
  // or end of the week, and a month or, with Shift, a year; on months, one
  // or a row of three, and a year with the Page keys.
  const onGridKey = (event) => {
    const steps = monthly
      ? {
          ArrowLeft: () => addMonths(focused, -1),
          ArrowRight: () => addMonths(focused, 1),
          ArrowUp: () => addMonths(focused, -3),
          ArrowDown: () => addMonths(focused, 3),
          Home: () => new Date(focused.getFullYear(), 0, 1),
          End: () => new Date(focused.getFullYear(), 11, 1),
          PageUp: () => addMonths(focused, -12),
          PageDown: () => addMonths(focused, 12),
        }
      : {
          ArrowLeft: () => addDays(focused, -1),
          ArrowRight: () => addDays(focused, 1),
          ArrowUp: () => addDays(focused, -7),
          ArrowDown: () => addDays(focused, 7),
          Home: () => addDays(focused, -focused.getDay()),
          End: () => addDays(focused, 6 - focused.getDay()),
          PageUp: () => addMonths(focused, event.shiftKey ? -12 : -1),
          PageDown: () => addMonths(focused, event.shiftKey ? 12 : 1),
        };
    const step = steps[event.key];
    if (!step) return;
    event.preventDefault();
    followFocus.current = true;
    setFocused(step());
  };
  const showStep = (count) =>
    setFocused(addMonths(focused, monthly ? count * 12 : count));

  // The calendar hangs under its field, flipping above when there is more
  // room there, keeps inside the window, and follows the page as it
  // scrolls.
  const shownKey =
    focused && (monthly ? focused.getFullYear() : monthText(focused));
  useLayoutEffect(() => {
    if (!open || sheet) return undefined;
    const place = () => {
      const button = trigger.current;
      const box = panel.current;
      if (!button || !box) return;
      const rect = button.getBoundingClientRect();
      const { width, height } = box.getBoundingClientRect();
      const below = window.innerHeight - rect.bottom - GAP;
      const above = rect.top - GAP;
      const flip = height > below && above > below;
      const top = Math.max(
        EDGE,
        Math.min(
          flip ? rect.top - GAP - height : rect.bottom + GAP,
          window.innerHeight - height - EDGE,
        ),
      );
      const left = Math.max(
        EDGE,
        Math.min(rect.left, window.innerWidth - width - EDGE),
      );
      box.style.top = `${top}px`;
      box.style.left = `${left}px`;
      setPlacement((last) =>
        last && last.top === top && last.left === left ? last : { top, left },
      );
    };
    place();
    window.addEventListener("scroll", place, true);
    window.addEventListener("resize", place);
    return () => {
      window.removeEventListener("scroll", place, true);
      window.removeEventListener("resize", place);
    };
  }, [open, sheet, shownKey]);

  // Once the calendar is in place, the focused day takes focus, and keeps
  // it as keys move it. The sheet opens as a modal in the browser's top
  // layer.
  const placed = sheet || Boolean(placement);
  const focusedKey = focused && toText(focused);
  useEffect(() => {
    if (!open || !placed || !followFocus.current) return;
    followFocus.current = false;
    const box = sheet ? sheetRef.current : panel.current;
    box?.querySelector(`[data-day="${focusedKey}"]`)?.focus();
  }, [open, placed, sheet, focusedKey]);
  useEffect(() => {
    if (!open || !sheet) return undefined;
    const dialog = sheetRef.current;
    const overflow = document.body.style.overflow;
    if (dialog && !dialog.open) {
      dialog.showModal();
      document.body.style.overflow = "hidden";
    }
    return () => {
      if (dialog?.open) dialog.close();
      document.body.style.overflow = overflow;
    };
  }, [open, sheet]);

  // A press anywhere else closes the floating calendar and leaves the date
  // alone.
  useEffect(() => {
    if (!open || sheet) return undefined;
    const onPress = (event) => {
      if (
        wrapper.current?.contains(event.target) ||
        panel.current?.contains(event.target) ||
        [...(trigger.current?.labels || [])].some((element) =>
          element.contains(event.target),
        )
      )
        return;
      setOpen(false);
      setPlacement(null);
    };
    document.addEventListener("mousedown", onPress);
    return () => document.removeEventListener("mousedown", onPress);
  }, [open, sheet]);
  // Escape closes the floating calendar and goes back to the field, and
  // Tab keeps within it.
  const onPanelKey = (event) => {
    if (event.key === "Escape") {
      event.preventDefault();
      event.stopPropagation();
      close();
      return;
    }
    if (event.key !== "Tab") return;
    const stops = [
      ...panel.current.querySelectorAll('button:not([tabindex="-1"])'),
    ].filter((button) => !button.disabled);
    const index = stops.indexOf(document.activeElement);
    const next = event.shiftKey
      ? index <= 0
        ? stops.length - 1
        : index - 1
      : index === stops.length - 1
        ? 0
        : index + 1;
    event.preventDefault();
    stops[next]?.focus();
  };

  const titleId = `${id}-title`;
  const nowAllowed = allowed(now);
  const header = focused && (
    <div className="flex items-center justify-between gap-2">
      <h2
        id={titleId}
        aria-live="polite"
        className="m-0 pl-1 font-display text-[17px] font-light tracking-[-0.02em]"
      >
        {monthly ? focused.getFullYear() : shownMonth.format(focused)}
      </h2>
      <span className="flex items-center gap-0.5">
        <button
          type="button"
          disabled={!nowAllowed}
          className={calendarToday}
          onClick={() => choose(now)}
        >
          {monthly ? "This month" : "Today"}
        </button>
        <button
          type="button"
          aria-label={monthly ? "Previous year" : "Previous month"}
          className={calendarNav}
          onClick={() => showStep(-1)}
        >
          <FiChevronLeft aria-hidden="true" />
        </button>
        <button
          type="button"
          aria-label={monthly ? "Next year" : "Next month"}
          className={calendarNav}
          onClick={() => showStep(1)}
        >
          <FiChevronRight aria-hidden="true" />
        </button>
      </span>
    </div>
  );
  // One day, or month, of the calendar: faded and out of reach outside the
  // limits, ringed when it is today or this month, and filled when chosen.
  // Only the focused one is a stop for Tab; the keys move between the rest.
  const cell = (date, label, spoken, outside = false) => {
    const ok = allowed(date);
    const isChosen = Boolean(chosen) && toText(date) === toText(chosen);
    const isNow = toText(date) === toText(now);
    return (
      <button
        type="button"
        data-day={toText(date)}
        tabIndex={toText(date) === focusedKey ? 0 : -1}
        aria-label={spoken}
        aria-pressed={isChosen}
        aria-current={isNow ? "date" : undefined}
        aria-disabled={ok ? undefined : true}
        className={calendarCell({
          chosen: isChosen,
          now: isNow,
          ok,
          outside,
          big: sheet,
          monthly,
        })}
        onClick={() => choose(date)}
      >
        {label}
      </button>
    );
  };
  const grid =
    focused &&
    (monthly ? (
      <div
        role="group"
        aria-labelledby={titleId}
        className="grid grid-cols-3 gap-1.5 pt-1"
        onKeyDown={onGridKey}
      >
        {Array.from({ length: 12 }, (_, index) => {
          const date = new Date(focused.getFullYear(), index, 1);
          return (
            <span key={index} className="grid">
              {cell(date, shortMonth.format(date), shownMonth.format(date))}
            </span>
          );
        })}
      </div>
    ) : (
      <table
        aria-labelledby={titleId}
        className="w-full border-collapse"
        onKeyDown={onGridKey}
      >
        <thead>
          <tr>
            {WEEKDAYS.map(([short, day]) => (
              <th
                key={short}
                scope="col"
                abbr={day}
                className="pb-1 pt-0.5 text-center text-[12.5px] font-medium tracking-[0.04em] text-spot-muted"
              >
                {short}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {weeksOf(focused).map((week) => (
            <tr key={dayText(week[0])}>
              {week.map((date) => (
                <td key={dayText(date)} className="p-0 py-0.5 text-center">
                  {cell(
                    date,
                    date.getDate(),
                    spokenDay.format(date),
                    date.getMonth() !== focused.getMonth(),
                  )}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    ));
  const label = monthly ? "Choose a month" : "Choose a date";
  const calendar =
    open &&
    (sheet ? (
      <dialog
        ref={sheetRef}
        id={`${id}-calendar`}
        aria-label={label}
        className={selectSheet}
        onCancel={(event) => {
          event.preventDefault();
          close();
        }}
        onClick={(event) => {
          if (event.target === sheetRef.current) close();
        }}
      >
        <div className="flex flex-col gap-2 px-4 pb-[max(16px,env(safe-area-inset-bottom))] pt-2.5">
          <span
            aria-hidden="true"
            className="h-1 w-10 shrink-0 self-center rounded-full bg-[#2327352e]"
          />
          {header}
          {grid}
        </div>
      </dialog>
    ) : (
      <div
        ref={panel}
        id={`${id}-calendar`}
        role="dialog"
        aria-modal="true"
        aria-label={label}
        className={`fixed z-50 ${calendarPanel} ${monthly ? "w-[272px]" : "w-[292px]"} ${placement ? (media("(prefers-reduced-motion: reduce)") ? "" : "animate-ov-lift") : "invisible"}`}
        onKeyDown={onPanelKey}
      >
        {header}
        {grid}
      </div>
    ));

  const shown = chosen
    ? monthly
      ? shownMonth.format(chosen)
      : shownDay.format(chosen)
    : placeholder || (monthly ? "Pick a month" : "Pick a date");
  const clearable = !required && Boolean(chosen) && !disabled;
  return (
    <div ref={wrapper} className="relative block w-full">
      <button
        ref={trigger}
        id={id}
        type="button"
        role="combobox"
        aria-haspopup="dialog"
        aria-expanded={open}
        aria-controls={`${id}-calendar`}
        aria-labelledby={labelId ? `${labelId} ${id}-value` : undefined}
        aria-describedby={describedBy}
        aria-invalid={invalid || (missing && !chosen) ? true : undefined}
        disabled={disabled}
        className={`${selectTrigger.form} gap-2.5 ${clearable ? "pr-10" : "pr-3"}`}
        onClick={() => (open ? close() : openCalendar())}
        onKeyDown={(event) => {
          if (["ArrowDown", "ArrowUp"].includes(event.key) && !open) {
            event.preventDefault();
            openCalendar();
          }
        }}
        {...rest}
      >
        <FiCalendar aria-hidden="true" className="!text-spot-violet" />
        <span
          id={`${id}-value`}
          className={`flex-1 truncate ${chosen ? "" : "text-spot-muted"}`}
        >
          {shown}
        </span>
      </button>
      {clearable && (
        <button
          type="button"
          aria-label={monthly ? "Clear the month" : "Clear the date"}
          className="absolute right-2 top-1/2 grid size-7 -translate-y-1/2 place-items-center rounded-full bg-transparent text-spot-muted transition-colors hover:bg-[#2327350d] hover:text-spot-ink coarse:right-0 coarse:size-11"
          onClick={() => {
            onChange?.("");
            trigger.current?.focus();
          }}
        >
          <FiX aria-hidden="true" className="size-3.5" />
        </button>
      )}
      {/* The native field the form checks and reads: under the button, so
          the browser's message for a missing or out-of-range date shows
          there, and a change made to it directly is a choice too. */}
      <input
        type={type}
        tabIndex={-1}
        aria-hidden="true"
        className="pointer-events-none absolute inset-0 h-full w-full opacity-0"
        name={name}
        required={required}
        min={min}
        max={max}
        value={value || ""}
        disabled={disabled}
        onChange={(event) => onChange?.(event.target.value)}
        onInvalid={() => setMissing(true)}
      />
      {/* Drawn inside an open modal dialog when the field is in one, so it
          shows above the backdrop, and otherwise at the app's root, where
          the workspace styles apply. */}
      {calendar &&
        createPortal(
          <div className="dashboard-app">{calendar}</div>,
          trigger.current?.closest("dialog[open]") ||
            document.getElementById("root") ||
            document.body,
        )}
    </div>
  );
};

export default DatePicker;
