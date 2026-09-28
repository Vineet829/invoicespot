import { useEffect, useId, useRef, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import {
  FiAlertCircle,
  FiArrowLeft,
  FiChevronLeft,
  FiChevronRight,
  FiChevronsLeft,
  FiChevronsRight,
  FiInbox,
  FiX,
} from "react-icons/fi";
import DashboardShell from "../../features/dashboard/components/DashboardShell";
import "../../styles/dashboard.css";
import "../../styles/workspace.css";

export const errorMessage = (
  error,
  fallback = "Something went wrong. Please try again.",
) => error?.data?.message || fallback;
export const money = (value, currency = "") =>
  `${currency ? `${currency} ` : ""}${new Intl.NumberFormat("en", { minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(Number(value) || 0)}`;
export const dateLabel = (value) =>
  value && Number.isFinite(new Date(value).getTime())
    ? new Date(value).toLocaleDateString("en", {
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

export const WorkspacePage = ({ children, section, ...counts }) => (
  <DashboardShell section={section} {...counts}>
    <div className="workspace-page">{children}</div>
  </DashboardShell>
);

export const PageHeader = ({ title, description, eyebrow, actions, back }) => {
  const navigate = useNavigate();
  return (
    <>
      {back && (
        <button
          type="button"
          className="workspace-back"
          onClick={() => navigate(-1)}
        >
          <FiArrowLeft aria-hidden="true" /> Go back
        </button>
      )}
      <header className="workspace-heading">
        <div>
          <p className="dashboard-eyebrow">{eyebrow || "YOUR WORKSPACE"}</p>
          <h1>
            {title}
            <span>.</span>
          </h1>
          {description && <p>{description}</p>}
        </div>
        {actions && <div className="workspace-actions">{actions}</div>}
      </header>
    </>
  );
};

export const ActionLink = ({ children, to, secondary = false, ...props }) => (
  <Link
    to={to}
    className={`workspace-button${secondary ? " is-secondary" : ""}`}
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
  className = "",
}) => (
  <section className={`workspace-panel ${className}`}>
    {title && (
      <header className="workspace-panel-heading">
        <div>
          <h2>{title}</h2>
          {description && <p>{description}</p>}
        </div>
        {aside}
      </header>
    )}
    {children}
  </section>
);
export const InfoGrid = ({ items }) => (
  <dl className="workspace-info-grid">
    {items.map(([label, value]) => (
      <div key={label}>
        <dt>{label}</dt>
        <dd>{value === 0 ? "0" : value || "Not provided"}</dd>
      </div>
    ))}
  </dl>
);
export const Badge = ({ children, tone = "neutral" }) => (
  <span className={`workspace-badge is-${tone}`}>
    <span aria-hidden="true" />
    {children}
  </span>
);
export const statusTone = (value) =>
  value === "Paid"
    ? "paid"
    : value === "Not Fully Paid"
      ? "partial"
      : "neutral";

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
  return (
    <div className={`workspace-field ${className}`}>
      <label htmlFor={id}>
        {label}
        {props.required && <span aria-hidden="true"> *</span>}
      </label>
      <Control
        id={id}
        aria-invalid={error ? true : undefined}
        aria-describedby={error || hint ? `${id}-hint` : undefined}
        {...props}
      >
        {children}
      </Control>
      {(error || hint) && (
        <p
          id={`${id}-hint`}
          className={error ? "workspace-field-error" : "workspace-field-hint"}
        >
          {error || hint}
        </p>
      )}
    </div>
  );
};
export const EmptyState = ({ title, text, icon: Icon = FiInbox }) => (
  <div className="workspace-empty">
    <span>
      <Icon aria-hidden="true" />
    </span>
    <h2>{title}</h2>
    <p>{text}</p>
  </div>
);
export const LoadingState = () => (
  <div className="workspace-loading" role="status">
    <span className="dashboard-sr-only">Loading workspace data…</span>
    <div className="dashboard-skeleton" />
    <div className="dashboard-skeleton" />
    <div className="dashboard-skeleton" />
  </div>
);
export const ErrorState = ({ error, retry }) => (
  <div className="dashboard-error" role="alert">
    <FiAlertCircle aria-hidden="true" />
    <div>
      <strong>We couldn’t load this page.</strong>
      <span>{errorMessage(error)}</span>
    </div>
    {retry && (
      <button className="workspace-button is-secondary" onClick={retry}>
        Try again
      </button>
    )}
  </div>
);

export const usePagination = (rows = []) => {
  const [requestedPage, setPage] = useState(0);
  const [size, setSize] = useState(5);
  const last = size === -1 ? 0 : Math.max(0, Math.ceil(rows.length / size) - 1);
  const page = Math.min(requestedPage, last);
  const start = size === -1 ? 0 : page * size;
  return {
    page,
    size,
    last,
    start,
    count: rows.length,
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
    <div className="workspace-pagination">
      <label>
        Rows per page
        <select
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
      <span role="status">
        {count ? start + 1 : 0}–{start + rows.length} of {count}
      </span>
      <div>
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
          },
          {
            label: "Last page",
            icon: FiChevronsRight,
            target: last,
            disabled: page === last,
          },
        ].map(({ label, icon: Icon, target, disabled }) => (
          <button
            key={label}
            type="button"
            className="dashboard-icon-button"
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

export const DataTable = ({
  columns,
  rows,
  label,
  rowKey = (row) => row._id,
}) => (
  <div className="workspace-table-wrap">
    <table className="workspace-table">
      <caption className="dashboard-sr-only">{label}</caption>
      <thead>
        <tr>
          {columns.map((column) => (
            <th key={column.label} scope="col" className={column.className}>
              {column.label}
            </th>
          ))}
        </tr>
      </thead>
      <tbody>
        {rows.map((row, index) => (
          <tr key={rowKey(row, index)}>
            {columns.map((column) => (
              <td
                key={column.label}
                className={column.className}
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

export const ConfirmDialog = ({
  open,
  title,
  children,
  confirmLabel,
  onConfirm,
  onClose,
  busy,
}) => {
  const ref = useRef(null);
  const cancelRef = useRef(null);
  const titleId = useId();
  useEffect(() => {
    const dialog = ref.current;
    if (!open) {
      if (dialog.open) dialog.close();
      return undefined;
    }
    const previousFocus = document.activeElement;
    const overflow = document.body.style.overflow;
    if (!dialog.open) dialog.showModal();
    cancelRef.current?.focus();
    document.body.style.overflow = "hidden";
    return () => {
      if (dialog.open) dialog.close();
      document.body.style.overflow = overflow;
      if (previousFocus?.isConnected) previousFocus.focus();
    };
  }, [open]);
  return (
    <dialog
      ref={ref}
      className="workspace-dialog"
      aria-labelledby={titleId}
      onCancel={(event) => {
        event.preventDefault();
        if (!busy) onClose();
      }}
      onClick={(event) => {
        if (event.target === event.currentTarget && !busy) onClose();
      }}
    >
      <div className="workspace-dialog-body">
        <button
          type="button"
          className="dashboard-icon-button workspace-dialog-close"
          aria-label="Close confirmation"
          onClick={onClose}
          disabled={busy}
        >
          <FiX aria-hidden="true" />
        </button>
        <h2 id={titleId}>{title}</h2>
        <p>{children}</p>
        <div className="workspace-dialog-actions">
          <button
            ref={cancelRef}
            type="button"
            className="workspace-button is-secondary"
            onClick={onClose}
            disabled={busy}
          >
            Cancel
          </button>
          <button
            type="button"
            className="workspace-button is-danger"
            onClick={onConfirm}
            disabled={busy}
          >
            {busy ? "Please wait…" : confirmLabel}
          </button>
        </div>
      </div>
    </dialog>
  );
};
