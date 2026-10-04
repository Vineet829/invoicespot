import { Link } from "react-router-dom";
import {
  FiChevronDown,
  FiChevronRight,
  FiChevronUp,
  FiMinus,
} from "react-icons/fi";
import { money } from "../../../components/workspace/Workspace";
import { outstandingLink } from "../../documents/listLinks";
import {
  billedLink,
  changeFrom,
  findPeriod,
  formatMoney,
  invoiceCount,
  percentLabel,
  receivedLink,
  reportLink,
  trendColumns,
  trendPoints,
} from "../dashboardModel";
import { DashboardNumber, tileCard, tileRow } from "./DashboardPanels";

// Each tile's colours: the financial cards' blue, lilac and peach, and sage
// for profit.
const TONES = {
  blue: {
    tile: "bg-card-blue",
    label: "text-card-blue-label",
    ink: "text-card-blue-ink",
    muted: "text-card-blue-muted",
    decimal: "text-card-blue-decimal",
    line: "stroke-card-blue-bar",
    area: "fill-card-blue-bar",
    dot: "bg-card-blue-bar",
  },
  lilac: {
    tile: "bg-card-lilac",
    label: "text-card-lilac-label",
    ink: "text-card-lilac-ink",
    muted: "text-card-lilac-muted",
    decimal: "text-card-lilac-decimal",
    line: "stroke-card-lilac-bar",
    area: "fill-card-lilac-bar",
    dot: "bg-card-lilac-bar",
  },
  sage: {
    tile: "bg-card-sage",
    label: "text-card-sage-label",
    ink: "text-card-sage-ink",
    muted: "text-card-sage-muted",
    decimal: "text-card-sage-decimal",
  },
  peach: {
    tile: "bg-card-peach",
    label: "text-card-peach-label",
    ink: "text-card-peach-ink",
    muted: "text-card-peach-muted",
    decimal: "text-card-peach-decimal",
  },
};

const CHANGES = {
  up: { icon: FiChevronUp, color: "text-change-up", words: "up" },
  down: { icon: FiChevronDown, color: "text-change-down", words: "down" },
  flat: { icon: FiMinus, color: "text-spot-muted", words: "no change" },
};
// A change in words: "up 29.1%", or "no change".
const changeWords = (change) =>
  change.direction === "flat"
    ? CHANGES.flat.words
    : `${CHANGES[change.direction].words} ${percentLabel(change.percent)}`;

// How a figure moved against the period before: an arrow and the size of
// the change, green when it rose and rust when it fell, with the
// comparison named beside it.
const Change = ({ change, compared }) => {
  const { icon: Icon, color, words } = CHANGES[change.direction];
  return (
    <span className="flex flex-wrap items-center gap-x-2 gap-y-1">
      <span
        className={`inline-flex items-center gap-[3px] rounded-full bg-white/80 py-[3px] pl-[5px] pr-2 text-[12.5px] font-medium leading-[1.3] ${color}`}
      >
        <Icon aria-hidden="true" className="size-3.5 stroke-[2.2]" />
        <span className="sr-only">{words} </span>
        {percentLabel(change.percent)}
      </span>
      <span className="text-[12px]">{compared}</span>
    </span>
  );
};

// A trend across the period as a line over a light wash, with a dot on the
// latest figure.
const Sparkline = ({ values, colors }) => {
  const points = trendPoints(values);
  if (!points.length) return null;
  const line = points.map(({ x, y }) => `${x},${y}`).join(" ");
  const end = points.at(-1);
  return (
    <span className="relative block h-10" aria-hidden="true">
      <span className="absolute inset-x-0 inset-y-1">
        <svg
          className="absolute inset-0 size-full overflow-visible"
          viewBox="0 0 100 100"
          preserveAspectRatio="none"
          focusable="false"
        >
          <polygon
            className={`${colors.area} opacity-[.12]`}
            points={`${points[0].x},100 ${line} ${end.x},100`}
          />
          <polyline
            className={colors.line}
            points={line}
            fill="none"
            strokeWidth="2"
            strokeLinejoin="round"
            strokeLinecap="round"
            vectorEffect="non-scaling-stroke"
          />
        </svg>
        <span
          className={`absolute -ml-[5.5px] -mt-[5.5px] size-[11px] rounded-full border-2 border-white ${colors.dot}`}
          style={{ left: `${end.x}%`, top: `${end.y}%` }}
        />
      </span>
    </span>
  );
};

// Profit in each part of the period as columns: profit stands on zero and
// a loss hangs below it, in a darker rust. The latest column is solid.
const TrendColumns = ({ values }) => {
  const { zero, columns } = trendColumns(values);
  return (
    <span
      className={`relative flex h-10 ${columns.length > 16 ? "gap-0.5" : "gap-1"}`}
      aria-hidden="true"
    >
      {columns.map((column, index) => (
        <span className="relative flex-1" key={index}>
          {column.height > 0 && (
            <span
              className={`absolute inset-x-0 ${column.value < 0 ? "rounded-b-[3px] bg-card-sage-loss" : "rounded-t-[3px] bg-card-sage-bar"} ${index < columns.length - 1 ? "opacity-[.55]" : ""}`}
              style={{ top: `${column.top}%`, height: `${column.height}%` }}
            />
          )}
        </span>
      ))}
      {zero < 100 && (
        <span
          className="absolute inset-x-0 border-t border-card-sage-ink/25"
          style={{ top: `${zero}%` }}
        />
      )}
    </span>
  );
};

// What is owed now, split into what is not due yet and what is overdue.
const OwedSplit = ({ owed, currency }) => {
  const parts = [
    ["notDue", "bg-owed-due"],
    ["overdue", "bg-owed-overdue"],
  ].filter(([key]) => Number(owed[key]) > 0);
  return (
    <span className="flex flex-col gap-[7px]">
      <span
        className={`flex h-3 gap-0.5 overflow-hidden rounded-full ${parts.length ? "" : "bg-white/50"}`}
        aria-hidden="true"
      >
        {parts.map(([key, color]) => (
          <span
            className={`min-w-1 ${color}`}
            key={key}
            style={{ flex: `${Number(owed[key])} 1 0` }}
          />
        ))}
      </span>
      <span className="flex flex-wrap justify-between gap-x-3 text-[12px]">
        <span>Not due {formatMoney(owed.notDue, currency)}</span>
        <span>Overdue {formatMoney(owed.overdue, currency)}</span>
      </span>
    </span>
  );
};

// A tile is a link to the records behind its figure. Its name for assistive
// technology carries the figure, the change and where it leads; the
// figure's size follows the tile's width, so long amounts still fit.
const Tile = ({ to, label, title, value, tone, line, chart }) => {
  const colors = TONES[tone];
  return (
    <Link
      to={to}
      aria-label={label}
      className={`${tileCard} transition-shadow [container-type:inline-size] hover:shadow-float ${colors.tile}`}
      style={{ "--chars": value.length }}
    >
      <span className="flex items-center justify-between gap-2">
        <span
          className={`rounded-full bg-white/70 px-[11px] py-[5px] text-[12.5px] leading-[1.25] ${colors.label}`}
        >
          {title}
        </span>
        <FiChevronRight
          aria-hidden="true"
          className={`size-[15px] stroke-[1.5] ${colors.muted}`}
        />
      </span>
      <span className={colors.ink}>
        <DashboardNumber
          value={value}
          className="[font-size:min(40px,calc(100cqi_/_(var(--chars)_*_0.5)))]"
          decimalClass={colors.decimal}
        />
      </span>
      {line && <span className={colors.muted}>{line}</span>}
      <span className="mt-auto block pt-1">{chart}</span>
    </Link>
  );
};

// The period's billed, received and profit, each against the period before
// when there was one, and what is owed today. Each opens the records behind
// it for the same period, profit the profit and loss report, in the same
// currency when one was chosen.
const OverviewTiles = ({ snapshot, currencyChosen }) => {
  const { currency, totals, owedNow, trend } = snapshot;
  const period = findPeriod(snapshot.period.key);
  const chosen = currencyChosen ? currency : undefined;
  const figure = (key, title, opens) => {
    const { current, previous } = totals[key];
    const change = totals.hasPrevious ? changeFrom(current, previous) : null;
    return {
      value: formatMoney(current, currency),
      label: `${title} ${period.during}: ${money(current, currency)}${change ? `, ${changeWords(change)} ${period.compared}` : ""}. View ${opens}`,
      line: change && <Change change={change} compared={period.compared} />,
    };
  };
  const owedLine = owedNow.invoices
    ? `${invoiceCount(owedNow.invoices)} · ${owedNow.overdueInvoices ? owedNow.overdueInvoices.toLocaleString("en") : "none"} overdue`
    : "No unpaid invoices";
  const owedLabel = owedNow.invoices
    ? `${money(owedNow.total, currency)} on ${invoiceCount(owedNow.invoices)}, ${owedNow.overdueInvoices ? `${money(owedNow.overdue, currency)} of it overdue on ${invoiceCount(owedNow.overdueInvoices)}` : "none of it overdue"}`
    : `${money(owedNow.total, currency)}, no unpaid invoices`;
  return (
    <section aria-labelledby="figures-title">
      <h2 id="figures-title" className="sr-only">
        Figures {period.during}
      </h2>
      <div className={tileRow}>
        <Tile
          to={billedLink(period.key, chosen)}
          title="Billed"
          tone="blue"
          {...figure(
            "billed",
            "Billed",
            "the invoices, receipts and credit notes",
          )}
          chart={
            <Sparkline
              values={trend.map((bucket) => bucket.billed)}
              colors={TONES.blue}
            />
          }
        />
        <Tile
          to={receivedLink(period.key, chosen)}
          title="Received"
          tone="lilac"
          {...figure("received", "Received", "the payments")}
          chart={
            <Sparkline
              values={trend.map((bucket) => bucket.received)}
              colors={TONES.lilac}
            />
          }
        />
        <Tile
          to={reportLink("profit", period.key, chosen)}
          title="Profit"
          tone="sage"
          {...figure("profit", "Profit", "the profit and loss report")}
          chart={
            <TrendColumns
              values={trend.map(
                (bucket) => Number(bucket.income) - Number(bucket.expenses),
              )}
            />
          }
        />
        <Tile
          to={outstandingLink(chosen)}
          title="Outstanding now"
          tone="peach"
          value={formatMoney(owedNow.total, currency)}
          label={`Outstanding now: ${owedLabel}. View the invoices owed, soonest due first`}
          line={<span className="text-[12.5px]">{owedLine}</span>}
          chart={<OwedSplit owed={owedNow} currency={currency} />}
        />
      </div>
    </section>
  );
};

export default OverviewTiles;
