import { useEffect, useId, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import {
  FiAlertCircle,
  FiArrowDown,
  FiArrowLeft,
  FiArrowUp,
  FiChevronLeft,
  FiChevronRight,
  FiChevronsLeft,
  FiChevronsRight,
  FiInbox,
  FiSearch,
} from "react-icons/fi";
import DashboardShell from "../../features/dashboard/components/DashboardShell";
import { Dialog } from "./Dialog";
import { Select } from "./Select";
import { DatePicker } from "./DatePicker";
import { parseDate } from "../../utils/dates";
import { formatAmount } from "../../utils/money";
import { STATUS_LABELS } from "../../features/dashboard/dashboardModel";
import {
  dangerButton,
  errorBanner,
  errorDetail,
  errorTitle,
  fieldLabel,
  fileControl,
  inputControl,
  pageSubtitle,
  pageTitle,
  pagerButton,
  panel,
  panelNote,
  primaryButton,
  retryButton,
  searchControl,
  secondaryButton,
  sectionTitle,
  skeleton,
  textareaControl,
} from "./pastel";

export const errorMessage = (
  error,
  fallback = "Something went wrong. Please try again.",
) => error?.data?.message || fallback;
export const money = (value, currency = "") =>
  `${currency ? `${currency} ` : ""}${formatAmount(value, currency)}`;
export const dateLabel = (value) =>
  value && Number.isFinite(parseDate(value).getTime())
    ? parseDate(value).toLocaleDateString("en", {
        day: "numeric",
        month: "short",
        year: "numeric",
      })
    : "—";
// A due date. An overdue one takes the overview's overdue colour, with the
// icon after the date so the dates in a column stay aligned.
export const DueDate = ({ value, overdue = false }) =>
  overdue ? (
    <span
      className="inline-flex items-center gap-1.5 whitespace-nowrap text-overdue-strong"
      title="Overdue"
    >
      {dateLabel(value)}
      <FiAlertCircle
        aria-hidden="true"
        className="size-[15px] shrink-0 stroke-[1.8]"
      />
      <span className="sr-only">, overdue</span>
    </span>
  ) : (
    dateLabel(value)
  );
// The day a date field holds. A day sent as YYYY-MM-DD stays that day in any
// time zone; a moment is its day where the browser is.
export const dateInput = (value) => {
  const date = value ? parseDate(value) : new Date();
  return Number.isFinite(date.getTime())
    ? `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`
    : "";
};

// Page sections rise one after another, like the overview's panels. On a
// `fill` page, a `fill` panel reaches the bottom of wide windows.
export const WorkspacePage = ({ children, section, fill = false }) => (
  <DashboardShell section={section} fill={fill}>
    <div
      className={`flex min-w-0 flex-col gap-6 compact:gap-5 max-[700px]:gap-5 [&>:not(dialog)]:animate-ov-rise [&>:nth-child(2)]:[--rise-delay:80ms] [&>:nth-child(3)]:[--rise-delay:160ms] [&>:nth-child(n+4)]:[--rise-delay:240ms] ${fill ? "min-[1360px]:flex-1" : ""}`}
    >
      {children}
    </div>
  </DashboardShell>
);

// The title keeps room for a word as long as "Documents." beside the
// actions; when the actions need more of the row, such as a long row of
// tabs, they go under the title, and wrap within the width, rather than
// squeezing it.
export const PageHeader = ({ title, description, actions, back }) => {
  const navigate = useNavigate();
  return (
    <header className="flex flex-wrap items-end justify-between gap-8 max-[1359px]:gap-5 max-[700px]:flex-col max-[700px]:items-stretch">
      <div className="min-w-0 flex-[1_1_300px] max-[1359px]:flex-[1_1_220px] max-[700px]:flex-initial">
        {back && (
          <button
            type="button"
            className="mb-4 inline-flex min-h-8 items-center gap-2 bg-transparent p-0 text-[13px] text-spot-nav transition-colors hover:text-spot-night coarse:min-h-11 [&>svg]:size-4"
            onClick={() => navigate(-1)}
          >
            <FiArrowLeft aria-hidden="true" /> Go back
          </button>
        )}
        <h1 className={`${pageTitle} [overflow-wrap:anywhere]`}>
          {title}
          <span>.</span>
        </h1>
        {description && <p className={pageSubtitle}>{description}</p>}
      </div>
      {actions && (
        <div className="flex max-w-full shrink-0 flex-wrap items-center gap-2.5 max-[700px]:[&>*]:flex-1">
          {actions}
        </div>
      )}
    </header>
  );
};

// A link styled as a button. With a `reason`, it can't be followed, and the
// reason is shown on hover and read out with it. A custom className replaces
// the button's look, its disabled state included.
export const ActionLink = ({
  children,
  to,
  secondary = false,
  reason,
  className = secondary ? secondaryButton : primaryButton,
  ...props
}) => {
  const noteId = useId();
  if (reason) {
    return (
      <>
        <button
          type="button"
          className={className}
          disabled
          title={reason}
          aria-describedby={noteId}
        >
          {children}
        </button>
        <span id={noteId} className="sr-only">
          {reason}
        </span>
      </>
    );
  }
  return (
    <Link to={to} className={className} {...props}>
      {children}
    </Link>
  );
};
// An `id` lets a link from another page open this one at the panel, which
// can then take focus.
export const Panel = ({
  children,
  id,
  title,
  description,
  aside,
  fill = false,
  className = "",
}) => (
  <section
    id={id}
    tabIndex={id ? -1 : undefined}
    className={`${panel} ${id ? "scroll-mt-6" : ""} ${fill ? "min-[1360px]:flex min-[1360px]:flex-1 min-[1360px]:flex-col" : ""} ${className}`}
  >
    {title && (
      <header className="mb-5 flex items-start justify-between gap-4 compact:mb-4 max-[700px]:flex-wrap">
        <div className="min-w-0">
          <h2 className={sectionTitle}>{title}</h2>
          {description && (
            <p className={`mt-1.5 ${panelNote}`}>{description}</p>
          )}
        </div>
        {aside}
      </header>
    )}
    {children}
  </section>
);
export const InfoGrid = ({ items }) => (
  <dl className="m-0 grid grid-cols-3 gap-x-8 gap-y-6 max-[1359px]:grid-cols-2 max-[700px]:grid-cols-1 max-[700px]:gap-y-5">
    {items.map(([label, value]) => {
      const missing = value !== 0 && !value;
      return (
        <div key={label} className="min-w-0">
          <dt className={panelNote}>{label}</dt>
          <dd
            className={`mt-1.5 text-[15px] [overflow-wrap:anywhere] ${missing ? "text-spot-faint" : "text-spot-ink"}`}
          >
            {value === 0 ? "0" : value || "Not provided"}
          </dd>
        </div>
      );
    })}
  </dl>
);

const BADGE_TONES = {
  neutral: "bg-[#2327350d] text-spot-body",
  paid: "bg-spot-violet/10 text-spot-violet",
  partial: "bg-spot-lilac/20 text-avatar-lilac-ink",
  unpaid: "bg-spot-coral/[.14] text-overdue-strong",
};
// The overview's status colours: violet, lilac and coral.
const BADGE_DOTS = {
  paid: "bg-spot-violet",
  partial: "bg-spot-lilac",
  unpaid: "bg-spot-coral",
};
// Its text sets its baseline and the dot is centred beside it, so in a table
// the text sits on the row's baseline.
export const Badge = ({ children, tone = "neutral" }) => (
  <span
    className={`inline-flex items-baseline gap-1.5 whitespace-nowrap rounded-full px-2.5 py-1 text-[13px] leading-[1.3] ${BADGE_TONES[tone] || BADGE_TONES.neutral}`}
  >
    {BADGE_DOTS[tone] && (
      <span
        className={`size-1.5 shrink-0 self-center rounded-full ${BADGE_DOTS[tone]}`}
        aria-hidden="true"
      />
    )}
    {children}
  </span>
);
export const statusTone = (value) =>
  value === "Paid"
    ? "paid"
    : value === "Not Fully Paid"
      ? "partial"
      : value === "Not Paid"
        ? "unpaid"
        : "neutral";
// A stored payment status in the app's words for it.
export const StatusBadge = ({ status }) => (
  <Badge tone={statusTone(status)}>{STATUS_LABELS[status] || status}</Badge>
);

// An amount in the overview's figure style: light Sora, smaller decimals.
// A custom className replaces the default weight and letter spacing. The
// currency sets how digits are grouped; hideCode leaves its code off, where a
// column's other amounts already name it.
export const Amount = ({
  value,
  currency,
  hideCode = false,
  className = "font-light tracking-[-0.03em]",
}) => {
  const text = formatAmount(value, currency);
  return (
    <span className={`whitespace-nowrap font-display ${className}`}>
      {currency && !hideCode && (
        <span className="mr-1.5 font-ui text-[max(0.72em,11.5px)] tracking-normal text-spot-muted">
          {currency}
        </span>
      )}
      {text.slice(0, -3)}
      <span className="text-[max(0.8em,12px)] text-spot-faint">
        {text.slice(-3)}
      </span>
    </span>
  );
};

// A row's share of the largest row, from 0 to 1, drawn under its name in a
// table that sums something up. The figures are in the row's other cells.
// In a table, its column keeps room for the bar, up to its
// 240px or 40% of the table's width.
export const SHARE_BAR_TRACK = "minmax(min(240px,40%),auto)";
export const ShareBar = ({ ratio, tone }) => (
  <span
    className="mt-2 block h-1.5 w-full max-w-[240px] overflow-hidden rounded-full bg-[#23273510]"
    aria-hidden="true"
  >
    <span
      className={`block h-full origin-left animate-ov-bar rounded-[inherit] ${tone}`}
      style={{ width: `${ratio * 100}%` }}
    />
  </span>
);

// A labelled control with its hint or error below. `as="select"` draws the
// styled dropdown, and a date or month field the calendar; each reports its
// choice as a change event, so a form reads it as it would a native control.
export const Field = ({
  label,
  error,
  hint,
  as = "input",
  id: providedId,
  className = "",
  children,
  ...props
}) => {
  const generatedId = useId();
  const id = providedId || generatedId;
  const Control = as;
  const controlClass =
    as === "textarea"
      ? textareaControl
      : props.type === "file"
        ? fileControl
        : inputControl;
  const describedBy = error || hint ? `${id}-hint` : undefined;
  return (
    <div className={`flex min-w-0 flex-col gap-2 ${className}`}>
      <label id={`${id}-label`} htmlFor={id} className={fieldLabel}>
        {label}
        {props.required && (
          <span className="text-spot-violet" aria-hidden="true">
            {" *"}
          </span>
        )}
      </label>
      {props.type === "date" || props.type === "month" ? (
        <DatePicker
          id={id}
          labelId={`${id}-label`}
          invalid={Boolean(error)}
          aria-describedby={describedBy}
          {...props}
          onChange={(value) =>
            props.onChange?.({ target: { value, name: props.name } })
          }
        />
      ) : as === "select" ? (
        <Select
          id={id}
          label={label}
          invalid={Boolean(error)}
          aria-describedby={describedBy}
          {...props}
          onChange={(value) =>
            props.onChange?.({ target: { value, name: props.name } })
          }
        >
          {children}
        </Select>
      ) : (
        <Control
          id={id}
          className={controlClass}
          aria-invalid={error ? true : undefined}
          aria-describedby={describedBy}
          {...props}
        >
          {children}
        </Control>
      )}
      {(error || hint) && (
        <p
          id={`${id}-hint`}
          className={`text-[13px] leading-[1.5] ${error ? "text-overdue-strong" : "text-spot-muted"}`}
        >
          {error || hint}
        </p>
      )}
    </div>
  );
};
export const EmptyState = ({ title, text, icon: Icon = FiInbox, action }) => (
  <div className="flex min-h-[260px] flex-col items-center justify-center px-6 py-10 text-center">
    <span className="mb-4 grid size-14 place-items-center rounded-[18px] bg-white/80 text-spot-violet shadow-key [&>svg]:size-6 [&>svg]:stroke-[1.5]">
      <Icon aria-hidden="true" />
    </span>
    <h2 className="font-display text-[18px] font-light leading-[1.3] text-spot-ink">
      {title}
    </h2>
    <p className="mt-2 max-w-[340px] text-[13px] leading-[1.6] text-spot-muted">
      {text}
    </p>
    {action && <div className="mt-5">{action}</div>}
  </div>
);

// A search box that searches once typing pauses, or at once on Enter. It
// follows `value` when the search changes elsewhere, such as the back button.
export const SearchField = ({
  value,
  onSearch,
  label,
  placeholder,
  className = "",
}) => {
  const [text, setText] = useState(value);
  const [shown, setShown] = useState(value);
  if (value !== shown) {
    setShown(value);
    if (value !== text.trim()) setText(value);
  }
  useEffect(() => {
    if (text.trim() === value) return undefined;
    const timer = setTimeout(() => onSearch(text.trim()), 300);
    return () => clearTimeout(timer);
  }, [text, value, onSearch]);
  return (
    <label className={`relative block min-w-0 ${className}`}>
      <span className="sr-only">{label}</span>
      <FiSearch
        aria-hidden="true"
        className="pointer-events-none absolute left-3.5 top-1/2 size-4 -translate-y-1/2 text-spot-muted"
      />
      <input
        type="search"
        className={searchControl}
        value={text}
        placeholder={placeholder}
        autoComplete="off"
        onChange={(event) => setText(event.target.value)}
        onKeyDown={(event) => {
          if (event.key === "Enter") onSearch(text.trim());
        }}
      />
    </label>
  );
};
// Placeholders keep the panel in place and never show figures.
export const LoadingState = () => (
  <div role="status">
    <span className="sr-only">Loading workspace data…</span>
    <div className={panel} aria-hidden="true">
      <span className={`${skeleton} h-6 w-[185px] rounded-md`} />
      <div className="mt-5">
        {Array.from({ length: 5 }, (_, index) => (
          <div
            className="flex h-[46px] items-center gap-3 border-t border-[#23273512]"
            key={index}
          >
            <span className={`${skeleton} size-8 rounded-[11px]`} />
            <span className={`${skeleton} h-3 w-1/4 rounded-md`} />
            <span className={`${skeleton} ml-auto h-3 w-[15%] rounded-md`} />
          </div>
        ))}
      </div>
    </div>
  </div>
);
export const ErrorState = ({ error, retry }) => (
  <div className={errorBanner} role="alert">
    <FiAlertCircle aria-hidden="true" />
    <div className="flex-1">
      <strong className={errorTitle}>We couldn’t load this page.</strong>
      <span className={errorDetail}>{errorMessage(error)}</span>
    </div>
    {retry && (
      <button type="button" className={retryButton} onClick={retry}>
        Try again
      </button>
    )}
  </div>
);

export const usePagination = (rows = [], initialSize = 5) => {
  const [requestedPage, setPage] = useState(0);
  const [size, setSize] = useState(initialSize);
  const last = size === -1 ? 0 : Math.max(0, Math.ceil(rows.length / size) - 1);
  const page = Math.min(requestedPage, last);
  const start = size === -1 ? 0 : page * size;
  return {
    page,
    size,
    last,
    start,
    count: rows.length,
    // Rows on a full page; a short last page keeps their height.
    fullPage: size === -1 ? rows.length : Math.min(size, rows.length),
    rows: size === -1 ? rows : rows.slice(start, start + size),
    setPage,
    setSize: (value) => {
      setSize(value);
      setPage(0);
    },
  };
};
// The same shape as usePagination for a list the server pages. The caller
// owns the page (from 0) and size; the rows and count come from the server.
export const serverPagination = ({
  rows = [],
  count = 0,
  page,
  size,
  setPage,
  setSize,
}) => ({
  page,
  size,
  last: Math.max(0, Math.ceil(count / size) - 1),
  start: page * size,
  count,
  fullPage: Math.min(size, count),
  rows,
  setPage,
  setSize,
});

export const Pagination = ({ pagination, sizes = [5, 10, 25, -1] }) => {
  const { page, size, last, start, count, rows, setPage, setSize } = pagination;
  return (
    <div className="mt-2 flex items-center justify-between gap-3 border-t border-[#23273512] pt-3 max-[700px]:flex-wrap">
      <label className="flex items-center gap-2 text-[13px] text-spot-muted">
        Rows per page
        <Select
          size="mini"
          value={size}
          onChange={(value) => setSize(Number(value))}
        >
          {sizes.map((n) => (
            <option key={n} value={n}>
              {n === -1 ? "All" : n}
            </option>
          ))}
        </Select>
      </label>
      <div className="flex items-center gap-1.5 max-[700px]:ml-auto max-[700px]:flex-wrap">
        <span
          className="mr-2 text-[13px] text-spot-text max-[700px]:mr-0.5"
          role="status"
        >
          {count ? start + 1 : 0}–{start + rows.length} of {count}
        </span>
        {[
          {
            label: "First page",
            icon: FiChevronsLeft,
            target: 0,
            disabled: page === 0,
          },
          {
            label: "Previous page",
            icon: FiChevronLeft,
            target: page - 1,
            disabled: page === 0,
          },
          {
            label: "Next page",
            icon: FiChevronRight,
            target: page + 1,
            disabled: page === last,
            primary: true,
          },
          {
            label: "Last page",
            icon: FiChevronsRight,
            target: last,
            disabled: page === last,
          },
        ].map(({ label, icon: Icon, target, disabled, primary }) => (
          <button
            key={label}
            type="button"
            className={`${pagerButton} ${primary ? "enabled:bg-white enabled:shadow-key" : ""}`}
            aria-label={label}
            disabled={disabled}
            onClick={() => setPage(target)}
          >
            <Icon aria-hidden="true" />
          </button>
        ))}
      </div>
    </div>
  );
};

// Tables copy the overview's payment table. Each column's `type` sets its
// style, and every table draws a type the same: one size for it in every
// table on a window, larger from 1536px wide, where the widest tables still
// fit on single lines, whatever the panel's size, so no two tables side by
// side or page to page differ. When the panel is too
// narrow for every column, each row becomes a card: the main value first,
// whichever column it is, then labelled fields in as many columns as fit,
// then the actions on a line of their own.
const CARD_LABEL =
  "stacked:before:mb-1 stacked:before:block stacked:before:text-[12px] stacked:before:text-spot-muted stacked:before:content-[attr(data-label)]";
const COLUMN_TYPES = {
  text: {
    th: "pr-3.5 text-left",
    td: `pr-3.5 text-[13px] text-spot-body min-[1536px]:text-[15px] ${CARD_LABEL}`,
  },
  main: {
    th: "pr-3.5 text-left",
    td: "pr-3.5 text-[15px] font-medium text-spot-ink [overflow-wrap:anywhere] min-[1536px]:text-[17px] stacked:order-first stacked:col-span-full stacked:pr-12",
  },
  email: {
    th: "pr-3.5 text-left",
    td: `pr-3.5 text-[13px] text-spot-body [overflow-wrap:anywhere] min-[1536px]:text-[15px] tabled:whitespace-nowrap tabled:[overflow-wrap:normal] stacked:col-span-full ${CARD_LABEL}`,
  },
  // A row's number, right-aligned so 9 and 10 end in one place.
  index: {
    th: "w-10 pr-3.5 text-right",
    td: "w-10 pr-3.5 text-right text-[13px] tabular-nums text-spot-faint min-[1536px]:text-[15px] stacked:absolute stacked:right-0 stacked:top-[18px] stacked:w-auto stacked:pr-0",
  },
  // A count, right-aligned so its digits line up.
  number: {
    th: "pr-3.5 text-right",
    td: `pr-3.5 text-right text-[13px] tabular-nums text-spot-body min-[1536px]:text-[15px] stacked:text-left ${CARD_LABEL}`,
  },
  amount: {
    th: "pr-8 text-right",
    td: `pr-8 text-right text-[17px] text-spot-ink min-[1536px]:text-[20px] stacked:pr-0 stacked:text-left ${CARD_LABEL}`,
  },
  // Buttons, centred under their heading so the two share a centre line,
  // the last column's still meeting the table's right edge.
  action: {
    th: "w-px pr-3.5 text-center",
    td: "w-px whitespace-nowrap pr-3.5 tabled:text-center stacked:w-auto",
  },
};
// A `fillRows` table takes the rest of its panel on wide windows, its rows
// sharing the height, but never shorter than every table's 46px rows nor
// taller than 88px. It is at least as tall as its 35px heading and its rows
// at 46px, so a page that doesn't fit the window scrolls as a whole and no
// row is ever cut off inside the panel. Its text is every table's size.
const FILL = {
  wrapper:
    "min-[1360px]:min-h-[calc(35px+var(--fill-rows)*46px)] min-[1360px]:flex-[1_1_0] min-[1360px]:overflow-y-auto min-[1360px]:[container-type:size] min-[1360px]:[scrollbar-color:#a996e0_#ffffff26] min-[1360px]:[scrollbar-width:thin] min-[1360px]:[--fill-row:clamp(46px,calc((100cqh_-_35px)_/_var(--fill-rows)),88px)]",
  row: "tabled:min-h-[var(--fill-row,46px)]",
};

// A table, while it is one, lays its rows on one grid: each column as wide
// as its widest entry, and the room left over shared out equally between the
// columns, so every gap is the same and the first and last columns meet the
// panel's edges. An email address keeps to one line while the table is a
// table, so a tight table wraps names rather than addresses, and a column
// can ask for its own track. Every cell
// of a row, and every heading, sits on one baseline, whatever its size, with
// the row's content centred in its height. A row is 46px, or taller when a
// cell holds two lines, never letting its content spill into the next. The
// rows carry the borders and the hover, which a cell would break at the gaps.
const evenTrack = ({ track }) => track || "auto";
const WHOLE = new Set(["email", "index", "number", "amount", "action"]);
const EVEN = {
  table: "tabled:grid tabled:justify-between tabled:gap-x-4",
  head: "tabled:col-span-full tabled:grid tabled:grid-cols-subgrid tabled:bg-spot-thead",
  headRow:
    "tabled:col-span-full tabled:grid tabled:grid-cols-subgrid tabled:items-baseline tabled:border-b tabled:border-[#23273517]",
  th: "tabled:block tabled:w-auto tabled:whitespace-nowrap tabled:border-b-0 tabled:bg-transparent tabled:pr-0 tabled:leading-7",
  body: "tabled:col-span-full tabled:grid tabled:grid-cols-subgrid tabled:before:col-span-full",
  row: "tabled:col-span-full tabled:grid tabled:grid-cols-subgrid tabled:content-center tabled:items-baseline tabled:hover:bg-[#5b47a00a] tabled:[&:hover>*]:bg-transparent tabled:[&:not(:first-child)>*]:border-t-0 tabled:[&:not(:first-child)]:border-t tabled:[&:not(:first-child)]:border-[#23273512]",
  // Text may shrink and wrap; figures, addresses, actions and any column
  // marked `nowrap` keep their whole width, so no cell ever runs into the
  // next.
  td: "tabled:w-auto tabled:pr-0",
  shrink: "tabled:min-w-0",
  whole: "tabled:min-w-max",
};

// A column with `sortBy` gets a header button when the table has `onSort`;
// `sort` ({ field, dir }) marks the column the rows are sorted by. Cards
// have no headers, so their list needs another way to sort. A `narrow`
// table has a few short columns, so it stays a table in a narrower panel; a
// `wide` one has many long ones, such as several actions, so it needs a
// wider panel to stay one.
export const DataTable = ({
  columns,
  rows,
  label,
  rowKey = (row) => row._id,
  fillRows = 0,
  sort,
  onSort,
  busy = false,
  narrow = false,
  wide = false,
}) => {
  // In a card, placing the first action in column 1 starts a new line.
  const firstAction = columns.findIndex(({ type }) => type === "action");
  const fill = fillRows > 0;
  return (
    <div
      className={`relative transition-opacity [container-type:inline-size] ${narrow ? "[container-name:narrow-table]" : wide ? "[container-name:broad-table]" : columns.length >= 8 ? "[container-name:wide-table]" : "[container-name:table]"} ${fill ? FILL.wrapper : ""} ${busy ? "opacity-60" : ""}`}
      style={fill ? { "--fill-rows": fillRows } : undefined}
      aria-busy={busy || undefined}
    >
      <table
        className={`w-full border-separate border-spacing-0 stacked:block ${EVEN.table}`}
        style={{
          gridTemplateColumns: columns
            .map((column) => evenTrack(column))
            .join(" "),
        }}
      >
        <caption className="sr-only">{label}</caption>
        <thead
          className={`sticky top-0 z-[1] stacked:absolute stacked:size-px stacked:overflow-hidden stacked:whitespace-nowrap stacked:[clip-path:inset(50%)] ${EVEN.head}`}
        >
          <tr className={EVEN.headRow}>
            {columns.map(({ label: heading, type = "text", sortBy }) => {
              const sorted = sortBy && sort?.field === sortBy;
              const SortIcon = sort?.dir === "asc" ? FiArrowUp : FiArrowDown;
              return (
                <th
                  key={heading}
                  scope="col"
                  aria-sort={
                    sorted
                      ? sort.dir === "asc"
                        ? "ascending"
                        : "descending"
                      : undefined
                  }
                  className={`h-7 border-b border-[#23273517] bg-spot-thead p-0 text-[13px] font-normal text-spot-muted last:pr-0 min-[1536px]:text-[14px] ${COLUMN_TYPES[type].th} ${EVEN.th}`}
                >
                  {sortBy && onSort ? (
                    <>
                      <button
                        type="button"
                        className={`inline-flex items-center gap-1 rounded-md bg-transparent p-0 transition-colors hover:text-spot-night stacked:hidden [&>svg]:size-3 [&>svg]:stroke-[2] ${sorted ? "text-spot-night" : "text-inherit"}`}
                        onClick={() => onSort(sortBy)}
                      >
                        {heading}
                        {sorted && <SortIcon aria-hidden="true" />}
                      </button>
                      <span className="hidden stacked:inline">{heading}</span>
                    </>
                  ) : (
                    heading
                  )}
                </th>
              );
            })}
          </tr>
        </thead>
        <tbody
          className={`before:table-row before:h-1.5 before:content-[''] stacked:block stacked:before:hidden ${EVEN.body}`}
        >
          {rows.map((row, index) => (
            <tr
              key={rowKey(row, index)}
              className={`h-[46px] tabled:h-auto ${fill ? FILL.row : "tabled:min-h-[46px]"} [&:hover>*]:bg-[#5b47a00a] [&:not(:first-child)>*]:border-t [&:not(:first-child)>*]:border-[#23273512] stacked:relative stacked:grid stacked:h-auto stacked:grid-cols-[repeat(auto-fill,minmax(140px,1fr))] stacked:gap-x-3 stacked:gap-y-4 stacked:py-[18px] stacked:hover:bg-[#5b47a00a] stacked:[&:hover>*]:bg-transparent stacked:[&:not(:first-child)>*]:border-t-0 stacked:[&:not(:first-child)]:border-t stacked:[&:not(:first-child)]:border-[#23273512] ${EVEN.row}`}
            >
              {columns.map((column, columnIndex) => (
                <td
                  key={column.label}
                  className={`p-0 align-middle last:pr-0 ${COLUMN_TYPES[column.type || "text"].td} ${columnIndex === firstAction ? "stacked:col-start-1" : ""} ${EVEN.td} ${column.nowrap || WHOLE.has(column.type) ? EVEN.whole : EVEN.shrink}`}
                  data-label={column.label}
                >
                  {column.render(row, index)}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
};

export const ConfirmDialog = ({
  open,
  title,
  children,
  confirmLabel,
  onConfirm,
  onClose,
  busy,
}) => (
  <Dialog
    open={open}
    title={title}
    onClose={onClose}
    busy={busy}
    closeLabel="Close confirmation"
  >
    <p className="mt-3 text-[14px] leading-[1.6] text-spot-body [overflow-wrap:anywhere]">
      {children}
    </p>
    <div className="mt-7 flex flex-wrap justify-end gap-2.5">
      <button
        data-autofocus
        type="button"
        className={secondaryButton}
        onClick={onClose}
        disabled={busy}
      >
        Cancel
      </button>
      <button
        type="button"
        className={dangerButton}
        onClick={onConfirm}
        disabled={busy}
      >
        {busy ? "Please wait…" : confirmLabel}
      </button>
    </div>
  </Dialog>
);
