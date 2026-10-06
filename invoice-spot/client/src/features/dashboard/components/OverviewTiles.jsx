import { Link } from "react-router-dom";
import {
  FiChevronDown,
  FiChevronRight,
  FiChevronUp,
  FiMinus,
} from "react-icons/fi";
import { money } from "../../../components/workspace/Workspace";
import {
  billedLink,
  changeFrom,
  findPeriod,
  formatMoney,
  percentLabel,
  receivedLink,
  reportLink,
  trendColumns,
  trendPoints,
} from "../dashboardModel";
import {
  DashboardNumber,
  figureRow,
  financeCard,
  financeFigure,
  financeLabel,
  financeTones,
} from "./DashboardPanels";

// The colours of the tiles in the homepage's picture of the overview: the
// financial cards' blue, lilac and peach, and sage for profit. The homepage
// draws its picture with these and the parts below, at a smaller size; the
// overview's own cards take these for their trends.
export const TONES = {
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
// latest figure. The class sets its height.
export const Sparkline = ({ values, colors, className = "h-10" }) => {
  const points = trendPoints(values);
  if (!points.length) return null;
  const line = points.map(({ x, y }) => `${x},${y}`).join(" ");
  const end = points.at(-1);
  return (
    <span className={`relative block ${className}`} aria-hidden="true">
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
// a loss hangs below it, in a darker rust. The latest column is solid. The
// class sets its height.
export const TrendColumns = ({ values, className = "h-10" }) => {
  const { zero, columns } = trendColumns(values);
  return (
    <span
      className={`relative flex ${className} ${columns.length > 16 ? "gap-0.5" : "gap-1"}`}
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
// The class sets its text size, which the bar follows, and its spacing.
export const OwedSplit = ({
  owed,
  currency,
  className = "gap-[7px] text-[12px]",
}) => {
  const parts = [
    ["notDue", "bg-owed-due"],
    ["overdue", "bg-owed-overdue"],
  ].filter(([key]) => Number(owed[key]) > 0);
  return (
    <span className={`flex flex-col ${className}`}>
      <span
        className={`flex h-[1em] gap-0.5 overflow-hidden rounded-full ${parts.length ? "" : "bg-white/50"}`}
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
      <span className="flex flex-wrap justify-between gap-x-3">
        <span>Not due {formatMoney(owed.notDue, currency)}</span>
        <span>Overdue {formatMoney(owed.overdue, currency)}</span>
      </span>
    </span>
  );
};

// A figure card is a link to the view behind its figure, as large as the
// Reports page's cards. Its name for assistive technology carries the
// figure, what it includes, the change and where it leads. The figure and
// the line under it keep to the top, and the change and the trend to the
// foot, so all three cards line up whatever each has under its figure.
const FigureCard = ({ to, label, title, value, tone, detail, line, chart }) => {
  const colors = financeTones[tone];
  return (
    <Link
      to={to}
      aria-label={label}
      className={`${financeCard(tone)} transition-shadow hover:shadow-float`}
      style={{ "--chars": value.length }}
    >
      <span className="flex items-center justify-between gap-2.5">
        <span className={financeLabel(tone)}>{title}</span>
        <FiChevronRight
          aria-hidden="true"
          className={`size-[18px] stroke-[1.5] ${colors.muted}`}
        />
      </span>
      <span className={`flex flex-col gap-2 ${colors.ink}`}>
        <DashboardNumber
          value={value}
          className={financeFigure}
          decimalClass={colors.decimal}
        />
        {detail && (
          <span
            className={`text-[15px] [@container_finance-card_(min-width:162px)]:text-[16px] ${colors.muted}`}
          >
            {detail}
          </span>
        )}
      </span>
      <span className={`mt-auto flex flex-col gap-3 pt-1 ${colors.muted}`}>
        {line}
        {chart}
      </span>
    </Link>
  );
};

// The period's billed, received and profit, each against the period before
// when there was one, and the tax billed includes. Each opens the view that
// explains it, for the same period, in the same currency when one was
// chosen: billed the Documents tab's Sales, received the Payments list with
// its summary, and profit the Reports page's profit and loss.
const OverviewTiles = ({ snapshot, currencyChosen }) => {
  const { currency, totals, trend } = snapshot;
  const period = findPeriod(snapshot.period.key);
  const chosen = currencyChosen ? currency : undefined;
  const figure = (key, title, opens, includes = "") => {
    const { current, previous } = totals[key];
    const change = totals.hasPrevious ? changeFrom(current, previous) : null;
    return {
      value: formatMoney(current, currency),
      label: `${title} ${period.during}: ${money(current, currency)}${includes}${change ? `, ${changeWords(change)} ${period.compared}` : ""}. View ${opens}`,
      line: change && <Change change={change} compared={period.compared} />,
    };
  };
  // An older server's answer has no tax.
  const taxed = totals.tax != null;
  return (
    <section aria-labelledby="figures-title">
      <h2 id="figures-title" className="sr-only">
        Figures {period.during}
      </h2>
      <div className={figureRow}>
        <FigureCard
          to={billedLink(period.key, chosen)}
          title="Billed"
          tone="blue"
          {...figure(
            "billed",
            "Billed",
            "the sales",
            taxed ? `, including ${money(totals.tax, currency)} tax` : "",
          )}
          detail={taxed && `includes ${formatMoney(totals.tax, currency)} tax`}
          chart={
            <Sparkline
              values={trend.map((bucket) => bucket.billed)}
              colors={TONES.blue}
            />
          }
        />
        <FigureCard
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
        <FigureCard
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
      </div>
    </section>
  );
};

export default OverviewTiles;
