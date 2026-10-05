import {
  Children,
  Fragment,
  isValidElement,
  useEffect,
  useId,
  useLayoutEffect,
  useRef,
  useState,
} from "react";
import { createPortal } from "react-dom";
import { FiCheck, FiChevronDown, FiSearch } from "react-icons/fi";
import {
  selectFooter,
  selectGroup,
  selectMenu,
  selectOption,
  selectOptionActive,
  selectSearch,
  selectSheet,
  selectSheetSearch,
  selectTrigger,
} from "./pastel";

// The menu sits this far from its trigger, grows to its longest option up to
// this width, keeps this much of the window's edge clear, and shows at most
// this much before scrolling.
const GAP = 4;
const MAX_WIDTH = 420;
const EDGE = 8;
const MAX_HEIGHT = 320;
// Letters typed within this time of each other make one type-ahead word.
const TYPE_AHEAD_MS = 500;
// Lists longer than this get a search box unless told otherwise.
const SEARCH_ABOVE = 12;

const media = (query) => {
  try {
    return window.matchMedia(query).matches;
  } catch {
    return false;
  }
};
// A finger or a narrow window gets the sheet instead of the floating menu.
const usesSheet = () => media("(pointer: coarse)") || window.innerWidth < 700;

const textOf = (children) =>
  Children.toArray(children)
    .filter((child) => typeof child === "string" || typeof child === "number")
    .join("");

// Options given as <option> and <optgroup> children, so call sites keep the
// markup they had with a native select.
const parseOptions = (children) => {
  const options = [];
  const add = (element, group) => {
    if (!isValidElement(element) || element.type !== "option") return;
    const label = textOf(element.props.children);
    options.push({
      value: String(element.props.value ?? label),
      label,
      disabled: Boolean(element.props.disabled),
      group,
    });
  };
  Children.forEach(children, (child) => {
    if (!isValidElement(child)) return;
    if (child.type === "optgroup") {
      Children.forEach(child.props.children, (option) =>
        add(option, child.props.label),
      );
    } else {
      add(child);
    }
  });
  return options;
};
// Values are compared and reported as strings, as a native select's are.
const normalize = (options) =>
  options.map((option, index) => ({
    ...option,
    index,
    value: String(option.value),
    label: option.label ?? String(option.value),
  }));

const matches = (option, term) =>
  [option.label, option.description, option.meta].some(
    (text) => text && String(text).toLowerCase().includes(term),
  );
const firstEnabled = (options, from = 0, step = 1) => {
  for (let i = from; i >= 0 && i < options.length; i += step) {
    if (!options[i].disabled) return i;
  }
  return -1;
};

// The text that names a control, for the sheet's title: a given label, or
// the label element around or pointing at the trigger, without the
// trigger's own words.
const labelText = (trigger, wrapper) => {
  const label = trigger?.labels?.[0];
  if (!label) return "";
  return [...label.childNodes]
    .filter((node) => !node.contains(wrapper))
    .map((node) => node.textContent)
    .join("")
    .replace(/\s*\*\s*$/, "")
    .trim();
};

// A dropdown drawn in the app's own style, in place of a native select: a
// combobox button that opens a listbox under it, or a sheet on a phone. It
// takes `options` ({ value, label, description?, meta?, disabled?, group? })
// or <option> children, reports the chosen value to `onChange`, and keeps a
// hidden input in sync when it has a `name`, so forms can read it. Lists
// longer than twelve get a search box.
export const Select = ({
  value,
  onChange,
  options: given,
  children,
  placeholder = "Choose…",
  size = "form",
  searchable: searchableGiven,
  label,
  id: givenId,
  name,
  disabled = false,
  invalid = false,
  required = false,
  className = "",
  autoFocus = false,
  fit = "longest",
  "aria-label": ariaLabel,
  "aria-labelledby": labelledBy,
  "aria-describedby": describedBy,
  ...rest
}) => {
  const generatedId = useId();
  const id = givenId || `${generatedId}select`;
  const listId = `${id}-list`;
  const optionId = (option) => `${id}-option-${option.index}`;
  const wrapper = useRef(null);
  const trigger = useRef(null);
  const menu = useRef(null);
  const sheetRef = useRef(null);
  const search = useRef(null);
  const typed = useRef({ text: "", at: 0 });
  const natural = useRef(null);
  const placedAt = useRef(null);
  const [open, setOpen] = useState(false);
  const [sheet, setSheet] = useState(false);
  const [query, setQuery] = useState("");
  const [active, setActive] = useState(-1);
  const [placement, setPlacement] = useState(null);
  const [missing, setMissing] = useState(false);
  const [title, setTitle] = useState("");

  const options = normalize(given || parseOptions(children));
  const current = String(value ?? "");
  const chosen = options.find((option) => option.value === current);
  const searchable = searchableGiven ?? options.length > SEARCH_ABOVE;
  const term = query.trim().toLowerCase();
  const shown =
    searchable && term
      ? options.filter((option) => matches(option, term))
      : options;
  const activeOption = open ? shown[active] : undefined;

  // Opens at the chosen option, or `step` options on from it, or at `at`;
  // a letter typed on a searchable control starts the search with it.
  const openMenu = ({ at, step = 0, typedChar = "" } = {}) => {
    if (disabled || open) return;
    let start;
    if (typedChar) {
      start = firstEnabled(
        options.filter((option) => matches(option, typedChar.toLowerCase())),
      );
    } else {
      const selected = options.findIndex((option) => option.value === current);
      start = at ?? (selected >= 0 ? selected : firstEnabled(options));
      if (step && start >= 0) {
        const next = firstEnabled(options, start + step, step);
        if (next >= 0) start = next;
      }
    }
    setSheet(usesSheet());
    setQuery(typedChar);
    setPlacement(null);
    natural.current = null;
    placedAt.current = null;
    setActive(start);
    setTitle(label || ariaLabel || labelText(trigger.current, wrapper.current));
    setOpen(true);
    // Safari leaves a clicked button unfocused; the keys need it focused.
    if (!searchable) trigger.current?.focus();
  };
  const close = (refocus = true) => {
    setOpen(false);
    setPlacement(null);
    typed.current = { text: "", at: 0 };
    if (refocus) trigger.current?.focus();
  };
  const choose = (option) => {
    if (!option || option.disabled) return;
    close();
    if (option.value !== current) onChange?.(option.value);
  };

  // Keys move through the visible options, skipping disabled ones and
  // stopping at the ends.
  const move = (step) => {
    if (!shown.length) return;
    let next = Math.min(Math.max(active + step, 0), shown.length - 1);
    const direction = step < 0 ? -1 : 1;
    while (next >= 0 && next < shown.length && shown[next].disabled)
      next += direction;
    if (next >= 0 && next < shown.length) setActive(next);
  };
  const jump = (toEnd) => {
    const next = toEnd
      ? firstEnabled(shown, shown.length - 1, -1)
      : firstEnabled(shown);
    if (next >= 0) setActive(next);
  };
  // Letters typed in quick succession find the next option starting with
  // them; the same letter again goes on to the next such option.
  const typeAhead = (char, from = active) => {
    const now = Date.now();
    const word =
      now - typed.current.at < TYPE_AHEAD_MS ? typed.current.text + char : char;
    typed.current = { text: word, at: now };
    const text = word.toLowerCase();
    const repeated = text.length > 1 && [...text].every((c) => c === text[0]);
    const needle = repeated ? text[0] : text;
    const start = text.length === 1 || repeated ? from + 1 : from;
    for (let n = 0; n < shown.length; n += 1) {
      const index = (start + n + shown.length) % shown.length;
      const option = shown[index];
      if (!option.disabled && option.label.toLowerCase().startsWith(needle))
        return index;
    }
    return -1;
  };
  const printable = (event) =>
    event.key.length === 1 && !event.ctrlKey && !event.metaKey && !event.altKey;

  const onKeyDown = (event) => {
    const { key } = event;
    if (!open) {
      const opener = {
        Enter: {},
        " ": {},
        ArrowDown: { step: 1 },
        ArrowUp: { step: -1 },
        Home: { at: firstEnabled(options) },
        End: { at: firstEnabled(options, options.length - 1, -1) },
      }[key];
      if (opener) {
        event.preventDefault();
        openMenu(opener);
      } else if (printable(event)) {
        event.preventDefault();
        if (searchable) {
          openMenu({ typedChar: key });
        } else {
          const selected = options.findIndex((o) => o.value === current);
          const found = typeAhead(key, selected);
          openMenu({ at: found >= 0 ? found : undefined });
        }
      }
      return;
    }
    const inSearch = event.target === search.current;
    const keys = {
      ArrowDown: () => move(1),
      ArrowUp: () => move(-1),
      Home: () => jump(false),
      End: () => jump(true),
      PageDown: () => move(10),
      PageUp: () => move(-10),
      Enter: () => choose(shown[active]),
      " ": inSearch ? undefined : () => choose(shown[active]),
      Escape: () => {
        event.stopPropagation();
        close();
      },
    };
    if (keys[key]) {
      event.preventDefault();
      keys[key]();
    } else if (key === "Tab") {
      // Focus goes on from the trigger, wherever it was.
      close();
    } else if (!inSearch && printable(event)) {
      event.preventDefault();
      const found = typeAhead(key);
      if (found >= 0) setActive(found);
    }
  };
  const onSearch = (event) => {
    const next = event.target.value;
    setQuery(next);
    const word = next.trim().toLowerCase();
    setActive(
      firstEnabled(
        word ? options.filter((option) => matches(option, word)) : options,
      ),
    );
  };

  useEffect(() => {
    if (autoFocus) trigger.current?.focus();
  }, [autoFocus]);

  // The floating menu hangs under the trigger, as wide as it and at least
  // as wide as its longest option, flipping above when there is less than
  // its height below and more above, and follows the page as it scrolls.
  useLayoutEffect(() => {
    if (!open || sheet) return undefined;
    const place = (event) => {
      const button = trigger.current;
      const panel = menu.current;
      // Scrolling inside the menu is not a reason to move it.
      if (!button || !panel) return;
      if (event?.target instanceof Node && panel.contains(event.target)) return;
      const rect = button.getBoundingClientRect();
      if (natural.current === null) {
        // The menu's own width, once per open: measured from the left
        // edge, so nothing cuts the longest option, over every option
        // however the list is filtered, and rounded up, so no label
        // loses a fraction of a pixel.
        panel.style.left = "0px";
        panel.style.width = "";
        natural.current = Math.ceil(panel.getBoundingClientRect().width);
      }
      const width = Math.max(rect.width, Math.min(natural.current, MAX_WIDTH));
      panel.style.width = `${width}px`;
      const height = Math.ceil(panel.getBoundingClientRect().height);
      const below = window.innerHeight - rect.bottom - GAP;
      const above = rect.top - GAP;
      const flip = height > below && above > below;
      const top = flip ? rect.top - GAP - height : rect.bottom + GAP;
      const left = Math.max(
        EDGE,
        Math.min(rect.left, window.innerWidth - width - EDGE),
      );
      panel.style.top = `${top}px`;
      panel.style.left = `${left}px`;
      const last = placedAt.current;
      if (
        last &&
        last.top === top &&
        last.left === left &&
        last.width === width &&
        last.flip === flip
      )
        return;
      placedAt.current = { top, left, width, flip };
      setPlacement(placedAt.current);
    };
    place();
    window.addEventListener("scroll", place, true);
    window.addEventListener("resize", place);
    return () => {
      window.removeEventListener("scroll", place, true);
      window.removeEventListener("resize", place);
    };
  }, [open, sheet, shown.length]);

  // A click anywhere else closes the menu and leaves the value alone, and
  // focus with it.
  useEffect(() => {
    if (!open || sheet) return undefined;
    const onPress = (event) => {
      const button = trigger.current;
      if (
        wrapper.current?.contains(event.target) ||
        menu.current?.contains(event.target) ||
        [...(button?.labels || [])].some((element) =>
          element.contains(event.target),
        )
      )
        return;
      setOpen(false);
      setPlacement(null);
      // Pressed on the backdrop of the dialog the control is in: the click
      // that follows would close the dialog too, so it goes no further.
      const dialog = button?.closest("dialog[open]");
      if (dialog && event.target === dialog) {
        document.addEventListener("click", (click) => click.stopPropagation(), {
          capture: true,
          once: true,
        });
      }
    };
    document.addEventListener("mousedown", onPress);
    return () => document.removeEventListener("mousedown", onPress);
  }, [open, sheet]);

  // The search opens focused once the menu is in place (hidden, it could
  // not take focus), and the sheet opens as a modal in the browser's top
  // layer, giving focus back to the trigger when it closes.
  const placed = sheet || Boolean(placement);
  useEffect(() => {
    if (!open || !placed) return undefined;
    const dialog = sheetRef.current;
    const button = trigger.current;
    const overflow = document.body.style.overflow;
    if (sheet && dialog && !dialog.open) {
      dialog.showModal();
      document.body.style.overflow = "hidden";
    }
    if (searchable) search.current?.focus();
    else if (sheet) dialog?.querySelector("[role=listbox]")?.focus();
    return () => {
      if (sheet && dialog?.open) dialog.close();
      document.body.style.overflow = overflow;
      if (sheet) button?.focus();
    };
  }, [open, sheet, placed, searchable]);

  // The active option stays in view.
  const activeIndex = activeOption ? activeOption.index : -1;
  useEffect(() => {
    if (!open || activeIndex < 0) return;
    document
      .getElementById(`${id}-option-${activeIndex}`)
      ?.scrollIntoView?.({ block: "nearest" });
  }, [open, id, activeIndex]);

  const searchRow = searchable && (
    <div className={sheet ? selectSheetSearch : selectSearch}>
      <FiSearch aria-hidden="true" />
      <input
        ref={search}
        type="text"
        role="combobox"
        aria-label={`Search ${title || "options"}`}
        aria-expanded="true"
        aria-haspopup="listbox"
        aria-controls={listId}
        aria-activedescendant={
          activeOption ? optionId(activeOption) : undefined
        }
        aria-autocomplete="list"
        className="min-w-0 flex-1 border-0 bg-transparent p-0 text-spot-ink outline-none placeholder:text-spot-muted"
        placeholder="Type to search"
        autoComplete="off"
        spellCheck={false}
        value={query}
        onChange={onSearch}
        onKeyDown={onKeyDown}
      />
      {!sheet && (
        <kbd
          aria-hidden="true"
          className="rounded-md border border-[#23273524] px-1.5 py-px font-ui text-[10.5px]"
        >
          Esc
        </kbd>
      )}
    </div>
  );
  const rowHeight = sheet
    ? "min-h-11"
    : size === "mini"
      ? "min-h-8"
      : "min-h-9";
  // In a sheet without a search, the list itself takes focus and the keys.
  const listFocus =
    sheet && !searchable
      ? {
          tabIndex: 0,
          onKeyDown,
          "aria-activedescendant": activeOption
            ? optionId(activeOption)
            : undefined,
        }
      : {};
  const list = (
    <ul
      id={listId}
      role="listbox"
      aria-label={title || undefined}
      {...listFocus}
      className="m-0 min-h-0 flex-1 list-none overflow-y-auto overscroll-contain p-0 outline-none [scrollbar-width:thin]"
    >
      {shown.length === 0 && (
        <li
          role="presentation"
          className="px-2.5 py-3 text-[13px] text-spot-muted"
        >
          Nothing matches
        </li>
      )}
      {shown.map((option, index) => {
        const selected = option.value === current;
        const heading =
          option.group && option.group !== shown[index - 1]?.group;
        return (
          <Fragment key={option.index}>
            {heading && (
              <li role="presentation" className={selectGroup}>
                {option.group}
              </li>
            )}
            <li
              id={optionId(option)}
              role="option"
              aria-selected={selected}
              aria-disabled={option.disabled || undefined}
              aria-labelledby={`${optionId(option)}-label${option.meta ? ` ${optionId(option)}-meta` : ""}`}
              aria-describedby={
                option.description ? `${optionId(option)}-note` : undefined
              }
              data-value={option.value}
              className={`${selectOption} ${option.description ? "min-h-[52px] py-1.5" : rowHeight} ${index === active ? selectOptionActive : ""} ${selected ? "font-medium" : ""}`}
              onMouseMove={() => {
                if (!option.disabled && index !== active) setActive(index);
              }}
              onClick={() => choose(option)}
            >
              <span className="flex min-w-0 flex-col">
                <span id={`${optionId(option)}-label`} className="truncate">
                  {option.label}
                </span>
                {option.description && (
                  <span
                    id={`${optionId(option)}-note`}
                    className="text-[12px] font-normal text-spot-muted"
                  >
                    {option.description}
                  </span>
                )}
              </span>
              {(option.meta || selected) && (
                <span className="ml-auto flex shrink-0 items-center gap-2 whitespace-nowrap text-[12px] font-normal text-spot-muted">
                  {option.meta && (
                    <span id={`${optionId(option)}-meta`}>{option.meta}</span>
                  )}
                  {selected && (
                    <FiCheck
                      aria-hidden="true"
                      className="size-[15px] stroke-[2.2] text-spot-violet"
                    />
                  )}
                </span>
              )}
            </li>
          </Fragment>
        );
      })}
    </ul>
  );
  const sizer = (
    <ul
      aria-hidden="true"
      className="invisible m-0 h-0 list-none overflow-hidden p-0"
    >
      {options.map((option) => (
        <li key={option.index} className={`${selectOption} h-0 min-h-0`}>
          <span className="whitespace-nowrap">{option.label}</span>
          <span className="ml-auto flex shrink-0 items-center gap-2 whitespace-nowrap text-[12px]">
            {option.meta}
            <span className="size-[15px]" />
          </span>
        </li>
      ))}
    </ul>
  );
  const footer = searchable && (
    <p className={selectFooter} role="status">
      {term
        ? `${shown.length} of ${options.length} match`
        : `${options.length} options`}
    </p>
  );

  const menuNode = sheet ? (
    <dialog
      ref={sheetRef}
      className={selectSheet}
      aria-label={title || undefined}
      onCancel={(event) => {
        event.preventDefault();
        close();
      }}
      onClick={(event) => {
        if (event.target === event.currentTarget) close();
      }}
    >
      <div className="flex max-h-[85dvh] flex-col gap-2 px-3 pb-[max(16px,env(safe-area-inset-bottom))] pt-2.5">
        <span
          aria-hidden="true"
          className="h-1 w-10 shrink-0 self-center rounded-full bg-[#2327352e]"
        />
        <div className="flex shrink-0 items-center justify-between gap-3 px-1.5 py-1">
          <span className="text-[15px] font-medium">{title}</span>
          <button
            type="button"
            className="min-h-11 bg-transparent px-2 text-[13.5px] text-spot-violet"
            onClick={() => close()}
          >
            Done
          </button>
        </div>
        {searchRow}
        {list}
        {footer}
      </div>
    </dialog>
  ) : (
    <div
      ref={menu}
      className={`fixed z-50 ${selectMenu} ${placement ? (media("(prefers-reduced-motion: reduce)") ? "" : "animate-ov-lift") : "invisible"}`}
      style={
        placement
          ? {
              top: placement.top,
              left: placement.left,
              width: placement.width,
              maxHeight: MAX_HEIGHT,
              "--lift": placement.flip ? "-4px" : "4px",
            }
          : { top: 0, left: 0, maxWidth: MAX_WIDTH, maxHeight: MAX_HEIGHT }
      }
      onMouseDown={(event) => {
        // Clicks in the menu leave focus where it is, except in the search.
        if (event.target !== search.current) event.preventDefault();
      }}
    >
      {searchRow}
      {list}
      {footer}
      {sizer}
    </div>
  );
  // The menu is drawn outside the page's flow: inside an open modal dialog
  // when the trigger is in one, so it shows above the backdrop, and
  // otherwise at the app's root, where the workspace styles apply.
  const portal =
    open &&
    createPortal(
      <div className="dashboard-app">{menuNode}</div>,
      trigger.current?.closest("dialog[open]") ||
        document.getElementById("root") ||
        document.body,
    );

  return (
    <span
      ref={wrapper}
      className={`relative ${size === "form" ? "block w-full" : "inline-block max-w-full align-middle"} ${className}`}
    >
      <button
        ref={trigger}
        type="button"
        id={id}
        role="combobox"
        aria-expanded={open}
        aria-haspopup="listbox"
        aria-controls={listId}
        aria-activedescendant={
          !sheet && !searchable && activeOption
            ? optionId(activeOption)
            : undefined
        }
        aria-label={ariaLabel}
        aria-labelledby={labelledBy}
        aria-describedby={describedBy}
        aria-invalid={invalid || (missing && !current) ? true : undefined}
        aria-required={required || undefined}
        value={current}
        disabled={disabled}
        className={selectTrigger[size]}
        onClick={() => (open ? close() : openMenu())}
        onKeyDown={onKeyDown}
        {...rest}
      >
        <span className="grid min-w-0 flex-1">
          <span
            className={`col-start-1 row-start-1 truncate ${chosen ? "" : "text-spot-muted"}`}
          >
            {chosen ? chosen.label : placeholder}
          </span>
          {fit === "longest" && size !== "form" && (
            <span
              aria-hidden="true"
              className="invisible col-start-1 row-start-1 h-0 overflow-hidden"
            >
              {options.map((option) => (
                <span key={option.index} className="block">
                  {option.label}
                </span>
              ))}
            </span>
          )}
        </span>
        <FiChevronDown
          aria-hidden="true"
          className={open ? "rotate-180" : ""}
        />
      </button>
      {required ? (
        // A required control keeps the form's own checks: this input is
        // what the browser finds empty, under the trigger, so its message
        // shows there, naming the control.
        <input
          className="pointer-events-none absolute inset-0 h-full w-full opacity-0"
          tabIndex={-1}
          aria-labelledby={labelledBy}
          aria-label={labelledBy ? undefined : label || ariaLabel}
          name={name}
          value={current}
          required
          autoComplete="off"
          onChange={() => {}}
          onInvalid={() => setMissing(true)}
          onKeyDown={(event) => {
            trigger.current?.focus();
            onKeyDown(event);
          }}
        />
      ) : (
        name && <input type="hidden" name={name} value={current} />
      )}
      {portal}
    </span>
  );
};
