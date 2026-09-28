const NumberCharacters = ({ text, offset = 0 }) =>
  [...text].map((character, index) =>
    /\d/.test(character) ? (
      <span
        className="dashboard-number-digit"
        style={{ "--digit-delay": `${Math.min(index + offset, 10) * 55}ms` }}
        key={index}
      >
        <span
          className="dashboard-number-reel"
          data-roll={Array.from(
            { length: 4 },
            (_, step) => (Number(character) + step + 6) % 10,
          ).join("\n")}
        >
          {character}
        </span>
      </span>
    ) : (
      <span className="dashboard-number-separator" key={index}>
        {character}
      </span>
    ),
  );

const DashboardNumber = ({ value }) => {
  const text = String(value);
  const hasDecimals = /\.\d{2}$/.test(text);

  return (
    <strong className="dashboard-stat-value" title={text}>
      <span className="dashboard-sr-only">{text}</span>
      {/* Keep the real value available immediately while the decorative reels settle. */}
      <span className="dashboard-number" aria-hidden="true" key={text}>
        <NumberCharacters text={hasDecimals ? text.slice(0, -3) : text} />
        {hasDecimals && (
          <span className="dashboard-stat-decimal">
            <NumberCharacters text={text.slice(-3)} offset={text.length - 3} />
          </span>
        )}
      </span>
    </strong>
  );
};

// Decorative views of the values already shown in text, never extra metrics.
const BalanceGauge = ({ fill }) => (
  <div className="dashboard-balance-gauge" aria-hidden="true">
    <span
      className="dashboard-balance-fill"
      style={{ width: `${fill * 100}%` }}
    />
  </div>
);

export const StatusRings = ({ paid, partial, unpaid, total }) => {
  const counts = [paid, partial, unpaid];
  const valid =
    Number.isFinite(total) &&
    total > 0 &&
    counts.every(
      (count) => Number.isFinite(count) && count >= 0 && count <= total,
    );

  return (
    <svg
      className="dashboard-status-rings"
      viewBox="0 0 210 210"
      aria-hidden="true"
      focusable="false"
    >
      {counts.map((count, index) => (
        <g key={index} className={`dashboard-status-ring is-ring-${index}`}>
          <circle
            className="dashboard-ring-track"
            cx="105"
            cy="105"
            r={88 - index * 23}
          />
          <circle
            className="dashboard-ring-fill"
            cx="105"
            cy="105"
            r={88 - index * 23}
            pathLength="100"
            style={{
              "--ring-offset": valid ? 100 - (count / total) * 100 : 100,
              "--ring-delay": `${index * 140 + 300}ms`,
              opacity: valid && count > 0 ? 1 : 0,
            }}
          />
        </g>
      ))}
      <g className="dashboard-ring-icon">
        <path d="M98 91h11l8 8v19a3 3 0 0 1-3 3H96a3 3 0 0 1-3-3V94a3 3 0 0 1 3-3Z" />
        <path d="M108 92v8h8m-17 7h12m-12 7h9" />
      </g>
    </svg>
  );
};

export const StatCard = ({
  label,
  value,
  detail,
  icon: Icon,
  accent,
  tone,
  fill,
  featured = false,
  variant = "financial",
}) => (
  <div
    className={`dashboard-stat dashboard-stat-${variant}${accent ? " dashboard-stat-accent" : ""}${tone ? ` is-${tone}` : ""}${featured ? " is-featured" : ""}`}
    data-long-value={String(value).length > 12 ? true : undefined}
    data-wide-value={String(value).length > 9 ? true : undefined}
    style={{ "--value-length": String(value).length }}
  >
    <div className="dashboard-stat-label">
      <span>{label}</span>
      <Icon aria-hidden="true" />
    </div>
    <DashboardNumber value={value} />
    {detail && <span className="dashboard-stat-detail">{detail}</span>}
    {Number.isFinite(fill) && fill >= 0 && fill <= 1 && (
      <BalanceGauge fill={fill} />
    )}
  </div>
);

export const DashboardLoading = () => (
  <div
    className="dashboard-loading dashboard-overview-layout"
    role="status"
    aria-label="Loading your business overview"
  >
    <span className="dashboard-sr-only">Loading your business overview…</span>
    <div className="dashboard-financial-stats" aria-hidden="true">
      <div className="dashboard-summary-heading dashboard-skeleton">
        <span className="dashboard-skeleton-line is-heading" />
      </div>
      <div className="dashboard-financial-cards">
        {Array.from({ length: 3 }, (_, index) => (
          <div
            className={`dashboard-skeleton dashboard-stat-financial${index === 0 ? " is-featured" : index === 1 ? " dashboard-stat-accent" : ""}`}
            key={index}
          >
            <span className="dashboard-skeleton-line is-label" />
            <span className="dashboard-skeleton-line is-figure" />
            <span className="dashboard-skeleton-line is-detail" />
          </div>
        ))}
      </div>
    </div>
    <div className="dashboard-workspace-summary" aria-hidden="true">
      <div className="dashboard-summary-heading dashboard-skeleton">
        <span className="dashboard-skeleton-line is-heading" />
        <span className="dashboard-skeleton-line is-label" />
      </div>
      <div className="dashboard-stats dashboard-count-stats">
        <div className="dashboard-workspace-totals">
          {Array.from({ length: 2 }, (_, index) => (
            <div
              className="dashboard-skeleton dashboard-stat-total"
              key={index}
            >
              <span className="dashboard-skeleton-line is-number" />
              <span className="dashboard-skeleton-line is-label" />
            </div>
          ))}
        </div>
      </div>
    </div>
    <div className="dashboard-document-status" aria-hidden="true">
      <div className="dashboard-summary-heading dashboard-skeleton">
        <span className="dashboard-skeleton-line is-heading" />
      </div>
      <div className="dashboard-status-body">
        <StatusRings total={0} paid={0} partial={0} unpaid={0} />
        <div className="dashboard-workspace-statuses">
          {Array.from({ length: 3 }, (_, index) => (
            <div
              className="dashboard-skeleton dashboard-stat-status"
              key={index}
            >
              <span className="dashboard-skeleton-line is-label" />
              <span className="dashboard-skeleton-line is-number" />
            </div>
          ))}
        </div>
      </div>
      <div className="dashboard-overdue dashboard-skeleton">
        <span className="dashboard-skeleton-line is-label" />
      </div>
    </div>
    <div
      className="dashboard-history dashboard-skeleton-history"
      aria-hidden="true"
    >
      <div className="dashboard-panel-header dashboard-skeleton">
        <span className="dashboard-skeleton-line is-heading" />
      </div>
      <div className="dashboard-history-body">
        <div className="dashboard-skeleton-table-heading dashboard-skeleton" />
        {Array.from({ length: 5 }, (_, index) => (
          <div
            className="dashboard-skeleton-payment dashboard-skeleton"
            key={index}
          >
            <span className="dashboard-skeleton-avatar" />
            <span className="dashboard-skeleton-line is-payer" />
            <span className="dashboard-skeleton-line is-date" />
            <span className="dashboard-skeleton-line is-amount" />
          </div>
        ))}
        <div className="dashboard-skeleton-pagination dashboard-skeleton">
          <span className="dashboard-skeleton-line is-label" />
          <span className="dashboard-skeleton-line is-label" />
        </div>
      </div>
    </div>
  </div>
);
