import { useId, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import {
  FiAlertCircle,
  FiArrowLeft,
  FiChevronLeft,
  FiChevronRight,
  FiChevronsLeft,
  FiChevronsRight,
  FiInbox,
} from "react-icons/fi";
import DashboardShell from "../../features/dashboard/components/DashboardShell";
import { Dialog } from "./Dialog";
import { parseDate } from "../../utils/dates";
import { STATUS_LABELS } from "../../features/dashboard/dashboardModel";
import {
  dangerButton,
  errorBanner,
  errorDetail,
  errorTitle,
  fieldLabel,
  fileControl,
  inputControl,
  pageSizeSelect,
  pageSubtitle,
  pageTitle,
  pagerButton,
  panel,
  panelNote,
  primaryButton,
  retryButton,
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
  `${currency ? `${currency} ` : ""}${new Intl.NumberFormat("en", { minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(Number(value) || 0)}`;
export const dateLabel = (value) =>
  value && Number.isFinite(parseDate(value).getTime())
    ? parseDate(value).toLocaleDateString("en", {
        day: "numeric",
        month: "short",
        year: "numeric",
      })
    : "—";
export const dateInput = (value) => {
  const date = value ? new Date(value) : new Date();
  return Number.isFinite(date.getTime())
    ? `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`
    : "";
};
export const dateInstant = (value) =>
  value ? new Date(`${value}T12:00:00`).toISOString() : null;

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

export const PageHeader = ({ title, description, actions, back }) => {
  const navigate = useNavigate();
  return (
    <header className="flex items-end justify-between gap-8 max-[1359px]:gap-5 max-[700px]:flex-col max-[700px]:items-stretch">
      <div className="min-w-0">
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
        <div className="flex shrink-0 flex-wrap items-center gap-2.5 max-[700px]:[&>*]:flex-1">
          {actions}
        </div>
      )}
    </header>
  );
};

export const ActionLink = ({ children, to, secondary = false, ...props }) => (
  <Link
    to={to}
    className={secondary ? secondaryButton : primaryButton}
    {...props}
  >
    {children}
  </Link>
);
export const Panel = ({
  children,
  title,
  description,
  aside,
  fill = false,
  className = "",
}) => (
  <section
    className={`${panel} ${fill ? "min-[1360px]:flex min-[1360px]:flex-1 min-[1360px]:flex-col" : ""} ${className}`}
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
export const Badge = ({ children, tone = "neutral" }) => (
  <span
    className={`inline-flex items-center gap-1.5 whitespace-nowrap rounded-full px-2.5 py-1 text-[12.5px] leading-[1.3] ${BADGE_TONES[tone] || BADGE_TONES.neutral}`}
  >
    {BADGE_DOTS[tone] && (
      <span
        className={`size-1.5 shrink-0 rounded-full ${BADGE_DOTS[tone]}`}
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
// A stored payment status in the words the overview uses.
export const StatusBadge = ({ status }) => (
  <Badge tone={statusTone(status)}>{STATUS_LABELS[status] || status}</Badge>
);

// An amount in the overview's figure style: light Sora, smaller decimals.
// A custom className replaces the default weight and letter spacing.
export const Amount = ({
  value,
  currency,
  className = "font-light tracking-[-0.03em]",
}) => {
  const text = money(value);
  return (
    <span className={`whitespace-nowrap font-display ${className}`}>
      {currency && (
        <span className="mr-1.5 font-ui text-[0.6em] tracking-normal text-spot-muted">
          {currency}
        </span>
      )}
      {text.slice(0, -3)}
      <span className="text-[0.7em] text-spot-faint">{text.slice(-3)}</span>
    </span>
  );
};

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
  return (
    <div className={`flex min-w-0 flex-col gap-2 ${className}`}>
      <label htmlFor={id} className={fieldLabel}>
        {label}
        {props.required && (
          <span className="text-spot-violet" aria-hidden="true">
            {" *"}
          </span>
        )}
      </label>
      <Control
        id={id}
        className={controlClass}
        aria-invalid={error ? true : undefined}
        aria-describedby={error || hint ? `${id}-hint` : undefined}
        {...props}
      >
        {children}
      </Control>
      {(error || hint) && (
        <p
          id={`${id}-hint`}
          className={`text-[12px] leading-[1.5] ${error ? "text-overdue-strong" : "text-spot-muted"}`}
        >
          {error || hint}
        </p>
      )}
    </div>
  );
};
export const EmptyState = ({ title, text, icon: Icon = FiInbox }) => (
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
  </div>
);
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
export const Pagination = ({ pagination }) => {
  const { page, size, last, start, count, rows, setPage, setSize } = pagination;
  return (
    <div className="mt-2 flex items-center justify-between gap-3 border-t border-[#23273512] pt-3 max-[700px]:flex-wrap">
      <label className="flex items-center gap-2 text-[12.5px] text-spot-muted">
        Rows per page
        <select
          className={pageSizeSelect}
          value={size}
          onChange={(event) => setSize(Number(event.target.value))}
        >
          {[5, 10, 25, -1].map((n) => (
            <option key={n} value={n}>
              {n === -1 ? "All" : n}
            </option>
          ))}
        </select>
      </label>
      <div className="flex items-center gap-1.5 max-[700px]:ml-auto max-[700px]:flex-wrap">
        <span
          className="mr-2 text-[12.5px] text-spot-text max-[700px]:mr-0.5"
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
// style. When the panel is too narrow for every column, each row becomes a
// card: the main value, then labelled fields in as many columns as fit,
// then the actions on a line of their own.
const CARD_LABEL =
  "stacked:before:mb-1 stacked:before:block stacked:before:text-[11px] stacked:before:text-spot-muted stacked:before:content-[attr(data-label)]";
const COLUMN_TYPES = {
  text: {
    th: "pr-3.5 text-left",
    td: `pr-3.5 text-[13px] text-spot-body ${CARD_LABEL}`,
  },
  main: {
    th: "pr-3.5 text-left",
    td: "pr-3.5 text-[14.5px] font-medium text-spot-ink [overflow-wrap:anywhere] stacked:col-span-full stacked:pr-12",
  },
  email: {
    th: "pr-3.5 text-left",
    td: `pr-3.5 text-[13px] text-spot-body [overflow-wrap:anywhere] stacked:col-span-full ${CARD_LABEL}`,
  },
  index: {
    th: "w-10 pr-3.5 text-left",
    td: "w-10 pr-3.5 text-[12px] tabular-nums text-spot-faint stacked:absolute stacked:right-0 stacked:top-[18px] stacked:w-auto stacked:pr-0",
  },
  amount: {
    th: "pr-8 text-right",
    td: `pr-8 text-right text-[17px] text-spot-ink stacked:pr-0 stacked:text-left ${CARD_LABEL}`,
  },
  action: {
    th: "w-px pr-3.5 text-left",
    td: "w-px whitespace-nowrap pr-3.5 stacked:w-auto",
  },
};
// A `fillRows` table takes the rest of its panel on wide windows. Up to ten
// of its rows share the height (40–88px each); longer pages scroll inside it,
// so no row is cut off at the bottom. The text grows with the room left:
// --fill-grow runs from 0 to 42px as the rows get taller than 46px or the
// table wider, whichever is more.
const FILL = {
  wrapper:
    "min-[1360px]:min-h-[240px] min-[1360px]:flex-[1_1_0] min-[1360px]:overflow-y-auto min-[1360px]:[container-type:size] min-[1360px]:[scrollbar-color:#a996e0_#ffffff26] min-[1360px]:[scrollbar-width:thin] min-[1360px]:[--fill-row:clamp(40px,calc((100cqh_-_34px)_/_var(--fill-rows)),88px)] min-[1360px]:[--fill-grow:clamp(0px,max(var(--fill-row)_-_46px,(100cqi_-_1016px)_*_0.0968),42px)]",
  row: "min-[1360px]:[height:var(--fill-row)]",
  // From the 46px row sizes up to 18px text, 20px names and 24px amounts.
  text: {
    text: "min-[1360px]:[font-size:calc(14.5px_+_var(--fill-grow)_*_0.0833)]",
    main: "min-[1360px]:[font-size:calc(16px_+_var(--fill-grow)_*_0.0952)]",
    email: "min-[1360px]:[font-size:calc(14.5px_+_var(--fill-grow)_*_0.0833)]",
    index: "min-[1360px]:[font-size:calc(13px_+_var(--fill-grow)_*_0.0476)]",
    amount: "min-[1360px]:[font-size:calc(19px_+_var(--fill-grow)_*_0.119)]",
    action: "",
  },
};

export const DataTable = ({
  columns,
  rows,
  label,
  rowKey = (row) => row._id,
  fillRows = 0,
}) => {
  // In a card, placing the first action in column 1 starts a new line.
  const firstAction = columns.findIndex(({ type }) => type === "action");
  const fill = fillRows > 0;
  return (
    <div
      className={`[container-type:inline-size] ${columns.length >= 8 ? "[container-name:wide-table]" : "[container-name:table]"} ${fill ? FILL.wrapper : ""}`}
      style={fill ? { "--fill-rows": Math.min(fillRows, 10) } : undefined}
    >
      <table className="w-full border-separate border-spacing-0 stacked:block">
        <caption className="sr-only">{label}</caption>
        <thead className="sticky top-0 z-[1] stacked:absolute stacked:size-px stacked:overflow-hidden stacked:whitespace-nowrap stacked:[clip-path:inset(50%)]">
          <tr>
            {columns.map(({ label: heading, type = "text" }) => (
              <th
                key={heading}
                scope="col"
                className={`h-7 border-b border-[#23273517] bg-spot-thead p-0 text-[12px] font-normal text-spot-muted last:pr-0 ${COLUMN_TYPES[type].th}`}
              >
                {heading}
              </th>
            ))}
          </tr>
        </thead>
        <tbody className="before:table-row before:h-1.5 before:content-[''] stacked:block stacked:before:hidden">
          {rows.map((row, index) => (
            <tr
              key={rowKey(row, index)}
              className={`h-[46px] ${fill ? FILL.row : ""} [&:hover>*]:bg-[#5b47a00a] [&:not(:first-child)>*]:border-t [&:not(:first-child)>*]:border-[#23273512] stacked:relative stacked:grid stacked:h-auto stacked:grid-cols-[repeat(auto-fill,minmax(140px,1fr))] stacked:gap-x-3 stacked:gap-y-4 stacked:py-[18px] stacked:hover:bg-[#5b47a00a] stacked:[&:hover>*]:bg-transparent stacked:[&:not(:first-child)>*]:border-t-0 stacked:[&:not(:first-child)]:border-t stacked:[&:not(:first-child)]:border-[#23273512]`}
            >
              {columns.map((column, columnIndex) => (
                <td
                  key={column.label}
                  className={`p-0 align-middle last:pr-0 ${COLUMN_TYPES[column.type || "text"].td} ${fill ? FILL.text[column.type || "text"] : ""} ${columnIndex === firstAction ? "stacked:col-start-1" : ""}`}
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
